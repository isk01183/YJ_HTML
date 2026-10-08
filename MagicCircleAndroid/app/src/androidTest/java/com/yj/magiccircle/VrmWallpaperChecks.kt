package com.yj.magiccircle

import android.content.Context
import java.io.File
import java.util.UUID

object VrmWallpaperChecks {
    fun run(context: Context) {
        val root=File(context.cacheDir,"vrm-wallpaper-check-${UUID.randomUUID()}")
        try {
            check(VrmWallpaperStore.freeSlot(setOf("vrm-slot-0","vrm-slot-1","vrm-slot-2"))==null)
            check(VrmWallpaperStore.freeSlot(setOf("vrm-slot-0","vrm-slot-2"))=="vrm-slot-1")
            val a=VrmWallpaperStore.stageFiles(root,"vrm-slot-0","a".repeat(64),VrmPlacement())
            val b=VrmWallpaperStore.stageFiles(root,"vrm-slot-1","b".repeat(64),VrmPlacement(.2f))
            VrmWallpaperStore.stageFiles(root,"vrm-slot-0","c".repeat(64),VrmPlacement(scale=1.3f))
            check(a.modelId=="a".repeat(64) && b.modelId=="b".repeat(64))
            check(VrmWallpaperStore.snapshot(root,"vrm-slot-0").modelId=="c".repeat(64))
            check(VrmWallpaperStore.snapshot(root,"vrm-slot-1")==b)
            check(runCatching {VrmWallpaperStore.stageFiles(root,"../outside","a".repeat(64),VrmPlacement())}.isFailure)
            check(runCatching {VrmWallpaperStore.stageFiles(root,"vrm-slot-1","../model",VrmPlacement())}.isFailure)
            val current=VrmWallpaperStore.snapshot(root,"vrm-slot-0")
            check(!current.acceptsRetry(a.generation+":retry"))
            check(current.acceptsRetry(current.generation+":retry"))
        } finally {root.deleteRecursively()}
    }
}
