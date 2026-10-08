package com.elsco.mindwaymaths.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "questions", indices = [Index(value = ["exam", "subject", "topic"])])
data class QuestionEntity(@PrimaryKey val id: String, val exam: String, val subject: String, val topic: String,
    val difficulty: String, val type: String, val year: Int?, val payload: String)
@Entity(tableName = "catalog", primaryKeys = ["collection", "id"])
data class CatalogEntity(val collection: String, val id: String, val sortOrder: Int, val payload: String)
@Entity(tableName = "attempts", indices = [Index(value = ["uid", "timestamp"]), Index(value = ["uid", "questionId"])])
data class AttemptEntity(@PrimaryKey val id: String, val uid: String, val questionId: String, val topic: String,
    val correct: Boolean, val timeTaken: Long, val timestamp: Long, val day: String, val payload: String, val synced: Boolean = false)
@Entity(tableName = "mastery", primaryKeys = ["uid", "questionId"])
data class MasteryEntity(val uid: String, val questionId: String, val topic: String, val score: Int, val mistakes: Int, val payload: String)
@Entity(tableName = "bookmarks", primaryKeys = ["uid", "questionId"])
data class BookmarkEntity(val uid: String, val questionId: String, val active: Boolean, val updatedAt: Long, val synced: Boolean = false)
@Entity(tableName = "sessions", indices = [Index(value = ["uid", "updatedAt"])])
data class SessionEntity(@PrimaryKey val id: String, val uid: String, val completed: Boolean, val updatedAt: Long, val payload: String)
@Entity(tableName = "results", indices = [Index(value = ["uid", "timestamp"])])
data class ResultEntity(@PrimaryKey val id: String, val uid: String, val timestamp: Long, val payload: String, val synced: Boolean = false)
data class AttemptStats(val solved: Int, val correct: Int, val averageMillis: Long)
data class DayActivity(val day: String, val count: Int, val correct: Int)
data class TopicMastery(val topic: String, val score: Int)
@Entity(tableName = "sync_cursors", primaryKeys = ["uid", "collection"])
data class SyncCursor(val uid: String, val collection: String, val timestamp: Long, val documentId: String)

@Dao interface LearningDao {
    @Query("SELECT * FROM sync_cursors WHERE uid = :uid AND collection = :collection") suspend fun cursor(uid: String, collection: String): SyncCursor?
    @Upsert suspend fun cursor(cursor: SyncCursor)
    @Query("DELETE FROM sync_cursors WHERE uid = :uid") suspend fun deleteCursors(uid: String)
    @Upsert suspend fun questions(items: List<QuestionEntity>)
    @Query("DELETE FROM questions WHERE id IN (:ids)") suspend fun removeQuestions(ids: List<String>)
    @Query("SELECT * FROM questions WHERE (:exam = '' OR exam = :exam) AND (:subject = '' OR subject = :subject) AND (:topic = '' OR topic = :topic) AND (:difficulty = '' OR difficulty = :difficulty) AND (:type = '' OR type = :type) LIMIT 1000")
    suspend fun questions(exam: String, subject: String, topic: String, difficulty: String, type: String): List<QuestionEntity>
    @Upsert suspend fun catalog(items: List<CatalogEntity>)
    @Query("DELETE FROM catalog WHERE collection = :collection AND id IN (:ids)") suspend fun removeCatalog(collection: String, ids: List<String>)
    @Query("SELECT * FROM catalog WHERE collection = :collection ORDER BY sortOrder, id") fun catalog(collection: String): Flow<List<CatalogEntity>>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun attempt(item: AttemptEntity): Long
    @Query("SELECT * FROM attempts WHERE uid = :uid AND synced = 0 LIMIT 100") suspend fun pendingAttempts(uid: String): List<AttemptEntity>
    @Query("UPDATE attempts SET synced = 1 WHERE id IN (:ids)") suspend fun markAttemptsSynced(ids: List<String>)
    @Query("SELECT COUNT(*) FROM attempts WHERE uid = :uid AND questionId = :questionId") suspend fun attemptCount(uid: String, questionId: String): Int
    @Query("SELECT COUNT(*) AS solved, COALESCE(SUM(correct), 0) AS correct, COALESCE(AVG(timeTaken), 0) AS averageMillis FROM attempts WHERE uid = :uid") fun stats(uid: String): Flow<AttemptStats>
    @Query("SELECT day, COUNT(*) AS count, SUM(correct) AS correct FROM attempts WHERE uid = :uid GROUP BY day ORDER BY day DESC LIMIT 366") fun days(uid: String): Flow<List<DayActivity>>
    @Upsert suspend fun mastery(item: MasteryEntity)
    @Query("SELECT * FROM mastery WHERE uid = :uid AND questionId = :id") suspend fun mastery(uid: String, id: String): MasteryEntity?
    @Query("SELECT * FROM mastery WHERE uid = :uid") suspend fun allMastery(uid: String): List<MasteryEntity>
    @Query("SELECT topic, AVG(score) AS score FROM mastery WHERE uid = :uid GROUP BY topic") fun topicMastery(uid: String): Flow<List<TopicMastery>>
    @Query("SELECT COUNT(*) FROM mastery WHERE uid = :uid AND mistakes > 0 AND score <= 60") fun incorrectCount(uid: String): Flow<Int>
    @Upsert suspend fun bookmark(item: BookmarkEntity)
    @Query("SELECT * FROM bookmarks WHERE uid = :uid") fun bookmarks(uid: String): Flow<List<BookmarkEntity>>
    @Query("SELECT * FROM bookmarks WHERE uid = :uid AND synced = 0 LIMIT 100") suspend fun pendingBookmarks(uid: String): List<BookmarkEntity>
    @Query("UPDATE bookmarks SET synced = 1 WHERE uid = :uid AND questionId = :id AND updatedAt = :version") suspend fun markBookmarkSynced(uid: String, id: String, version: Long)
    @Upsert suspend fun session(item: SessionEntity)
    @Query("SELECT * FROM sessions WHERE id = :id AND uid = :uid") fun session(id: String, uid: String): Flow<SessionEntity?>
    @Query("SELECT * FROM sessions WHERE uid = :uid AND completed = 0 ORDER BY updatedAt DESC LIMIT 1") suspend fun latestSession(uid: String): SessionEntity?
    @Upsert suspend fun result(item: ResultEntity)
    @Query("SELECT * FROM results WHERE uid = :uid ORDER BY timestamp DESC LIMIT 100") fun history(uid: String): Flow<List<ResultEntity>>
    @Query("SELECT COUNT(*) FROM results WHERE uid = :uid") fun testCount(uid: String): Flow<Int>
    @Query("SELECT * FROM results WHERE uid = :uid AND synced = 0 LIMIT 8") suspend fun pendingResults(uid: String): List<ResultEntity>
    @Query("UPDATE results SET synced = 1 WHERE id IN (:ids)") suspend fun markResultsSynced(ids: List<String>)
    @Query("DELETE FROM attempts WHERE uid = :uid") suspend fun deleteAttempts(uid: String)
    @Query("DELETE FROM mastery WHERE uid = :uid") suspend fun deleteMastery(uid: String)
    @Query("DELETE FROM bookmarks WHERE uid = :uid") suspend fun deleteBookmarks(uid: String)
    @Query("DELETE FROM sessions WHERE uid = :uid") suspend fun deleteSessions(uid: String)
    @Query("DELETE FROM results WHERE uid = :uid") suspend fun deleteResults(uid: String)
}
@Database(entities = [QuestionEntity::class, CatalogEntity::class, AttemptEntity::class, MasteryEntity::class,
    BookmarkEntity::class, SessionEntity::class, ResultEntity::class, SyncCursor::class], version = 1, exportSchema = true)
abstract class MindwayDatabase : RoomDatabase() { abstract fun learningDao(): LearningDao }
