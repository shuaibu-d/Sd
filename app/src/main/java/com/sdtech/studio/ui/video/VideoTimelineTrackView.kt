package com.sdtech.studio.ui.video

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sdtech.studio.ui.components.SDIcon
import com.sdtech.studio.ui.components.SDIconKind
import com.sdtech.studio.ui.theme.CloudWhite
import com.sdtech.studio.ui.theme.DarkSurface
import com.sdtech.studio.ui.theme.DarkSurfaceHigh
import com.sdtech.studio.ui.theme.DarkSurfaceHighest
import com.sdtech.studio.ui.theme.DeepNavy
import com.sdtech.studio.ui.theme.InnovationTeal
import com.sdtech.studio.ui.theme.MidnightNavy
import com.sdtech.studio.ui.theme.MutedTextDark
import com.sdtech.studio.ui.theme.PremiumGold
import com.sdtech.studio.video.domain.Clip
import com.sdtech.studio.video.domain.TimelineProject
import com.sdtech.studio.video.domain.Track
import com.sdtech.studio.video.domain.TrackType
import kotlin.math.abs
import kotlin.math.roundToInt

private val TRACK_HEADER_WIDTH = 72.dp
private val TRACK_ROW_HEIGHT = 52.dp
private const val BASE_DP_PER_SECOND = 48f

@Composable
fun StudioTransportBar(
    isPlaying: Boolean,
    playheadMs: Long,
    totalDurationMs: Long,
    zoomScale: Float,
    isImmersive: Boolean,
    selectedCount: Int,
    onTogglePlay: () -> Unit,
    onStepBackward: () -> Unit,
    onStepForward: () -> Unit,
    onZoomChange: (Float) -> Unit,
    onToggleImmersive: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .testTag("video_transport_bar"),
        color = DeepNavy,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Play/Pause + Frame Step + Compact Stacked Timecode
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                IconButton(
                    onClick = onStepBackward,
                    modifier = Modifier
                        .size(48.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("transport_step_back_button")
                        .semantics { contentDescription = "Step back 0.5 seconds" }
                ) {
                    SDIcon(SDIconKind.Back, tint = MutedTextDark, size = 18.dp)
                }

                IconButton(
                    onClick = onTogglePlay,
                    modifier = Modifier
                        .size(48.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("transport_play_pause_button")
                        .semantics {
                            contentDescription = if (isPlaying) "Pause video preview" else "Play video preview"
                        }
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(InnovationTeal, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isPlaying) {
                            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                Box(Modifier.size(width = 3.dp, height = 12.dp).background(MidnightNavy, RoundedCornerShape(1.dp)))
                                Box(Modifier.size(width = 3.dp, height = 12.dp).background(MidnightNavy, RoundedCornerShape(1.dp)))
                            }
                        } else {
                            SDIcon(SDIconKind.Play, tint = MidnightNavy, size = 17.dp)
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .padding(horizontal = 2.dp)
                        .testTag("transport_timecode_text")
                        .semantics {
                            contentDescription = "Timecode ${formatStudioTimecode(playheadMs)} of ${formatStudioTimecode(totalDurationMs)}"
                        }
                ) {
                    Text(
                        text = formatStudioTimecode(playheadMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = PremiumGold,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        text = "/ ${formatStudioTimecode(totalDurationMs)}",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = MutedTextDark,
                        maxLines = 1
                    )
                }
            }

            // Right: Zoom controls + Immersive mode toggle
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                IconButton(
                    onClick = { onZoomChange((zoomScale - 0.25f).coerceIn(0.4f, 3.5f)) },
                    modifier = Modifier
                        .size(48.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("transport_zoom_out_button")
                        .semantics { contentDescription = "Zoom out timeline" }
                ) {
                    Text("−", style = MaterialTheme.typography.titleMedium, color = CloudWhite, fontWeight = FontWeight.Bold)
                }

                Box(
                    modifier = Modifier
                        .height(48.dp)
                        .widthIn(min = 48.dp)
                        .minimumInteractiveComponentSize()
                        .clickable { onZoomChange(1.0f) }
                        .padding(horizontal = 4.dp)
                        .testTag("transport_zoom_level_badge")
                        .semantics { contentDescription = "Timeline zoom ${(zoomScale * 100).roundToInt()} percent. Tap to reset zoom" },
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        color = DarkSurfaceHigh,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "${(zoomScale * 100).roundToInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = CloudWhite,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                        )
                    }
                }

                IconButton(
                    onClick = { onZoomChange((zoomScale + 0.25f).coerceIn(0.4f, 3.5f)) },
                    modifier = Modifier
                        .size(48.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("transport_zoom_in_button")
                        .semantics { contentDescription = "Zoom in timeline" }
                ) {
                    Text("+", style = MaterialTheme.typography.titleMedium, color = CloudWhite, fontWeight = FontWeight.Bold)
                }

                IconButton(
                    onClick = onToggleImmersive,
                    modifier = Modifier
                        .size(48.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("transport_immersive_button")
                        .semantics {
                            contentDescription = if (isImmersive) "Exit full screen immersive preview" else "Enter full screen immersive preview"
                        }
                ) {
                    Surface(
                        color = if (isImmersive) PremiumGold.copy(alpha = 0.22f) else DarkSurfaceHigh,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.size(34.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = if (isImmersive) "⤡" else "⤢",
                                style = MaterialTheme.typography.titleSmall,
                                color = if (isImmersive) PremiumGold else CloudWhite
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MultiTrackTimelineView(
    project: TimelineProject,
    playheadMs: Long,
    selectedClipIds: Set<String>,
    zoomScale: Float,
    scrollOffsetMs: Long,
    onPlayheadChange: (Long) -> Unit,
    onZoomAndScrollChange: (newZoom: Float, newScrollMs: Long) -> Unit,
    onTapClip: (String) -> Unit,
    onLongPressClip: (String) -> Unit,
    onClearSelection: () -> Unit,
    onMoveClip: (clipId: String, newStartMs: Long, isFinished: Boolean) -> Unit,
    onTrimClipStart: (clipId: String, newStartMs: Long, newDurationMs: Long, newSourceInMs: Long, isFinished: Boolean) -> Unit,
    onTrimClipEnd: (clipId: String, newDurationMs: Long, newSourceOutMs: Long, isFinished: Boolean) -> Unit,
    onToggleTrackMute: (String) -> Unit = {},
    onToggleTrackLock: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val dpPerSecond = BASE_DP_PER_SECOND * zoomScale.coerceIn(0.4f, 3.5f)
    val pxPerSecond = with(density) { dpPerSecond.dp.toPx() }.coerceAtLeast(12f)
    val pxPerMs = pxPerSecond / 1_000f

    var activeSnapGuideMs by remember { mutableStateOf<Long?>(null) }
    val currentZoom by rememberUpdatedState(zoomScale)
    val currentScrollMs by rememberUpdatedState(scrollOffsetMs)
    val currentProject by rememberUpdatedState(project)
    val currentPlayheadMs by rememberUpdatedState(playheadMs)

    val maxTimelineMs = (project.totalDurationMs + 6_000L).coerceAtLeast(12_000L)
    val verticalScrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MidnightNavy)
            .testTag("multi_track_timeline")
            .semantics { contentDescription = "Multi-track video timeline" }
            // Multi-touch Pinch-to-Zoom & Two-Finger Pan
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val pointerCount = event.changes.count { it.pressed }
                        if (pointerCount >= 2) {
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()
                            val centroidSize = event.calculateCentroidSize(useCurrent = false)
                            if (abs(zoomChange - 1f) > 0.01f || abs(panChange.x) > 1f || centroidSize > 8f) {
                                val nextZoom = (currentZoom * zoomChange).coerceIn(0.4f, 3.5f)
                                val effPxPerMs = (with(density) { (BASE_DP_PER_SECOND * nextZoom).dp.toPx() } / 1_000f).coerceAtLeast(0.01f)
                                val deltaScrollMs = (-panChange.x / effPxPerMs).toLong()
                                val nextScroll = (currentScrollMs + deltaScrollMs).coerceIn(0L, maxTimelineMs)
                                onZoomAndScrollChange(nextZoom, nextScroll)
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
    ) {
        // Time Ruler Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp)
                .background(DeepNavy)
        ) {
            Box(
                modifier = Modifier
                    .width(TRACK_HEADER_WIDTH)
                    .fillMaxHeight()
                    .background(DeepNavy)
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = "${project.fps} FPS",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = PremiumGold
                )
            }

            // Interactive Time Ruler
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .testTag("timeline_ruler")
                    .semantics { contentDescription = "Timeline time ruler and playhead scrubber" }
                    .pointerInput(pxPerMs) {
                        detectTapGestures { offset ->
                            val rawMs = currentScrollMs + (offset.x / pxPerMs).toLong()
                            val snap = TimelineSnapEngine.snapTime(
                                candidateMs = rawMs.coerceIn(0L, currentProject.totalDurationMs.coerceAtLeast(1_000L)),
                                project = currentProject,
                                playheadMs = currentPlayheadMs
                            )
                            if (snap.didSnap) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            onPlayheadChange(snap.snappedMs)
                        }
                    }
                    .pointerInput(pxPerMs) {
                        var lastSnappedTarget: Long? = null
                        detectHorizontalDragGestures(
                            onDragEnd = { activeSnapGuideMs = null; lastSnappedTarget = null },
                            onDragCancel = { activeSnapGuideMs = null; lastSnappedTarget = null }
                        ) { change, _ ->
                            change.consume()
                            val rawMs = currentScrollMs + (change.position.x / pxPerMs).toLong()
                            val snap = TimelineSnapEngine.snapTime(
                                candidateMs = rawMs.coerceIn(0L, currentProject.totalDurationMs.coerceAtLeast(1_000L)),
                                project = currentProject,
                                playheadMs = currentPlayheadMs
                            )
                            activeSnapGuideMs = snap.snapGuideMs
                            if (snap.didSnap && snap.snapGuideMs != lastSnappedTarget) {
                                lastSnappedTarget = snap.snapGuideMs
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            } else if (!snap.didSnap) {
                                lastSnappedTarget = null
                            }
                            onPlayheadChange(snap.snappedMs)
                        }
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val widthPx = size.width
                    val heightPx = size.height
                    val startSec = (scrollOffsetMs / 1_000L).toInt()
                    val visibleSecs = (widthPx / pxPerSecond).toInt() + 2
                    for (s in startSec..(startSec + visibleSecs)) {
                        val secMs = s * 1_000L
                        val x = (secMs - scrollOffsetMs) * pxPerMs
                        if (x in -20f..(widthPx + 20f)) {
                            drawLine(
                                color = Color(0xFF4A5E78),
                                start = Offset(x, heightPx * 0.45f),
                                end = Offset(x, heightPx),
                                strokeWidth = 1.5f
                            )
                            // Half-second sub-tick
                            val halfX = x + 500f * pxPerMs
                            if (halfX <= widthPx) {
                                drawLine(
                                    color = Color(0xFF30445E),
                                    start = Offset(halfX, heightPx * 0.7f),
                                    end = Offset(halfX, heightPx),
                                    strokeWidth = 1f
                                )
                            }
                        }
                    }

                    // Playhead cap on ruler
                    val playheadX = ((playheadMs - scrollOffsetMs) * pxPerMs).coerceIn(0f, widthPx)
                    val capPath = Path().apply {
                        moveTo(playheadX - 6.dp.toPx(), 2.dp.toPx())
                        lineTo(playheadX + 6.dp.toPx(), 2.dp.toPx())
                        lineTo(playheadX + 6.dp.toPx(), 10.dp.toPx())
                        lineTo(playheadX, 16.dp.toPx())
                        lineTo(playheadX - 6.dp.toPx(), 10.dp.toPx())
                        close()
                    }
                    drawPath(capPath, PremiumGold)
                }
            }
        }

        // Tracks Stack + Playhead Overlay
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(verticalScrollState)
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                project.tracks.forEach { track ->
                    TimelineTrackRow(
                        track = track,
                        project = project,
                        playheadMs = playheadMs,
                        selectedClipIds = selectedClipIds,
                        scrollOffsetMs = scrollOffsetMs,
                        pxPerMs = pxPerMs,
                        dpPerSecond = dpPerSecond,
                        onTapClip = onTapClip,
                        onLongPressClip = onLongPressClip,
                        onClearSelection = onClearSelection,
                        onMoveClip = { clipId, newStart, finished, snapGuide ->
                            activeSnapGuideMs = snapGuide
                            onMoveClip(clipId, newStart, finished)
                        },
                        onTrimClipStart = { clipId, newStart, newDur, newIn, finished, snapGuide ->
                            activeSnapGuideMs = snapGuide
                            onTrimClipStart(clipId, newStart, newDur, newIn, finished)
                        },
                        onTrimClipEnd = { clipId, newDur, newOut, finished, snapGuide ->
                            activeSnapGuideMs = snapGuide
                            onTrimClipEnd(clipId, newDur, newOut, finished)
                        },
                        onToggleMute = { onToggleTrackMute(track.id) },
                        onToggleLock = { onToggleTrackLock(track.id) }
                    )
                }
                Spacer(Modifier.height(12.dp))
            }

            // Playhead & Magnetic Snap Guide Line Overlay across all tracks
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = TRACK_HEADER_WIDTH)
            ) {
                activeSnapGuideMs?.let { snapMs ->
                    val snapX = (snapMs - scrollOffsetMs) * pxPerMs
                    if (snapX in 0f..size.width) {
                        drawLine(
                            color = InnovationTeal,
                            start = Offset(snapX, 0f),
                            end = Offset(snapX, size.height),
                            strokeWidth = 2.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                }

                val playheadX = (playheadMs - scrollOffsetMs) * pxPerMs
                if (playheadX in 0f..size.width) {
                    drawLine(
                        color = PremiumGold,
                        start = Offset(playheadX, 0f),
                        end = Offset(playheadX, size.height),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }
        }
    }
}

@Composable
private fun TimelineTrackRow(
    track: Track,
    project: TimelineProject,
    playheadMs: Long,
    selectedClipIds: Set<String>,
    scrollOffsetMs: Long,
    pxPerMs: Float,
    dpPerSecond: Float,
    onTapClip: (String) -> Unit,
    onLongPressClip: (String) -> Unit,
    onClearSelection: () -> Unit,
    onMoveClip: (clipId: String, newStartMs: Long, isFinished: Boolean, snapGuideMs: Long?) -> Unit,
    onTrimClipStart: (clipId: String, newStartMs: Long, newDurationMs: Long, newSourceInMs: Long, isFinished: Boolean, snapGuideMs: Long?) -> Unit,
    onTrimClipEnd: (clipId: String, newDurationMs: Long, newSourceOutMs: Long, isFinished: Boolean, snapGuideMs: Long?) -> Unit,
    onToggleMute: () -> Unit,
    onToggleLock: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(TRACK_ROW_HEIGHT)
            .testTag("timeline_track_${track.type.name.lowercase()}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Track Header (Left Column)
        Surface(
            modifier = Modifier
                .width(TRACK_HEADER_WIDTH)
                .fillMaxHeight()
                .clickable(onClick = onToggleMute)
                .semantics { contentDescription = "${track.name} track header" },
            color = DarkSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    SDIcon(
                        kind = trackIconFor(track.type),
                        tint = trackAccentColor(track.type),
                        size = 13.dp
                    )
                    Text(
                        text = shortTrackLabel(track.type),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = if (track.muted) MutedTextDark.copy(alpha = 0.5f) else CloudWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    text = when {
                        track.locked -> "LOCKED"
                        track.muted -> "MUTED"
                        else -> "${track.clips.size} clips"
                    },
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = if (track.muted || track.locked) PremiumGold else MutedTextDark,
                    maxLines = 1
                )
            }
        }

        // Track Lane
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(DarkSurface.copy(alpha = 0.65f))
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onClearSelection() })
                }
        ) {
            track.clips.forEach { clip ->
                TimelineClipItem(
                    clip = clip,
                    trackType = track.type,
                    isSelected = clip.id in selectedClipIds,
                    isMultiSelect = selectedClipIds.size > 1 && clip.id in selectedClipIds,
                    project = project,
                    playheadMs = playheadMs,
                    scrollOffsetMs = scrollOffsetMs,
                    pxPerMs = pxPerMs,
                    dpPerSecond = dpPerSecond,
                    isLocked = track.locked,
                    onTap = { onTapClip(clip.id) },
                    onLongPress = { onLongPressClip(clip.id) },
                    onMove = { newStart, finished, snapGuide ->
                        onMoveClip(clip.id, newStart, finished, snapGuide)
                    },
                    onTrimStart = { newStart, newDur, newIn, finished, snapGuide ->
                        onTrimClipStart(clip.id, newStart, newDur, newIn, finished, snapGuide)
                    },
                    onTrimEnd = { newDur, newOut, finished, snapGuide ->
                        onTrimClipEnd(clip.id, newDur, newOut, finished, snapGuide)
                    }
                )
            }
        }
    }
}

@Composable
private fun TimelineClipItem(
    clip: Clip,
    trackType: TrackType,
    isSelected: Boolean,
    isMultiSelect: Boolean,
    project: TimelineProject,
    playheadMs: Long,
    scrollOffsetMs: Long,
    pxPerMs: Float,
    dpPerSecond: Float,
    isLocked: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onMove: (newStartMs: Long, isFinished: Boolean, snapGuideMs: Long?) -> Unit,
    onTrimStart: (newStartMs: Long, newDurationMs: Long, newSourceInMs: Long, isFinished: Boolean, snapGuideMs: Long?) -> Unit,
    onTrimEnd: (newDurationMs: Long, newSourceOutMs: Long, isFinished: Boolean, snapGuideMs: Long?) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val currentClip by rememberUpdatedState(clip)
    val currentProject by rememberUpdatedState(project)
    val currentPlayhead by rememberUpdatedState(playheadMs)
    val currentPxPerMs by rememberUpdatedState(pxPerMs)
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnLongPress by rememberUpdatedState(onLongPress)
    val currentOnMove by rememberUpdatedState(onMove)
    val currentOnTrimStart by rememberUpdatedState(onTrimStart)
    val currentOnTrimEnd by rememberUpdatedState(onTrimEnd)

    val offsetPx = ((clip.startMs - scrollOffsetMs) * pxPerMs).roundToInt()
    val rawWidthDp = (clip.durationMs / 1_000f) * dpPerSecond
    val clipWidthDp: Dp = rawWidthDp.coerceAtLeast(56f).dp
    val accent = trackAccentColor(trackType)

    val clipTitle = remember(clip.id, clip.text, clip.sceneType, trackType) {
        when {
            clip.text.isNotBlank() -> clip.text.lineSequence().first().take(24)
            clip.metadata["role"] == "logo" -> "Brand Logo"
            clip.metadata["role"] == "voice" -> "Voice Take"
            else -> "${clip.sceneType.name} (${formatStudioTimecode(clip.durationMs)})"
        }
    }

    Box(
        modifier = Modifier
            .offset { IntOffset(offsetPx, 0) }
            .width(clipWidthDp)
            .height(48.dp)
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        accent.copy(alpha = if (isSelected) 0.42f else 0.24f),
                        DarkSurfaceHighest.copy(alpha = 0.92f)
                    )
                )
            )
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = when {
                    isSelected -> PremiumGold
                    else -> accent.copy(alpha = 0.55f)
                },
                shape = RoundedCornerShape(8.dp)
            )
            .testTag("timeline_clip_${clip.id}")
            .semantics {
                contentDescription = buildString {
                    append("Timeline clip $clipTitle, ")
                    append("start ${clip.startMs}ms, duration ${clip.durationMs}ms")
                    if (isSelected) append(", selected")
                    if (isMultiSelect) append(", multi-selected")
                }
            }
            // Tap to select, Long-press to multi-select
            .pointerInput(clip.id) {
                detectTapGestures(
                    onTap = { currentOnTap() },
                    onLongPress = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        currentOnLongPress()
                    }
                )
            }
            // Drag clip body to move
            .pointerInput(clip.id, isLocked) {
                if (isLocked) return@pointerInput
                var initialStartMs = 0L
                var dragAccumPx = 0f
                var lastSnapTarget: Long? = null
                detectHorizontalDragGestures(
                    onDragStart = {
                        initialStartMs = currentClip.startMs
                        dragAccumPx = 0f
                        lastSnapTarget = null
                    },
                    onDragEnd = {
                        val deltaMs = (dragAccumPx / currentPxPerMs).toLong()
                        val rawStart = (initialStartMs + deltaMs).coerceAtLeast(0L)
                        currentOnMove(rawStart, true, null)
                        lastSnapTarget = null
                    },
                    onDragCancel = {
                        lastSnapTarget = null
                    }
                ) { change, dragAmount ->
                    change.consume()
                    dragAccumPx += dragAmount
                    val deltaMs = (dragAccumPx / currentPxPerMs).toLong()
                    val candidateStart = (initialStartMs + deltaMs).coerceAtLeast(0L)
                    val snap = TimelineSnapEngine.snapTime(
                        candidateMs = candidateStart,
                        project = currentProject,
                        playheadMs = currentPlayhead,
                        excludeClipIds = setOf(currentClip.id)
                    )
                    if (snap.didSnap && snap.snapGuideMs != lastSnapTarget) {
                        lastSnapTarget = snap.snapGuideMs
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    } else if (!snap.didSnap) {
                        lastSnapTarget = null
                    }
                    currentOnMove(snap.snappedMs, false, snap.snapGuideMs)
                }
            }
    ) {
        // Waveform or visual stripe inside clip
        if (clip.waveformLevels.isNotEmpty()) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                val levels = clip.waveformLevels
                val step = size.width / levels.size.coerceAtLeast(1)
                for (i in levels.indices) {
                    val h = (levels[i].coerceIn(0.1f, 1f) * size.height).coerceAtLeast(2f)
                    drawLine(
                        color = accent.copy(alpha = 0.65f),
                        start = Offset(i * step + step / 2f, (size.height - h) / 2f),
                        end = Offset(i * step + step / 2f, (size.height + h) / 2f),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }
        }

        // Clip Label & Duration
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = if (isSelected) 16.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = clipTitle,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = CloudWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (isMultiSelect) {
                Surface(
                    color = PremiumGold,
                    shape = CircleShape,
                    modifier = Modifier.padding(start = 4.dp).size(14.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        SDIcon(SDIconKind.Check, tint = MidnightNavy, size = 10.dp)
                    }
                }
            }
        }

        // Left & Right Trim Handles when Selected (48dp minimum interactive touch targets!)
        if (isSelected && !isLocked) {
            // Left Trim Handle
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(width = 24.dp, height = 48.dp)
                    .minimumInteractiveComponentSize()
                    .testTag("trim_handle_left_${clip.id}")
                    .semantics {
                        contentDescription = "Trim start handle for clip ${clip.id}"
                    }
                    .pointerInput(clip.id) {
                        var initialStartMs = 0L
                        var initialDurationMs = 0L
                        var initialSourceInMs = 0L
                        var dragAccumPx = 0f
                        var lastSnapTarget: Long? = null

                        detectHorizontalDragGestures(
                            onDragStart = {
                                initialStartMs = currentClip.startMs
                                initialDurationMs = currentClip.durationMs
                                initialSourceInMs = currentClip.sourceInMs
                                dragAccumPx = 0f
                                lastSnapTarget = null
                            },
                            onDragEnd = {
                                val rawDeltaMs = (dragAccumPx / currentPxPerMs).toLong()
                                val maxTrimIn = (initialDurationMs - TimelineSnapEngine.MIN_CLIP_DURATION_MS).coerceAtLeast(0L)
                                val clampedDelta = rawDeltaMs.coerceIn(-initialStartMs, maxTrimIn)
                                val newStart = (initialStartMs + clampedDelta).coerceAtLeast(0L)
                                val newDur = (initialDurationMs - clampedDelta).coerceAtLeast(TimelineSnapEngine.MIN_CLIP_DURATION_MS)
                                val newSourceIn = (initialSourceInMs + clampedDelta).coerceAtLeast(0L)
                                currentOnTrimStart(newStart, newDur, newSourceIn, true, null)
                                lastSnapTarget = null
                            },
                            onDragCancel = {
                                lastSnapTarget = null
                            }
                        ) { change, dragAmount ->
                            change.consume()
                            dragAccumPx += dragAmount
                            val rawDeltaMs = (dragAccumPx / currentPxPerMs).toLong()
                            val maxTrimIn = (initialDurationMs - TimelineSnapEngine.MIN_CLIP_DURATION_MS).coerceAtLeast(0L)
                            val clampedDelta = rawDeltaMs.coerceIn(-initialStartMs, maxTrimIn)
                            val candidateStart = (initialStartMs + clampedDelta).coerceAtLeast(0L)
                            val snap = TimelineSnapEngine.snapTime(
                                candidateMs = candidateStart,
                                project = currentProject,
                                playheadMs = currentPlayhead,
                                excludeClipIds = setOf(currentClip.id)
                            )
                            val snappedDelta = (snap.snappedMs - initialStartMs).coerceIn(-initialStartMs, maxTrimIn)
                            val finalStart = (initialStartMs + snappedDelta).coerceAtLeast(0L)
                            val finalDur = (initialDurationMs - snappedDelta).coerceAtLeast(TimelineSnapEngine.MIN_CLIP_DURATION_MS)
                            val finalSourceIn = (initialSourceInMs + snappedDelta).coerceAtLeast(0L)
                            if (snap.didSnap && snap.snapGuideMs != lastSnapTarget) {
                                lastSnapTarget = snap.snapGuideMs
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            } else if (!snap.didSnap) {
                                lastSnapTarget = null
                            }
                            currentOnTrimStart(finalStart, finalDur, finalSourceIn, false, snap.snapGuideMs)
                        }
                    },
                contentAlignment = Alignment.CenterStart
            ) {
                Box(
                    modifier = Modifier
                        .width(12.dp)
                        .fillMaxHeight()
                        .background(
                            PremiumGold,
                            RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 2.dp, height = 16.dp)
                            .background(MidnightNavy, RoundedCornerShape(1.dp))
                    )
                }
            }

            // Right Trim Handle
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(width = 24.dp, height = 48.dp)
                    .minimumInteractiveComponentSize()
                    .testTag("trim_handle_right_${clip.id}")
                    .semantics {
                        contentDescription = "Trim end handle for clip ${clip.id}"
                    }
                    .pointerInput(clip.id) {
                        var initialStartMs = 0L
                        var initialDurationMs = 0L
                        var initialSourceInMs = 0L
                        var dragAccumPx = 0f
                        var lastSnapTarget: Long? = null

                        detectHorizontalDragGestures(
                            onDragStart = {
                                initialStartMs = currentClip.startMs
                                initialDurationMs = currentClip.durationMs
                                initialSourceInMs = currentClip.sourceInMs
                                dragAccumPx = 0f
                                lastSnapTarget = null
                            },
                            onDragEnd = {
                                val rawDeltaMs = (dragAccumPx / currentPxPerMs).toLong()
                                val newDur = (initialDurationMs + rawDeltaMs).coerceAtLeast(TimelineSnapEngine.MIN_CLIP_DURATION_MS)
                                val newOut = initialSourceInMs + newDur
                                currentOnTrimEnd(newDur, newOut, true, null)
                                lastSnapTarget = null
                            },
                            onDragCancel = {
                                lastSnapTarget = null
                            }
                        ) { change, dragAmount ->
                            change.consume()
                            dragAccumPx += dragAmount
                            val rawDeltaMs = (dragAccumPx / currentPxPerMs).toLong()
                            val candidateDur = (initialDurationMs + rawDeltaMs).coerceAtLeast(TimelineSnapEngine.MIN_CLIP_DURATION_MS)
                            val candidateEnd = initialStartMs + candidateDur
                            val snap = TimelineSnapEngine.snapTime(
                                candidateMs = candidateEnd,
                                project = currentProject,
                                playheadMs = currentPlayhead,
                                excludeClipIds = setOf(currentClip.id)
                            )
                            val finalDur = (snap.snappedMs - initialStartMs).coerceAtLeast(TimelineSnapEngine.MIN_CLIP_DURATION_MS)
                            val finalOut = initialSourceInMs + finalDur
                            if (snap.didSnap && snap.snapGuideMs != lastSnapTarget) {
                                lastSnapTarget = snap.snapGuideMs
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            } else if (!snap.didSnap) {
                                lastSnapTarget = null
                            }
                            currentOnTrimEnd(finalDur, finalOut, false, snap.snapGuideMs)
                        }
                    },
                contentAlignment = Alignment.CenterEnd
            ) {
                Box(
                    modifier = Modifier
                        .width(12.dp)
                        .fillMaxHeight()
                        .background(
                            PremiumGold,
                            RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 2.dp, height = 16.dp)
                            .background(MidnightNavy, RoundedCornerShape(1.dp))
                    )
                }
            }
        }
    }
}

private fun trackIconFor(type: TrackType): SDIconKind = when (type) {
    TrackType.VIDEO -> SDIconKind.Video
    TrackType.OVERLAY -> SDIconKind.Brand
    TrackType.TEXT -> SDIconKind.Text
    TrackType.CAPTION -> SDIconKind.Edit
    TrackType.AUDIO_VOICE -> SDIconKind.Mic
    TrackType.AUDIO_MUSIC -> SDIconKind.Sparkle
    TrackType.AUDIO_SFX -> SDIconKind.Play
}

private fun shortTrackLabel(type: TrackType): String = when (type) {
    TrackType.VIDEO -> "Video"
    TrackType.OVERLAY -> "Logo"
    TrackType.TEXT -> "Text"
    TrackType.CAPTION -> "Caption"
    TrackType.AUDIO_VOICE -> "Voice"
    TrackType.AUDIO_MUSIC -> "Music"
    TrackType.AUDIO_SFX -> "SFX"
}

private fun trackAccentColor(type: TrackType): Color = when (type) {
    TrackType.VIDEO -> InnovationTeal
    TrackType.OVERLAY -> PremiumGold
    TrackType.TEXT -> Color(0xFF38C6BD)
    TrackType.CAPTION -> Color(0xFFE5C467)
    TrackType.AUDIO_VOICE -> Color(0xFF28B59A)
    TrackType.AUDIO_MUSIC -> Color(0xFF5A9BD5)
    TrackType.AUDIO_SFX -> Color(0xFFD98B5B)
}
