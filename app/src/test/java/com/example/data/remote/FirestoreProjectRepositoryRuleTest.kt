package com.example.data.remote

import com.example.base.FirestoreEmulatorTestBase
import com.example.domain.model.CreatorPresetDto
import com.example.domain.model.VideoProjectDto
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.UUID

class FirestoreProjectRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun backupProject_authenticatedOwner_succeedsAndEmitsViaFlow() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = FirestoreProjectRepository(firestore)
        val projectId = "proj_${UUID.randomUUID().toString().replace("-", "").take(12)}"

        val project = VideoProjectDto(
            id = projectId,
            userId = uid,
            title = "Cyber Drift Edit",
            aspectRatio = "9:16",
            resolution = "4K",
            fps = 60L,
            durationMs = 15000L,
            layerCount = 3L,
            layersJson = "{\"layers\":[]}"
        )

        val result = withTimeout(DEFAULT_TIMEOUT_MS) { repository.backupProject(project) }
        assertTrue("Expected backupProject to succeed: ${result.exceptionOrNull()}", result.isSuccess)

        val observed = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeCloudProjects(uid).first { list -> list.any { it.id == projectId } }
        }
        assertTrue(observed.any { it.id == projectId && it.title == "Cyber Drift Edit" })
    }

    @Test
    fun observeCloudProjects_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = FirestoreProjectRepository(firestore)
        val projectId = "proj_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.backupProject(
                VideoProjectDto(
                    id = projectId,
                    userId = aliceUid,
                    title = "Private Edit",
                    aspectRatio = "16:9",
                    resolution = "1080p",
                    fps = 30L,
                    durationMs = 8000L,
                    layerCount = 1L,
                    layersJson = "{\"layers\":[]}"
                )
            )
        }

        signInTestUser(BOB_EMAIL)
        val bobRepo = FirestoreProjectRepository(firestore)
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                bobRepo.observeCloudProjects(aliceUid).first()
            }
            fail("Expected PERMISSION_DENIED for cross-user project read")
        } catch (e: Exception) {
            val fsError = checkNotNull(e.unwrapFirestoreException()) { "Expected FirebaseFirestoreException, got $e" }
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsError.code)
        }
    }

    @Test
    fun observeCloudProjects_unauthenticated_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repository = FirestoreProjectRepository(firestore)

        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeCloudProjects("alice_uid").first()
            }
            fail("Expected PERMISSION_DENIED when unauthenticated")
        } catch (e: Exception) {
            val fsError = checkNotNull(e.unwrapFirestoreException()) { "Expected FirebaseFirestoreException, got $e" }
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsError.code)
        }
    }

    @Test
    fun publishAndLikePreset_validFlow_succeeds() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = FirestoreProjectRepository(firestore)
        val presetId = "preset_${UUID.randomUUID().toString().replace("-", "").take(12)}"

        val createResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.publishPreset(
                CreatorPresetDto(
                    id = presetId,
                    authorId = aliceUid,
                    authorName = "AliceVFX",
                    title = "Anamorphic Glow Ramp",
                    category = "Velocity",
                    description = "Smooth expo-out velocity with cyan bloom",
                    isPublished = true,
                    isPremium = false,
                    effectSummary = "Glow + Speed Ramp",
                    presetPayloadJson = "{\"glow\":0.8}"
                )
            )
        }
        assertTrue(createResult.isSuccess)

        signInTestUser(BOB_EMAIL)
        val bobRepo = FirestoreProjectRepository(firestore)
        val published = withTimeout(FLOW_TIMEOUT_MS) {
            bobRepo.observePublishedPresets().first { list -> list.any { it.id == presetId } }
        }
        assertTrue(published.any { it.id == presetId })

        val likeResult = withTimeout(DEFAULT_TIMEOUT_MS) { bobRepo.likePreset(presetId) }
        assertTrue("Expected likePreset to succeed: ${likeResult.exceptionOrNull()}", likeResult.isSuccess)
    }

    private companion object {
        const val ALICE_EMAIL = "alice_vm@test.com"
        const val BOB_EMAIL = "bob_vm@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3500L
    }
}
