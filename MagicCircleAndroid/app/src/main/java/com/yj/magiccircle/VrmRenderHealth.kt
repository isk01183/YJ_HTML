package com.yj.magiccircle

/** Monotonic, visible-time budget. Surface recreation never replenishes retries. */
internal class VrmRenderHealth {
    private var visible=false
    private var last=0L
    private var waiting=0L
    private var healthy=0L
    private var lastFrames=-1L
    private var wasReady=false
    private var retries=0
    private fun elapsed(now: Long): Long {val delta=(now-last).coerceAtLeast(0);last=now;return if(visible)delta else 0}
    fun setVisible(value: Boolean,nowMs: Long) {
        val delta=elapsed(nowMs)
        if(!wasReady)waiting+=delta
        visible=value
        if(!value)healthy=0
    }
    fun newAttempt(nowMs: Long) {last=nowMs;waiting=0;healthy=0;lastFrames=-1;wasReady=false}
    fun sample(ready: Boolean,frames: Long,nowMs: Long): VrmFailure? {
        val delta=elapsed(nowMs)
        if(ready && frames>lastFrames) {
            if(wasReady)healthy+=delta
            waiting=0
            if(healthy>=30000)retries=0
        } else {waiting+=delta;healthy=0}
        wasReady=ready;lastFrames=frames
        return if(waiting>=45000)VrmFailure.TIMEOUT else null
    }
    fun onFailure(kind: VrmFailure): Long? {
        healthy=0;wasReady=false
        if(kind==VrmFailure.MODEL || kind==VrmFailure.PAGE || retries>=2)return null
        return if(retries++==0)1000L else 3000L
    }
    fun reset(nowMs: Long) {retries=0;newAttempt(nowMs)}
}
