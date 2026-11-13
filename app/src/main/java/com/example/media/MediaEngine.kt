package com.example.media

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Crop
import androidx.media3.effect.Presentation
import androidx.media3.effect.RgbAdjustment
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.example.domain.model.ExportConfig
import com.example.domain.model.LayerType
import com.example.domain.model.TimelineLayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

data class MediaMetadataInfo(
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val thumbnail: Bitmap?
)

data class HardwareCapabilities(
    val supports4K: Boolean,
    val supports2K: Boolean,
    val maxSupportedFps: Int,
    val supportedEncoderName: String
)

object MediaEngine {

    fun probeHardwareCapabilities(): HardwareCapabilities {
        var supports4K = false
        var supports2K = false
        var maxFps = 60
        var encoderName = "H.264 / AVC"

        try {
            val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            for (info in codecList.codecInfos) {
                if (!info.isEncoder) continue
                val types = info.supportedTypes
                if (types.any { it.equals(MediaFormat.MIMETYPE_VIDEO_AVC, ignoreCase = true) ||
                        it.equals(MediaFormat.MIMETYPE_VIDEO_HEVC, ignoreCase = true) }
                ) {
                    val mime = if (types.any { it.equals(MediaFormat.MIMETYPE_VIDEO_HEVC, ignoreCase = true) }) {
                        encoderName = "HEVC / H.265 + AVC"
                        MediaFormat.MIMETYPE_VIDEO_HEVC
                    } else {
                        MediaFormat.MIMETYPE_VIDEO_AVC
                    }
                    val caps = info.getCapabilitiesForType(mime).videoCapabilities ?: continue
                    if (caps.isSizeSupported(3840, 2160) || caps.isSizeSupported(2160, 3840)) {
                        supports4K = true
                    }
                    if (caps.isSizeSupported(2560, 1440) || caps.isSizeSupported(1440, 2560)) {
                        supports2K = true
                    }
                    val fpsUpper = caps.supportedFrameRates.upper
                    if (fpsUpper >= 120) {
                        maxFps = 120
                    }
                }
            }
        } catch (_: Exception) {
            // Safe fallback for restricted virtualized devices
            supports2K = true
        }

        return HardwareCapabilities(
            supports4K = supports4K,
            supports2K = supports2K || supports4K,
            maxSupportedFps = maxFps,
            supportedEncoderName = encoderName
        )
    }

    suspend fun extractMediaInfo(context: Context, uri: Uri, isVideo: Boolean): MediaMetadataInfo =
        withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                val widthStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                val heightStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                val durationMs = durationStr?.toLongOrNull()?.coerceAtLeast(1000L) ?: 6000L
                val width = widthStr?.toIntOrNull() ?: 1080
                val height = heightStr?.toIntOrNull() ?: 1920
                val frame = if (isVideo) {
                    retriever.getFrameAtTime(500_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                } else {
                    null
                }
                MediaMetadataInfo(durationMs, width, height, frame)
            } catch (_: Exception) {
                MediaMetadataInfo(6000L, 1080, 1920, null)
            } finally {
                try {
                    retriever.release()
                } catch (_: Exception) {
                }
            }
        }

    fun generateWaveformEnvelope(seed: Int, bars: Int = 28): List<Float> {
        return List(bars) { idx ->
            val phase = (idx + 1) * 0.45f + (abs(seed) % 13) * 0.3f
            val valRaw = 0.25f + 0.65f * abs(kotlin.math.sin(phase.toDouble())).toFloat()
            valRaw.coerceIn(0.15f, 0.96f)
        }
    }

    @OptIn(UnstableApi::class)
    fun exportWithMedia3(
        context: Context,
        projectTitle: String,
        primaryLayer: TimelineLayer?,
        config: ExportConfig,
        onProgress: (Int) -> Unit,
        onCompleted: (File) -> Unit,
        onError: (String) -> Unit
    ): Transformer? {
        val outputDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir
        if (!outputDir.exists()) outputDir.mkdirs()
        val sanitized = projectTitle.replace(Regex("[^a-zA-Z0-9_-]"), "_").ifBlank { "victor_motion" }
        val outputFile = File(outputDir, "${sanitized}_${System.currentTimeMillis()}.mp4")

        val mediaUriStr = primaryLayer?.mediaUri.orEmpty()
        if (mediaUriStr.isBlank()) {
            // No physical video URI attached yet: callers handle studio render summary cleanly
            return null
        }

        val uri = Uri.parse(mediaUriStr)
        val mediaItem = MediaItem.Builder()
            .setUri(uri)
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(0L)
                    .setEndPositionMs((primaryLayer?.endMs ?: 5000L).coerceAtLeast(1000L))
                    .build()
            )
            .build()

        val videoEffects = mutableListOf<Effect>()
        if (primaryLayer != null) {
            if (primaryLayer.rotation != 0f || primaryLayer.scale != 1f) {
                videoEffects.add(
                    ScaleAndRotateTransformation.Builder()
                        .setRotationDegrees(-primaryLayer.rotation)
                        .setScale(primaryLayer.scale, primaryLayer.scale)
                        .build()
                )
            }
            if (primaryLayer.cropLeft > 0f || primaryLayer.cropTop > 0f ||
                primaryLayer.cropRight < 1f || primaryLayer.cropBottom < 1f
            ) {
                val left = (primaryLayer.cropLeft * 2f - 1f).coerceIn(-1f, 0.8f)
                val right = (primaryLayer.cropRight * 2f - 1f).coerceIn(left + 0.1f, 1f)
                val bottom = (1f - primaryLayer.cropBottom * 2f).coerceIn(-1f, 0.8f)
                val top = (1f - primaryLayer.cropTop * 2f).coerceIn(bottom + 0.1f, 1f)
                videoEffects.add(Crop(left, right, bottom, top))
            }
            val fx = primaryLayer.effects
            if (fx.exposure != 0f || fx.temperature != 0f) {
                val rScale = (1f + fx.exposure + fx.temperature * 0.2f).coerceIn(0.4f, 1.8f)
                val gScale = (1f + fx.exposure).coerceIn(0.4f, 1.8f)
                val bScale = (1f + fx.exposure - fx.temperature * 0.2f).coerceIn(0.4f, 1.8f)
                videoEffects.add(
                    RgbAdjustment.Builder()
                        .setRedScale(rScale)
                        .setGreenScale(gScale)
                        .setBlueScale(bScale)
                        .build()
                )
            }
        }

        val targetHeight = when (config.resolution) {
            "4K" -> 2160
            "2K" -> 1440
            "720p" -> 720
            else -> 1080
        }
        videoEffects.add(Presentation.createForHeight(targetHeight))

        val editedItem = EditedMediaItem.Builder(mediaItem)
            .setRemoveAudio(primaryLayer?.isMuted == true || primaryLayer?.type == LayerType.IMAGE)
            .setEffects(Effects(emptyList(), videoEffects))
            .build()

        val mainHandler = Handler(Looper.getMainLooper())
        val progressHolder = ProgressHolder()
        var isFinished = false

        val transformer = Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    isFinished = true
                    onProgress(100)
                    onCompleted(outputFile)
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    isFinished = true
                    onError(exportException.localizedMessage ?: "Export pipeline error.")
                }
            })
            .build()

        transformer.start(editedItem, outputFile.absolutePath)

        mainHandler.post(object : Runnable {
            override fun run() {
                if (isFinished) return
                val state = transformer.getProgress(progressHolder)
                if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                    onProgress(progressHolder.progress.coerceIn(0, 99))
                }
                if (!isFinished && state != Transformer.PROGRESS_STATE_NOT_STARTED) {
                    mainHandler.postDelayed(this, 250L)
                }
            }
        })

        return transformer
    }
}
