package com.example.domain.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import kotlinx.serialization.Serializable

@Serializable
enum class LayerType(val displayName: String) {
    VIDEO("Video"),
    IMAGE("Image"),
    AUDIO("Audio"),
    TEXT("Text"),
    EFFECT("FX"),
    CAPTION("Captions")
}

@Serializable
enum class BlendModeType(val displayName: String) {
    NORMAL("Normal"),
    ADD("Add"),
    SCREEN("Screen"),
    MULTIPLY("Multiply"),
    OVERLAY("Overlay")
}

@Serializable
enum class EasingCurveType(val displayName: String) {
    LINEAR("Linear"),
    EASE_IN_OUT("Ease In-Out"),
    EXPO_OUT("Expo Out"),
    SPRING_BOUNCE("Spring"),
    BEZIER_CUSTOM("Custom Bezier")
}

@Serializable
enum class KeyframeProperty(val label: String) {
    POS_X("Position X"),
    POS_Y("Position Y"),
    SCALE("Scale"),
    ROTATION("Rotation"),
    OPACITY("Opacity")
}

@Serializable
enum class MaskShapeType(val displayName: String) {
    NONE("None"),
    CIRCLE("Circle"),
    RECTANGLE("Rounded Rect"),
    CINEMA_BARS("Cinema 21:9"),
    AI_SUBJECT_MATTE("AI Subject Matte")
}

@Serializable
data class KeyframePoint(
    val id: String = "",
    val timeMs: Long = 0L,
    val property: KeyframeProperty = KeyframeProperty.SCALE,
    val value: Float = 1f,
    val easing: EasingCurveType = EasingCurveType.EASE_IN_OUT,
    val handleInX: Float = 0.25f,
    val handleInY: Float = 0.1f,
    val handleOutX: Float = 0.75f,
    val handleOutY: Float = 0.9f
)

@Serializable
data class VisualEffectsConfig(
    val glowIntensity: Float = 0f,
    val blurRadius: Float = 0f,
    val shakeAmplitude: Float = 0f,
    val rgbSplit: Float = 0f,
    val chromaticAberration: Float = 0f,
    val motionBlur: Boolean = false,
    val vignette: Float = 0f,
    val exposure: Float = 0f,
    val contrast: Float = 1f,
    val saturation: Float = 1f,
    val temperature: Float = 0f,
    val colorGradeName: String = "Natural",
    val maskShape: MaskShapeType = MaskShapeType.NONE,
    val maskFeather: Float = 0.15f
)

@Serializable
data class TextStyleConfig(
    val text: String = "VICTOR MOTION",
    val colorHex: Long = 0xFFFFFFFF,
    val outlineColorHex: Long = 0xFF00E5FF,
    val outlineWidth: Float = 0f,
    val shadowBlur: Float = 4f,
    val fontSizeSp: Float = 28f,
    val letterSpacing: Float = 1.2f,
    val animationPreset: String = "Kinetic Pop"
)

@Serializable
data class TimelineLayer(
    val id: String = "",
    val name: String = "Layer",
    val type: LayerType = LayerType.VIDEO,
    val trackIndex: Int = 0,
    val startMs: Long = 0L,
    val endMs: Long = 5000L,
    val mediaUri: String = "",
    val speed: Float = 1f,
    val velocityPreset: String = "Uniform",
    val isReversed: Boolean = false,
    val isFrozen: Boolean = false,
    val freezeTimeMs: Long = 0L,
    val volume: Float = 1f,
    val isMuted: Boolean = false,
    val fadeInMs: Long = 0L,
    val fadeOutMs: Long = 0L,
    val blendMode: BlendModeType = BlendModeType.NORMAL,
    val posX: Float = 0f,
    val posY: Float = 0f,
    val scale: Float = 1f,
    val rotation: Float = 0f,
    val opacity: Float = 1f,
    val cropLeft: Float = 0f,
    val cropTop: Float = 0f,
    val cropRight: Float = 1f,
    val cropBottom: Float = 1f,
    val keyframes: List<KeyframePoint> = emptyList(),
    val effects: VisualEffectsConfig = VisualEffectsConfig(),
    val textConfig: TextStyleConfig? = null,
    val waveformSamples: List<Float> = emptyList(),
    val transitionIn: String = "None"
)

@Serializable
data class CaptionSegment(
    val id: String = "",
    val startMs: Long = 0L,
    val endMs: Long = 2000L,
    val text: String = ""
)

@Serializable
data class ProjectTimelinePayload(
    val layers: List<TimelineLayer> = emptyList(),
    val beatMarkersMs: List<Long> = emptyList(),
    val captions: List<CaptionSegment> = emptyList()
)

@Serializable
data class PresetPayload(
    val effects: VisualEffectsConfig = VisualEffectsConfig(),
    val speed: Float = 1f,
    val velocityPreset: String = "Uniform",
    val textAnimation: String = "Kinetic Pop",
    val transitionName: String = "Cross Dissolve"
)

@Serializable
data class AiSuggestion(
    val title: String = "",
    val category: String = "",
    val rationale: String = "",
    val recommendedEffect: String = "",
    val glowDelta: Float = 0f,
    val rgbSplitDelta: Float = 0f,
    val contrastValue: Float = 1.1f,
    val speedMultiplier: Float = 1f
)

data class ExportConfig(
    val resolution: String = "1080p",
    val fps: Int = 60,
    val bitrateMbps: Int = 24,
    val removeWatermark: Boolean = true
)

data class StudioProject(
    val id: String,
    val userId: String,
    val title: String,
    val aspectRatio: String,
    val resolution: String,
    val fps: Int,
    val durationMs: Long,
    val payload: ProjectTimelinePayload,
    val templateOriginId: String? = null,
    val isSyncedToCloud: Boolean = false,
    val updatedAtMillis: Long = System.currentTimeMillis()
)

// Firestore DTOs
@IgnoreExtraProperties
data class UserProfileDto(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "Creator",
    val photoUrl: String? = null,
    val subscriptionTier: String = "free",
    val preferredExportQuality: String = "1080p",
    val preferredFps: Long = 60L,
    val cloudBackupEnabled: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class VideoProjectDto(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "Untitled Project",
    val aspectRatio: String = "9:16",
    val resolution: String = "1080p",
    val fps: Long = 60L,
    val durationMs: Long = 10000L,
    val layerCount: Long = 0L,
    val layersJson: String = "{}",
    val templateOriginId: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class CreatorPresetDto(
    @DocumentId val id: String = "",
    val authorId: String = "",
    val authorName: String = "Creator",
    val title: String = "Custom Preset",
    val category: String = "MotionFX",
    val description: String = "",
    val isPublished: Boolean = true,
    val isPremium: Boolean = false,
    val effectSummary: String = "",
    val presetPayloadJson: String = "{}",
    val likesCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
