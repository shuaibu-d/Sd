package com.sdtech.studio.video.domain

import com.sdtech.studio.video.Scene
import com.sdtech.studio.video.SceneBackgroundType
import com.sdtech.studio.video.SceneTextStyle
import com.sdtech.studio.video.SceneTransition
import com.sdtech.studio.video.SceneType
import java.util.UUID
import kotlin.math.abs

/**
 * Base reversible command for TimelineProject v2 edits.
 */
sealed interface TimelineCommand {
    val id: String
    val timestampMs: Long
    val description: String
    val isSliderDrag: Boolean

    fun apply(project: TimelineProject): TimelineProject
    fun revert(project: TimelineProject): TimelineProject

    fun canMergeWith(next: TimelineCommand, mergeWindowMs: Long): Boolean = false
    fun mergeWith(next: TimelineCommand): TimelineCommand = next
}

/**
 * 1. Add Command: Adds a single Clip to a Track, or adds a SceneBlock (background + text + logo + optional voice).
 */
class AddClipCommand(
    val clip: Clip? = null,
    val targetTrackId: String? = null,
    val targetTrackType: TrackType? = null,
    val sceneToAdd: Scene? = null,
    val sceneInsertIndex: Int? = null,
    override val id: String = UUID.randomUUID().toString(),
    override val timestampMs: Long = System.currentTimeMillis(),
    override val description: String = if (sceneToAdd != null) "Add SceneBlock" else "Add Clip",
    override val isSliderDrag: Boolean = false
) : TimelineCommand {

    private var previousProjectSnapshot: TimelineProject? = null

    override fun apply(project: TimelineProject): TimelineProject {
        previousProjectSnapshot = project
        if (sceneToAdd != null) {
            return applyAddScene(project, sceneToAdd, sceneInsertIndex)
        }
        val clipToInsert = requireNotNull(clip) { "Either clip or sceneToAdd must be provided to AddClipCommand" }
        val resolvedTrack = when {
            targetTrackId != null -> project.findTrack(targetTrackId)
            targetTrackType != null -> project.findTrackByType(targetTrackType)
            clipToInsert.trackId.isNotBlank() -> project.findTrack(clipToInsert.trackId)
            else -> project.findTrackByType(TrackType.VIDEO)
        } ?: throw IllegalArgumentException("Target track not found for AddClipCommand")

        val boundClip = clipToInsert.copy(trackId = resolvedTrack.id)
        val updatedTracks = project.tracks.map { track ->
            if (track.id == resolvedTrack.id) {
                track.copy(clips = track.clips.filterNot { it.id == boundClip.id } + boundClip)
            } else {
                track
            }
        }
        return project.copy(tracks = updatedTracks)
    }

    private fun applyAddScene(project: TimelineProject, scene: Scene, insertIndex: Int?): TimelineProject {
        val currentScenes = project.toScenes().toMutableList()
        val safeIndex = (insertIndex ?: currentScenes.size).coerceIn(0, currentScenes.size)
        currentScenes.add(safeIndex, scene)
        return ProjectDocumentMigrator.fromScenes(
            projectId = project.id,
            canvasRatio = project.canvasRatio,
            scenes = currentScenes,
            fps = project.fps,
            createdAtMs = project.createdAtMs,
            updatedAtMs = timestampMs
        )
    }

    override fun revert(project: TimelineProject): TimelineProject {
        val snapshot = previousProjectSnapshot
        if (snapshot != null) return snapshot

        if (sceneToAdd != null) {
            val remaining = project.toScenes().filterNot { it.id == sceneToAdd.id }
            return ProjectDocumentMigrator.fromScenes(
                projectId = project.id,
                canvasRatio = project.canvasRatio,
                scenes = remaining,
                fps = project.fps,
                createdAtMs = project.createdAtMs,
                updatedAtMs = project.updatedAtMs
            )
        }
        val clipId = clip?.id ?: return project
        return project.copy(
            tracks = project.tracks.map { track ->
                track.copy(clips = track.clips.filterNot { it.id == clipId })
            }
        )
    }

    companion object {
        fun forScene(
            scene: Scene,
            insertIndex: Int? = null,
            timestampMs: Long = System.currentTimeMillis()
        ): AddClipCommand = AddClipCommand(
            sceneToAdd = scene,
            sceneInsertIndex = insertIndex,
            timestampMs = timestampMs,
            description = "Add Scene ${scene.id}"
        )
    }
}

/**
 * 2. Move Command: Moves a Clip (and its grouped SceneBlock clips when grouped and propagateToGroup=true)
 * to a new startMs, trackId, or layerOrder, or reorders a SceneBlock in sequence.
 * Merges rapid slider/drag movements within mergeWindowMs.
 */
class MoveClipCommand(
    val clipId: String,
    val newStartMs: Long,
    val targetTrackId: String? = null,
    val newLayerOrder: Int? = null,
    val propagateToGroup: Boolean = true,
    val reorderSceneFromIndex: Int? = null,
    val reorderSceneToIndex: Int? = null,
    override val isSliderDrag: Boolean = false,
    override val id: String = UUID.randomUUID().toString(),
    override val timestampMs: Long = System.currentTimeMillis(),
    override val description: String = "Move Clip $clipId",
    private val initialSnapshot: TimelineProject? = null
) : TimelineCommand {

    private var savedSnapshot: TimelineProject? = initialSnapshot

    override fun apply(project: TimelineProject): TimelineProject {
        if (savedSnapshot == null) {
            savedSnapshot = project
        }
        if (reorderSceneFromIndex != null && reorderSceneToIndex != null) {
            val scenes = project.toScenes().toMutableList()
            if (reorderSceneFromIndex in scenes.indices && reorderSceneToIndex in scenes.indices) {
                val moved = scenes.removeAt(reorderSceneFromIndex)
                scenes.add(reorderSceneToIndex, moved)
                return ProjectDocumentMigrator.fromScenes(
                    projectId = project.id,
                    canvasRatio = project.canvasRatio,
                    scenes = scenes,
                    fps = project.fps,
                    createdAtMs = project.createdAtMs,
                    updatedAtMs = timestampMs
                )
            }
            return project
        }

        val targetClip = project.findClip(clipId) ?: return project
        val clampedNewStart = newStartMs.coerceAtLeast(0L)
        val deltaMs = clampedNewStart - targetClip.startMs
        val groupId = targetClip.groupId
        val sceneBlock = if (groupId != null) project.findSceneBlock(groupId) else null
        val shouldMoveGroup = propagateToGroup && groupId != null && (sceneBlock == null || sceneBlock.isGrouped)

        var tracks = project.tracks
        if (shouldMoveGroup) {
            tracks = tracks.map { track ->
                track.copy(
                    clips = track.clips.map { c ->
                        if (c.groupId == groupId) {
                            c.copy(
                                startMs = (c.startMs + deltaMs).coerceAtLeast(0L),
                                layerOrder = if (c.id == clipId && newLayerOrder != null) newLayerOrder else c.layerOrder
                            )
                        } else {
                            c
                        }
                    }
                )
            }
        } else {
            val currentTrack = project.findTrackForClip(clipId) ?: return project
            val destTrackId = targetTrackId ?: currentTrack.id
            val updatedClip = targetClip.copy(
                trackId = destTrackId,
                startMs = clampedNewStart,
                layerOrder = newLayerOrder ?: targetClip.layerOrder
            )
            tracks = if (destTrackId == currentTrack.id) {
                tracks.map { track ->
                    if (track.id == currentTrack.id) {
                        track.copy(clips = track.clips.map { if (it.id == clipId) updatedClip else it })
                    } else {
                        track
                    }
                }
            } else {
                tracks.map { track ->
                    when (track.id) {
                        currentTrack.id -> track.copy(clips = track.clips.filterNot { it.id == clipId })
                        destTrackId -> track.copy(clips = track.clips + updatedClip)
                        else -> track
                    }
                }
            }
        }
        return project.copy(tracks = tracks)
    }

    override fun revert(project: TimelineProject): TimelineProject {
        return savedSnapshot ?: project
    }

    override fun canMergeWith(next: TimelineCommand, mergeWindowMs: Long): Boolean {
        if (!this.isSliderDrag || next !is MoveClipCommand || !next.isSliderDrag) return false
        if (this.clipId != next.clipId) return false
        if (this.targetTrackId != next.targetTrackId) return false
        if (this.reorderSceneFromIndex != null || next.reorderSceneFromIndex != null) return false
        return abs(next.timestampMs - this.timestampMs) <= mergeWindowMs
    }

    override fun mergeWith(next: TimelineCommand): TimelineCommand {
        val nextMove = next as MoveClipCommand
        return MoveClipCommand(
            clipId = clipId,
            newStartMs = nextMove.newStartMs,
            targetTrackId = nextMove.targetTrackId,
            newLayerOrder = nextMove.newLayerOrder ?: this.newLayerOrder,
            propagateToGroup = nextMove.propagateToGroup,
            isSliderDrag = true,
            id = this.id,
            timestampMs = nextMove.timestampMs,
            description = nextMove.description,
            initialSnapshot = this.savedSnapshot
        )
    }

    companion object {
        fun reorderScene(
            sceneId: String,
            fromIndex: Int,
            toIndex: Int,
            timestampMs: Long = System.currentTimeMillis()
        ): MoveClipCommand = MoveClipCommand(
            clipId = "${sceneId}_bg",
            newStartMs = 0L,
            reorderSceneFromIndex = fromIndex,
            reorderSceneToIndex = toIndex,
            isSliderDrag = false,
            timestampMs = timestampMs,
            description = "Reorder Scene $sceneId from $fromIndex to $toIndex"
        )
    }
}

/**
 * 3. Trim Command: Trims a clip's startMs, durationMs, sourceInMs, sourceOutMs.
 * When grouped in a SceneBlock and propagateToGroup=true, trims all grouped clips in lockstep.
 * Merges rapid trim handle drags within mergeWindowMs.
 */
class TrimClipCommand(
    val clipId: String,
    val newStartMs: Long? = null,
    val newDurationMs: Long,
    val newSourceInMs: Long? = null,
    val newSourceOutMs: Long? = null,
    val setManualDuration: Boolean = false,
    val propagateToGroup: Boolean = true,
    override val isSliderDrag: Boolean = false,
    override val id: String = UUID.randomUUID().toString(),
    override val timestampMs: Long = System.currentTimeMillis(),
    override val description: String = "Trim Clip $clipId",
    private val initialSnapshot: TimelineProject? = null
) : TimelineCommand {

    private var savedSnapshot: TimelineProject? = initialSnapshot

    override fun apply(project: TimelineProject): TimelineProject {
        if (savedSnapshot == null) {
            savedSnapshot = project
        }
        val targetClip = project.findClip(clipId) ?: return project
        val validDuration = newDurationMs.coerceAtLeast(1L)
        val resolvedStart = (newStartMs ?: targetClip.startMs).coerceAtLeast(0L)
        val resolvedSourceIn = (newSourceInMs ?: targetClip.sourceInMs).coerceAtLeast(0L)
        val resolvedSourceOut = (newSourceOutMs ?: (resolvedSourceIn + validDuration)).coerceAtLeast(resolvedSourceIn + 1L)
        val explicitTrimEnd = newSourceOutMs != null || targetClip.hasExplicitTrimEnd

        val groupId = targetClip.groupId
        val sceneBlock = if (groupId != null) project.findSceneBlock(groupId) else null
        val shouldTrimGroup = propagateToGroup && groupId != null && (sceneBlock == null || sceneBlock.isGrouped)

        val updatedTracks = project.tracks.map { track ->
            track.copy(
                clips = track.clips.map { c ->
                    when {
                        c.id == clipId -> c.copy(
                            startMs = resolvedStart,
                            durationMs = validDuration,
                            sourceInMs = resolvedSourceIn,
                            sourceOutMs = resolvedSourceOut,
                            hasExplicitTrimEnd = explicitTrimEnd,
                            manualDurationMs = if (setManualDuration) validDuration else c.manualDurationMs
                        )
                        shouldTrimGroup && c.groupId == groupId -> c.copy(
                            startMs = resolvedStart,
                            durationMs = if (c.metadata["role"] == "voice" && c.voiceDurationMs != null) {
                                c.durationMs
                            } else {
                                validDuration
                            },
                            sourceOutMs = if (c.metadata["role"] == "voice" && c.voiceDurationMs != null) {
                                c.sourceOutMs
                            } else {
                                c.sourceInMs + validDuration
                            },
                            manualDurationMs = if (setManualDuration) validDuration else c.manualDurationMs
                        )
                        else -> c
                    }
                }
            )
        }

        val updatedBlocks = if (shouldTrimGroup && setManualDuration) {
            project.sceneBlocks.map { block ->
                if (block.id == groupId) block.copy(manualDurationMs = validDuration) else block
            }
        } else {
            project.sceneBlocks
        }

        return project.copy(tracks = updatedTracks, sceneBlocks = updatedBlocks)
    }

    override fun revert(project: TimelineProject): TimelineProject {
        return savedSnapshot ?: project
    }

    override fun canMergeWith(next: TimelineCommand, mergeWindowMs: Long): Boolean {
        if (!this.isSliderDrag || next !is TrimClipCommand || !next.isSliderDrag) return false
        if (this.clipId != next.clipId) return false
        return abs(next.timestampMs - this.timestampMs) <= mergeWindowMs
    }

    override fun mergeWith(next: TimelineCommand): TimelineCommand {
        val nextTrim = next as TrimClipCommand
        return TrimClipCommand(
            clipId = clipId,
            newStartMs = nextTrim.newStartMs ?: this.newStartMs,
            newDurationMs = nextTrim.newDurationMs,
            newSourceInMs = nextTrim.newSourceInMs ?: this.newSourceInMs,
            newSourceOutMs = nextTrim.newSourceOutMs ?: this.newSourceOutMs,
            setManualDuration = nextTrim.setManualDuration || this.setManualDuration,
            propagateToGroup = nextTrim.propagateToGroup,
            isSliderDrag = true,
            id = this.id,
            timestampMs = nextTrim.timestampMs,
            description = nextTrim.description,
            initialSnapshot = this.savedSnapshot
        )
    }
}

/**
 * 4. Split Command: Splits a clip at splitOffsetMs (0 < splitOffsetMs < clip.durationMs)
 * into two contiguous clips, or splits a grouped SceneBlock into two SceneBlocks.
 */
class SplitClipCommand(
    val clipId: String,
    val splitOffsetMs: Long,
    val leftClipId: String = "${clipId}_left",
    val rightClipId: String = "${clipId}_right",
    val splitGroup: Boolean = true,
    val replacementScenes: List<Scene>? = null,
    val targetSceneIndex: Int? = null,
    override val id: String = UUID.randomUUID().toString(),
    override val timestampMs: Long = System.currentTimeMillis(),
    override val description: String = "Split Clip $clipId at ${splitOffsetMs}ms",
    override val isSliderDrag: Boolean = false
) : TimelineCommand {

    private var savedSnapshot: TimelineProject? = null

    override fun apply(project: TimelineProject): TimelineProject {
        savedSnapshot = project

        if (replacementScenes != null && targetSceneIndex != null) {
            val scenes = project.toScenes().toMutableList()
            if (targetSceneIndex in scenes.indices && replacementScenes.isNotEmpty()) {
                scenes.removeAt(targetSceneIndex)
                scenes.addAll(targetSceneIndex, replacementScenes)
                return ProjectDocumentMigrator.fromScenes(
                    projectId = project.id,
                    canvasRatio = project.canvasRatio,
                    scenes = scenes,
                    fps = project.fps,
                    createdAtMs = project.createdAtMs,
                    updatedAtMs = timestampMs
                )
            }
            return project
        }

        val targetClip = project.findClip(clipId) ?: return project
        if (splitOffsetMs <= 0L || splitOffsetMs >= targetClip.durationMs) {
            return project
        }

        val groupId = targetClip.groupId
        val sceneBlock = if (groupId != null) project.findSceneBlock(groupId) else null
        val shouldSplitSceneBlock = splitGroup && sceneBlock != null && sceneBlock.isGrouped

        if (shouldSplitSceneBlock && sceneBlock != null) {
            return splitGroupedSceneBlock(project, sceneBlock, splitOffsetMs)
        }

        val (leftClip, rightClip) = splitSingleClip(targetClip, splitOffsetMs, leftClipId, rightClipId, targetClip.groupId, targetClip.groupId)
        val updatedTracks = project.tracks.map { track ->
            val index = track.clips.indexOfFirst { it.id == clipId }
            if (index >= 0) {
                val mutable = track.clips.toMutableList()
                mutable.removeAt(index)
                mutable.add(index, leftClip)
                mutable.add(index + 1, rightClip)
                track.copy(clips = mutable)
            } else {
                track
            }
        }
        return project.copy(tracks = updatedTracks)
    }

    private fun splitGroupedSceneBlock(
        project: TimelineProject,
        block: SceneBlock,
        offsetMs: Long
    ): TimelineProject {
        val leftBlockId = "${block.id}_part1"
        val rightBlockId = "${block.id}_part2"

        val updatedTracks = project.tracks.map { track ->
            val newClips = ArrayList<Clip>(track.clips.size + 1)
            for (clip in track.clips) {
                if (clip.groupId == block.id && clip.durationMs > offsetMs) {
                    val words = clip.text.trim().split(Regex("\\s+")).filter(String::isNotBlank)
                    val halfWords = (words.size / 2).coerceAtLeast(1)
                    val leftText = if (clip.text.isNotBlank() && words.size > 1) {
                        words.take(halfWords).joinToString(" ")
                    } else {
                        clip.text
                    }
                    val rightText = if (clip.text.isNotBlank() && words.size > 1) {
                        words.drop(halfWords).joinToString(" ")
                    } else {
                        clip.text
                    }
                    val roleSuffix = clip.metadata["role"] ?: "clip"
                    val lId = if (clip.id == clipId) leftClipId else "${leftBlockId}_$roleSuffix"
                    val rId = if (clip.id == clipId) rightClipId else "${rightBlockId}_$roleSuffix"
                    val (lClip, rClip) = splitSingleClip(
                        clip = clip,
                        offsetMs = offsetMs,
                        leftId = lId,
                        rightId = rId,
                        leftGroupId = leftBlockId,
                        rightGroupId = rightBlockId,
                        leftText = leftText,
                        rightText = rightText
                    )
                    newClips.add(lClip)
                    newClips.add(rClip)
                } else {
                    newClips.add(clip)
                }
            }
            track.copy(clips = newClips)
        }

        val blockIdx = project.sceneBlocks.indexOfFirst { it.id == block.id }
        val updatedBlocks = project.sceneBlocks.toMutableList()
        if (blockIdx >= 0) {
            val leftBgId = if (block.backgroundClipId == clipId) leftClipId else "${leftBlockId}_background"
            val rightBgId = if (block.backgroundClipId == clipId) rightClipId else "${rightBlockId}_background"
            val leftTxtId = if (block.textClipId == clipId) leftClipId else "${leftBlockId}_text"
            val rightTxtId = if (block.textClipId == clipId) rightClipId else "${rightBlockId}_text"
            val leftLogoId = if (block.logoClipId == clipId) leftClipId else "${leftBlockId}_logo"
            val rightLogoId = if (block.logoClipId == clipId) rightClipId else "${rightBlockId}_logo"
            val leftVoiceId = block.voiceClipId?.let { if (it == clipId) leftClipId else "${leftBlockId}_voice" }
            val rightVoiceId = block.voiceClipId?.let { if (it == clipId) rightClipId else "${rightBlockId}_voice" }

            val leftBlock = block.copy(
                id = leftBlockId,
                backgroundClipId = leftBgId,
                textClipId = leftTxtId,
                logoClipId = leftLogoId,
                voiceClipId = leftVoiceId,
                clipIds = listOfNotNull(leftBgId, leftTxtId, leftLogoId, leftVoiceId),
                manualDurationMs = offsetMs
            )
            val rightDuration = (project.findClip(block.backgroundClipId)?.durationMs ?: (offsetMs * 2)) - offsetMs
            val rightBlock = block.copy(
                id = rightBlockId,
                backgroundClipId = rightBgId,
                textClipId = rightTxtId,
                logoClipId = rightLogoId,
                voiceClipId = rightVoiceId,
                clipIds = listOfNotNull(rightBgId, rightTxtId, rightLogoId, rightVoiceId),
                manualDurationMs = rightDuration.coerceAtLeast(1L)
            )
            updatedBlocks.removeAt(blockIdx)
            updatedBlocks.add(blockIdx, leftBlock)
            updatedBlocks.add(blockIdx + 1, rightBlock)
        }

        return project.copy(tracks = updatedTracks, sceneBlocks = updatedBlocks)
    }

    private fun splitSingleClip(
        clip: Clip,
        offsetMs: Long,
        leftId: String,
        rightId: String,
        leftGroupId: String?,
        rightGroupId: String?,
        leftText: String = clip.text,
        rightText: String = clip.text
    ): Pair<Clip, Clip> {
        val rightDuration = (clip.durationMs - offsetMs).coerceAtLeast(1L)
        val leftKeyframes = clip.keyframes.filter { it.timeOffsetMs <= offsetMs }
        val rightKeyframes = clip.keyframes
            .filter { it.timeOffsetMs > offsetMs }
            .map { it.copy(id = "${it.id}_r", timeOffsetMs = it.timeOffsetMs - offsetMs) }

        val leftClip = clip.copy(
            id = leftId,
            durationMs = offsetMs,
            sourceOutMs = clip.sourceInMs + offsetMs,
            keyframes = leftKeyframes,
            groupId = leftGroupId,
            text = leftText,
            manualDurationMs = clip.manualDurationMs?.let { offsetMs }
        )
        val rightClip = clip.copy(
            id = rightId,
            startMs = clip.startMs + offsetMs,
            durationMs = rightDuration,
            sourceInMs = clip.sourceInMs + offsetMs,
            sourceOutMs = clip.sourceOutMs.coerceAtLeast(clip.sourceInMs + offsetMs + rightDuration),
            keyframes = rightKeyframes,
            groupId = rightGroupId,
            text = rightText,
            manualDurationMs = clip.manualDurationMs?.let { rightDuration }
        )
        return leftClip to rightClip
    }

    override fun revert(project: TimelineProject): TimelineProject {
        return savedSnapshot ?: project
    }
}

/**
 * 5. Delete Command: Deletes a Clip (or its entire grouped SceneBlock when deleteGroup=true).
 * Reversible: restores the deleted clip(s) and SceneBlock to their exact original positions.
 */
class DeleteClipCommand(
    val clipId: String,
    val deleteGroup: Boolean = true,
    override val id: String = UUID.randomUUID().toString(),
    override val timestampMs: Long = System.currentTimeMillis(),
    override val description: String = "Delete Clip $clipId",
    override val isSliderDrag: Boolean = false
) : TimelineCommand {

    private var savedSnapshot: TimelineProject? = null

    override fun apply(project: TimelineProject): TimelineProject {
        savedSnapshot = project
        val targetClip = project.findClip(clipId)
        val sceneBlock = project.findSceneBlock(clipId) ?: targetClip?.groupId?.let { project.findSceneBlock(it) }
        val shouldDeleteGroup = deleteGroup && sceneBlock != null && sceneBlock.isGrouped

        if (shouldDeleteGroup && sceneBlock != null) {
            val idsToRemove = sceneBlock.clipIds.toSet()
            val updatedTracks = project.tracks.map { track ->
                track.copy(clips = track.clips.filterNot { it.id in idsToRemove || it.groupId == sceneBlock.id })
            }
            val updatedBlocks = project.sceneBlocks.filterNot { it.id == sceneBlock.id }
            return project.copy(tracks = updatedTracks, sceneBlocks = updatedBlocks)
        }

        if (targetClip == null) return project
        val updatedTracks = project.tracks.map { track ->
            track.copy(clips = track.clips.filterNot { it.id == clipId })
        }
        val updatedBlocks = project.sceneBlocks.map { block ->
            if (clipId in block.clipIds) {
                block.copy(clipIds = block.clipIds.filterNot { it == clipId })
            } else {
                block
            }
        }
        return project.copy(tracks = updatedTracks, sceneBlocks = updatedBlocks)
    }

    override fun revert(project: TimelineProject): TimelineProject {
        return savedSnapshot ?: project
    }
}

/**
 * 6. SetProperty Command: Updates properties on a Clip, grouped SceneBlock, or TimelineProject.
 * Merges rapid slider drags on the same targetId and propertyKey within mergeWindowMs into a single command.
 */
class SetPropertyCommand(
    val targetId: String,
    val propertyKey: String,
    val updater: (TimelineProject) -> TimelineProject,
    override val isSliderDrag: Boolean = false,
    override val id: String = UUID.randomUUID().toString(),
    override val timestampMs: Long = System.currentTimeMillis(),
    override val description: String = "Set $propertyKey on $targetId",
    private val initialSnapshot: TimelineProject? = null
) : TimelineCommand {

    private var savedSnapshot: TimelineProject? = initialSnapshot

    override fun apply(project: TimelineProject): TimelineProject {
        if (savedSnapshot == null) {
            savedSnapshot = project
        }
        return updater(project)
    }

    override fun revert(project: TimelineProject): TimelineProject {
        return savedSnapshot ?: project
    }

    override fun canMergeWith(next: TimelineCommand, mergeWindowMs: Long): Boolean {
        if (!this.isSliderDrag || next !is SetPropertyCommand || !next.isSliderDrag) return false
        if (this.targetId != next.targetId || this.propertyKey != next.propertyKey) return false
        return abs(next.timestampMs - this.timestampMs) <= mergeWindowMs
    }

    override fun mergeWith(next: TimelineCommand): TimelineCommand {
        val nextProp = next as SetPropertyCommand
        return SetPropertyCommand(
            targetId = targetId,
            propertyKey = propertyKey,
            updater = nextProp.updater,
            isSliderDrag = true,
            id = this.id,
            timestampMs = nextProp.timestampMs,
            description = nextProp.description,
            initialSnapshot = this.savedSnapshot
        )
    }

    companion object {
        private fun updateClipInProject(
            project: TimelineProject,
            clipId: String,
            transformClip: (Clip) -> Clip
        ): TimelineProject {
            return project.copy(
                tracks = project.tracks.map { track ->
                    if (track.clips.any { it.id == clipId }) {
                        track.copy(clips = track.clips.map { if (it.id == clipId) transformClip(it) else it })
                    } else {
                        track
                    }
                }
            )
        }

        fun transform(
            clipId: String,
            newTransform: ClipTransform,
            propertyKey: String = "transform",
            isSliderDrag: Boolean = false,
            timestampMs: Long = System.currentTimeMillis()
        ): SetPropertyCommand = SetPropertyCommand(
            targetId = clipId,
            propertyKey = propertyKey,
            isSliderDrag = isSliderDrag,
            timestampMs = timestampMs,
            description = "Set $propertyKey on $clipId",
            updater = { project -> updateClipInProject(project, clipId) { it.copy(transform = newTransform) } }
        )

        fun scale(
            clipId: String,
            newScale: Float,
            isSliderDrag: Boolean = true,
            timestampMs: Long = System.currentTimeMillis()
        ): SetPropertyCommand = SetPropertyCommand(
            targetId = clipId,
            propertyKey = "transform.scale",
            isSliderDrag = isSliderDrag,
            timestampMs = timestampMs,
            description = "Set scale=$newScale on $clipId",
            updater = { project ->
                updateClipInProject(project, clipId) { it.copy(transform = it.transform.copy(scale = newScale)) }
            }
        )

        fun opacity(
            clipId: String,
            newOpacity: Float,
            isSliderDrag: Boolean = true,
            timestampMs: Long = System.currentTimeMillis()
        ): SetPropertyCommand = SetPropertyCommand(
            targetId = clipId,
            propertyKey = "transform.opacity",
            isSliderDrag = isSliderDrag,
            timestampMs = timestampMs,
            description = "Set opacity=$newOpacity on $clipId",
            updater = { project ->
                updateClipInProject(project, clipId) {
                    it.copy(transform = it.transform.copy(opacity = newOpacity.coerceIn(0f, 1f)))
                }
            }
        )

        fun position(
            clipId: String,
            newX: Float,
            newY: Float,
            isSliderDrag: Boolean = true,
            timestampMs: Long = System.currentTimeMillis()
        ): SetPropertyCommand = SetPropertyCommand(
            targetId = clipId,
            propertyKey = "transform.position",
            isSliderDrag = isSliderDrag,
            timestampMs = timestampMs,
            description = "Set position=($newX, $newY) on $clipId",
            updater = { project ->
                updateClipInProject(project, clipId) { it.copy(transform = it.transform.copy(x = newX, y = newY)) }
            }
        )

        fun rotation(
            clipId: String,
            newRotation: Float,
            isSliderDrag: Boolean = true,
            timestampMs: Long = System.currentTimeMillis()
        ): SetPropertyCommand = SetPropertyCommand(
            targetId = clipId,
            propertyKey = "transform.rotation",
            isSliderDrag = isSliderDrag,
            timestampMs = timestampMs,
            description = "Set rotation=$newRotation on $clipId",
            updater = { project ->
                updateClipInProject(project, clipId) { it.copy(transform = it.transform.copy(rotation = newRotation)) }
            }
        )

        fun volume(
            clipId: String,
            newVolume: Float,
            isSliderDrag: Boolean = true,
            timestampMs: Long = System.currentTimeMillis()
        ): SetPropertyCommand = SetPropertyCommand(
            targetId = clipId,
            propertyKey = "volume",
            isSliderDrag = isSliderDrag,
            timestampMs = timestampMs,
            description = "Set volume=$newVolume on $clipId",
            updater = { project ->
                updateClipInProject(project, clipId) { it.copy(volume = newVolume.coerceIn(0f, 2f)) }
            }
        )

        fun text(
            clipId: String,
            newText: String,
            newTextStyle: SceneTextStyle? = null,
            isSliderDrag: Boolean = false,
            timestampMs: Long = System.currentTimeMillis()
        ): SetPropertyCommand = SetPropertyCommand(
            targetId = clipId,
            propertyKey = "text",
            isSliderDrag = isSliderDrag,
            timestampMs = timestampMs,
            description = "Set text on $clipId",
            updater = { project ->
                updateClipInProject(project, clipId) {
                    val updatedStyle = newTextStyle ?: it.textStyle
                    it.copy(
                        text = newText,
                        textStyle = updatedStyle,
                        templateId = SceneBlockFactory.templateIdFor(it.sceneType, updatedStyle)
                    )
                }
            }
        )

        fun background(
            clipId: String,
            backgroundType: SceneBackgroundType,
            sourceUri: String? = null,
            colorHex: String? = null,
            secondaryColorHex: String? = null,
            timestampMs: Long = System.currentTimeMillis()
        ): SetPropertyCommand = SetPropertyCommand(
            targetId = clipId,
            propertyKey = "background",
            isSliderDrag = false,
            timestampMs = timestampMs,
            description = "Set background=$backgroundType on $clipId",
            updater = { project ->
                updateClipInProject(project, clipId) {
                    it.copy(
                        backgroundType = backgroundType,
                        sourceUri = sourceUri,
                        colorHex = colorHex ?: it.colorHex,
                        secondaryColorHex = secondaryColorHex ?: it.secondaryColorHex
                    )
                }
            }
        )

        fun effects(
            clipId: String,
            newEffects: List<ClipEffect>,
            isSliderDrag: Boolean = false,
            timestampMs: Long = System.currentTimeMillis()
        ): SetPropertyCommand = SetPropertyCommand(
            targetId = clipId,
            propertyKey = "effects",
            isSliderDrag = isSliderDrag,
            timestampMs = timestampMs,
            description = "Update effects on $clipId",
            updater = { project ->
                updateClipInProject(project, clipId) { it.copy(effects = newEffects) }
            }
        )

        fun keyframes(
            clipId: String,
            newKeyframes: List<Keyframe>,
            isSliderDrag: Boolean = false,
            timestampMs: Long = System.currentTimeMillis()
        ): SetPropertyCommand = SetPropertyCommand(
            targetId = clipId,
            propertyKey = "keyframes",
            isSliderDrag = isSliderDrag,
            timestampMs = timestampMs,
            description = "Update keyframes on $clipId",
            updater = { project ->
                updateClipInProject(project, clipId) { it.copy(keyframes = newKeyframes) }
            }
        )

        fun animations(
            clipId: String,
            newAnimations: AnimationPresets,
            timestampMs: Long = System.currentTimeMillis()
        ): SetPropertyCommand = SetPropertyCommand(
            targetId = clipId,
            propertyKey = "animations",
            isSliderDrag = false,
            timestampMs = timestampMs,
            description = "Update animations on $clipId",
            updater = { project ->
                updateClipInProject(project, clipId) { it.copy(animations = newAnimations) }
            }
        )

        fun layerOrder(
            clipId: String,
            newLayerOrder: Int,
            timestampMs: Long = System.currentTimeMillis()
        ): SetPropertyCommand = SetPropertyCommand(
            targetId = clipId,
            propertyKey = "layerOrder",
            isSliderDrag = false,
            timestampMs = timestampMs,
            description = "Set layerOrder=$newLayerOrder on $clipId",
            updater = { project ->
                updateClipInProject(project, clipId) { it.copy(layerOrder = newLayerOrder) }
            }
        )

        fun updateScene(
            sceneId: String,
            propertyKey: String = "scene",
            isSliderDrag: Boolean = false,
            timestampMs: Long = System.currentTimeMillis(),
            transformScene: (Scene) -> Scene
        ): SetPropertyCommand = SetPropertyCommand(
            targetId = sceneId,
            propertyKey = propertyKey,
            isSliderDrag = isSliderDrag,
            timestampMs = timestampMs,
            description = "Update Scene $sceneId ($propertyKey)",
            updater = { project ->
                val updatedScenes = project.toScenes().map { scene ->
                    if (scene.id == sceneId) transformScene(scene) else scene
                }
                ProjectDocumentMigrator.fromScenes(
                    projectId = project.id,
                    canvasRatio = project.canvasRatio,
                    scenes = updatedScenes,
                    fps = project.fps,
                    createdAtMs = project.createdAtMs,
                    updatedAtMs = timestampMs
                )
            }
        )

        fun replaceAllScenes(
            newScenes: List<Scene>,
            propertyKey: String = "scenes.replace",
            timestampMs: Long = System.currentTimeMillis()
        ): SetPropertyCommand = SetPropertyCommand(
            targetId = "project_scenes",
            propertyKey = propertyKey,
            isSliderDrag = false,
            timestampMs = timestampMs,
            description = "Replace scenes ($propertyKey)",
            updater = { project ->
                ProjectDocumentMigrator.fromScenes(
                    projectId = project.id,
                    canvasRatio = project.canvasRatio,
                    scenes = newScenes,
                    fps = project.fps,
                    createdAtMs = project.createdAtMs,
                    updatedAtMs = timestampMs
                )
            }
        )

        fun canvasRatio(
            newRatio: String,
            timestampMs: Long = System.currentTimeMillis()
        ): SetPropertyCommand = SetPropertyCommand(
            targetId = "project",
            propertyKey = "canvasRatio",
            isSliderDrag = false,
            timestampMs = timestampMs,
            description = "Set canvasRatio=$newRatio",
            updater = { project -> project.copy(canvasRatio = newRatio) }
        )
    }
}

/**
 * 7. Group Command: Groups a set of clips (or re-groups a SceneBlock) so they edit as a unit,
 * or ungroups a SceneBlock/groupId so its clips can be edited independently.
 */
class GroupCommand(
    val groupId: String,
    val clipIds: List<String> = emptyList(),
    val ungroup: Boolean = false,
    override val id: String = UUID.randomUUID().toString(),
    override val timestampMs: Long = System.currentTimeMillis(),
    override val description: String = if (ungroup) "Ungroup $groupId" else "Group $groupId",
    override val isSliderDrag: Boolean = false
) : TimelineCommand {

    private var savedSnapshot: TimelineProject? = null

    override fun apply(project: TimelineProject): TimelineProject {
        savedSnapshot = project
        if (ungroup) {
            val targetIds = if (clipIds.isNotEmpty()) {
                clipIds.toSet()
            } else {
                project.findSceneBlock(groupId)?.clipIds?.toSet()
                    ?: project.getGroupedClips(groupId).map { it.id }.toSet()
            }
            val updatedTracks = project.tracks.map { track ->
                track.copy(
                    clips = track.clips.map { clip ->
                        if (clip.id in targetIds || clip.groupId == groupId) {
                            clip.copy(groupId = null)
                        } else {
                            clip
                        }
                    }
                )
            }
            val updatedBlocks = project.sceneBlocks.map { block ->
                if (block.id == groupId) {
                    block.copy(isGrouped = false)
                } else {
                    block
                }
            }
            return project.copy(tracks = updatedTracks, sceneBlocks = updatedBlocks)
        } else {
            val targetIds = if (clipIds.isNotEmpty()) {
                clipIds.toSet()
            } else {
                project.findSceneBlock(groupId)?.clipIds?.toSet().orEmpty()
            }
            val updatedTracks = project.tracks.map { track ->
                track.copy(
                    clips = track.clips.map { clip ->
                        if (clip.id in targetIds) {
                            clip.copy(groupId = groupId)
                        } else {
                            clip
                        }
                    }
                )
            }
            val existingBlock = project.findSceneBlock(groupId)
            val updatedBlocks = if (existingBlock != null) {
                project.sceneBlocks.map { block ->
                    if (block.id == groupId) {
                        block.copy(
                            isGrouped = true,
                            clipIds = (block.clipIds + targetIds).distinct()
                        )
                    } else {
                        block
                    }
                }
            } else if (targetIds.size >= 2) {
                val clips = targetIds.mapNotNull { project.findClip(it) }
                val bg = clips.firstOrNull { project.findTrackForClip(it.id)?.type == TrackType.VIDEO } ?: clips.first()
                val txt = clips.firstOrNull { project.findTrackForClip(it.id)?.type == TrackType.TEXT } ?: bg
                val logo = clips.firstOrNull { project.findTrackForClip(it.id)?.type == TrackType.OVERLAY } ?: bg
                val voice = clips.firstOrNull { project.findTrackForClip(it.id)?.type == TrackType.AUDIO_VOICE }
                project.sceneBlocks + SceneBlock(
                    id = groupId,
                    sceneType = bg.sceneType,
                    templateId = bg.templateId ?: "scene_statement",
                    backgroundClipId = bg.id,
                    textClipId = txt.id,
                    logoClipId = logo.id,
                    voiceClipId = voice?.id,
                    clipIds = targetIds.toList(),
                    isGrouped = true,
                    transition = bg.transition,
                    manualDurationMs = bg.manualDurationMs
                )
            } else {
                project.sceneBlocks
            }
            return project.copy(tracks = updatedTracks, sceneBlocks = updatedBlocks)
        }
    }

    override fun revert(project: TimelineProject): TimelineProject {
        return savedSnapshot ?: project
    }

    companion object {
        fun ungroup(
            groupId: String,
            timestampMs: Long = System.currentTimeMillis()
        ): GroupCommand = GroupCommand(
            groupId = groupId,
            ungroup = true,
            timestampMs = timestampMs
        )

        fun group(
            groupId: String,
            clipIds: List<String>,
            timestampMs: Long = System.currentTimeMillis()
        ): GroupCommand = GroupCommand(
            groupId = groupId,
            clipIds = clipIds,
            ungroup = false,
            timestampMs = timestampMs
        )
    }
}

/**
 * Manages the Undo/Redo command stack with a default depth of 100 and automatic merging
 * of rapid slider drags within [mergeWindowMs].
 */
class CommandHistory(
    initialProject: TimelineProject,
    val maxDepth: Int = DEFAULT_MAX_DEPTH,
    val mergeWindowMs: Long = DEFAULT_MERGE_WINDOW_MS
) {
    var currentProject: TimelineProject = initialProject
        private set

    private val undoDeque = ArrayDeque<TimelineCommand>(maxDepth)
    private val redoDeque = ArrayDeque<TimelineCommand>(maxDepth)
    private var mergeBarrierActive: Boolean = false

    val canUndo: Boolean
        get() = undoDeque.isNotEmpty()

    val canRedo: Boolean
        get() = redoDeque.isNotEmpty()

    val undoCount: Int
        get() = undoDeque.size

    val redoCount: Int
        get() = redoDeque.size

    val undoStack: List<TimelineCommand>
        get() = undoDeque.toList()

    val redoStack: List<TimelineCommand>
        get() = redoDeque.toList()

    fun execute(command: TimelineCommand): TimelineProject {
        val nextProject = command.apply(currentProject)
        currentProject = nextProject
        redoDeque.clear()

        val lastCommand = undoDeque.lastOrNull()
        if (!mergeBarrierActive && lastCommand != null && lastCommand.canMergeWith(command, mergeWindowMs)) {
            undoDeque.removeLast()
            undoDeque.addLast(lastCommand.mergeWith(command))
        } else {
            if (undoDeque.size >= maxDepth) {
                undoDeque.removeFirst()
            }
            undoDeque.addLast(command)
        }
        mergeBarrierActive = false
        return currentProject
    }

    /**
     * Marks the end of a continuous slider drag gesture so the next drag on the same slider
     * will start a fresh undo entry even if it begins within [mergeWindowMs].
     */
    fun commitSliderGesture() {
        mergeBarrierActive = true
    }

    fun undo(): TimelineProject? {
        if (undoDeque.isEmpty()) return null
        val command = undoDeque.removeLast()
        currentProject = command.revert(currentProject)
        if (redoDeque.size >= maxDepth) {
            redoDeque.removeFirst()
        }
        redoDeque.addLast(command)
        mergeBarrierActive = true
        return currentProject
    }

    fun redo(): TimelineProject? {
        if (redoDeque.isEmpty()) return null
        val command = redoDeque.removeLast()
        currentProject = command.apply(currentProject)
        if (undoDeque.size >= maxDepth) {
            undoDeque.removeFirst()
        }
        undoDeque.addLast(command)
        mergeBarrierActive = true
        return currentProject
    }

    fun reset(project: TimelineProject) {
        currentProject = project
        undoDeque.clear()
        redoDeque.clear()
        mergeBarrierActive = false
    }

    companion object {
        const val DEFAULT_MAX_DEPTH = 100
        const val DEFAULT_MERGE_WINDOW_MS = 500L
    }
}
