package com.elsco.mindwaymaths.data.repository

import androidx.room.withTransaction
import com.google.firebase.firestore.SetOptions
import com.elsco.mindwaymaths.data.local.*
import com.elsco.mindwaymaths.data.remote.*
import com.elsco.mindwaymaths.domain.model.*
import com.elsco.mindwaymaths.domain.repository.*
import com.elsco.mindwaymaths.domain.usecase.LearningEngine
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.time.*
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton class OfflineLearningRepository @Inject constructor(
    private val database: MindwayDatabase, private val dao: LearningDao, private val remote: FirestoreContentSource,
    private val firebase: FirebaseProvider, private val auth: AuthRepository, private val json: Json,
    private val engine: LearningEngine,
) : LearningRepository {
    private val mutation = Mutex()
    private val syncMutex = Mutex()
    private fun uid() = requireNotNull(auth.uid) { "Sign in to continue" }
    override fun catalog(collection: String) = dao.catalog(collection).map { rows -> rows.mapNotNull { runCatching { json.decodeFromString<CatalogItem>(it.payload) }.getOrNull() } }
    override fun videos() = dao.catalog("videos").map { rows -> rows.mapNotNull { runCatching { json.decodeFromString<VideoLesson>(it.payload) }.getOrNull() } }
    override fun pdfs() = dao.catalog("pdfs").map { rows -> rows.mapNotNull { runCatching { json.decodeFromString<PdfMaterial>(it.payload) }.getOrNull() } }
    override suspend fun refreshCatalog(collection: String, nextPage: Boolean): Boolean {
        require(collection in setOf("exams", "subjects", "topics", "videos", "pdfs", "dailyChallenges", "tests"))
        val page = remote.page(collection, next = nextPage)
        dao.removeCatalog(collection, page.filter { it.second["isActive"]?.jsonPrimitive?.booleanOrNull == false }.map { it.first })
        dao.catalog(page.filter { it.second["isActive"]?.jsonPrimitive?.booleanOrNull != false }.map { (id, data) ->
            CatalogEntity(collection, id, data["order"]?.jsonPrimitive?.intOrNull ?: data["sortOrder"]?.jsonPrimitive?.intOrNull ?: 0, data.toString())
        })
        return page.size == 50
    }
    override suspend fun refreshQuestions(filter: PracticeFilter, nextPage: Boolean): Boolean {
        val page = remote.page("questions", filter, nextPage)
        dao.removeQuestions(page.filter { it.second["isActive"]?.jsonPrimitive?.booleanOrNull == false }.map { it.first })
        dao.questions(page.mapNotNull { (_, data) -> runCatching { json.decodeFromJsonElement<Question>(data) }.getOrNull() }
            .filter { it.isValid() && it.isActive }.map { QuestionEntity(it.id, it.exam, it.subject, it.topic, it.difficulty, it.type.name, it.year, json.encodeToString(it)) })
        return page.size == 50
    }
    override suspend fun createSession(filter: PracticeFilter, config: TestConfig?): StudySession {
        val uid = uid()
        val now = System.currentTimeMillis()
        val mastery = dao.allMastery(uid).associate { it.questionId to json.decodeFromString<Mastery>(it.payload) }
        val bookmarks = dao.bookmarks(uid).first().filter { it.active }.map { it.questionId }.toSet()
        var questions = dao.questions(filter.exam, filter.subject, filter.topic, filter.difficulty, filter.type).map { json.decodeFromString<Question>(it.payload) }
        questions = questions.filter { q -> when (filter.mode) {
            PracticeMode.WEAK -> (mastery[q.id]?.score ?: 100) <= 30
            PracticeMode.INCORRECT -> (mastery[q.id]?.mistakes ?: 0) > 0 && (mastery[q.id]?.score ?: 0) <= 60
            PracticeMode.BOOKMARKS -> q.id in bookmarks
            PracticeMode.PREVIOUS_YEAR -> q.year != null
            PracticeMode.REVISION -> mastery[q.id]?.nextDue?.let { it <= now } == true
            else -> true
        } }
        val weakTopics = mastery.values.groupBy { it.topic }.filterValues { list -> list.map { it.score }.average() <= 30 }.keys
        questions = when (filter.mode) {
            PracticeMode.RANDOM, PracticeMode.SPEED -> questions.shuffled()
            PracticeMode.DAILY -> questions.shuffled(kotlin.random.Random(LocalDate.now(ZoneId.of("Asia/Kolkata")).toEpochDay().toInt()))
            else -> questions.sortedByDescending { engine.priority(mastery[it.id], now, it.topic in weakTopics) }
        }.take(config?.count ?: 20)
        require(questions.isNotEmpty()) { "No saved questions match these filters. Download a topic first, or choose another mode." }
        val session = StudySession(UUID.randomUUID().toString(), uid, questions, config, filter.mode, now,
            config?.let { now + it.durationMinutes * 60_000L })
        saveSession(session)
        return session
    }
    override fun session(id: String) = dao.session(id, uid()).map { it?.let { json.decodeFromString<StudySession>(it.payload) } }
    override suspend fun saveSession(session: StudySession) {
        require(session.uid == uid())
        dao.session(SessionEntity(session.id, session.uid, session.completed, System.currentTimeMillis(), json.encodeToString(session)))
    }
    private suspend fun record(session: StudySession, question: Question, answer: Answer, now: Long) {
        val number = dao.attemptCount(session.uid, question.id) + 1
        val attempt = Attempt("${session.id}_${question.id}", question.id, session.uid, question.topic, answer.option,
            answer.numerical, engine.correct(question, answer), answer.timeMillis.coerceIn(0, 10_800_000), number, now)
        val day = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate().toString()
        val inserted = dao.attempt(AttemptEntity(attempt.id, attempt.uid, question.id, question.topic, attempt.correct,
            attempt.timeTaken, now, day, json.encodeToString(attempt)))
        if (inserted == -1L) return // Crash/retry-safe exactly once per session question.
        val previous = dao.mastery(session.uid, question.id)?.let { json.decodeFromString<Mastery>(it.payload) } ?: Mastery(question.id, question.topic)
        val updated = engine.updateMastery(previous, attempt.correct, attempt.timeTaken, now)
        dao.mastery(MasteryEntity(session.uid, question.id, question.topic, updated.score, updated.mistakes, json.encodeToString(updated)))
    }
    override suspend fun submitAnswer(session: StudySession): StudySession = mutation.withLock {
        require(session.uid == uid() && session.config == null)
        val question = session.questions[session.index]
        val answer = session.answers[question.id]
        require(engine.answered(answer)) { "Choose an answer first" }
        if (question.id in session.submittedQuestions) return@withLock session
        val updated = session.copy(submittedQuestions = session.submittedQuestions + question.id)
        database.withTransaction { record(session, question, requireNotNull(answer), System.currentTimeMillis()); saveSession(updated) }
        updated
    }
    override suspend fun finishTest(session: StudySession): TestResult = mutation.withLock {
        require(session.uid == uid())
        dao.history(session.uid).first().find { it.id == session.id }?.let { return@withLock json.decodeFromString<TestResult>(it.payload) }
        val now = System.currentTimeMillis()
        val result = engine.score(session, now)
        database.withTransaction {
            session.questions.forEach { question -> session.answers[question.id]?.takeIf { engine.answered(it) }?.let { record(session, question, it, now) } }
            dao.result(ResultEntity(session.id, session.uid, now, json.encodeToString(result)))
            saveSession(session.copy(completed = true))
        }
        result
    }
    override fun history() = dao.history(uid()).map { it.map { row -> json.decodeFromString<TestResult>(row.payload) } }
    override fun bookmarkIds() = dao.bookmarks(uid()).map { rows -> rows.filter { it.active }.map { it.questionId }.toSet() }
    override suspend fun toggleBookmark(questionId: String) {
        val uid = uid()
        val old = dao.bookmarks(uid).first().firstOrNull { it.questionId == questionId }
        dao.bookmark(BookmarkEntity(uid, questionId, old?.active != true, System.currentTimeMillis()))
    }
    override fun progress(): Flow<ProgressSummary> {
        val uid = uid()
        val base = combine(dao.stats(uid), dao.days(uid), dao.topicMastery(uid)) { stats, days, topics ->
            val today = LocalDate.now()
            val day = days.firstOrNull { it.day == today.toString() }
            ProgressSummary(stats.solved, stats.correct, stats.averageMillis, day?.count ?: 0, day?.correct ?: 0,
                engine.streak(days.map { LocalDate.parse(it.day) }.toSet(), today), dailyActivity = days.associate { it.day to it.count },
                topicMastery = topics.associate { it.topic to it.score })
        }
        return combine(base, dao.incorrectCount(uid), dao.bookmarks(uid), dao.testCount(uid)) { summary, incorrect, bookmarks, tests ->
            summary.copy(incorrect = incorrect, bookmarks = bookmarks.count { it.active }, tests = tests)
        }
    }
    override suspend fun latestSession() = dao.latestSession(uid())?.let { json.decodeFromString<StudySession>(it.payload) }

    override suspend fun sync() = syncMutex.withLock {
        val uid = uid()
        val root = firebase.store.collection("users").document(uid)
        if (root.get(com.google.firebase.firestore.Source.SERVER).await().getBoolean("deletionPending") == true) return@withLock
        val attempts = dao.pendingAttempts(uid)
        val results = dao.pendingResults(uid)
        val bookmarks = dao.pendingBookmarks(uid)
        val remoteResults = results.associate { row ->
            val snapshot = dao.session(row.id, uid).first()?.payload
            row.id to (jsonMap(row.payload) + mapOf("payload" to row.payload,
                "snapshot" to (snapshot?.let(::compressSnapshot) ?: ""), "receivedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()))
        }
        check(auth.uid == uid)
        firebase.store.runBatch { batch ->
            attempts.forEach { batch.set(root.collection("attempts").document(it.id), jsonMap(it.payload) + ("receivedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp())) }
            results.forEach { batch.set(root.collection("testHistory").document(it.id), remoteResults.getValue(it.id)) }
        }.await()
        dao.markAttemptsSynced(attempts.map { it.id }); dao.markResultsSynced(results.map { it.id })
        for (bookmark in bookmarks) {
            val ref = root.collection("bookmarks").document(bookmark.questionId)
            firebase.store.runTransaction { transaction ->
                val remote = transaction.get(ref)
                if ((remote.getLong("updatedAt") ?: 0) <= bookmark.updatedAt) transaction.set(ref, mapOf(
                    "questionId" to bookmark.questionId, "active" to bookmark.active, "updatedAt" to bookmark.updatedAt))
            }.await()
            dao.markBookmarkSynced(uid, bookmark.questionId, bookmark.updatedAt)
        }
        // Ordered cursor pages restore the entire history over bounded WorkManager runs.
        // Server receive time, not device attempt time, prevents late offline uploads being skipped.
        var attemptQuery = root.collection("attempts").orderBy("receivedAt").orderBy(com.google.firebase.firestore.FieldPath.documentId()).limit(100)
        dao.cursor(uid, "attempts")?.let { attemptQuery = attemptQuery.startAfter(com.google.firebase.Timestamp(java.util.Date(it.timestamp)), it.documentId) }
        val remoteAttempts = attemptQuery.get(com.google.firebase.firestore.Source.SERVER).await()
        database.withTransaction {
            remoteAttempts.documents.forEach { doc ->
                val qid = doc.getString("questionId") ?: return@forEach
                val topic = doc.getString("topic") ?: return@forEach
                val timestamp = doc.getLong("timestamp") ?: return@forEach
                val a = Attempt(doc.id, qid, uid, topic, doc.getLong("selectedOption")?.toInt(), doc.getString("numericalAnswer"),
                    doc.getBoolean("correct") == true, doc.getLong("timeTaken") ?: 0, doc.getLong("attemptNumber")?.toInt() ?: 1, timestamp)
                val inserted = dao.attempt(AttemptEntity(a.id, uid, qid, topic, a.correct, a.timeTaken, timestamp,
                    Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate().toString(), json.encodeToString(a), true))
                if (inserted != -1L) {
                    val old = dao.mastery(uid, qid)?.let { json.decodeFromString<Mastery>(it.payload) } ?: Mastery(qid, topic)
                    val m = engine.updateMastery(old, a.correct, a.timeTaken, timestamp)
                    dao.mastery(MasteryEntity(uid, qid, topic, m.score, m.mistakes, json.encodeToString(m)))
                }
            }
            remoteAttempts.documents.lastOrNull()?.let { doc ->
                dao.cursor(SyncCursor(uid, "attempts", doc.getTimestamp("receivedAt")!!.toDate().time, doc.id))
            }
        }
        var bookmarkQuery = root.collection("bookmarks").orderBy("updatedAt").orderBy(com.google.firebase.firestore.FieldPath.documentId()).limit(100)
        dao.cursor(uid, "bookmarks")?.let { bookmarkQuery = bookmarkQuery.startAfter(it.timestamp, it.documentId) }
        val bookmarkPage = bookmarkQuery.get(com.google.firebase.firestore.Source.SERVER).await()
        val localBookmarks = dao.bookmarks(uid).first().associateBy { it.questionId }
        bookmarkPage.documents.forEach { doc ->
            val local = localBookmarks[doc.id]
            val version = doc.getLong("updatedAt") ?: 0
            if (local == null || local.updatedAt <= version) dao.bookmark(BookmarkEntity(uid, doc.id, doc.getBoolean("active") == true, version, true))
        }
        bookmarkPage.documents.lastOrNull()?.let { dao.cursor(SyncCursor(uid, "bookmarks", it.getLong("updatedAt") ?: 0, it.id)) }
        var historyQuery = root.collection("testHistory").orderBy("receivedAt").orderBy(com.google.firebase.firestore.FieldPath.documentId()).limit(50)
        dao.cursor(uid, "testHistory")?.let { historyQuery = historyQuery.startAfter(com.google.firebase.Timestamp(java.util.Date(it.timestamp)), it.documentId) }
        val historyPage = historyQuery.get(com.google.firebase.firestore.Source.SERVER).await()
        historyPage.documents.forEach { doc ->
            val payload = doc.getString("payload") ?: return@forEach
            val restored = runCatching { json.decodeFromString<TestResult>(payload) }.getOrNull() ?: return@forEach
            if (restored.uid == uid) dao.result(ResultEntity(doc.id, uid, restored.timestamp, payload, true))
            doc.getString("snapshot")?.takeIf { it.isNotBlank() }?.let { compressed ->
                val session = runCatching { json.decodeFromString<StudySession>(decompressSnapshot(compressed)) }.getOrNull()
                if (session?.uid == uid && session.id == doc.id && session.completed) dao.session(SessionEntity(session.id, uid, true, restored.timestamp, json.encodeToString(session)))
            }
        }
        historyPage.documents.lastOrNull()?.let { dao.cursor(SyncCursor(uid, "testHistory", it.getTimestamp("receivedAt")!!.toDate().time, it.id)) }
        val summary = progress().first()
        firebase.store.collection("progress").document(uid).set(mapOf("totalQuestionsSolved" to summary.solved,
            "totalCorrect" to summary.correct, "totalTests" to summary.tests, "streak" to summary.streak,
            "updatedAt" to System.currentTimeMillis()), SetOptions.merge()).await()
        root.set(mapOf("totalQuestionsSolved" to summary.solved, "totalCorrect" to summary.correct,
            "totalTests" to summary.tests, "streak" to summary.streak), SetOptions.merge()).await()
        if (attempts.size == 100 || results.size == 8 || bookmarks.size == 100 || remoteAttempts.size() == 100 || historyPage.size() == 50 || bookmarkPage.size() == 100) {
            throw MoreSyncPagesException()
        }
    }
    private fun jsonMap(payload: String): Map<String, Any?> {
        fun convert(e: JsonElement): Any? = when (e) {
            JsonNull -> null
            is JsonObject -> e.mapValues { convert(it.value) }
            is JsonArray -> e.map { convert(it) }
            is JsonPrimitive -> if (e.isString) e.content else e.booleanOrNull ?: e.longOrNull ?: e.doubleOrNull ?: e.content
        }
        return json.parseToJsonElement(payload).jsonObject.mapValues { convert(it.value) }
    }
    private fun compressSnapshot(payload: String): String {
        val output = java.io.ByteArrayOutputStream()
        java.util.zip.GZIPOutputStream(output).use { it.write(payload.toByteArray(Charsets.UTF_8)) }
        val result = java.util.Base64.getEncoder().encodeToString(output.toByteArray())
        require(result.length < 800_000) { "Test snapshot too large" }
        return result
    }
    private fun decompressSnapshot(payload: String): String {
        require(payload.length < 800_000)
        val stream = java.util.zip.GZIPInputStream(java.io.ByteArrayInputStream(java.util.Base64.getDecoder().decode(payload)))
        return stream.use {
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val n = it.read(buffer); if (n < 0) break
                require(output.size() + n <= 4_000_000)
                output.write(buffer, 0, n)
            }
            val bytes = output.toByteArray()
            require(bytes.size <= 4_000_000)
            bytes.toString(Charsets.UTF_8)
        }
    }
    override suspend fun clearUser(uid: String) = database.withTransaction {
        dao.deleteAttempts(uid); dao.deleteMastery(uid); dao.deleteBookmarks(uid); dao.deleteSessions(uid); dao.deleteResults(uid); dao.deleteCursors(uid)
    }
}
class MoreSyncPagesException : Exception()
