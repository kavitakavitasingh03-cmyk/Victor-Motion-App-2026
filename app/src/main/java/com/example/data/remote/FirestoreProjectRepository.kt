package com.example.data.remote

import android.content.Context
import com.example.R
import com.example.domain.model.CreatorPresetDto
import com.example.domain.model.UserProfileDto
import com.example.domain.model.VideoProjectDto
import com.example.security.OperationType
import com.example.security.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class FirestoreProjectRepository(
    private val db: FirebaseFirestore
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observeUserProfile(userId: String): Flow<UserProfileDto?> = flow {
        val docRef = db.collection("users").document(userId)
        docRef.snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(
                        UserProfileDto::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                } else {
                    null
                }
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val toLog = firestoreError ?: (error as? Exception)
                if (toLog != null && auth.currentUser?.uid == userId) {
                    handleFirestoreError(toLog, OperationType.GET, docRef.path)
                }
                throw error
            }
            .collect { emit(it) }
    }

    suspend fun ensureUserProfile(
        displayName: String,
        photoUrl: String?
    ): Result<Unit> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val docRef = db.collection("users").document(uid)
        return try {
            val snap = docRef.get().await()
            if (!snap.exists()) {
                val payload = mutableMapOf<String, Any>(
                    "userId" to uid,
                    "displayName" to displayName.ifBlank { "Creator" }.take(80),
                    "subscriptionTier" to "free",
                    "preferredExportQuality" to "1080p",
                    "preferredFps" to 60L,
                    "cloudBackupEnabled" to true,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (!photoUrl.isNullOrBlank()) {
                    payload["photoUrl"] = photoUrl.take(500)
                }
                docRef.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateUserPreferences(
        subscriptionTier: String? = null,
        preferredExportQuality: String? = null,
        preferredFps: Int? = null,
        cloudBackupEnabled: Boolean? = null
    ): Result<Unit> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val docRef = db.collection("users").document(uid)
        val updates = mutableMapOf<String, Any>(
            "updatedAt" to FieldValue.serverTimestamp()
        )
        subscriptionTier?.let { updates["subscriptionTier"] = it }
        preferredExportQuality?.let { updates["preferredExportQuality"] = it }
        preferredFps?.let { updates["preferredFps"] = it.toLong() }
        cloudBackupEnabled?.let { updates["cloudBackupEnabled"] = it }

        return try {
            docRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    fun observeCloudProjects(userId: String): Flow<List<VideoProjectDto>> = flow {
        val colRef = db.collection("users").document(userId).collection("projects")
        colRef.snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    VideoProjectDto::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val toLog = firestoreError ?: (error as? Exception)
                if (toLog != null && auth.currentUser?.uid == userId) {
                    handleFirestoreError(toLog, OperationType.LIST, colRef.path)
                }
                throw error
            }
            .collect { emit(it) }
    }

    suspend fun backupProject(project: VideoProjectDto): Result<Unit> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val safeProjectId = project.id.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
        val docRef = db.collection("users").document(uid).collection("projects").document(safeProjectId)
        return try {
            val existing = docRef.get().await()
            if (existing.exists()) {
                val updates = mutableMapOf<String, Any>(
                    "title" to project.title.ifBlank { "Untitled" }.take(120),
                    "aspectRatio" to project.aspectRatio,
                    "resolution" to project.resolution,
                    "fps" to project.fps,
                    "durationMs" to project.durationMs,
                    "layerCount" to project.layerCount,
                    "layersJson" to project.layersJson.take(200000),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                project.templateOriginId?.takeIf { it.isNotBlank() }?.let {
                    updates["templateOriginId"] = it.take(128)
                }
                docRef.update(updates).await()
            } else {
                val payload = mutableMapOf<String, Any>(
                    "userId" to uid,
                    "title" to project.title.ifBlank { "Untitled" }.take(120),
                    "aspectRatio" to project.aspectRatio,
                    "resolution" to project.resolution,
                    "fps" to project.fps,
                    "durationMs" to project.durationMs,
                    "layerCount" to project.layerCount,
                    "layersJson" to project.layersJson.take(200000),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                project.templateOriginId?.takeIf { it.isNotBlank() }?.let {
                    payload["templateOriginId"] = it.take(128)
                }
                docRef.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteCloudProject(projectId: String): Result<Unit> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val docRef = db.collection("users").document(uid).collection("projects").document(projectId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    fun observePublishedPresets(): Flow<List<CreatorPresetDto>> = flow {
        val uid = requireUserId()
        val query = db.collection("presets").whereEqualTo("isPublished", true)
        query.snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    CreatorPresetDto::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val toLog = firestoreError ?: (error as? Exception)
                if (toLog != null && auth.currentUser?.uid == uid) {
                    handleFirestoreError(toLog, OperationType.LIST, "presets")
                }
                throw error
            }
            .collect { emit(it) }
    }

    fun observeMyPresets(userId: String): Flow<List<CreatorPresetDto>> = flow {
        val query = db.collection("presets").whereEqualTo("authorId", userId)
        query.snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    CreatorPresetDto::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val toLog = firestoreError ?: (error as? Exception)
                if (toLog != null && auth.currentUser?.uid == userId) {
                    handleFirestoreError(toLog, OperationType.LIST, "presets")
                }
                throw error
            }
            .collect { emit(it) }
    }

    suspend fun publishPreset(preset: CreatorPresetDto): Result<String> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val docRef = if (preset.id.isNotBlank()) {
            db.collection("presets").document(preset.id)
        } else {
            db.collection("presets").document()
        }
        val payload = mapOf(
            "authorId" to uid,
            "authorName" to preset.authorName.ifBlank { "Creator" }.take(80),
            "title" to preset.title.ifBlank { "Custom Preset" }.take(100),
            "category" to preset.category,
            "description" to preset.description.take(400),
            "isPublished" to preset.isPublished,
            "isPremium" to preset.isPremium,
            "effectSummary" to preset.effectSummary.take(300),
            "presetPayloadJson" to preset.presetPayloadJson.ifBlank { "{}" }.take(50000),
            "likesCount" to 0L,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun likePreset(presetId: String): Result<Unit> {
        try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val docRef = db.collection("presets").document(presetId)
        return try {
            docRef.update(
                mapOf(
                    "likesCount" to FieldValue.increment(1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deletePreset(presetId: String): Result<Unit> {
        try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val docRef = db.collection("presets").document(presetId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }
}
