package com.sdtech.studio.ui.video

import android.graphics.Bitmap
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
import com.sdtech.studio.video.Scene
import com.sdtech.studio.video.SceneBackgroundType
import com.sdtech.studio.video.SceneTextStyle
import com.sdtech.studio.video.SceneTransition
import com.sdtech.studio.video.SceneType
import com.sdtech.studio.video.ScriptSceneSplitter
import com.sdtech.studio.video.domain.Clip
import com.sdtech.studio.video.domain.SceneBlock
import com.sdtech.studio.video.domain.TimelineProject
import com.sdtech.studio.video.domain.TrackType

@Composable
fun StudioBottomContextToolbar(
    activeTab: StudioToolTab?,
    selectedClips: List<Clip>,
    isGrouped: Boolean,
    onSelectTab: (StudioToolTab) -> Unit,
    onAddScene: () -> Unit,
    onSplitSelected: () -> Unit,
    onDeleteSelected: () -> Unit,
    onTrimQuickDelta: (Long) -> Unit,
    onMoveQuickDelta: (Long) -> Unit,
    onToggleGroup: () -> Unit,
    onClearSelection: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("bottom_context_toolbar")
            .semantics { contentDescription = "Bottom studio context toolbar" },
        color = DeepNavy,
        tonalElevation = 6.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
        ) {
            // Contextual Quick Action Strip when clip(s) are selected
            if (selectedClips.isNotEmpty()) {
                val primary = selectedClips.last()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkSurface)
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                        .testTag("contextual_clip_action_bar"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        color = PremiumGold.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (selectedClips.size > 1) {
                                "${selectedClips.size} SELECTED"
                            } else {
                                "${primary.id.take(12)} • ${formatStudioTimecode(primary.durationMs)}"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = PremiumGold,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                                .testTag("selected_clip_status_badge")
                        )
                    }

                    QuickContextActionButton(
                        label = "Trim -0.5s",
                        tag = "context_trim_shorter_button",
                        contentDesc = "Trim selected clip shorter by 0.5 seconds",
                        onClick = { onTrimQuickDelta(-500L) }
                    )
                    QuickContextActionButton(
                        label = "Trim +0.5s",
                        tag = "context_trim_longer_button",
                        contentDesc = "Trim selected clip longer by 0.5 seconds",
                        onClick = { onTrimQuickDelta(500L) }
                    )
                    QuickContextActionButton(
                        label = "Move -0.5s",
                        tag = "context_move_left_button",
                        contentDesc = "Move selected clip earlier by 0.5 seconds",
                        onClick = { onMoveQuickDelta(-500L) }
                    )
                    QuickContextActionButton(
                        label = "Move +0.5s",
                        tag = "context_move_right_button",
                        contentDesc = "Move selected clip later by 0.5 seconds",
                        onClick = { onMoveQuickDelta(500L) }
                    )
                    QuickContextActionButton(
                        label = "Split",
                        tag = "split_scene_button",
                        contentDesc = "Split selected clip at midpoint",
                        onClick = onSplitSelected
                    )
                    QuickContextActionButton(
                        label = if (isGrouped) "Ungroup" else "Group",
                        tag = "scene_group_toggle_chip",
                        contentDesc = if (isGrouped) "Ungroup scene clips" else "Group selected clips",
                        onClick = onToggleGroup
                    )
                    QuickContextActionButton(
                        label = "Delete",
                        tag = "delete_scene_button",
                        contentDesc = "Delete selected clip",
                        tint = Color(0xFFFF8A80),
                        onClick = onDeleteSelected
                    )
                    QuickContextActionButton(
                        label = "Deselect",
                        tag = "context_clear_selection_button",
                        contentDesc = "Clear clip selection",
                        onClick = onClearSelection
                    )
                }
                HorizontalDivider(color = Color(0xFF263A53), thickness = 1.dp)
            }

            // Primary 7 Studio Categories (One-Handed Reach, 48dp+ Touch Targets)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Quick Add Scene button in primary toolbar
                Surface(
                    onClick = onAddScene,
                    color = InnovationTeal.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .height(52.dp)
                        .widthIn(min = 56.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("add_scene_button")
                        .semantics { contentDescription = "Add new scene to timeline" }
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        SDIcon(SDIconKind.Plus, tint = InnovationTeal, size = 18.dp)
                        Text(
                            text = "Add",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = InnovationTeal,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                StudioToolTab.entries.forEach { tab ->
                    val isSelected = activeTab == tab
                    Surface(
                        onClick = { onSelectTab(tab) },
                        color = if (isSelected) InnovationTeal else DarkSurface,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .height(52.dp)
                            .widthIn(min = 64.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("toolbar_tab_${tab.name.lowercase()}")
                            .semantics { contentDescription = tab.contentDesc }
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            SDIcon(
                                kind = tab.icon,
                                tint = if (isSelected) MidnightNavy else CloudWhite,
                                size = 18.dp
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = tab.label,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = if (isSelected) MidnightNavy else CloudWhite,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickContextActionButton(
    label: String,
    tag: String,
    contentDesc: String,
    tint: Color = CloudWhite,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = DarkSurfaceHigh,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .height(48.dp)
            .minimumInteractiveComponentSize()
            .testTag(tag)
            .semantics { contentDescription = contentDesc }
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = tint,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun StudioBottomSheetPanel(
    activeTab: StudioToolTab,
    maxHeight: Dp,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 180.dp, max = maxHeight)
            .testTag("studio_bottom_sheet")
            .semantics { contentDescription = "${activeTab.label} tool bottom sheet" },
        color = DarkSurface,
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        tonalElevation = 8.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Sheet Drag Handle & Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SDIcon(activeTab.icon, tint = InnovationTeal, size = 18.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${activeTab.label} Inspector",
                    style = MaterialTheme.typography.titleMedium,
                    color = CloudWhite,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(48.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("close_bottom_sheet_button")
                        .semantics { contentDescription = "Close ${activeTab.label} panel" }
                ) {
                    SDIcon(SDIconKind.Close, tint = MutedTextDark, size = 18.dp)
                }
            }
            HorizontalDivider(color = Color(0xFF263A53), thickness = 1.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
            ) {
                content()
            }
        }
    }
}

@Composable
fun StudioInspectorContent(
    activeTab: StudioToolTab,
    project: TimelineProject,
    scenes: List<Scene>,
    selectedSceneIndex: Int,
    selectedClips: List<Clip>,
    selectedBlock: SceneBlock?,
    ratio: String,
    showSafeZones: Boolean,
    previewBitmaps: Map<String, Bitmap>,
    onSelectTab: (StudioToolTab) -> Unit,
    onSelectSceneIndex: (Int) -> Unit,
    onUpdateSceneText: (String) -> Unit,
    onUpdateSceneStyle: (SceneTextStyle) -> Unit,
    onUpdateSceneType: (SceneType) -> Unit,
    onUpdateSceneBackground: (SceneBackgroundType) -> Unit,
    onUpdateSceneTransition: (SceneTransition) -> Unit,
    onPickImageBackground: () -> Unit,
    onPickVideoBackground: () -> Unit,
    onAddScene: () -> Unit,
    onDeleteSelected: () -> Unit,
    onSplitSelected: () -> Unit,
    onMergeNextScene: () -> Unit,
    onToggleGroup: () -> Unit,
    onChangeRatio: (String) -> Unit,
    onToggleSafeZones: (Boolean) -> Unit,
    onOpenVoiceOver: () -> Unit,
    onOpenQuickPreview: () -> Unit,
    onAddCaptionClip: (String) -> Unit,
    onAddAudioSfxClip: () -> Unit,
    onUpdateClipVolume: (clipId: String, volume: Float) -> Unit,
    onUpdateClipScale: (clipId: String, scale: Float) -> Unit,
    onUpdateClipOpacity: (clipId: String, opacity: Float) -> Unit,
    onTrimQuickDelta: (Long) -> Unit,
    onMoveQuickDelta: (Long) -> Unit,
    showCategoryTabsInHeader: Boolean = false,
    modifier: Modifier = Modifier
) {
    val selectedScene = scenes.getOrNull(selectedSceneIndex)
    val primaryClip = selectedClips.lastOrNull()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkSurface)
    ) {
        if (showCategoryTabsInHeader) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DeepNavy)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                StudioToolTab.entries.forEach { tab ->
                    FilterChip(
                        selected = activeTab == tab,
                        onClick = { onSelectTab(tab) },
                        label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = {
                            SDIcon(
                                kind = tab.icon,
                                tint = if (activeTab == tab) MidnightNavy else InnovationTeal,
                                size = 14.dp
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = InnovationTeal,
                            selectedLabelColor = MidnightNavy,
                            containerColor = DarkSurfaceHigh,
                            labelColor = CloudWhite
                        ),
                        modifier = Modifier
                            .height(48.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("inspector_tab_${tab.name.lowercase()}")
                            .semantics { contentDescription = "Inspector ${tab.label} tab" }
                    )
                }
            }
            HorizontalDivider(color = Color(0xFF263A53), thickness = 1.dp)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Selected Clip Summary Card (always visible when a clip is selected)
            if (primaryClip != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceHigh),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = if (selectedClips.size > 1) {
                                        "Multi-Selection (${selectedClips.size} clips)"
                                    } else {
                                        "Clip: ${primaryClip.id}"
                                    },
                                    style = MaterialTheme.typography.titleSmall,
                                    color = PremiumGold,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Start: ${primaryClip.startMs}ms • Duration: ${primaryClip.durationMs}ms • In/Out: ${primaryClip.sourceInMs}..${primaryClip.sourceOutMs}ms",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MutedTextDark
                                )
                            }
                            val isGrouped = selectedBlock?.isGrouped ?: (primaryClip.groupId != null)
                            FilterChip(
                                selected = isGrouped,
                                onClick = onToggleGroup,
                                label = { Text(if (isGrouped) "Grouped" else "Ungrouped", style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier
                                    .height(48.dp)
                                    .minimumInteractiveComponentSize()
                                    .testTag("inspector_group_toggle_chip")
                                    .semantics { contentDescription = "Toggle clip group lock" }
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedButton(
                                onClick = { onTrimQuickDelta(-500L) },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("inspector_trim_minus_button")
                                    .semantics { contentDescription = "Trim clip duration minus 500 milliseconds" },
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Text("Trim -0.5s", style = MaterialTheme.typography.labelSmall)
                            }
                            OutlinedButton(
                                onClick = { onTrimQuickDelta(500L) },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("inspector_trim_plus_button")
                                    .semantics { contentDescription = "Trim clip duration plus 500 milliseconds" },
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Text("Trim +0.5s", style = MaterialTheme.typography.labelSmall)
                            }
                            OutlinedButton(
                                onClick = { onMoveQuickDelta(500L) },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("inspector_move_plus_button")
                                    .semantics { contentDescription = "Move clip start plus 500 milliseconds" },
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Text("Move +0.5s", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

            when (activeTab) {
                StudioToolTab.TEXT -> {
                    Text(
                        text = "Scene & Clip Text",
                        style = MaterialTheme.typography.titleMedium,
                        color = CloudWhite,
                        fontWeight = FontWeight.Bold
                    )
                    if (selectedScene != null) {
                        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Text("SCENE ${selectedSceneIndex + 1}  /  ON-SCREEN COPY", style = MaterialTheme.typography.labelSmall, color = InnovationTeal, fontWeight = FontWeight.SemiBold)
                            BasicTextField(
                                value = selectedScene.text,
                                onValueChange = onUpdateSceneText,
                                modifier = Modifier.fillMaxWidth().testTag("scene_text_input").semantics { contentDescription = "Scene text editor input" },
                                textStyle = MaterialTheme.typography.bodyLarge.copy(color = CloudWhite),
                                minLines = 2,
                                maxLines = 4
                            )
                            androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                        val words = ScriptSceneSplitter.wordCount(selectedScene.text)
                        Text(
                            text = "$words / ${ScriptSceneSplitter.MAX_WORDS_PER_SCENE} words recommended per scene",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (words > ScriptSceneSplitter.MAX_WORDS_PER_SCENE) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MutedTextDark
                            }
                        )
                        Text(
                            text = "Typography Preset",
                            style = MaterialTheme.typography.labelMedium,
                            color = CloudWhite
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        ) {
                            listOf(
                                SceneTextStyle.BIG_STATEMENT to "Big Statement",
                                SceneTextStyle.BULLET_POINT to "Bullet List",
                                SceneTextStyle.HIGHLIGHTED_KEYWORD to "Keyword Highlight"
                            ).forEach { (style, label) ->
                                FilterChip(
                                    selected = selectedScene.textStyle == style,
                                    onClick = { onUpdateSceneStyle(style) },
                                    label = { Text(label) },
                                    modifier = Modifier
                                        .height(48.dp)
                                        .minimumInteractiveComponentSize()
                                        .semantics { contentDescription = "Select $label text style" }
                                )
                            }
                        }
                    }
                }

                StudioToolTab.MEDIA -> {
                    Text(
                        text = "Media & Scene Background",
                        style = MaterialTheme.typography.titleMedium,
                        color = CloudWhite,
                        fontWeight = FontWeight.Bold
                    )
                    if (selectedScene != null) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        ) {
                            FilterChip(
                                selected = selectedScene.backgroundType == SceneBackgroundType.BRAND_COLOR,
                                onClick = { onUpdateSceneBackground(SceneBackgroundType.BRAND_COLOR) },
                                label = { Text("Brand Navy") },
                                modifier = Modifier.height(48.dp).minimumInteractiveComponentSize()
                            )
                            FilterChip(
                                selected = selectedScene.backgroundType == SceneBackgroundType.GRADIENT,
                                onClick = { onUpdateSceneBackground(SceneBackgroundType.GRADIENT) },
                                label = { Text("Teal Gradient") },
                                modifier = Modifier.height(48.dp).minimumInteractiveComponentSize()
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedButton(
                                onClick = onPickImageBackground,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .semantics { contentDescription = "Import image background" }
                            ) {
                                Text("Import Photo")
                            }
                            OutlinedButton(
                                onClick = onPickVideoBackground,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .semantics { contentDescription = "Import video clip background" }
                            ) {
                                Text("Import Video")
                            }
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = onAddScene,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = InnovationTeal,
                                    contentColor = MidnightNavy
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .semantics { contentDescription = "Add new scene" }
                            ) {
                                Text("Add Scene")
                            }
                            OutlinedButton(
                                onClick = onMergeNextScene,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("merge_scene_button")
                                    .semantics { contentDescription = "Merge with next scene" }
                            ) {
                                Text("Merge Next")
                            }
                        }
                    }
                }

                StudioToolTab.AUDIO -> {
                    Text(
                        text = "Voice-Over & Audio Tracks",
                        style = MaterialTheme.typography.titleMedium,
                        color = CloudWhite,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = onOpenVoiceOver,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = InnovationTeal,
                                contentColor = MidnightNavy
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("record_voiceover_button")
                                .semantics { contentDescription = "Open voice-over studio recorder" }
                        ) {
                            SDIcon(SDIconKind.Mic, tint = MidnightNavy, size = 18.dp)
                            Spacer(Modifier.width(6.dp))
                            Text("Record Voice-Over")
                        }
                        OutlinedButton(
                            onClick = onAddAudioSfxClip,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("add_sfx_clip_button")
                                .semantics { contentDescription = "Add sound effect marker clip" }
                        ) {
                            Text("+ SFX Cue")
                        }
                    }
                    if (primaryClip != null) {
                        Text(
                            text = "Clip Volume • ${(primaryClip.volume * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = CloudWhite
                        )
                        Slider(
                            value = primaryClip.volume,
                            onValueChange = { onUpdateClipVolume(primaryClip.id, it) },
                            valueRange = 0f..2f,
                            modifier = Modifier
                                .fillMaxWidth()
                                .semantics { contentDescription = "Clip volume slider" }
                        )
                    }
                    Text(
                        text = "EBU R128 loudness normalization (−16 LUFS, −1.0 dBTP true-peak ceiling) is active on voice takes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedTextDark
                    )
                }

                StudioToolTab.CAPTIONS -> {
                    Text(
                        text = "Captions Track",
                        style = MaterialTheme.typography.titleMedium,
                        color = CloudWhite,
                        fontWeight = FontWeight.Bold
                    )
                    val captionTrack = project.findTrackByType(TrackType.CAPTION)
                    Button(
                        onClick = {
                            val defaultText = selectedScene?.text?.lineSequence()?.firstOrNull()
                                ?.takeIf { it.isNotBlank() }
                                ?: "Key takeaway caption"
                            onAddCaptionClip(defaultText)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = InnovationTeal,
                            contentColor = MidnightNavy
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("add_caption_clip_button")
                            .semantics { contentDescription = "Add caption clip at playhead" }
                    ) {
                        Text("+ Add Caption at Playhead")
                    }
                    Text(
                        text = "${captionTrack?.clips?.size ?: 0} caption clips on timeline. Burn-in captions can also be enabled on export.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedTextDark
                    )
                }

                StudioToolTab.BRAND -> {
                    Text(
                        text = "Canvas Format & Brand Overlay",
                        style = MaterialTheme.typography.titleMedium,
                        color = CloudWhite,
                        fontWeight = FontWeight.Bold
                    )
                    Text("Aspect Ratio", style = MaterialTheme.typography.labelMedium, color = MutedTextDark)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        listOf("9:16", "4:5", "1:1", "16:9").forEach { value ->
                            FilterChip(
                                selected = ratio == value,
                                onClick = { onChangeRatio(value) },
                                label = { Text(value) },
                                modifier = Modifier
                                    .height(48.dp)
                                    .minimumInteractiveComponentSize()
                                    .testTag("ratio_chip_$value")
                                    .semantics { contentDescription = "Set video aspect ratio to $value" }
                            )
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Show Safe-Zone Guides", style = MaterialTheme.typography.bodyMedium, color = CloudWhite)
                        FilterChip(
                            selected = showSafeZones,
                            onClick = { onToggleSafeZones(!showSafeZones) },
                            label = { Text(if (showSafeZones) "Guides On" else "Guides Off") },
                            modifier = Modifier
                                .height(48.dp)
                                .minimumInteractiveComponentSize()
                                .testTag("toggle_safe_zones_chip")
                        )
                    }
                    if (primaryClip != null) {
                        Text(
                            text = "Layer Scale (${"%.2f".format(primaryClip.transform.scale)}x) & Opacity (${(primaryClip.transform.opacity * 100).toInt()}%)",
                            style = MaterialTheme.typography.labelMedium,
                            color = CloudWhite
                        )
                        Slider(
                            value = primaryClip.transform.scale,
                            onValueChange = { onUpdateClipScale(primaryClip.id, it) },
                            valueRange = 0.5f..2.0f,
                            modifier = Modifier.semantics { contentDescription = "Layer scale slider" }
                        )
                        Slider(
                            value = primaryClip.transform.opacity,
                            onValueChange = { onUpdateClipOpacity(primaryClip.id, it) },
                            valueRange = 0.1f..1.0f,
                            modifier = Modifier.semantics { contentDescription = "Layer opacity slider" }
                        )
                    }
                }

                StudioToolTab.EFFECTS -> {
                    Text(
                        text = "Studio Effects & Transitions (Skeleton)",
                        style = MaterialTheme.typography.titleMedium,
                        color = CloudWhite,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Skeleton UI wired to v2 Clip selection. Real-time GPU effect shaders are scheduled for the next module.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedTextDark
                    )
                    if (selectedScene != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = selectedScene.transition == SceneTransition.NONE,
                                onClick = { onUpdateSceneTransition(SceneTransition.NONE) },
                                label = { Text("Cut (None)") },
                                modifier = Modifier.height(48.dp).minimumInteractiveComponentSize()
                            )
                            FilterChip(
                                selected = selectedScene.transition == SceneTransition.FADE,
                                onClick = { onUpdateSceneTransition(SceneTransition.FADE) },
                                label = { Text("Smooth Fade") },
                                modifier = Modifier.height(48.dp).minimumInteractiveComponentSize()
                            )
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        listOf("Studio Clean", "Teal Cinema", "Gold Warmth", "Focus Vignette").forEach { fxName ->
                            Surface(
                                color = DarkSurfaceHigh,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .width(110.dp)
                                    .height(64.dp)
                                    .border(1.dp, Color(0xFF30445E), RoundedCornerShape(12.dp))
                            ) {
                                Column(
                                    modifier = Modifier.padding(8.dp),
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(fxName, style = MaterialTheme.typography.labelSmall, color = CloudWhite)
                                    Text("v2 Ready", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = PremiumGold)
                                }
                            }
                        }
                    }
                }

                StudioToolTab.TEMPLATES -> {
                    Text(
                        text = "Scene Templates & Sequence",
                        style = MaterialTheme.typography.titleMedium,
                        color = CloudWhite,
                        fontWeight = FontWeight.Bold
                    )
                    if (selectedScene != null) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        ) {
                            listOf(
                                SceneType.INTRO to "Intro Scene",
                                SceneType.CONTENT to "Key Statement",
                                SceneType.OUTRO to "Branded Outro"
                            ).forEach { (type, label) ->
                                FilterChip(
                                    selected = selectedScene.type == type,
                                    onClick = { onUpdateSceneType(type) },
                                    label = { Text(label) },
                                    modifier = Modifier.height(48.dp).minimumInteractiveComponentSize()
                                )
                            }
                        }
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        itemsIndexed(scenes, key = { _, s -> s.id }) { index, scene ->
                            val isCurrent = index == selectedSceneIndex
                            Card(
                                onClick = { onSelectSceneIndex(index) },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isCurrent) DarkSurfaceHighest else DarkSurfaceHigh
                                ),
                                modifier = Modifier
                                    .width(104.dp)
                                    .border(
                                        width = if (isCurrent) 2.dp else 1.dp,
                                        color = if (isCurrent) PremiumGold else Color(0xFF30445E),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .testTag("scene_template_card_$index")
                            ) {
                                Column(
                                    modifier = Modifier.padding(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    val bmp = previewBitmaps[scene.id]
                                    if (bmp != null && !bmp.isRecycled) {
                                        Image(
                                            bitmap = bmp.asImageBitmap(),
                                            contentDescription = "Scene ${index + 1} template preview",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(76.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(76.dp)
                                                .background(MidnightNavy, RoundedCornerShape(8.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("S${index + 1}", style = MaterialTheme.typography.labelMedium, color = PremiumGold)
                                        }
                                    }
                                    Text(
                                        text = "${index + 1}. ${scene.type.name}",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        color = CloudWhite,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                    OutlinedButton(
                        onClick = onOpenQuickPreview,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("quick_preview_button")
                            .semantics { contentDescription = "Open full quick preview player" }
                    ) {
                        Text("Open Full Quick Preview")
                    }
                }
            }
        }
    }
}
