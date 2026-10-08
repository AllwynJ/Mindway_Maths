package com.elsco.mindwaymaths.data.remote

import com.google.firebase.firestore.*
import com.elsco.mindwaymaths.domain.model.PracticeFilter
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton class FirestoreContentSource @Inject constructor(private val firebase: FirebaseProvider) {
    private val cursors = mutableMapOf<String, DocumentSnapshot>()
    suspend fun page(collection: String, filter: PracticeFilter? = null, next: Boolean = false): List<Pair<String, JsonObject>> {
        check(firebase.configured && firebase.auth.currentUser != null)
        val key = "$collection:$filter"
        var query: Query = firebase.store.collection(collection)
        // Inactive tombstones are readable to allow removal of cached content.
        if (filter != null) {
            if (filter.exam.isNotBlank()) query = query.whereEqualTo("exam", filter.exam)
            if (filter.subject.isNotBlank()) query = query.whereEqualTo("subject", filter.subject)
            if (filter.topic.isNotBlank()) query = query.whereEqualTo("topic", filter.topic)
        }
        query = query.orderBy(FieldPath.documentId()).limit(50)
        if (next) cursors[key]?.let { query = query.startAfter(it) }
        val page = withTimeout(15_000) { query.get(Source.SERVER).await() }
        page.documents.lastOrNull()?.let { cursors[key] = it }
        return page.documents.map { it.id to JsonObject(it.data.orEmpty().mapValues { (_, v) -> element(v) } + ("id" to JsonPrimitive(it.id))) }
    }
    private fun element(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is Boolean -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        is String -> JsonPrimitive(value)
        is com.google.firebase.Timestamp -> JsonPrimitive(value.toDate().time)
        is Map<*, *> -> JsonObject(value.entries.associate { it.key.toString() to element(it.value) })
        is List<*> -> JsonArray(value.map(::element))
        else -> JsonNull
    }
}
