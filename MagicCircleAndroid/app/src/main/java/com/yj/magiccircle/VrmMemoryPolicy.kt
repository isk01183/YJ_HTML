package com.yj.magiccircle

internal object VrmMemoryPolicy {
    fun budget(total: Long,available: Long,threshold: Long,lowMemory: Boolean,lowRam: Boolean): Long {
        if(lowMemory || lowRam || total<=0 || available !in 0..total || threshold<0 || available<=threshold)return 0
        // A snapshot is not a memory reservation; leave room for Android, other apps and graphics overhead.
        return minOf(768L*1024*1024,total/4,(available-threshold)/2)
    }
}
