package com.ss.ugc.android.editor.core

import android.graphics.Bitmap
import android.util.Log
import android.view.SurfaceView
import androidx.annotation.Keep
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import com.bytedance.ies.nle.editor_jni.*
import com.bytedance.ies.nleeditor.getMainSegment
import com.bytedance.ies.nlemedia.KeyFrameListener
import com.bytedance.ies.nlemediajava.keyframe.bean.KeyframeInfo
import com.ss.ugc.android.editor.core.api.adjust.IAdjustEditor
import com.ss.ugc.android.editor.core.api.animation.IAnimationEditor
import com.ss.ugc.android.editor.core.api.audio.IAudioEditor
import com.ss.ugc.android.editor.core.api.canvas.ICanvasEditor
import com.ss.ugc.android.editor.core.api.common.ICommonEditor
import com.ss.ugc.android.editor.core.api.filter.IFilterEditor
import com.ss.ugc.android.editor.core.api.keyframe.KeyframeUpdate
import com.ss.ugc.android.editor.core.api.sticker.IStickerEditor
import com.ss.ugc.android.editor.core.api.transition.ITransitionEditor
import com.ss.ugc.android.editor.core.api.video.IVideoEditor
import com.ss.ugc.android.editor.core.api.video.ImageCoverInfo
import com.ss.ugc.android.editor.core.event.*
import com.ss.ugc.android.editor.core.impl.*
import com.ss.ugc.android.editor.core.listener.OnUndoRedoListener
import com.ss.ugc.android.editor.core.manager.IVideoPlayer
import com.ss.ugc.android.editor.core.manager.UndoRedoManager
import com.ss.ugc.android.editor.core.manager.VideoPlayer
import com.ss.ugc.android.editor.core.utils.DLog
import com.ss.ugc.android.editor.core.utils.LiveDataBus
import com.ss.ugc.android.editor.core.utils.ThreadManager
import com.ss.ugc.android.editor.core.vm.BaseViewModel
import kotlin.math.roundToLong

@Keep
class NLEEditorContext(activity: FragmentActivity) : BaseViewModel(activity), IEditorContext {

    companion object {
        const val LOG_TAG = "EditorContext"
        const val STATE_PAUSE = 0
        const val STATE_PLAY = 1
        const val STATE_BACK = 2
        const val STATE_SEEK = 3
        const val DELETE_CLIP = 4
        const val SPLIT_CLIP = 5 //?????????????????????
        const val FREEZE_FRAME = 6 //??????
        const val SLOT_COPY = 7 //??????
        const val SLOT_REPLACE = 8 //??????
    }

    var undoRedoManager: UndoRedoManager = UndoRedoManager(this)
    override val changeResolutionEvent: MutableLiveData<Int> by lazy {
        val resolution = MutableLiveData<Int>()
        resolution.value = 1080
        resolution
    }
    override val changeFpsEvent: MutableLiveData<Int> by lazy {
        val fps = MutableLiveData<Int>()
        fps.value = 30
        fps
    }

    override val changeRatioEvent: MutableLiveData<Float> by lazy {
        MutableLiveData<Float>()
    }

    override val mainTrackSlotMoveEvent: MutableLiveData<MainTrackSlotMoveEvent> =
        MutableLiveData<MainTrackSlotMoveEvent>()
    override val mainTrackSlotClipEvent: MutableLiveData<MainTrackSlotClipEvent> =
        MutableLiveData<MainTrackSlotClipEvent>()
    override val trackSlotMoveEvent: MutableLiveData<TrackSlotMoveEvent> =
        MutableLiveData<TrackSlotMoveEvent>()
    override val trackSlotClipEvent: MutableLiveData<TrackSlotClipEvent> =
        MutableLiveData<TrackSlotClipEvent>()
    override val selectedTrackSlotEvent: MutableLiveData<SelectedTrackSlotEvent> =
        MutableLiveData<SelectedTrackSlotEvent>()
    override val transitionTrackSlotClickEvent: MutableLiveData<TransitionTrackSlotClickEvent> =
        MutableLiveData<TransitionTrackSlotClickEvent>()

    override val selectStickerEvent: MutableLiveData<SelectStickerEvent> =
        MutableLiveData<SelectStickerEvent>()
    override val selectSlotEvent: MutableLiveData<SelectSlotEvent> =
        MutableLiveData<SelectSlotEvent>()
    override val slotChangeChangeEvent: MutableLiveData<SelectSlotEvent> =
        MutableLiveData<SelectSlotEvent>()
    override val slotSelectChangeEvent: MutableLiveData<SelectSlotEvent> =
        MutableLiveData<SelectSlotEvent>()
    override val panelEvent: MutableLiveData<PanelEvent> = MutableLiveData<PanelEvent>()
    override val videoPositionEvent: MutableLiveData<Long> = MutableLiveData<Long>()
    override val volumeChangedEvent: MutableLiveData<VolumeEvent> = LiveDataBus.BusMutableLiveData()
    override val animationChangedEvent: MutableLiveData<NLEVideoAnimation?> =
        MutableLiveData<NLEVideoAnimation?>()
    override val moveStickerSlotEvent: MutableLiveData<UpdateClipRangeEvent> =
        MutableLiveData<UpdateClipRangeEvent>()
    override val clipStickerSlotEvent: MutableLiveData<AdjustClipRangeEvent> =
        MutableLiveData<AdjustClipRangeEvent>()

    override val oriAudioMuteEvent: MutableLiveData<Boolean> = MutableLiveData<Boolean>()

    override val closePanelEvent = MutableLiveData<ClosePanelEvent>()
    override val updateTrackEvent = MutableLiveData<Boolean>()
    /**
     * ???????????????????????? undo???redo???????????????
     */
    override val nleEditor: NLEEditor by lazy {
    NLEEditor()
    }

    override var nleModel: NLEModel = nleEditor.model
        get() = nleEditor.model//get newest


    override var nleMainTrack: NLETrack = NLETrack().apply {
        mainTrack = true
    }

    override var selectedNleTrackSlot: NLETrackSlot? = null
    override var selectedNleTrack: NLETrack? = null
    override var selectedNleCoverTrack: NLETrack? = null
    override var selectedNleCoverTrackSlot: NLETrackSlot? = null
    override var currentAudioSlot: NLETrackSlot? = null
    override var preTransitionNleSlot: NLETrackSlot? = null
    override var nextTransitionNleSlot: NLETrackSlot? = null

    override val videoPlayer: IVideoPlayer = VideoPlayer(this)
    override val videoEditor: IVideoEditor = VideoEditor(this)
    override val canvasEditor: ICanvasEditor = CanvasEditor(this)
    override val stickerEditor: IStickerEditor = StickerEditor(this)
    override val filterEditor: IFilterEditor = FilterEditor(this)
    override val animationEditor: IAnimationEditor = AnimationEditor(this)
    override val transitionEditor: ITransitionEditor = TransitionEditor(this)
    override val audioEditor: IAudioEditor = AudioEditor(this)
    override val adjustEditor: IAdjustEditor = AdjustEditor(this)
    override val commonEditor: ICommonEditor = CommonEditor(this)
    override val keyframeEditor = KeyframeEditor(this)
    private var hasInitialized = false
    override var isLoopPlay = false
    override var hasSettingTemplate = false // ????????????????????????????????????


    val isCoverMode
        get() = nleModel.cover?.enable ?: false

    val imageCoverInfo: MutableLiveData<ImageCoverInfo> = MutableLiveData()
    val resetCoverEvent: MutableLiveData<Boolean> = MutableLiveData()
    val saveCoverEvent: MutableLiveData<PanelEvent> = MutableLiveData()
    val closeCoverTextPanelEvent: MutableLiveData<Boolean> = MutableLiveData()
    val closeCanvasPanelEvent: MutableLiveData<Boolean> = MutableLiveData()
    val customCanvasImage: MutableLiveData<String> = MutableLiveData()
    var videoFrameLoader: IVideoFrameLoader? = null
    private val mMutableKeyframeUpdateEvent: MutableLiveData<KeyframeUpdate> = MutableLiveData();
    override val keyframeUpdateEvent: LiveData<KeyframeUpdate> get() = mMutableKeyframeUpdateEvent
    override var keyframeTimeRange: (() -> Float)? = null
    override val slotReplaceEvent: MutableLiveData<SelectSlotEvent> =
        MutableLiveData<SelectSlotEvent>()
    override val updateCoverEvent: MutableLiveData<Boolean> =
        MutableLiveData<Boolean>()

    // ??????????????????init?????????
    init {
        // ?????? ??????/undo/redo ????????????
        System.loadLibrary("NLEEditorJni")
        //????????? NLE
        nleEditor.toString()
    }

    override fun init(workSpace: String?, surfaceView: SurfaceView) {
        if (!hasInitialized) {
            // ?????? ??????/undo/redo ????????????
            videoPlayer.init(surfaceView, workSpace)
            registerEvent()
            hasInitialized = true
            setKeyframeListener()
        }
    }

    private fun registerEvent() {
        mainTrackSlotMoveEvent.observe(activity, Observer {
            it?.apply {
                onMoveMainTrackSlot(nleTrackSlot, fromIndex, toIndex)
            }
        })
        mainTrackSlotClipEvent.observe(activity, Observer {
            it?.apply {
                onClipMainTrackSlot(slot, start, duration, side)
            }
        })
        trackSlotMoveEvent.observe(activity, Observer {
            it?.apply {
                onMoveTrackSlot(fromTrackIndex, toTrackIndex, nleTrackSlot, newStart, curPosition)
            }
        })
        trackSlotClipEvent.observe(activity, Observer {
            it?.apply {
                onClipTrackSlot(slot, startDiff, duration)
            }
        })
        selectedTrackSlotEvent.observe(activity, Observer {
            it?.apply {
                updateSelectedTrackSlot(nleTrack, nleTrackSlot)
            }
        })
        transitionTrackSlotClickEvent.observe(activity, Observer {
            it?.apply {
                updateTransitionTrackSlot(nleTrackSlot, nextSegment)
            }
        })
        oriAudioMuteEvent.observe(activity, Observer {
            it?.apply {
                closeOriVolume(it)
            }
        })

        videoPositionEvent.observe(activity) {
            //????????????????????????????????????????????????????????????
            if (selectedNleTrack?.mainTrack == false) {
                it?.let { time ->
                    keyframeEditor.updateSlotFromLocal(time)
                }
            }
        }
    }

    /**
     * ????????????/??????????????????
     */
    private fun closeOriVolume(isAllMute: Boolean) {
        nleMainTrack.sortedSlots.forEach { it ->
            it.getMainSegment<NLESegmentVideo>()?.enableAudio = !isAllMute
            it.keyframes.forEach {
                it.getMainSegment<NLESegmentVideo>()?.enableAudio = !isAllMute
            }
        }
        //??????????????????
        keyframeEditor.updateSlotFromLocal(videoPlayer.curPosition() * 1000L)
        nleEditor.commitDone()
        volumeChangedEvent.postValue(VolumeEvent())  //??????????????????
    }

    // ???????????????
    override fun moveTrack(listener: (Int, Int) -> Unit) {
        videoPlayer.isPlaying = true
        ThreadManager.getTheadPool().execute {
            while (videoPlayer.isPlaying) {
                val curPosition = videoPlayer.curPosition()
                if (curPosition != 0) {//????????????????????????prepare????????????????????????0??????????????????????????????
                    listener.invoke(curPosition, videoPlayer.totalDuration())
                }
                Thread.sleep(20)
            }
        }
    }

    override fun stopTrack() {
        videoPlayer.isPlaying = false
    }

    private fun onMoveMainTrackSlot(nleTrackSlot: NLETrackSlot, fromIndex: Int, toIndex: Int) {
        if (fromIndex == toIndex) {
            return
        }
        nleTrackSlot.apply {
            NLESegmentVideo.dynamicCast(mainSegment)?.apply {
                nleTrackSlot.videoAnims?.forEach {
                    videoPlayer.refreshCurrentFrame()
                }

                //??????nle
                nleMainTrack.removeSlot(nleTrackSlot)
                nleMainTrack.addSlotAtIndex(nleTrackSlot, toIndex)
                //??????slot?????????????????????????????????????????????????????????
                if (toIndex == nleMainTrack.slots.size - 1) {
                    nleTrackSlot.endTransition = null
                }
                nleMainTrack.slots.forEachIndexed { index, nleTrackSlot ->
                    if (index == nleMainTrack.slots.size - 1) {
                        nleTrackSlot.endTransition = null
                    }
                }
                nleEditor.commitDone()

                //TODO: 2021/3/26 ??????????????? ????????? ?????????????????????
//                updateTransition()
                videoPlayer.seekToPosition(
                    nleMainTrack.getSlotByIndex(toIndex).startTime.toMilli().toInt()
                )
            }
        }
    }

    /**
     * @param start:????????????slot???????????????
     * @param duration: ??????????????????
     * start ??? duration  ?????????????????????????????????????????????
     * @param side : ??????????????????????????????
     */
    private fun onClipMainTrackSlot(slot: NLETrackSlot, start: Int, duration: Int, side: Int) {
        // NLETrackSlot ???????????? speed, repeatCount, ???????????? endTime; endTime ?????? Segment ????????????????????????
        // slot.endTime = slot.startTime + duration * 1000
        val videoSeg = NLESegmentVideo.dynamicCast(slot.mainSegment)

        //??????move????????????slot?????????????????????
        val move = start * 1000 - videoSeg.timeClipStart
        val originStartTime = slot.startTime

        videoSeg.timeClipStart = (start * 1000).toLong()
        videoSeg.timeClipEnd = (start * 1000).toLong() + duration * 1000
        val realDuration = (duration * 1000 / (NLESegmentVideo.dynamicCast(slot.mainSegment)
            ?.avgSpeed() ?: 1f)).roundToLong()
        slot.endTime = slot.startTime + realDuration

        slot.startTime = originStartTime

        nleMainTrack.timeSort()
        slot.adjustKeyFrame((keyframeTimeRange?.invoke() ?: 0f).toLong())
        nleEditor.commitDone()
    }

    private fun onMoveTrackSlot(
        fromTrackIndex: Int,
        toTrackIndex: Int,
        slot: NLETrackSlot,
        newStart: Long,
        curPosition: Long
    ) {
        updateSelectedTrackSlot(nleModel.getTrackBySlot(slot), slot)
        if (slot.isEffectSlot()) {
            // ????????????
            onMoveVideoEffect(slot, newStart, fromTrackIndex, toTrackIndex)
            return
        }
        val trackType = nleModel.getTrackBySlot(slot)?.getVETrackType()
        DLog.d(LOG_TAG, "onMoveTrackSlot trackType = ${trackType ?: "null"}")
        when (trackType) {
            Constants.TRACK_STICKER -> {
                moveStickerClipRange(newStart)
                onMoveNle(Constants.TRACK_STICKER, slot, newStart, fromTrackIndex, toTrackIndex)
                videoPlayer.player?.setCanvasMinDuration(
                    (nleModel.maxTargetEnd / 1000).toInt(),
                    true
                )
            }

            Constants.TRACK_VIDEO -> {
                onMoveNle(Constants.TRACK_VIDEO, slot, newStart, fromTrackIndex, toTrackIndex)
                videoPlayer.seekToPosition(newStart.toMilli().toInt())
            }

            Constants.TRACK_AUDIO -> {
                onMoveNle(Constants.TRACK_AUDIO, slot, newStart, fromTrackIndex, toTrackIndex)
            }
            // ??????
            Constants.TRACK_FILTER_ADJUST -> {
                onMoveNle(
                    Constants.TRACK_FILTER_ADJUST,
                    slot,
                    newStart,
                    fromTrackIndex,
                    toTrackIndex
                )
            }
            Constants.TRACK_FILTER_FILTER -> {
                onMoveNle(
                    Constants.TRACK_FILTER_FILTER,
                    slot,
                    newStart,
                    fromTrackIndex,
                    toTrackIndex
                )
            }

            else -> {

            }
        }
    }

    private fun onMoveVideoEffect(
        slot: NLETrackSlot,
        newStart: Long,
        fromTrackIndex: Int,
        toTrackIndex: Int
    ) {
        DLog.d("move video effect $newStart $toTrackIndex")
        var hasChange = false
        slot.apply {
            val duration = this.duration
            //?????????????????????????????????
            val newStart = newStart.coerceAtMost(nleModel.maxTargetEnd - duration)
            if (newStart != this.startTime) {
                this.startTime = newStart
                hasChange = true
            }
            val newEnd = (this.startTime + duration).coerceAtMost(nleModel.maxTargetEnd)
            if (newEnd != this.endTime) {
                this.endTime = newEnd
                hasChange = true
            }
            if (this.layer != toTrackIndex) {
                hasChange = true
                this.layer = toTrackIndex
            }
        }
        if (hasChange) {
            nleEditor.commitDone()
        } else {
            //?????????????????????????????????????????????????????????????????????????????????track
            updateTrackEvent.value = false
        }
    }

    private fun onClipTrackSlot(slot: NLETrackSlot, startDiff: Long, duration: Long) {
        if (slot.isEffectSlot()) {
            // ????????????
            DLog.d("onClip ???????????? slot?????????")
            val segment = NLESegmentEffect.dynamicCast(slot.mainSegment)
            slot.apply {
//                val preDuration = this.duration
                this.startTime += startDiff
                endTime = startTime + duration
//                    segment.timeClipStart += startDiff
//                    segment.timeClipEnd = segment.timeClipStart + duration
                nleEditor.commitDone()
            }
            return
        }
        val trackType = nleModel.getTrackBySlot(slot)?.getVETrackType()
        DLog.d(LOG_TAG, "onClipTrackSlot trackType = ${trackType ?: "null"}")

        when (trackType) {
            Constants.TRACK_STICKER -> {
                clipStickerSlotEvent.value = AdjustClipRangeEvent(slot, startDiff, duration, true)
                slot.adjustKeyFrame((keyframeTimeRange?.invoke() ?: 0f).toLong())
                commit()
                videoPlayer.player?.setCanvasMinDuration(
                    (nleModel.maxTargetEnd / 1000).toInt(),
                    true
                )
            }

            Constants.TRACK_VIDEO -> {
                val segment = NLESegmentVideo.dynamicCast(slot.mainSegment)
                slot.apply {
                    this.startTime += (startDiff)
                    endTime = startTime + duration
                    segment.timeClipStart += (startDiff * segment.absSpeed).toLong()
                    segment.timeClipEnd =
                        segment.timeClipStart + (duration * segment.absSpeed).toLong()
                    adjustKeyFrame((keyframeTimeRange?.invoke() ?: 0f).toLong())
                    nleEditor.commitDone()
                }
            }

            Constants.TRACK_AUDIO -> {
                val segment = NLESegmentAudio.dynamicCast(slot.mainSegment)
                slot.apply {
                    this.startTime += startDiff
                    this.endTime = startTime + duration
                    segment.timeClipStart += startDiff
                    segment.timeClipEnd = segment.timeClipStart + duration
                    // ????????????????????????
                    segment.fadeInLength =
                        if (segment.fadeInLength >= duration) duration else segment.fadeInLength
                    segment.fadeOutLength =
                        if (segment.fadeOutLength >= duration) duration else segment.fadeOutLength
                    slot.adjustKeyFrame((keyframeTimeRange?.invoke() ?: 0f).toLong())
                    nleEditor.commitDone()
                }
            }

            Constants.TRACK_VIDEO_EFFECT -> {
                slot.apply {
                    this.startTime += startDiff
                    endTime = startTime + duration
                    nleEditor.commitDone()
                }
            }
            // ??????
            Constants.TRACK_FILTER_ADJUST, Constants.TRACK_FILTER_FILTER -> {
                slot.apply {
                    this.startTime += startDiff
                    endTime = startTime + duration
                    adjustKeyFrame((keyframeTimeRange?.invoke() ?: 0f).toLong())
                    nleEditor.commitDone()
                }
            }

            else -> {

            }
        }
    }

    private fun updateTransitionTrackSlot(nleTrackSlot: NLETrackSlot?, nextSegment: NLETrackSlot?) {
        this.preTransitionNleSlot = nleTrackSlot
        this.nextTransitionNleSlot = nextSegment
    }

    /**
     * trackIndex??? ????????????????????????track??? ????????????trackIndex??????????????? ??????????????????trackIndex????????????track??????????????????slot??????
     */
    private fun onMoveNle(
        trackType: String,
        slot: NLETrackSlot,
        newStart: Long,
        fromTrackIndex: Int,
        toTrackIndex: Int,
        trackIndex: Int = 0
    ) {
        DLog.d(
            LOG_TAG,
            "onMoveNle trackType =$trackType, fromTrackIndex = $fromTrackIndex, toTrackIndex = $toTrackIndex "
        )
        slot.apply {
            val duration = this.duration
            this.startTime = newStart
            this.endTime = startTime+duration
            //??????????????????????????? ???????????????track?????????
            if (fromTrackIndex != toTrackIndex) { // ???????????? ??????track????????????slot ??????track?????????slot
                val trackBeforeSlot = nleModel.getTrackBySlot(slot) ?: return@apply
                trackBeforeSlot.removeSlot(slot) //1.remove
                val beforeLayer = trackBeforeSlot.getExtra("track_layer")
                val head = if (fromTrackIndex < toTrackIndex) fromTrackIndex + 1 else toTrackIndex
                val tail = if (fromTrackIndex < toTrackIndex) toTrackIndex else fromTrackIndex - 1
                for (index in head..tail) { //1 2
                    nleModel.tracks.filter {
                        !it.mainTrack && it.getVETrackType() == trackType && it.layer == index
                    }.also { it ->
                        if (it.isNotEmpty()) {
                            run {
                                it.forEach {
                                    if (toTrackIndex == it.layer) {
                                        DLog.d("onMove ??????layer???${index}???track,???????????????track---- ??????slot????????????track")
                                        it.addSlot(slot)
                                        updateSelectedTrackSlot(it, slot)
                                        return@run
                                    } else {
                                        DLog.d("onMove ??????layer???${index}???track,????????????track---- ????????????")
                                    }
                                }
                            }
                        } else {
                            NLETrack().apply {
                                mainTrack = false
                                setVETrackType(trackType)
                                layer = index

                                when (trackType) {
                                    Constants.TRACK_VIDEO -> {
                                        extraTrackType = NLETrackType.VIDEO
                                    }
                                    Constants.TRACK_STICKER -> {
                                        extraTrackType = NLETrackType.STICKER
                                    }
                                    Constants.TRACK_AUDIO -> {
                                        extraTrackType = NLETrackType.AUDIO
                                    }
                                    Constants.TRACK_FILTER_ADJUST, Constants.TRACK_FILTER_FILTER -> {
                                        extraTrackType = NLETrackType.FILTER
                                    }
                                }
                                if (toTrackIndex == index) {
                                    DLog.d("onMove ?????????layer???${index}???track---- ??????track??????????????????track ??????slot????????????track")
                                    slot.layer =
                                        toTrackIndex + 1//??????????????????fromTrackIndex???toTrackIndex??????0????????????????????????????????????????????????slot??????layer????????????1??????
                                    this.addSlot(slot)
                                    this.setExtra("track_layer", beforeLayer)
                                    updateSelectedTrackSlot(this, slot)  // ???????????? ????????????
                                } else {
                                    DLog.d("onMove ?????????layer???${index}???track---- ??????track???????????????track---- ????????????")
                                }
                                nleModel.addTrack(this) // ???????????????model
                            }
                        }
                    }
                }
            }
            nleEditor.commitDone()
        }
    }

    override fun updateSelectedTrackSlot(nleTrack: NLETrack?, nleTrackSlot: NLETrackSlot?) {
        this.selectedNleTrack = nleTrack
        this.selectedNleTrackSlot = nleTrackSlot
        volumeChangedEvent.value = VolumeEvent()
        slotSelectChangeEvent.value = SelectSlotEvent(nleTrackSlot)
    }

    private fun moveStickerClipRange(newStart: Long) {
        selectedNleTrack?.apply {
            selectedNleTrackSlot?.apply {
                moveStickerSlotEvent.value = UpdateClipRangeEvent(newStart, newStart + duration)
                startTime = newStart
                endTime = startTime + duration
            }
        }
    }

    override fun saveDraft(): String {
        return nleEditor.store()
    }

    override fun restoreDraft(data: String): NLEError {
        return nleEditor.restore(data)
    }

    override fun undo(): Boolean {
        return undoRedoManager.undo()
    }

    override fun redo(): Boolean {
        return undoRedoManager.redo()
    }

    override fun canUndo(): Boolean {
        return undoRedoManager.canUndo()
    }

    override fun canRedo(): Boolean {
        return undoRedoManager.canRedo()
    }

    override fun addUndoRedoListener(listener: OnUndoRedoListener) {
        undoRedoManager.addUndoRedoListener(listener)
    }

    override fun removeUndoRedoListener(listener: OnUndoRedoListener) {
        undoRedoManager.removeUndoRedoListener(listener)
    }

    override fun commit() {
        nleEditor.commit()
    }

    override fun done(actionMsg: String?) {
        nleEditor.commitDoneWithMsg(msg = actionMsg)
    }

    override fun transientDone() {
        nleEditor.commitDone()
        nleEditor.trim(0, 0)
    }

    private fun setKeyframeListener() {
        videoPlayer.player?.setKeyframeListener(object : KeyFrameListener {
            override fun onDisplayCallback(time: Int, mode: Int, type: Int) {
            }

            //???????????????????????????????????????
            override fun onProcessCallback(filterIndex: Int, time: Int, param: String) {
                val selectSlot = selectedNleTrackSlot ?: return
                val selectTrack = selectedNleTrack ?: return
                val curSlotTime = videoPlayer.player?.getCurrentPosition() ?: 0L
                if (curSlotTime < selectSlot.startTime || curSlotTime > selectSlot.endTime) {
                    return
                }
                keyframeEditor.updateSlotFromLocal(curSlotTime)//ve???????????????????????????????????????
                videoPlayer.player?.refreshAllKeyframeInfo(filterIndex, param, selectTrack, selectSlot)
                mMutableKeyframeUpdateEvent.postValue(KeyframeUpdate(filterIndex, time, param))
            }

        })
    }

    override fun getKeyframeInfo(filterIndex: Int): KeyframeInfo? =
        videoPlayer.player?.getKeyframeInfo(filterIndex)

    override fun getString(resId: Int): String = activity.getString(resId)

    override fun onPause() {
        super.onPause()
        videoPlayer.isPlayWhileEditorSwitch = videoPlayer.isPlaying
        videoPlayer.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        hasInitialized = false
        videoPlayer.destroy()
    }

}

interface IVideoFrameLoader {
    fun loadVideoFrame(
        selectedSlotPath: String,
        timeStamp: Int,
        isImage: Boolean,
        onCompleted: (videoFrame: Bitmap) -> Unit
    )
}