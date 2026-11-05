package com.sdtech.studio.ui.screens

import android.app.Activity
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sdtech.studio.SDStudioApplication
import com.sdtech.studio.data.local.entity.Project
import com.sdtech.studio.data.model.ProjectStatus
import com.sdtech.studio.data.model.ProjectType
import com.sdtech.studio.performance.ErrorBoundary
import com.sdtech.studio.performance.RenderDispatcher
import com.sdtech.studio.performance.ThermalGuard
import com.sdtech.studio.render.TemplateRepository
import com.sdtech.studio.ui.components.SDIcon
import com.sdtech.studio.ui.components.SDIconKind
import com.sdtech.studio.ui.theme.CloudWhite
import com.sdtech.studio.ui.theme.DarkSurface
import com.sdtech.studio.ui.theme.DarkSurfaceHigh
import com.sdtech.studio.ui.theme.DeepNavy
import com.sdtech.studio.ui.theme.InnovationTeal
import com.sdtech.studio.ui.theme.MidnightNavy
import com.sdtech.studio.ui.theme.MutedTextDark
import com.sdtech.studio.ui.theme.PremiumGold
import com.sdtech.studio.ui.video.MultiTrackTimelineView
import com.sdtech.studio.ui.video.StudioBottomContextToolbar
import com.sdtech.studio.ui.video.StudioBottomSheetPanel
import com.sdtech.studio.ui.video.StudioInspectorContent
import com.sdtech.studio.ui.video.StudioToolTab
import com.sdtech.studio.ui.video.StudioTransportBar
import com.sdtech.studio.ui.video.TimelineSnapEngine
import com.sdtech.studio.ui.video.findActiveClipsAtPlayhead
import com.sdtech.studio.ui.video.formatStudioTimecode
import com.sdtech.studio.ui.video.renderScenePreviewBitmap
import com.sdtech.studio.video.AndroidMediaPersistenceGateway
import com.sdtech.studio.video.Scene
import com.sdtech.studio.video.SceneBackgroundType
import com.sdtech.studio.video.SceneTextStyle
import com.sdtech.studio.video.SceneType
import com.sdtech.studio.video.ScriptSceneSplitter
import com.sdtech.studio.video.domain.AddClipCommand
import com.sdtech.studio.video.domain.Clip
import com.sdtech.studio.video.domain.CommandHistory
import com.sdtech.studio.video.domain.DeleteClipCommand
import com.sdtech.studio.video.domain.GroupCommand
import com.sdtech.studio.video.domain.MoveClipCommand
import com.sdtech.studio.video.domain.ProjectDocumentMigrator
import com.sdtech.studio.video.domain.ResolvedMediaReference
import com.sdtech.studio.video.domain.SetPropertyCommand
import com.sdtech.studio.video.domain.SplitClipCommand
import com.sdtech.studio.video.domain.TimelineAutosaveController
import com.sdtech.studio.video.domain.TimelineCommand
import com.sdtech.studio.video.domain.TimelineProject
import com.sdtech.studio.video.domain.TrackType
import com.sdtech.studio.video.domain.TrimClipCommand
import com.sdtech.studio.video.domain.toScenes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

@Composable
fun VideoScriptEditorScreen(
    projectIdArg: String? = null,
    onBack: () -> Unit = {},
    onOpenVoiceOver: (String) -> Unit = {},
    onOpenPreview: (String) -> Unit = {},
    onExport: (String) -> Unit = onOpenPreview,
    initialProjectForTest: TimelineProject? = null,
    onProjectChangedForTest: ((TimelineProject, Set<String>) -> Unit)? = null
) {
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val app = remember(context) {
        runCatching { (context.applicationContext as SDStudioApplication).appContainer }.getOrNull()
    }
    val mediaResolver = remember(context) { AndroidMediaPersistenceGateway.resolver(context) }
    val scope = rememberCoroutineScope()

    var projectId by rememberSaveable {
        mutableStateOf(initialProjectForTest?.id ?: projectIdArg ?: UUID.randomUUID().toString())
    }
    var ratio by rememberSaveable {
        mutableStateOf(initialProjectForTest?.canvasRatio ?: "9:16")
    }
    var projectTitle by rememberSaveable { mutableStateOf("Explainer video") }

    val defaultInitialScenes = remember {
        listOf(
            Scene(
                id = "scene_intro",
                type = SceneType.INTRO,
                text = "Your brand story starts here.\nIdeas that move people."
            ),
            Scene(
                id = "scene_outro",
                type = SceneType.OUTRO,
                text = "Follow for more.\nVisit our website or message us on WhatsApp."
            )
        )
    }

    val commandHistory = remember(projectId) {
        CommandHistory(
            initialProject = initialProjectForTest ?: ProjectDocumentMigrator.fromScenes(
                projectId = projectId,
                canvasRatio = ratio,
                scenes = defaultInitialScenes
            ),
            maxDepth = CommandHistory.DEFAULT_MAX_DEPTH
        )
    }
    var timelineProject by remember { mutableStateOf(commandHistory.currentProject) }
    var canUndo by remember { mutableStateOf(false) }
    var canRedo by remember { mutableStateOf(false) }
    val scenes = remember(timelineProject) { timelineProject.toScenes() }

    // Selection & Multi-Selection State
    var selectedClipIds by remember {
        val firstClipId = timelineProject.findTrackByType(TrackType.VIDEO)?.clips?.firstOrNull()?.id
        mutableStateOf(setOfNotNull(firstClipId))
    }
    var selectedSceneIndex by rememberSaveable { mutableIntStateOf(0) }
    var activeToolTab by rememberSaveable { mutableStateOf(StudioToolTab.TEXT) }
    var isBottomSheetOpen by rememberSaveable { mutableStateOf(false) }

    // Timeline & Playback State
    var playheadMs by rememberSaveable { mutableLongStateOf(0L) }
    var isPlaying by rememberSaveable { mutableStateOf(false) }
    var zoomScale by rememberSaveable { mutableFloatStateOf(1.0f) }
    var scrollOffsetMs by rememberSaveable { mutableLongStateOf(0L) }
    var isImmersiveMode by rememberSaveable { mutableStateOf(false) }
    var showSafeZones by rememberSaveable { mutableStateOf(true) }
    var canvasSplitFraction by rememberSaveable { mutableFloatStateOf(0.45f) }
    var showRenameDialog by rememberSaveable { mutableStateOf(false) }

    var projectCreated by remember { mutableStateOf(initialProjectForTest != null) }
    var isSaving by remember { mutableStateOf(false) }
    var previewBitmaps by remember { mutableStateOf<Map<String, Bitmap>>(emptyMap()) }
    var toastMessage by remember { mutableStateOf<String?>(null) }

    val brandKitFlow = remember(app) { app?.brandKitRepository?.brandKit }
    val logosFlow = remember(app) { app?.brandKitRepository?.logoAssets }
    val brandKit = brandKitFlow?.collectAsState(initial = null)?.value
    val logos = logosFlow?.collectAsState(initial = emptyList())?.value.orEmpty()

    // Notify test observer whenever project or selection changes
    LaunchedEffect(timelineProject, selectedClipIds) {
        onProjectChangedForTest?.invoke(timelineProject, selectedClipIds)
    }

    // Immersive mode system bars controller
    DisposableEffect(isImmersiveMode, view) {
        val window = (context as? Activity)?.window
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, view)
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (isImmersiveMode) {
                controller.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            val win = (context as? Activity)?.window
            if (win != null) {
                WindowCompat.getInsetsController(win, view)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // Playback loop
    LaunchedEffect(isPlaying, timelineProject.totalDurationMs) {
        if (!isPlaying) return@LaunchedEffect
        val total = timelineProject.totalDurationMs.coerceAtLeast(1_000L)
        if (playheadMs >= total) playheadMs = 0L
        while (isPlaying) {
            delay(50L)
            val next = playheadMs + 50L
            if (next >= total) {
                playheadMs = total
                isPlaying = false
            } else {
                playheadMs = next
            }
        }
    }

    suspend fun persistProject(projectToSave: TimelineProject) {
        val container = app ?: return
        if (initialProjectForTest != null) return
        isSaving = true
        val now = System.currentTimeMillis()
        val existing = container.projectRepository.getProject(projectId)
        val updatedDoc = projectToSave.copy(
            id = projectId,
            canvasRatio = ratio,
            createdAtMs = existing?.createdAt ?: now,
            updatedAtMs = now
        )
        container.projectRepository.saveProject(
            Project(
                id = projectId,
                type = ProjectType.VIDEO,
                title = projectTitle.ifBlank { "Explainer video" },
                ratio = ratio,
                status = existing?.status ?: ProjectStatus.DRAFT,
                payloadJson = ProjectDocumentMigrator.encodeProjectDocument(updatedDoc),
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
                thumbnailPath = existing?.thumbnailPath,
                caption = existing?.caption.orEmpty(),
                documentVersion = TimelineProject.CURRENT_VERSION
            )
        )
        projectCreated = true
        isSaving = false
    }

    val autosaveController = remember(projectId, scope) {
        TimelineAutosaveController(
            scope = scope,
            debounceMs = TimelineAutosaveController.DEFAULT_AUTOSAVE_DEBOUNCE_MS,
            onSave = { doc -> persistProject(doc) }
        )
    }

    fun applyCommand(command: TimelineCommand, commitGestureAfter: Boolean = false) {
        val updated = commandHistory.execute(command)
        if (commitGestureAfter) {
            commandHistory.commitSliderGesture()
        }
        timelineProject = updated
        ratio = updated.canvasRatio
        canUndo = commandHistory.canUndo
        canRedo = commandHistory.canRedo
        if (projectCreated && initialProjectForTest == null) {
            autosaveController.onEdit(updated)
        }
    }

    fun performUndo() {
        val prev = commandHistory.undo() ?: return
        timelineProject = prev
        ratio = prev.canvasRatio
        canUndo = commandHistory.canUndo
        canRedo = commandHistory.canRedo
        selectedSceneIndex = selectedSceneIndex.coerceIn(0, (prev.toScenes().size - 1).coerceAtLeast(0))
        selectedClipIds = selectedClipIds.filter { prev.findClip(it) != null }.toSet()
        if (projectCreated && initialProjectForTest == null) {
            autosaveController.onEdit(prev)
        }
    }

    fun performRedo() {
        val next = commandHistory.redo() ?: return
        timelineProject = next
        ratio = next.canvasRatio
        canUndo = commandHistory.canUndo
        canRedo = commandHistory.canRedo
        selectedSceneIndex = selectedSceneIndex.coerceIn(0, (next.toScenes().size - 1).coerceAtLeast(0))
        selectedClipIds = selectedClipIds.filter { next.findClip(it) != null }.toSet()
        if (projectCreated && initialProjectForTest == null) {
            autosaveController.onEdit(next)
        }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val currentScene = scenes.getOrNull(selectedSceneIndex)
        if (uri != null && currentScene != null) {
            val resolved = runCatching { mediaResolver.resolve(uri.toString(), "jpg") }
                .getOrDefault(ResolvedMediaReference(uri.toString(), false))
            applyCommand(
                SetPropertyCommand.updateScene(
                    sceneId = currentScene.id,
                    propertyKey = "background"
                ) {
                    it.copy(
                        backgroundType = SceneBackgroundType.IMAGE,
                        backgroundUri = resolved.storedUriOrPath
                    )
                }
            )
        }
    }

    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val currentScene = scenes.getOrNull(selectedSceneIndex)
        if (uri != null && currentScene != null) {
            val resolved = runCatching { mediaResolver.resolve(uri.toString(), "mp4") }
                .getOrDefault(ResolvedMediaReference(uri.toString(), false))
            applyCommand(
                SetPropertyCommand.updateScene(
                    sceneId = currentScene.id,
                    propertyKey = "background"
                ) {
                    it.copy(
                        backgroundType = SceneBackgroundType.VIDEO_CLIP,
                        backgroundUri = resolved.storedUriOrPath
                    )
                }
            )
        }
    }

    LaunchedEffect(Unit) {
        if (initialProjectForTest != null) return@LaunchedEffect
        val container = app ?: return@LaunchedEffect
        val existing = runCatching { container.projectRepository.getProject(projectId) }.getOrNull()
        val restored = existing?.takeIf { it.type == ProjectType.VIDEO }?.let {
            runCatching {
                ProjectDocumentMigrator.decodeProjectDocument(
                    projectId = it.id,
                    canvasRatio = it.ratio,
                    payloadJson = it.payloadJson
                )
            }.getOrNull()
        }
        if (restored != null && restored.toScenes().isNotEmpty()) {
            projectTitle = existing.title
            ratio = existing.ratio
            val synced = restored.copy(canvasRatio = existing.ratio)
            commandHistory.reset(synced)
            timelineProject = synced
            canUndo = false
            canRedo = false
            projectCreated = true
            selectedSceneIndex = 0
            selectedClipIds = setOfNotNull(
                synced.findTrackByType(TrackType.VIDEO)?.clips?.firstOrNull()?.id
            )
        } else {
            runCatching { ratio = container.settingsRepository.settings.first().lastUsedRatio }
            val initialDoc = ProjectDocumentMigrator.fromScenes(
                projectId = projectId,
                canvasRatio = ratio,
                scenes = defaultInitialScenes
            )
            commandHistory.reset(initialDoc)
            timelineProject = initialDoc
            selectedClipIds = setOfNotNull(
                initialDoc.findTrackByType(TrackType.VIDEO)?.clips?.firstOrNull()?.id
            )
            persistProject(initialDoc)
        }
    }

    LaunchedEffect(projectTitle) {
        if (!projectCreated || initialProjectForTest != null) return@LaunchedEffect
        autosaveController.onEdit(timelineProject)
    }

    DisposableEffect(lifecycleOwner, autosaveController) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && initialProjectForTest == null) {
                autosaveController.onStop()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (initialProjectForTest == null) {
                autosaveController.onStop()
            }
        }
    }

    var renderRetryToken by remember { mutableIntStateOf(0) }

    LaunchedEffect(scenes, ratio, brandKit, logos, renderRetryToken) {
        if (scenes.isEmpty()) return@LaunchedEffect
        delay(80)
        val templates = withContext(Dispatchers.IO) {
            runCatching { TemplateRepository(context).loadSceneTemplates().associateBy { it.id } }
                .getOrDefault(emptyMap())
        }
        if (templates.isEmpty()) return@LaunchedEffect
        val rendered = ErrorBoundary.runGuarded(
            source = "VideoStudioEditor:$projectId",
            title = "Scene preview issue",
            message = "Could not render one or more scene previews. Tap Retry to refresh.",
            onRetry = { renderRetryToken++ }
        ) {
            RenderDispatcher.withRenderContext {
                val output = mutableMapOf<String, Bitmap>()
                val thumbMaxEdge = (360 * ThermalGuard.effectivePreviewScale)
                    .toInt()
                    .coerceAtLeast(160)
                scenes.forEach { scene ->
                    val bmp = renderScenePreviewBitmap(
                        context = context,
                        projectId = projectId,
                        scene = scene,
                        ratio = ratio,
                        templates = templates,
                        brandKit = brandKit,
                        logos = logos,
                        maxEdgePx = thumbMaxEdge
                    )
                    if (bmp != null) output[scene.id] = bmp
                }
                output
            }
        }.orEmpty()
        previewBitmaps = rendered
    }

    val selectedClips = remember(timelineProject, selectedClipIds) {
        selectedClipIds.mapNotNull { timelineProject.findClip(it) }
    }
    val primaryClip = selectedClips.lastOrNull()
    val selectedBlock = remember(timelineProject, primaryClip, scenes, selectedSceneIndex) {
        primaryClip?.groupId?.let { timelineProject.findSceneBlock(it) }
            ?: primaryClip?.let { timelineProject.findSceneBlockForClip(it.id) }
            ?: scenes.getOrNull(selectedSceneIndex)?.let { timelineProject.findSceneBlock(it.id) }
    }

    // Handlers for selection & editing
    val handleTapClip: (String) -> Unit = { clipId ->
        val clip = timelineProject.findClip(clipId)
        selectedClipIds = setOf(clipId)
        if (clip != null) {
            playheadMs = clip.startMs
            val track = timelineProject.findTrackForClip(clipId)
            if (track != null) {
                activeToolTab = StudioToolTab.forTrackType(track.type)
            }
            val blockId = clip.groupId ?: clip.metadata["sceneId"]
            if (blockId != null) {
                val idx = scenes.indexOfFirst { it.id == blockId }
                if (idx >= 0) selectedSceneIndex = idx
            }
        }
    }

    val handleLongPressClip: (String) -> Unit = { clipId ->
        selectedClipIds = if (clipId in selectedClipIds && selectedClipIds.size > 1) {
            selectedClipIds - clipId
        } else {
            selectedClipIds + clipId
        }
    }

    val handleMoveClip: (String, Long, Boolean) -> Unit = { clipId, newStartMs, isFinished ->
        applyCommand(
            MoveClipCommand(
                clipId = clipId,
                newStartMs = newStartMs.coerceAtLeast(0L),
                isSliderDrag = true
            ),
            commitGestureAfter = isFinished
        )
    }

    val handleTrimClipStart: (String, Long, Long, Long, Boolean) -> Unit =
        { clipId, newStartMs, newDurationMs, newSourceInMs, isFinished ->
            applyCommand(
                TrimClipCommand(
                    clipId = clipId,
                    newStartMs = newStartMs,
                    newDurationMs = newDurationMs,
                    newSourceInMs = newSourceInMs,
                    setManualDuration = true,
                    isSliderDrag = true
                ),
                commitGestureAfter = isFinished
            )
        }

    val handleTrimClipEnd: (String, Long, Long, Boolean) -> Unit =
        { clipId, newDurationMs, newSourceOutMs, isFinished ->
            applyCommand(
                TrimClipCommand(
                    clipId = clipId,
                    newDurationMs = newDurationMs,
                    newSourceOutMs = newSourceOutMs,
                    setManualDuration = true,
                    isSliderDrag = true
                ),
                commitGestureAfter = isFinished
            )
        }

    val handleQuickTrimDelta: (Long) -> Unit = { deltaMs ->
        val target = primaryClip ?: timelineProject.allClips.firstOrNull()
        if (target != null) {
            val newDur = (target.durationMs + deltaMs).coerceAtLeast(TimelineSnapEngine.MIN_CLIP_DURATION_MS)
            applyCommand(
                TrimClipCommand(
                    clipId = target.id,
                    newDurationMs = newDur,
                    newSourceOutMs = target.sourceInMs + newDur,
                    setManualDuration = true,
                    isSliderDrag = false
                )
            )
        }
    }

    val handleQuickMoveDelta: (Long) -> Unit = { deltaMs ->
        val target = primaryClip ?: timelineProject.allClips.firstOrNull()
        if (target != null) {
            val newStart = (target.startMs + deltaMs).coerceAtLeast(0L)
            applyCommand(
                MoveClipCommand(
                    clipId = target.id,
                    newStartMs = newStart,
                    isSliderDrag = false
                )
            )
        }
    }

    val handleAddScene: () -> Unit = {
        if (scenes.size < ScriptSceneSplitter.MAX_SCENES) {
            val insertIdx = (selectedSceneIndex + 1).coerceAtMost(scenes.size)
            val newScene = Scene(
                type = SceneType.CONTENT,
                text = "Highlight your next big point clearly."
            )
            applyCommand(AddClipCommand.forScene(newScene, insertIdx))
            selectedSceneIndex = insertIdx.coerceAtMost(timelineProject.toScenes().lastIndex.coerceAtLeast(0))
            selectedClipIds = setOf("${newScene.id}_bg")
        } else {
            toastMessage = "Maximum ${ScriptSceneSplitter.MAX_SCENES} scenes reached."
        }
    }

    val handleDeleteSelected: () -> Unit = {
        val targetId = primaryClip?.id ?: scenes.getOrNull(selectedSceneIndex)?.id
        if (targetId != null) {
            applyCommand(DeleteClipCommand(clipId = targetId, deleteGroup = true))
            selectedSceneIndex = (selectedSceneIndex - 1).coerceAtLeast(0)
            selectedClipIds = setOfNotNull(
                timelineProject.findTrackByType(TrackType.VIDEO)?.clips?.firstOrNull()?.id
            )
        }
    }

    val handleSplitSelected: () -> Unit = {
        val target = primaryClip ?: timelineProject.findTrackByType(TrackType.VIDEO)?.clips?.getOrNull(selectedSceneIndex)
        if (target != null && target.durationMs >= 600L) {
            val splitOffset = (target.durationMs / 2L).coerceAtLeast(300L)
            applyCommand(
                SplitClipCommand(
                    clipId = target.id,
                    splitOffsetMs = splitOffset,
                    splitGroup = true
                )
            )
        } else {
            toastMessage = "Select a clip at least 0.6s long to split."
        }
    }

    val handleToggleGroup: () -> Unit = {
        val block = selectedBlock
        if (block != null) {
            if (block.isGrouped) {
                applyCommand(GroupCommand.ungroup(block.id))
            } else {
                applyCommand(GroupCommand.group(block.id, block.clipIds))
            }
        } else if (selectedClipIds.size >= 2) {
            val newGroupId = "grp_${UUID.randomUUID().toString().take(8)}"
            applyCommand(GroupCommand.group(newGroupId, selectedClipIds.toList()))
        }
    }

    val handleMergeNextScene: () -> Unit = {
        val currentScene = scenes.getOrNull(selectedSceneIndex)
        if (currentScene != null && selectedSceneIndex < scenes.lastIndex) {
            val next = scenes[selectedSceneIndex + 1]
            val mergedList = scenes.toMutableList().also {
                it[selectedSceneIndex] = currentScene.copy(
                    text = listOf(currentScene.text, next.text).filter(String::isNotBlank).joinToString(" ")
                )
                it.removeAt(selectedSceneIndex + 1)
            }
            applyCommand(SetPropertyCommand.replaceAllScenes(mergedList, "scenes.merge"))
        } else {
            toastMessage = "No subsequent scene to merge."
        }
    }

    BackHandler(enabled = isImmersiveMode || isBottomSheetOpen) {
        when {
            isImmersiveMode -> isImmersiveMode = false
            isBottomSheetOpen -> isBottomSheetOpen = false
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MidnightNavy)
            .testTag("video_editor_workspace")
    ) {
        val density = LocalDensity.current
        val workspaceWidth = maxWidth
        val workspaceHeight = maxHeight
        val isLandscapeOrTablet = workspaceWidth >= 600.dp || workspaceWidth > workspaceHeight

        if (!isLandscapeOrTablet) {
            // =========================================================================
            // PORTRAIT PHONE LAYOUT:
            // Top Bar -> Canvas (~45% height) -> Split Drag Handle -> Transport Bar
            // -> Multi-Track Timeline -> Bottom Context Toolbar + Non-Covering Sheet
            // =========================================================================
            val clampedSplit = if (isImmersiveMode) 0.72f else canvasSplitFraction.coerceIn(0.28f, 0.60f)
            val canvasHeight = workspaceHeight * clampedSplit
            // Bottom sheet max height never covers the canvas preview!
            val sheetMaxHeight = (workspaceHeight * (1f - clampedSplit) - 56.dp).coerceAtLeast(180.dp)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("portrait_studio_layout")
            ) {
                AnimatedVisibility(visible = !isImmersiveMode) {
                    StudioEditorTopBar(
                        projectTitle = projectTitle,
                        isSaving = isSaving,
                        version = timelineProject.version,
                        canUndo = canUndo,
                        canRedo = canRedo,
                        onBack = {
                            scope.launch {
                                if (initialProjectForTest == null) {
                                    autosaveController.flushNow()
                                    persistProject(timelineProject)
                                }
                                onBack()
                            }
                        },
                        onEditTitle = { showRenameDialog = true },
                        onUndo = { performUndo() },
                        onRedo = { performRedo() },
                        onExport = {
                            scope.launch {
                                if (initialProjectForTest == null) {
                                    autosaveController.flushNow()
                                    persistProject(timelineProject)
                                }
                                onExport(projectId)
                            }
                        }
                    )
                }

                // Canvas Preview (~45% of height by default)
                StudioCanvasPreviewBox(
                    project = timelineProject,
                    scenes = scenes,
                    selectedSceneIndex = selectedSceneIndex,
                    selectedClips = selectedClips,
                    playheadMs = playheadMs,
                    ratio = ratio,
                    showSafeZones = showSafeZones,
                    isImmersiveMode = isImmersiveMode,
                    previewBitmaps = previewBitmaps,
                    onExitImmersive = { isImmersiveMode = false },
                    onCycleRatio = {
                        val ratios = listOf("9:16", "4:5", "1:1", "16:9")
                        val next = ratios[(ratios.indexOf(ratio) + 1) % ratios.size]
                        applyCommand(SetPropertyCommand.canvasRatio(next))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(canvasHeight)
                )

                // Resizable Drag Handle between Canvas and Timeline
                CanvasTimelineSplitDragHandle(
                    onDragDeltaPx = { deltaPx ->
                        val totalPx = with(density) { workspaceHeight.toPx() }.coerceAtLeast(1f)
                        canvasSplitFraction = (canvasSplitFraction + deltaPx / totalPx).coerceIn(0.28f, 0.60f)
                    }
                )

                // Transport Bar (Play, Time, Zoom, Immersive)
                StudioTransportBar(
                    isPlaying = isPlaying,
                    playheadMs = playheadMs,
                    totalDurationMs = timelineProject.totalDurationMs,
                    zoomScale = zoomScale,
                    isImmersive = isImmersiveMode,
                    selectedCount = selectedClipIds.size,
                    onTogglePlay = { isPlaying = !isPlaying },
                    onStepBackward = { playheadMs = (playheadMs - 500L).coerceAtLeast(0L) },
                    onStepForward = {
                        playheadMs = (playheadMs + 500L).coerceAtMost(timelineProject.totalDurationMs)
                    },
                    onZoomChange = { zoomScale = it.coerceIn(0.4f, 3.5f) },
                    onToggleImmersive = { isImmersiveMode = !isImmersiveMode }
                )

                // Lower Workspace: Multi-Track Timeline + Non-Covering Bottom Sheet overlay
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    MultiTrackTimelineView(
                        project = timelineProject,
                        playheadMs = playheadMs,
                        selectedClipIds = selectedClipIds,
                        zoomScale = zoomScale,
                        scrollOffsetMs = scrollOffsetMs,
                        onPlayheadChange = { playheadMs = it },
                        onZoomAndScrollChange = { newZoom, newScroll ->
                            zoomScale = newZoom
                            scrollOffsetMs = newScroll
                        },
                        onTapClip = handleTapClip,
                        onLongPressClip = handleLongPressClip,
                        onClearSelection = { selectedClipIds = emptySet() },
                        onMoveClip = handleMoveClip,
                        onTrimClipStart = handleTrimClipStart,
                        onTrimClipEnd = handleTrimClipEnd,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Anchored Bottom Sheet that NEVER covers the Canvas!
                    if (isBottomSheetOpen && !isImmersiveMode) {
                        Box(
                            modifier = Modifier.align(Alignment.BottomCenter)
                        ) {
                            StudioBottomSheetPanel(
                                activeTab = activeToolTab,
                                maxHeight = sheetMaxHeight,
                                onDismiss = { isBottomSheetOpen = false }
                            ) {
                                StudioInspectorContent(
                                    activeTab = activeToolTab,
                                    project = timelineProject,
                                    scenes = scenes,
                                    selectedSceneIndex = selectedSceneIndex,
                                    selectedClips = selectedClips,
                                    selectedBlock = selectedBlock,
                                    ratio = ratio,
                                    showSafeZones = showSafeZones,
                                    previewBitmaps = previewBitmaps,
                                    onSelectTab = { activeToolTab = it },
                                    onSelectSceneIndex = { idx ->
                                        selectedSceneIndex = idx
                                        scenes.getOrNull(idx)?.let { sc ->
                                            selectedClipIds = setOf("${sc.id}_bg")
                                        }
                                    },
                                    onUpdateSceneText = { value ->
                                        val sc = scenes.getOrNull(selectedSceneIndex) ?: return@StudioInspectorContent
                                        applyCommand(
                                            SetPropertyCommand.updateScene(sc.id, "text", isSliderDrag = true) {
                                                it.copy(text = value.take(1_200))
                                            }
                                        )
                                    },
                                    onUpdateSceneStyle = { style ->
                                        val sc = scenes.getOrNull(selectedSceneIndex) ?: return@StudioInspectorContent
                                        applyCommand(
                                            SetPropertyCommand.updateScene(sc.id, "textStyle") {
                                                it.copy(textStyle = style)
                                            }
                                        )
                                    },
                                    onUpdateSceneType = { type ->
                                        val sc = scenes.getOrNull(selectedSceneIndex) ?: return@StudioInspectorContent
                                        applyCommand(
                                            SetPropertyCommand.updateScene(sc.id, "sceneType") {
                                                it.copy(type = type)
                                            }
                                        )
                                    },
                                    onUpdateSceneBackground = { bg ->
                                        val sc = scenes.getOrNull(selectedSceneIndex) ?: return@StudioInspectorContent
                                        applyCommand(
                                            SetPropertyCommand.updateScene(sc.id, "background") {
                                                it.copy(backgroundType = bg, backgroundUri = null)
                                            }
                                        )
                                    },
                                    onUpdateSceneTransition = { tr ->
                                        val sc = scenes.getOrNull(selectedSceneIndex) ?: return@StudioInspectorContent
                                        applyCommand(
                                            SetPropertyCommand.updateScene(sc.id, "transition") {
                                                it.copy(transition = tr)
                                            }
                                        )
                                    },
                                    onPickImageBackground = { imagePicker.launch(arrayOf("image/*")) },
                                    onPickVideoBackground = { videoPicker.launch(arrayOf("video/*")) },
                                    onAddScene = handleAddScene,
                                    onDeleteSelected = handleDeleteSelected,
                                    onSplitSelected = handleSplitSelected,
                                    onMergeNextScene = handleMergeNextScene,
                                    onToggleGroup = handleToggleGroup,
                                    onChangeRatio = { newRatio ->
                                        applyCommand(SetPropertyCommand.canvasRatio(newRatio))
                                    },
                                    onToggleSafeZones = { showSafeZones = it },
                                    onOpenVoiceOver = {
                                        scope.launch {
                                            if (initialProjectForTest == null) {
                                                autosaveController.flushNow()
                                                persistProject(timelineProject)
                                            }
                                            onOpenVoiceOver(projectId)
                                        }
                                    },
                                    onOpenQuickPreview = {
                                        scope.launch {
                                            if (initialProjectForTest == null) {
                                                autosaveController.flushNow()
                                                persistProject(timelineProject)
                                            }
                                            onOpenPreview(projectId)
                                        }
                                    },
                                    onAddCaptionClip = { text ->
                                        val clip = Clip(
                                            id = "cap_${UUID.randomUUID().toString().take(6)}",
                                            startMs = playheadMs,
                                            durationMs = 2_500L,
                                            text = text
                                        )
                                        applyCommand(AddClipCommand(clip = clip, targetTrackType = TrackType.CAPTION))
                                        selectedClipIds = setOf(clip.id)
                                    },
                                    onAddAudioSfxClip = {
                                        val clip = Clip(
                                            id = "sfx_${UUID.randomUUID().toString().take(6)}",
                                            startMs = playheadMs,
                                            durationMs = 1_500L,
                                            text = "Whoosh SFX",
                                            waveformLevels = listOf(0.2f, 0.8f, 1.0f, 0.6f, 0.3f, 0.1f)
                                        )
                                        applyCommand(AddClipCommand(clip = clip, targetTrackType = TrackType.AUDIO_SFX))
                                        selectedClipIds = setOf(clip.id)
                                    },
                                    onUpdateClipVolume = { clipId, vol ->
                                        applyCommand(SetPropertyCommand.volume(clipId, vol, isSliderDrag = true))
                                    },
                                    onUpdateClipScale = { clipId, scale ->
                                        applyCommand(SetPropertyCommand.scale(clipId, scale, isSliderDrag = true))
                                    },
                                    onUpdateClipOpacity = { clipId, opacity ->
                                        applyCommand(SetPropertyCommand.opacity(clipId, opacity, isSliderDrag = true))
                                    },
                                    onTrimQuickDelta = handleQuickTrimDelta,
                                    onMoveQuickDelta = handleQuickMoveDelta
                                )
                            }
                        }
                    }
                }

                // Bottom Context Toolbar (always reachable with one hand)
                AnimatedVisibility(visible = !isImmersiveMode) {
                    StudioBottomContextToolbar(
                        activeTab = if (isBottomSheetOpen) activeToolTab else null,
                        selectedClips = selectedClips,
                        isGrouped = selectedBlock?.isGrouped ?: true,
                        onSelectTab = { tab ->
                            if (isBottomSheetOpen && activeToolTab == tab) {
                                isBottomSheetOpen = false
                            } else {
                                activeToolTab = tab
                                isBottomSheetOpen = true
                            }
                        },
                        onAddScene = handleAddScene,
                        onSplitSelected = handleSplitSelected,
                        onDeleteSelected = handleDeleteSelected,
                        onTrimQuickDelta = handleQuickTrimDelta,
                        onMoveQuickDelta = handleQuickMoveDelta,
                        onToggleGroup = handleToggleGroup,
                        onClearSelection = { selectedClipIds = emptySet() }
                    )
                }
            }
        } else {
            // =========================================================================
            // LANDSCAPE & TABLET / FOLDABLE LAYOUT (Medium / Expanded):
            // Top Bar -> Upper Split (Canvas Left + Inspector Right)
            // -> Drag Handle -> Full-Width Multi-Track Timeline across the bottom
            // =========================================================================
            val upperSplit = if (isImmersiveMode) 0.72f else canvasSplitFraction.coerceIn(0.38f, 0.64f)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("landscape_tablet_studio_layout")
            ) {
                AnimatedVisibility(visible = !isImmersiveMode) {
                    StudioEditorTopBar(
                        projectTitle = projectTitle,
                        isSaving = isSaving,
                        version = timelineProject.version,
                        canUndo = canUndo,
                        canRedo = canRedo,
                        onBack = {
                            scope.launch {
                                if (initialProjectForTest == null) {
                                    autosaveController.flushNow()
                                    persistProject(timelineProject)
                                }
                                onBack()
                            }
                        },
                        onEditTitle = { showRenameDialog = true },
                        onUndo = { performUndo() },
                        onRedo = { performRedo() },
                        onExport = {
                            scope.launch {
                                if (initialProjectForTest == null) {
                                    autosaveController.flushNow()
                                    persistProject(timelineProject)
                                }
                                onExport(projectId)
                            }
                        }
                    )
                }

                // Upper Split: Canvas Left + Docked Inspector Right
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(workspaceHeight * upperSplit)
                ) {
                    // Left: Canvas + Transport Bar
                    Column(
                        modifier = Modifier
                            .weight(if (isImmersiveMode) 1f else 0.54f)
                            .fillMaxHeight()
                    ) {
                        StudioCanvasPreviewBox(
                            project = timelineProject,
                            scenes = scenes,
                            selectedSceneIndex = selectedSceneIndex,
                            selectedClips = selectedClips,
                            playheadMs = playheadMs,
                            ratio = ratio,
                            showSafeZones = showSafeZones,
                            isImmersiveMode = isImmersiveMode,
                            previewBitmaps = previewBitmaps,
                            onExitImmersive = { isImmersiveMode = false },
                            onCycleRatio = {
                                val ratios = listOf("9:16", "4:5", "1:1", "16:9")
                                val next = ratios[(ratios.indexOf(ratio) + 1) % ratios.size]
                                applyCommand(SetPropertyCommand.canvasRatio(next))
                            },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        )
                        StudioTransportBar(
                            isPlaying = isPlaying,
                            playheadMs = playheadMs,
                            totalDurationMs = timelineProject.totalDurationMs,
                            zoomScale = zoomScale,
                            isImmersive = isImmersiveMode,
                            selectedCount = selectedClipIds.size,
                            onTogglePlay = { isPlaying = !isPlaying },
                            onStepBackward = { playheadMs = (playheadMs - 500L).coerceAtLeast(0L) },
                            onStepForward = {
                                playheadMs = (playheadMs + 500L).coerceAtMost(timelineProject.totalDurationMs)
                            },
                            onZoomChange = { zoomScale = it.coerceIn(0.4f, 3.5f) },
                            onToggleImmersive = { isImmersiveMode = !isImmersiveMode }
                        )
                    }

                    // Right: Docked Studio Inspector Panel
                    if (!isImmersiveMode) {
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .fillMaxHeight()
                                .background(Color(0xFF263A53))
                        )
                        Box(
                            modifier = Modifier
                                .weight(0.46f)
                                .fillMaxHeight()
                                .testTag("studio_inspector_panel")
                                .semantics { contentDescription = "Docked studio inspector panel" }
                        ) {
                            StudioInspectorContent(
                                activeTab = activeToolTab,
                                project = timelineProject,
                                scenes = scenes,
                                selectedSceneIndex = selectedSceneIndex,
                                selectedClips = selectedClips,
                                selectedBlock = selectedBlock,
                                ratio = ratio,
                                showSafeZones = showSafeZones,
                                previewBitmaps = previewBitmaps,
                                onSelectTab = { activeToolTab = it },
                                onSelectSceneIndex = { idx ->
                                    selectedSceneIndex = idx
                                    scenes.getOrNull(idx)?.let { sc ->
                                        selectedClipIds = setOf("${sc.id}_bg")
                                    }
                                },
                                onUpdateSceneText = { value ->
                                    val sc = scenes.getOrNull(selectedSceneIndex) ?: return@StudioInspectorContent
                                    applyCommand(
                                        SetPropertyCommand.updateScene(sc.id, "text", isSliderDrag = true) {
                                            it.copy(text = value.take(1_200))
                                        }
                                    )
                                },
                                onUpdateSceneStyle = { style ->
                                    val sc = scenes.getOrNull(selectedSceneIndex) ?: return@StudioInspectorContent
                                    applyCommand(
                                        SetPropertyCommand.updateScene(sc.id, "textStyle") {
                                            it.copy(textStyle = style)
                                        }
                                    )
                                },
                                onUpdateSceneType = { type ->
                                    val sc = scenes.getOrNull(selectedSceneIndex) ?: return@StudioInspectorContent
                                    applyCommand(
                                        SetPropertyCommand.updateScene(sc.id, "sceneType") {
                                            it.copy(type = type)
                                        }
                                    )
                                },
                                onUpdateSceneBackground = { bg ->
                                    val sc = scenes.getOrNull(selectedSceneIndex) ?: return@StudioInspectorContent
                                    applyCommand(
                                        SetPropertyCommand.updateScene(sc.id, "background") {
                                            it.copy(backgroundType = bg, backgroundUri = null)
                                        }
                                    )
                                },
                                onUpdateSceneTransition = { tr ->
                                    val sc = scenes.getOrNull(selectedSceneIndex) ?: return@StudioInspectorContent
                                    applyCommand(
                                        SetPropertyCommand.updateScene(sc.id, "transition") {
                                            it.copy(transition = tr)
                                        }
                                    )
                                },
                                onPickImageBackground = { imagePicker.launch(arrayOf("image/*")) },
                                onPickVideoBackground = { videoPicker.launch(arrayOf("video/*")) },
                                onAddScene = handleAddScene,
                                onDeleteSelected = handleDeleteSelected,
                                onSplitSelected = handleSplitSelected,
                                onMergeNextScene = handleMergeNextScene,
                                onToggleGroup = handleToggleGroup,
                                onChangeRatio = { newRatio ->
                                    applyCommand(SetPropertyCommand.canvasRatio(newRatio))
                                },
                                onToggleSafeZones = { showSafeZones = it },
                                onOpenVoiceOver = {
                                    scope.launch {
                                        if (initialProjectForTest == null) {
                                            autosaveController.flushNow()
                                            persistProject(timelineProject)
                                        }
                                        onOpenVoiceOver(projectId)
                                    }
                                },
                                onOpenQuickPreview = {
                                    scope.launch {
                                        if (initialProjectForTest == null) {
                                            autosaveController.flushNow()
                                            persistProject(timelineProject)
                                        }
                                        onOpenPreview(projectId)
                                    }
                                },
                                onAddCaptionClip = { text ->
                                    val clip = Clip(
                                        id = "cap_${UUID.randomUUID().toString().take(6)}",
                                        startMs = playheadMs,
                                        durationMs = 2_500L,
                                        text = text
                                    )
                                    applyCommand(AddClipCommand(clip = clip, targetTrackType = TrackType.CAPTION))
                                    selectedClipIds = setOf(clip.id)
                                },
                                onAddAudioSfxClip = {
                                    val clip = Clip(
                                        id = "sfx_${UUID.randomUUID().toString().take(6)}",
                                        startMs = playheadMs,
                                        durationMs = 1_500L,
                                        text = "Whoosh SFX",
                                        waveformLevels = listOf(0.2f, 0.8f, 1.0f, 0.6f, 0.3f, 0.1f)
                                    )
                                    applyCommand(AddClipCommand(clip = clip, targetTrackType = TrackType.AUDIO_SFX))
                                    selectedClipIds = setOf(clip.id)
                                },
                                onUpdateClipVolume = { clipId, vol ->
                                    applyCommand(SetPropertyCommand.volume(clipId, vol, isSliderDrag = true))
                                },
                                onUpdateClipScale = { clipId, scale ->
                                    applyCommand(SetPropertyCommand.scale(clipId, scale, isSliderDrag = true))
                                },
                                onUpdateClipOpacity = { clipId, opacity ->
                                    applyCommand(SetPropertyCommand.opacity(clipId, opacity, isSliderDrag = true))
                                },
                                onTrimQuickDelta = handleQuickTrimDelta,
                                onMoveQuickDelta = handleQuickMoveDelta,
                                showCategoryTabsInHeader = true
                            )
                        }
                    }
                }

                // Drag handle resizing upper panes vs bottom timeline
                CanvasTimelineSplitDragHandle(
                    onDragDeltaPx = { deltaPx ->
                        val totalPx = with(density) { workspaceHeight.toPx() }.coerceAtLeast(1f)
                        canvasSplitFraction = (canvasSplitFraction + deltaPx / totalPx).coerceIn(0.38f, 0.64f)
                    }
                )

                // Full-width Multi-Track Timeline across the bottom
                MultiTrackTimelineView(
                    project = timelineProject,
                    playheadMs = playheadMs,
                    selectedClipIds = selectedClipIds,
                    zoomScale = zoomScale,
                    scrollOffsetMs = scrollOffsetMs,
                    onPlayheadChange = { playheadMs = it },
                    onZoomAndScrollChange = { newZoom, newScroll ->
                        zoomScale = newZoom
                        scrollOffsetMs = newScroll
                    },
                    onTapClip = handleTapClip,
                    onLongPressClip = handleLongPressClip,
                    onClearSelection = { selectedClipIds = emptySet() },
                    onMoveClip = handleMoveClip,
                    onTrimClipStart = handleTrimClipStart,
                    onTrimClipEnd = handleTrimClipEnd,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            }
        }

        // Toast Banner
        toastMessage?.let { msg ->
            Surface(
                color = DarkSurfaceHigh,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 64.dp, start = 16.dp, end = 16.dp)
                    .border(1.dp, PremiumGold, RoundedCornerShape(12.dp))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(msg, style = MaterialTheme.typography.bodySmall, color = CloudWhite)
                    TextButton(onClick = { toastMessage = null }) {
                        Text("OK", color = PremiumGold)
                    }
                }
            }
        }
    }

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Project Name") },
            text = {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("PROJECT TITLE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    BasicTextField(
                        value = projectTitle,
                        onValueChange = { projectTitle = it.take(120) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("video_title_input"),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface)
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            },
            confirmButton = {
                Button(onClick = { showRenameDialog = false }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun StudioEditorTopBar(
    projectTitle: String,
    isSaving: Boolean,
    version: Int,
    canUndo: Boolean,
    canRedo: Boolean,
    onBack: () -> Unit,
    onEditTitle: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onExport: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("video_editor_top_bar"),
        color = DeepNavy,
        tonalElevation = 3.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .height(56.dp)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(48.dp)
                    .minimumInteractiveComponentSize()
                    .testTag("video_editor_back_button")
                    .semantics { contentDescription = "Back to projects" }
            ) {
                SDIcon(SDIconKind.Back, tint = CloudWhite, size = 22.dp)
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onEditTitle)
                    .padding(horizontal = 6.dp, vertical = 4.dp)
                    .testTag("video_project_name_button")
                    .semantics { contentDescription = "Project name $projectTitle. Tap to rename" },
                verticalArrangement = Arrangement.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = projectTitle.ifBlank { "Explainer video" },
                        style = MaterialTheme.typography.titleMedium,
                        color = CloudWhite,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    SDIcon(SDIconKind.Edit, tint = PremiumGold, size = 13.dp)
                }
                Text(
                    text = if (isSaving) "Saving draft…" else "STUDIO v$version • Saved",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MutedTextDark,
                    maxLines = 1
                )
            }

            IconButton(
                onClick = onUndo,
                enabled = canUndo,
                modifier = Modifier
                    .size(48.dp)
                    .minimumInteractiveComponentSize()
                    .testTag("video_editor_undo_button")
                    .semantics { contentDescription = "Undo last timeline edit" }
            ) {
                SDIcon(
                    kind = SDIconKind.Undo,
                    tint = if (canUndo) CloudWhite else MutedTextDark.copy(alpha = 0.35f),
                    size = 20.dp
                )
            }

            IconButton(
                onClick = onRedo,
                enabled = canRedo,
                modifier = Modifier
                    .size(48.dp)
                    .minimumInteractiveComponentSize()
                    .testTag("video_editor_redo_button")
                    .semantics { contentDescription = "Redo timeline edit" }
            ) {
                SDIcon(
                    kind = SDIconKind.Redo,
                    tint = if (canRedo) CloudWhite else MutedTextDark.copy(alpha = 0.35f),
                    size = 20.dp
                )
            }

            Spacer(Modifier.width(4.dp))

            Button(
                onClick = onExport,
                colors = ButtonDefaults.buttonColors(
                    containerColor = InnovationTeal,
                    contentColor = MidnightNavy
                ),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                modifier = Modifier
                    .height(48.dp)
                    .minimumInteractiveComponentSize()
                    .testTag("video_editor_export_button")
                    .semantics { contentDescription = "Export video project" }
            ) {
                SDIcon(SDIconKind.Download, tint = MidnightNavy, size = 16.dp)
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Export",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun CanvasTimelineSplitDragHandle(
    onDragDeltaPx: (Float) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(22.dp)
            .minimumInteractiveComponentSize()
            .background(DeepNavy)
            .testTag("canvas_timeline_split_handle")
            .semantics { contentDescription = "Drag handle to resize canvas and timeline split" }
            .pointerInput(Unit) {
                detectVerticalDragGestures { change, dragAmount ->
                    change.consume()
                    onDragDeltaPx(dragAmount)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(width = 44.dp, height = 4.dp)
                .background(Color(0xFF4A5E78), CircleShape)
        )
    }
}

@Composable
private fun StudioCanvasPreviewBox(
    project: TimelineProject,
    scenes: List<Scene>,
    selectedSceneIndex: Int,
    selectedClips: List<Clip>,
    playheadMs: Long,
    ratio: String,
    showSafeZones: Boolean,
    isImmersiveMode: Boolean,
    previewBitmaps: Map<String, Bitmap>,
    onExitImmersive: () -> Unit,
    onCycleRatio: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeClips = remember(project, playheadMs) {
        findActiveClipsAtPlayhead(project, playheadMs)
    }
    val activeScene = remember(scenes, selectedSceneIndex, activeClips) {
        val sceneIdFromPlayhead = activeClips.firstNotNullOfOrNull { it.groupId ?: it.metadata["sceneId"] }
        scenes.firstOrNull { it.id == sceneIdFromPlayhead }
            ?: scenes.getOrNull(selectedSceneIndex)
            ?: scenes.firstOrNull()
    }
    val activeBitmap = activeScene?.let { previewBitmaps[it.id] }
    val activeTextClip = activeClips.firstOrNull { it.text.isNotBlank() }
    val headlineText = activeTextClip?.text
        ?: activeScene?.text
        ?: "Your brand story starts here."

    val ratioAspect = when (ratio) {
        "4:5" -> 0.8f
        "1:1" -> 1.0f
        "16:9" -> 1.7778f
        else -> 0.5625f
    }

    Box(
        modifier = modifier
            .background(Color(0xFF070E17))
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag("video_canvas_preview")
            .semantics { contentDescription = "Video canvas monitor preview ($ratio)" },
        contentAlignment = Alignment.Center
    ) {
        // Aspect-Ratio Locked Monitor Frame
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .aspectRatio(ratioAspect, matchHeightConstraintsFirst = ratio != "16:9")
                .clip(RoundedCornerShape(14.dp))
                .background(
                    Brush.linearGradient(
                        listOf(MidnightNavy, Color(0xFF162C46), Color(0xFF0E2238))
                    )
                )
                .border(
                    width = if (selectedClips.isNotEmpty()) 1.5.dp else 1.dp,
                    color = if (selectedClips.isNotEmpty()) PremiumGold else Color(0xFF263A53),
                    shape = RoundedCornerShape(14.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (activeBitmap != null && !activeBitmap.isRecycled) {
                Image(
                    bitmap = activeBitmap.asImageBitmap(),
                    contentDescription = "Rendered scene frame",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Instant procedural studio canvas frame so preview is always rich & crisp
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                        color = InnovationTeal.copy(alpha = 0.16f),
                        radius = size.minDimension * 0.48f,
                        center = Offset(size.width * 0.82f, size.height * 0.78f)
                    )
                    drawCircle(
                        color = PremiumGold.copy(alpha = 0.10f),
                        radius = size.minDimension * 0.30f,
                        center = Offset(size.width * 0.18f, size.height * 0.22f)
                    )
                }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = (activeScene?.type?.name ?: "STUDIO").uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = PremiumGold,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            color = InnovationTeal.copy(alpha = 0.22f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "SD",
                                style = MaterialTheme.typography.labelSmall,
                                color = InnovationTeal,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Text(
                        text = headlineText,
                        style = MaterialTheme.typography.titleMedium,
                        color = CloudWhite,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Start,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )

                    Box(
                        modifier = Modifier
                            .width(42.dp)
                            .height(3.dp)
                            .background(InnovationTeal, CircleShape)
                    )
                }
            }

            // Safe-Zone Wireframe Overlay
            if (showSafeZones) {
                Canvas(modifier = Modifier.fillMaxSize().padding(10.dp)) {
                    drawRect(
                        color = InnovationTeal.copy(alpha = 0.35f),
                        style = Stroke(
                            width = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
                        )
                    )
                }
            }
        }

        // Top-Left Monitor Telemetry Pill
        Surface(
            color = DeepNavy.copy(alpha = 0.82f),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(6.dp)
        ) {
            Text(
                text = "SCENE ${(selectedSceneIndex + 1).coerceAtLeast(1)}/${scenes.size.coerceAtLeast(1)}",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = CloudWhite,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }

        // Top-Right Ratio Selector Pill (48dp touch target)
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .height(48.dp)
                .widthIn(min = 48.dp)
                .minimumInteractiveComponentSize()
                .clickable(onClick = onCycleRatio)
                .padding(4.dp)
                .testTag("canvas_ratio_badge")
                .semantics { contentDescription = "Canvas aspect ratio $ratio. Tap to switch ratio" },
            contentAlignment = Alignment.TopEnd
        ) {
            Surface(
                color = DeepNavy.copy(alpha = 0.85f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.border(1.dp, InnovationTeal.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            ) {
                Text(
                    text = ratio,
                    style = MaterialTheme.typography.labelSmall,
                    color = InnovationTeal,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        // Floating Exit Immersive Mode Button
        if (isImmersiveMode) {
            Button(
                onClick = onExitImmersive,
                colors = ButtonDefaults.buttonColors(
                    containerColor = DeepNavy.copy(alpha = 0.9f),
                    contentColor = PremiumGold
                ),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .height(48.dp)
                    .minimumInteractiveComponentSize()
                    .testTag("exit_immersive_button")
                    .semantics { contentDescription = "Exit immersive full preview mode" }
            ) {
                Text("Exit Full Preview", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
        }
    }
}
