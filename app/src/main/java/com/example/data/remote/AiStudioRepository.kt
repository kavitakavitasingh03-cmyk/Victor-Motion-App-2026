package com.example.data.remote

import android.graphics.Bitmap
import android.util.Log
import com.example.domain.model.AiSuggestion
import com.example.domain.model.CaptionSegment
import com.example.domain.model.VisualEffectsConfig
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.util.UUID

class AiStudioRepository {

    private val captionsModel by lazy {
        val schema = Schema.obj(
            properties = mapOf(
                "captions" to Schema.array(
                    items = Schema.obj(
                        properties = mapOf(
                            "startMs" to Schema.integer(description = "Start time in milliseconds"),
                            "endMs" to Schema.integer(description = "End time in milliseconds"),
                            "text" to Schema.string(description = "Short punchy caption line")
                        )
                    ),
                    description = "Timed caption segments spanning the video duration"
                )
            )
        )
        Firebase.ai.generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = schema
            }
        )
    }

    private val suggestionsModel by lazy {
        val schema = Schema.obj(
            properties = mapOf(
                "suggestions" to Schema.array(
                    items = Schema.obj(
                        properties = mapOf(
                            "title" to Schema.string(description = "Short actionable edit title"),
                            "category" to Schema.string(description = "Category like Pacing, Lighting, FX, BeatSync"),
                            "rationale" to Schema.string(description = "Why this improves the timeline"),
                            "recommendedEffect" to Schema.string(description = "Effect preset summary"),
                            "glowDelta" to Schema.double(description = "Glow intensity 0.0 to 1.0"),
                            "rgbSplitDelta" to Schema.double(description = "RGB split intensity 0.0 to 1.0"),
                            "contrastValue" to Schema.double(description = "Contrast multiplier 0.8 to 1.6"),
                            "speedMultiplier" to Schema.double(description = "Recommended clip speed 0.5 to 2.5")
                        )
                    ),
                    description = "3 to 4 tailored pro editing suggestions"
                )
            )
        )
        Firebase.ai.generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = schema
            }
        )
    }

    private val enhanceModel by lazy {
        val schema = Schema.obj(
            properties = mapOf(
                "colorGradeName" to Schema.string(description = "Short cinematic grade name"),
                "exposure" to Schema.double(description = "Exposure shift -0.5 to 0.5"),
                "contrast" to Schema.double(description = "Contrast multiplier 0.8 to 1.5"),
                "saturation" to Schema.double(description = "Saturation multiplier 0.5 to 1.6"),
                "temperature" to Schema.double(description = "Color temperature -0.5 to 0.5"),
                "glowIntensity" to Schema.double(description = "Subtle highlight bloom 0.0 to 0.6"),
                "vignette" to Schema.double(description = "Vignette strength 0.0 to 0.6")
            )
        )
        Firebase.ai.generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = schema
            }
        )
    }

    private val beatSyncModel by lazy {
        val schema = Schema.obj(
            properties = mapOf(
                "bpm" to Schema.integer(description = "Detected or recommended BPM between 70 and 170"),
                "beatTimestampsMs" to Schema.array(
                    items = Schema.integer(description = "Beat hit timestamp in milliseconds"),
                    description = "List of rhythmic cut timestamps inside the timeline duration"
                )
            )
        )
        Firebase.ai.generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = schema
            }
        )
    }

    suspend fun generateAutoCaptions(
        projectTitle: String,
        topicPrompt: String,
        durationMs: Long
    ): Result<List<CaptionSegment>> {
        return try {
            val response = withTimeout(TIMEOUT_MS) {
                captionsModel.generateContent(
                    "Generate punchy social-ready video captions for a video project titled '$projectTitle'. " +
                        "Topic/script notes: '$topicPrompt'. Total duration is ${durationMs}ms. " +
                        "Return 4 to 6 non-overlapping timed segments in chronological order within 0..$durationMs ms."
                )
            }
            val raw = response.text ?: throw IllegalStateException("AI returned empty caption output.")
            val json = JSONObject(raw)
            val arr = json.getJSONArray("captions")
            val result = (0 until arr.length()).map { idx ->
                val obj = arr.getJSONObject(idx)
                val start = obj.optLong("startMs", idx * 2000L).coerceIn(0L, durationMs)
                val end = obj.optLong("endMs", start + 1800L).coerceIn(start + 400L, durationMs.coerceAtLeast(start + 400L))
                CaptionSegment(
                    id = "cap_${UUID.randomUUID().toString().take(8)}",
                    startMs = start,
                    endMs = end,
                    text = obj.getString("text")
                )
            }
            Result.success(result)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Auto captions timed out", e)
            Result.failure(IllegalStateException("AI caption generation timed out after 60 seconds."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Auto captions failed", e)
            Result.failure(e)
        }
    }

    suspend fun analyzeAndSuggestEdits(
        projectTitle: String,
        layerSummary: String,
        vibeGoal: String,
        previewFrame: Bitmap? = null
    ): Result<List<AiSuggestion>> {
        return try {
            val promptText = "You are a senior motion graphics director for Victor Motion. " +
                "Analyze project '$projectTitle' with layers: [$layerSummary]. " +
                "Creator goal: '$vibeGoal'. Provide 3 distinct, high-impact editing suggestions " +
                "covering velocity ramps, glow/RGB FX, and color grading."
            val response = withTimeout(TIMEOUT_MS) {
                if (previewFrame != null) {
                    suggestionsModel.generateContent(
                        content {
                            image(previewFrame)
                            text(promptText)
                        }
                    )
                } else {
                    suggestionsModel.generateContent(promptText)
                }
            }
            val raw = response.text ?: throw IllegalStateException("AI returned empty suggestions.")
            val json = JSONObject(raw)
            val arr = json.getJSONArray("suggestions")
            val list = (0 until arr.length()).map { idx ->
                val obj = arr.getJSONObject(idx)
                AiSuggestion(
                    title = obj.getString("title"),
                    category = obj.getString("category"),
                    rationale = obj.getString("rationale"),
                    recommendedEffect = obj.getString("recommendedEffect"),
                    glowDelta = obj.optDouble("glowDelta", 0.25).toFloat().coerceIn(0f, 1f),
                    rgbSplitDelta = obj.optDouble("rgbSplitDelta", 0.1).toFloat().coerceIn(0f, 1f),
                    contrastValue = obj.optDouble("contrastValue", 1.15).toFloat().coerceIn(0.7f, 1.8f),
                    speedMultiplier = obj.optDouble("speedMultiplier", 1.0).toFloat().coerceIn(0.25f, 3.0f)
                )
            }
            Result.success(list)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "AI suggestions timed out", e)
            Result.failure(IllegalStateException("AI editing analysis timed out after 60 seconds."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "AI suggestions failed", e)
            Result.failure(e)
        }
    }

    suspend fun enhanceVisualGrade(
        moodDescription: String,
        currentEffects: VisualEffectsConfig
    ): Result<VisualEffectsConfig> {
        return try {
            val response = withTimeout(TIMEOUT_MS) {
                enhanceModel.generateContent(
                    "Generate balanced AI color enhancement parameters for mood/style: '$moodDescription'. " +
                        "Return natural, non-clipped cinematic values."
                )
            }
            val raw = response.text ?: throw IllegalStateException("AI returned empty grade config.")
            val json = JSONObject(raw)
            Result.success(
                currentEffects.copy(
                    colorGradeName = json.optString("colorGradeName", "AI Enhanced"),
                    exposure = json.optDouble("exposure", 0.08).toFloat().coerceIn(-0.5f, 0.5f),
                    contrast = json.optDouble("contrast", 1.15).toFloat().coerceIn(0.7f, 1.6f),
                    saturation = json.optDouble("saturation", 1.18).toFloat().coerceIn(0.4f, 1.7f),
                    temperature = json.optDouble("temperature", 0.05).toFloat().coerceIn(-0.5f, 0.5f),
                    glowIntensity = json.optDouble("glowIntensity", 0.2).toFloat().coerceIn(0f, 0.7f),
                    vignette = json.optDouble("vignette", 0.2).toFloat().coerceIn(0f, 0.7f)
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "AI enhance timed out", e)
            Result.failure(IllegalStateException("AI enhancement timed out after 60 seconds."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "AI enhance failed", e)
            Result.failure(e)
        }
    }

    suspend fun generateBeatMarkers(
        trackMoodOrBpm: String,
        durationMs: Long
    ): Result<Pair<Int, List<Long>>> {
        return try {
            val response = withTimeout(TIMEOUT_MS) {
                beatSyncModel.generateContent(
                    "Generate beat-sync cut markers for music mood/tempo '$trackMoodOrBpm' " +
                        "spanning 0 to ${durationMs}ms (between 6 and 16 evenly spaced rhythmic hits)."
                )
            }
            val raw = response.text ?: throw IllegalStateException("AI returned empty beat markers.")
            val json = JSONObject(raw)
            val bpm = json.optInt("bpm", 124).coerceIn(60, 180)
            val arr = json.getJSONArray("beatTimestampsMs")
            val beats = (0 until arr.length())
                .map { arr.getLong(it).coerceIn(0L, durationMs) }
                .distinct()
                .sorted()
            Result.success(bpm to beats)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "AI beat sync timed out", e)
            Result.failure(IllegalStateException("AI beat sync timed out after 60 seconds."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "AI beat sync failed", e)
            Result.failure(e)
        }
    }

    private companion object {
        const val TAG = "AiStudioRepository"
        const val MODEL_NAME = "gemini-3.8-flash"
        const val TIMEOUT_MS = 60_000L
    }
}
