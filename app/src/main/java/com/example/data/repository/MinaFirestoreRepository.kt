package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.core.OperationType
import com.example.core.handleFirestoreError
import com.example.data.model.ChatMessage
import com.example.data.model.DocumentType
import com.example.data.model.FavoritePlace
import com.example.data.model.JournalEntry
import com.example.data.model.JournalMood
import com.example.data.model.MemoryCategory
import com.example.data.model.MemoryItem
import com.example.data.model.MinaLanguage
import com.example.data.model.MinaPersonality
import com.example.data.model.SavedDocument
import com.example.data.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class MinaFirestoreRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

    private fun <T> Flow<T>.handleFirestoreStreamError(
        uid: String,
        path: String
    ): Flow<T> = catch { error ->
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(exceptionToLog, OperationType.LIST, path)
        }
        throw error
    }

    // --- User Profile ---

    fun observeProfile(userId: String): Flow<UserProfile?> = flow {
        require(userId.isNotBlank()) { "User ID must not be blank" }
        val path = "users/$userId"
        emitAll(
            db.collection("users").document(userId)
                .snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    } else null
                }
                .handleFirestoreStreamError(userId, path)
        )
    }

    suspend fun ensureProfileExists(userId: String, defaultDisplayName: String): Result<Unit> {
        val path = "users/$userId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Session mismatch" }
            val docRef = db.collection("users").document(uid)
            val snap = docRef.get().await()
            if (!snap.exists()) {
                val safeName = defaultDisplayName.trim().ifBlank { "Friend" }.take(60)
                val payload = mapOf(
                    "userId" to uid,
                    "displayName" to safeName,
                    "bio" to "",
                    "privateNote" to "",
                    "avatarUri" to "",
                    "youtubeUrl" to "",
                    "instagramUrl" to "",
                    "socialUrl" to "",
                    "musicUrl" to "",
                    "personality" to MinaPersonality.CARING.name,
                    "emotionLevel" to 3L,
                    "language" to MinaLanguage.HINGLISH.name,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateProfile(
        displayName: String,
        bio: String,
        privateNote: String,
        avatarUri: String,
        youtubeUrl: String,
        instagramUrl: String,
        socialUrl: String,
        musicUrl: String,
        personality: MinaPersonality,
        emotionLevel: Int,
        language: MinaLanguage
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "users/$uid"
        return try {
            val payload = mapOf(
                "displayName" to displayName.trim().ifBlank { "Friend" }.take(60),
                "bio" to bio.trim().take(300),
                "privateNote" to privateNote.take(1000),
                "avatarUri" to avatarUri.take(500),
                "youtubeUrl" to youtubeUrl.trim().take(250),
                "instagramUrl" to instagramUrl.trim().take(250),
                "socialUrl" to socialUrl.trim().take(250),
                "musicUrl" to musicUrl.trim().take(250),
                "personality" to personality.name,
                "emotionLevel" to emotionLevel.coerceIn(1, 5).toLong(),
                "language" to language.name,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("users").document(uid).update(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateCompanionTone(
        personality: MinaPersonality,
        emotionLevel: Int,
        language: MinaLanguage
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "users/$uid"
        return try {
            db.collection("users").document(uid).update(
                mapOf(
                    "personality" to personality.name,
                    "emotionLevel" to emotionLevel.coerceIn(1, 5).toLong(),
                    "language" to language.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- Memories ---

    fun observeMemories(userId: String): Flow<List<MemoryItem>> = flow {
        val path = "users/$userId/memories"
        emitAll(
            db.collection("users").document(userId).collection("memories")
                .snapshots()
                .map { snap ->
                    snap.toObjects(MemoryItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .handleFirestoreStreamError(userId, path)
        )
    }

    suspend fun addMemory(
        title: String,
        content: String,
        category: MemoryCategory,
        encrypted: Boolean
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val docRef = db.collection("users").document(uid).collection("memories").document()
        return try {
            val safeTitle = title.trim().ifBlank { "Memory" }.take(120)
            val safeContent = content.trim().take(1000)
            require(safeContent.isNotEmpty()) { "Memory content cannot be empty" }
            docRef.set(
                mapOf(
                    "userId" to uid,
                    "title" to safeTitle,
                    "content" to safeContent,
                    "category" to category.name,
                    "encrypted" to encrypted,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateMemory(
        memoryId: String,
        title: String,
        content: String,
        category: MemoryCategory,
        encrypted: Boolean
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "users/$uid/memories/$memoryId"
        return try {
            require(memoryId.isNotBlank())
            val safeTitle = title.trim().ifBlank { "Memory" }.take(120)
            val safeContent = content.trim().take(1000)
            require(safeContent.isNotEmpty()) { "Memory content cannot be empty" }
            db.collection("users").document(uid).collection("memories").document(memoryId)
                .update(
                    mapOf(
                        "title" to safeTitle,
                        "content" to safeContent,
                        "category" to category.name,
                        "encrypted" to encrypted,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteMemory(memoryId: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "users/$uid/memories/$memoryId"
        return try {
            db.collection("users").document(uid).collection("memories").document(memoryId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- Chats ---

    fun observeChats(userId: String): Flow<List<ChatMessage>> = flow {
        val path = "users/$userId/chats"
        emitAll(
            db.collection("users").document(userId).collection("chats")
                .snapshots()
                .map { snap ->
                    snap.toObjects(ChatMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.createdAt ?: Timestamp.now() }
                }
                .handleFirestoreStreamError(userId, path)
        )
    }

    suspend fun addChatMessage(role: String, text: String, personality: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val docRef = db.collection("users").document(uid).collection("chats").document()
        return try {
            val safeText = text.trim().take(8000)
            require(safeText.isNotEmpty()) { "Message text cannot be empty" }
            docRef.set(
                mapOf(
                    "userId" to uid,
                    "role" to role,
                    "text" to safeText,
                    "personality" to personality.take(40),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun clearChats(): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "users/$uid/chats"
        return try {
            val snap = db.collection("users").document(uid).collection("chats").get().await()
            val batch = db.batch()
            snap.documents.forEach { batch.delete(it.reference) }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- Journals ---

    fun observeJournals(userId: String): Flow<List<JournalEntry>> = flow {
        val path = "users/$userId/journals"
        emitAll(
            db.collection("users").document(userId).collection("journals")
                .snapshots()
                .map { snap ->
                    snap.toObjects(JournalEntry::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .handleFirestoreStreamError(userId, path)
        )
    }

    suspend fun addJournalEntry(
        title: String,
        content: String,
        mood: JournalMood,
        encrypted: Boolean,
        aiReflection: String
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val docRef = db.collection("users").document(uid).collection("journals").document()
        return try {
            val safeTitle = title.trim().ifBlank { "Daily Reflection" }.take(140)
            val safeContent = content.trim().take(5000)
            require(safeContent.isNotEmpty()) { "Journal content cannot be empty" }
            docRef.set(
                mapOf(
                    "userId" to uid,
                    "title" to safeTitle,
                    "content" to safeContent,
                    "mood" to mood.name,
                    "encrypted" to encrypted,
                    "aiReflection" to aiReflection.take(2000),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteJournalEntry(entryId: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "users/$uid/journals/$entryId"
        return try {
            db.collection("users").document(uid).collection("journals").document(entryId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- Saved Documents ---

    fun observeDocuments(userId: String): Flow<List<SavedDocument>> = flow {
        val path = "users/$userId/documents"
        emitAll(
            db.collection("users").document(userId).collection("documents")
                .snapshots()
                .map { snap ->
                    snap.toObjects(SavedDocument::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
                }
                .handleFirestoreStreamError(userId, path)
        )
    }

    suspend fun saveDocument(
        existingId: String?,
        title: String,
        docType: DocumentType,
        content: String
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val col = db.collection("users").document(uid).collection("documents")
        val docRef = if (existingId.isNullOrBlank()) col.document() else col.document(existingId)
        return try {
            val safeTitle = title.trim().ifBlank { "${docType.label} Draft" }.take(140)
            val safeContent = content.trim().take(15000)
            require(safeContent.isNotEmpty()) { "Document content cannot be empty" }
            if (existingId.isNullOrBlank()) {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "title" to safeTitle,
                        "docType" to docType.name,
                        "content" to safeContent,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.update(
                    mapOf(
                        "title" to safeTitle,
                        "docType" to docType.name,
                        "content" to safeContent,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteDocument(docId: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "users/$uid/documents/$docId"
        return try {
            db.collection("users").document(uid).collection("documents").document(docId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- Favorite Places ---

    fun observePlaces(userId: String): Flow<List<FavoritePlace>> = flow {
        val path = "users/$userId/places"
        emitAll(
            db.collection("users").document(userId).collection("places")
                .snapshots()
                .map { snap ->
                    snap.toObjects(FavoritePlace::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .handleFirestoreStreamError(userId, path)
        )
    }

    suspend fun addPlace(name: String, address: String, lat: Double, lng: Double): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val docRef = db.collection("users").document(uid).collection("places").document()
        return try {
            docRef.set(
                mapOf(
                    "userId" to uid,
                    "name" to name.trim().ifBlank { "Saved Spot" }.take(120),
                    "address" to address.trim().take(300),
                    "latitude" to lat.coerceIn(-90.0, 90.0),
                    "longitude" to lng.coerceIn(-180.0, 180.0),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deletePlace(placeId: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "users/$uid/places/$placeId"
        return try {
            db.collection("users").document(uid).collection("places").document(placeId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }
}
