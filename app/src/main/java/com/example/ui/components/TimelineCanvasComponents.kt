package com.example.ui.components

import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.domain.model.EasingCurveType
import com.example.domain.model.LayerType
import com.example.domain.model.MaskShapeType
import com.example.domain.model.StudioProject
import com.example.domain.model.TimelineLayer
import com.example.ui.theme.AmberPlayhead
import com.example.ui.theme.CrimsonAccent
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.RoyalViolet
import com.example.ui.theme.StudioElevated
import com.example.ui.theme.StudioOutline
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceVariant
import com.example.ui.theme.TimecodeTextStyle
import com.example.ui.theme.TrackAudioColor
import com.example.ui.theme.TrackCaptionColor
import com.example.ui.theme.TrackEffectColor
import com.example.ui.theme.TrackImageColor
import com.example.ui.theme.TrackTextColor
import com.example.ui.theme.TrackVideoColor
import com.example.ui.viewmodel.EvaluatedLayerTransform
import kotlin.math.pow
import kotlin.math.roundToInt

@Composable
fun StudioPreviewCanvas(
    project: StudioProject,
    playheadMs: Long,
    isPlaying: Boolean,
    selectedLayerId: String?,
    evaluateTransform: (TimelineLayer, Long) -> EvaluatedLayerTransform,
    shakeOffsetProvider: (Float, Long) -> Pair<Float, Float>,
    modifier: Modifier = Modifier
) {
    val ratio = when (project.aspectRatio) {
        "16:9" -> 16f / 9f
        "1:1" -> 1f
        "4:5" -> 4f / 5f
        else -> 9f / 16f
    }

    val activeLayers = remember(project.payload.layers, playheadMs) {
        project.payload.layers
            .filter { playheadMs in it.startMs..it.endMs }
            .sortedBy { it.trackIndex }
    }
    val activeVideoLayer = activeLayers.firstOrNull {
        it.type == LayerType.VIDEO && it.mediaUri.isNotBlank()
    }
    val activeCaption = project.payload.captions.firstOrNull {
        playheadMs in it.startMs..it.endMs
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(ObsidianBg),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .aspectRatio(ratio, matchHeightConstraintsFirst = true)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    Brush.radialGradient(
                        listOf(Color(0xFF1A2038), Color(0xFF0A0C14))
                    )
                )
                .border(1.dp, StudioOutline, RoundedCornerShape(14.dp))
        ) {
            // 1. Real ExoPlayer surface when a video URI is present
            if (activeVideoLayer != null) {
                VideoPlaybackSurface(
                    uriString = activeVideoLayer.mediaUri,
                    playheadMs = (playheadMs - activeVideoLayer.startMs).coerceAtLeast(0L),
                    isPlaying = isPlaying,
                    speed = activeVideoLayer.speed.coerceIn(0.25f, 4f),
                    volume = if (activeVideoLayer.isMuted) 0f else activeVideoLayer.volume,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Studio grid & motion canvas backdrop when editing templates/overlays
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val step = 36.dp.toPx()
                    var x = step
                    while (x < size.width) {
                        drawLine(
                            color = Color.White.copy(alpha = 0.04f),
                            start = Offset(x, 0f),
                            end = Offset(x, size.height),
                            strokeWidth = 1f
                        )
                        x += step
                    }
                    var y = step
                    while (y < size.height) {
                        drawLine(
                            color = Color.White.copy(alpha = 0.04f),
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 1f
                        )
                        y += step
                    }
                }
            }

            // 2. Render visual layers (Video placeholder / Image / Text / FX) with live keyframe transforms
            activeLayers.forEach { layer ->
                val tr = evaluateTransform(layer, playheadMs)
                val (shakeX, shakeY) = shakeOffsetProvider(layer.effects.shakeAmplitude, playheadMs)
                val fx = layer.effects

                val layerShape = when (fx.maskShape) {
                    MaskShapeType.CIRCLE -> CircleShape
                    MaskShapeType.RECTANGLE -> RoundedCornerShape(24.dp)
                    else -> RoundedCornerShape(0.dp)
                }

                val colorMatrix = remember(fx.contrast, fx.saturation, fx.exposure) {
                    ColorMatrix().apply {
                        setToSaturation(fx.saturation)
                    }
                }

                val blurMod = if (fx.blurRadius > 0.5f) Modifier.blur((fx.blurRadius * 10f).dp) else Modifier

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(blurMod)
                        .clip(layerShape)
                        .graphicsLayer {
                            translationX = tr.posX * 140f + shakeX
                            translationY = tr.posY * 140f + shakeY
                            scaleX = tr.scale
                            scaleY = tr.scale
                            rotationZ = tr.rotation
                            alpha = tr.opacity
                        },
                    contentAlignment = Alignment.Center
                ) {
                    when (layer.type) {
                        LayerType.VIDEO -> {
                            if (layer.mediaUri.isBlank()) {
                                // Stylized active video node preview
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.linearGradient(
                                                listOf(
                                                    ElectricCyan.copy(alpha = 0.16f + fx.glowIntensity * 0.2f),
                                                    RoyalViolet.copy(alpha = 0.22f),
                                                    Color.Transparent
                                                )
                                            )
                                        )
                                )
                            }
                        }

                        LayerType.IMAGE -> {
                            if (layer.mediaUri.isNotBlank()) {
                                AsyncImage(
                                    model = Uri.parse(layer.mediaUri),
                                    contentDescription = layer.name,
                                    contentScale = ContentScale.Fit,
                                    colorFilter = ColorFilter.colorMatrix(colorMatrix),
                                    modifier = Modifier.fillMaxSize(0.75f)
                                )
                            }
                        }

                        LayerType.TEXT -> {
                            val tc = layer.textConfig
                            if (tc != null) {
                                // RGB Split ghost channel when RGB Split > 0
                                if (fx.rgbSplit > 0.02f) {
                                    Text(
                                        text = tc.text,
                                        color = ElectricCyan.copy(alpha = 0.55f),
                                        fontSize = tc.fontSizeSp.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = tc.letterSpacing.sp,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.offset(x = (-fx.rgbSplit * 12f).dp)
                                    )
                                    Text(
                                        text = tc.text,
                                        color = CrimsonAccent.copy(alpha = 0.55f),
                                        fontSize = tc.fontSizeSp.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = tc.letterSpacing.sp,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.offset(x = (fx.rgbSplit * 12f).dp)
                                    )
                                }
                                Text(
                                    text = tc.text,
                                    color = Color(tc.colorHex),
                                    fontSize = tc.fontSizeSp.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = tc.letterSpacing.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .padding(horizontal = 16.dp)
                                        .then(
                                            if (layer.id == selectedLayerId) {
                                                Modifier.border(
                                                    1.dp,
                                                    ElectricCyan.copy(alpha = 0.7f),
                                                    RoundedCornerShape(8.dp)
                                                ).padding(horizontal = 8.dp, vertical = 4.dp)
                                            } else Modifier
                                        )
                                )
                            }
                        }

                        LayerType.EFFECT -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.radialGradient(
                                            listOf(
                                                ElectricCyan.copy(alpha = fx.glowIntensity * 0.25f),
                                                RoyalViolet.copy(alpha = fx.chromaticAberration * 0.22f),
                                                Color.Transparent
                                            )
                                        )
                                    )
                            )
                        }

                        else -> Unit
                    }

                    // Glow & Color Grade tint overlay
                    if (fx.glowIntensity > 0.05f || fx.temperature != 0f) {
                        val tint = if (fx.temperature >= 0f) Color(0xFFFFA834) else Color(0xFF00D4FF)
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    tint.copy(alpha = (kotlin.math.abs(fx.temperature) * 0.14f).coerceAtMost(0.22f))
                                )
                        )
                    }

                    // Vignette overlay
                    if (fx.vignette > 0.05f) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.radialGradient(
                                        colorStops = arrayOf(
                                            0.45f to Color.Transparent,
                                            1.0f to Color.Black.copy(alpha = fx.vignette.coerceIn(0f, 0.85f))
                                        )
                                    )
                                )
                        )
                    }

                    // Cinema 21:9 Mask Bars
                    if (fx.maskShape == MaskShapeType.CINEMA_BARS) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(24.dp)
                                    .background(Color.Black)
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(24.dp)
                                    .background(Color.Black)
                            )
                        }
                    }
                }
            }

            // 3. Active Auto-Caption Overlay
            if (activeCaption != null) {
                Surface(
                    color = ObsidianBg.copy(alpha = 0.82f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 14.dp, vertical = 18.dp)
                        .border(1.dp, ElectricCyan.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                ) {
                    Text(
                        text = activeCaption.text,
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            // Top-left Live FX / FPS status badge
            val selected = activeLayers.firstOrNull { it.id == selectedLayerId }
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    color = ObsidianBg.copy(alpha = 0.72f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "${project.resolution} • ${project.fps}FPS",
                        style = TimecodeTextStyle,
                        fontSize = 10.sp,
                        color = ElectricCyan,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
                if (selected?.effects?.motionBlur == true) {
                    Surface(
                        color = RoyalViolet.copy(alpha = 0.82f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "MOTION BLUR",
                            style = TimecodeTextStyle,
                            fontSize = 9.sp,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoPlaybackSurface(
    uriString: String,
    playheadMs: Long,
    isPlaying: Boolean,
    speed: Float,
    volume: Float,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val player = remember(uriString) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(uriString)))
            repeatMode = Player.REPEAT_MODE_ONE
            prepare()
        }
    }

    LaunchedEffect(isPlaying) {
        player.playWhenReady = isPlaying
    }
    LaunchedEffect(speed) {
        player.setPlaybackSpeed(speed)
    }
    LaunchedEffect(volume) {
        player.volume = volume
    }
    LaunchedEffect(playheadMs, isPlaying) {
        if (!isPlaying && kotlin.math.abs(player.currentPosition - playheadMs) > 180L) {
            player.seekTo(playheadMs)
        }
    }

    DisposableEffect(player) {
        onDispose {
            player.release()
        }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
                this.player = player
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        },
        update = { view ->
            if (view.player != player) view.player = player
        },
        modifier = modifier
    )
}

@Composable
fun MultiLayerTimelineView(
    project: StudioProject,
    playheadMs: Long,
    selectedLayerId: String?,
    zoomScale: Float,
    onSelectLayer: (String) -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val pxPerSec = 58f * zoomScale
    val totalSec = (project.durationMs / 1000f).coerceAtLeast(8f)
    val timelineWidthDp = (totalSec * pxPerSec).dp
    val scrollState = rememberScrollState()
    val verticalScrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(StudioSurface)
            .border(1.dp, StudioOutline)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .horizontalScroll(scrollState)
                .padding(horizontal = 16.dp)
        ) {
            Column(
                modifier = Modifier.width(timelineWidthDp + 80.dp)
            ) {
                // Time Ruler & Beat Markers
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                        .background(StudioSurfaceVariant)
                        .clickable {
                            // Quick tap ruler to jump playhead
                            onSeek((playheadMs + 500L).coerceAtMost(project.durationMs))
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val pxPerMs = (pxPerSec.dp.toPx()) / 1000f
                        val secCount = totalSec.toInt() + 1
                        for (s in 0..secCount) {
                            val x = s * 1000f * pxPerMs
                            drawLine(
                                color = Color.White.copy(alpha = 0.25f),
                                start = Offset(x, size.height - 12.dp.toPx()),
                                end = Offset(x, size.height),
                                strokeWidth = 1.5f
                            )
                            val halfX = x + 500f * pxPerMs
                            drawLine(
                                color = Color.White.copy(alpha = 0.1f),
                                start = Offset(halfX, size.height - 6.dp.toPx()),
                                end = Offset(halfX, size.height),
                                strokeWidth = 1f
                            )
                        }
                        // Beat Markers
                        project.payload.beatMarkersMs.forEach { beatMs ->
                            val bx = beatMs * pxPerMs
                            drawCircle(
                                color = AmberPlayhead,
                                radius = 4.dp.toPx(),
                                center = Offset(bx, 8.dp.toPx())
                            )
                        }
                    }

                    // Second labels
                    for (s in 0..totalSec.toInt()) {
                        Text(
                            text = "${s}s",
                            style = TimecodeTextStyle,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .offset(x = (s * pxPerSec + 4).dp, y = 4.dp)
                        )
                    }
                }

                // Multi-Track Stack
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(verticalScrollState)
                        .padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    project.payload.layers.sortedBy { it.trackIndex }.forEach { layer ->
                        val isSelected = layer.id == selectedLayerId
                        val trackColor = when (layer.type) {
                            LayerType.VIDEO -> TrackVideoColor
                            LayerType.AUDIO -> TrackAudioColor
                            LayerType.TEXT -> TrackTextColor
                            LayerType.EFFECT -> TrackEffectColor
                            LayerType.IMAGE -> TrackImageColor
                            LayerType.CAPTION -> TrackCaptionColor
                        }
                        val trackIcon = when (layer.type) {
                            LayerType.VIDEO -> Icons.Filled.Videocam
                            LayerType.AUDIO -> Icons.Filled.AudioFile
                            LayerType.TEXT -> Icons.Filled.TextFields
                            LayerType.EFFECT -> Icons.Filled.AutoFixHigh
                            LayerType.IMAGE -> Icons.Filled.Image
                            LayerType.CAPTION -> Icons.Filled.ClosedCaption
                        }

                        val startDp = ((layer.startMs / 1000f) * pxPerSec).dp
                        val widthDp = (((layer.endMs - layer.startMs).coerceAtLeast(400L) / 1000f) * pxPerSec).dp

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                        ) {
                            Surface(
                                color = trackColor.copy(alpha = if (isSelected) 0.92f else 0.62f),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .offset(x = startDp)
                                    .width(widthDp)
                                    .fillMaxHeight()
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) ElectricCyan else Color.White.copy(alpha = 0.16f),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable { onSelectLayer(layer.id) }
                                    .testTag("timeline_clip_${layer.id}")
                            ) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    // Waveform bars inside audio / video clips
                                    if (layer.waveformSamples.isNotEmpty()) {
                                        Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 4.dp)) {
                                            val count = layer.waveformSamples.size
                                            val step = size.width / count.coerceAtLeast(1)
                                            layer.waveformSamples.forEachIndexed { idx, amp ->
                                                val h = size.height * amp * 0.7f
                                                val cx = idx * step + step * 0.5f
                                                drawLine(
                                                    color = Color.White.copy(alpha = 0.28f),
                                                    start = Offset(cx, size.height - h),
                                                    end = Offset(cx, size.height),
                                                    strokeWidth = (step * 0.45f).coerceAtLeast(2f),
                                                    cap = StrokeCap.Round
                                                )
                                            }
                                        }
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 8.dp)
                                    ) {
                                        Icon(
                                            imageVector = trackIcon,
                                            contentDescription = layer.type.displayName,
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = layer.name,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (layer.speed != 1f) {
                                            Text(
                                                text = "${layer.speed}x",
                                                style = TimecodeTextStyle,
                                                fontSize = 9.sp,
                                                color = AmberPlayhead
                                            )
                                        }
                                    }

                                    // Keyframe Diamonds
                                    val clipSpanMs = (layer.endMs - layer.startMs).coerceAtLeast(1L).toFloat()
                                    layer.keyframes.forEach { kf ->
                                        val frac = ((kf.timeMs - layer.startMs) / clipSpanMs).coerceIn(0f, 1f)
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomStart)
                                                .offset(
                                                    x = (widthDp * frac) - 4.dp,
                                                    y = (-3).dp
                                                )
                                                .size(8.dp)
                                                .graphicsLayer { rotationZ = 45f }
                                                .background(AmberPlayhead)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Captions track bar if captions exist
                    if (project.payload.captions.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(32.dp)
                        ) {
                            project.payload.captions.forEach { cap ->
                                val startDp = ((cap.startMs / 1000f) * pxPerSec).dp
                                val widthDp = (((cap.endMs - cap.startMs).coerceAtLeast(350L) / 1000f) * pxPerSec).dp
                                Surface(
                                    color = NeonEmerald.copy(alpha = 0.25f),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier
                                        .offset(x = startDp)
                                        .width(widthDp)
                                        .fillMaxHeight()
                                        .border(1.dp, NeonEmerald.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                                        .clickable { onSeek(cap.startMs) }
                                ) {
                                    Text(
                                        text = cap.text,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = NeonEmerald,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Vertical Playhead Needle
            val playheadDp = ((playheadMs / 1000f) * pxPerSec).dp
            Box(
                modifier = Modifier
                    .offset { IntOffset(playheadDp.roundToPx(), 0) }
                    .width(2.dp)
                    .fillMaxHeight()
                    .background(AmberPlayhead)
            )
            Box(
                modifier = Modifier
                    .offset { IntOffset((playheadDp - 5.dp).roundToPx(), 0) }
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(AmberPlayhead)
            )
        }
    }
}

@Composable
fun BezierGraphEditorCanvas(
    easing: EasingCurveType,
    modifier: Modifier = Modifier
) {
    Surface(
        color = ObsidianBg,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp)
            .border(1.dp, StudioOutline, RoundedCornerShape(14.dp))
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(14.dp)) {
            // Grid lines
            for (i in 0..4) {
                val y = size.height * (i / 4f)
                val x = size.width * (i / 4f)
                drawLine(Color.White.copy(alpha = 0.08f), Offset(0f, y), Offset(size.width, y), 1f)
                drawLine(Color.White.copy(alpha = 0.08f), Offset(x, 0f), Offset(x, size.height), 1f)
            }

            val path = Path()
            val samples = 48
            for (i in 0..samples) {
                val t = i / samples.toFloat()
                val v = when (easing) {
                    EasingCurveType.LINEAR -> t
                    EasingCurveType.EASE_IN_OUT -> t * t * (3f - 2f * t)
                    EasingCurveType.EXPO_OUT -> if (t >= 1f) 1f else 1f - 2f.pow(-10f * t)
                    EasingCurveType.SPRING_BOUNCE -> (1f - (1f - t).pow(3f) * kotlin.math.cos(t * Math.PI * 3.0).toFloat()).coerceIn(0f, 1.15f)
                    EasingCurveType.BEZIER_CUSTOM -> {
                        val u = 1f - t
                        3f * u * u * t * 0.25f + 3f * u * t * t * 0.9f + t * t * t
                    }
                }
                val px = t * size.width
                val py = (size.height - (v * size.height)).coerceIn(0f, size.height)
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }

            drawPath(
                path = path,
                color = ElectricCyan,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )

            // Start & end control nodes
            drawCircle(AmberPlayhead, 5.dp.toPx(), Offset(0f, size.height))
            drawCircle(AmberPlayhead, 5.dp.toPx(), Offset(size.width, 0f))
        }
    }
}
