package com.yj.magiccircle

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.SystemClock
import android.widget.Button
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

object VrmHairLifecycleChecks {
    fun run(test: Instrumentation,mode: String) {
        check(android.os.Build.PRODUCT.startsWith("sdk_"));require(mode in listOf("open","preparing","gallery"))
        val context=test.targetContext
        val field=VrmAvatarStore::class.java.getDeclaredField("instance").apply {isAccessible=true};val previous=field.get(null)
        field.set(null,VrmAvatarStore(File(context.cacheDir,"hair-lifecycle-${UUID.randomUUID()}")))
        val io=VrmAvatarActivity::class.java.getDeclaredField("io").apply {isAccessible=true}.get(null) as ExecutorService
        val first=CountDownLatch(1);val firstEntered=CountDownLatch(1);val second=CountDownLatch(1);val secondEntered=CountDownLatch(1)
        var activity: Activity?=null
        fun main(action:()->Unit){var error: Throwable?=null;test.runOnMainSync {try {action()}catch(e: Throwable){error=e}};error?.let {throw it}}
        fun waitFor(label: String,timeout: Long=90_000,condition:()->Boolean){val end=SystemClock.elapsedRealtime()+timeout;while(!condition()&&SystemClock.elapsedRealtime()<end)Thread.sleep(100);check(condition()){label}}
        fun button(tag: String)=activity!!.window.decorView.findViewWithTag<Button>(tag)
        fun ready(): Boolean {var result=false;main {result=button("avatar-save-new")?.isEnabled==true};return result}
        val active=VrmAvatarActivity::class.java.getDeclaredField("active").apply {isAccessible=true}
        try {
            if(mode!="gallery") {
                io.execute {firstEntered.countDown();check(first.await(60,TimeUnit.SECONDS))}
                check(firstEntered.await(10,TimeUnit.SECONDS))
            }
            activity=test.startActivitySync(Intent(context,VrmAvatarActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            if(mode=="preparing") {
                io.execute {secondEntered.countDown();check(second.await(60,TimeUnit.SECONDS))}
                first.countDown();check(secondEntered.await(10,TimeUnit.SECONDS))
                val value=VrmAvatarActivity::class.java.getDeclaredField("value").apply {isAccessible=true}
                waitFor("Initial open did not reach hair preparation",10_000){var found=false;main {found=value.get(activity)!=null};found}
            }
            if(mode=="gallery") {
                waitFor("Initial editor not ready",600_000,::ready)
                // Preserve the one-card UI from before a newly registered donor becomes available.
                val parts=VrmHairPartStore.get(context).prepare(128L*1024*1024);check(parts.size==2)
                main {
                    VrmAvatarActivity::class.java.getDeclaredField("parts").apply {isAccessible=true}.set(activity,parts.filter {it.styleId=="e-original"})
                    VrmAvatarActivity::class.java.getDeclaredField("generateThumbnails").apply {isAccessible=true}.setBoolean(activity,false)
                    VrmAvatarActivity::class.java.getDeclaredMethod("renderTools").apply {isAccessible=true}.invoke(activity)
                    check(button("avatar-hair-e-hair02")==null)
                }
            } else main {check(button("avatar-save-new")==null){"Initial preparation was not blocked"}}
            context.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            waitFor("Initial editor did not stop",15_000){var stopped=false;main {stopped=!active.getBoolean(activity)};stopped}
            context.startActivity(Intent(context,VrmAvatarActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
            waitFor("Initial editor did not resume",15_000){var resumed=false;main {resumed=active.getBoolean(activity)};resumed}
            first.countDown();second.countDown()
            if(mode=="gallery")waitFor("New hair card was not refreshed on resume"){var shown=false;main {shown=button("avatar-hair-e-hair02")!=null};shown}
            else waitFor("Initial preparation stalled after resume"){var shown=false;main {shown=button("avatar-save-new")!=null};shown}
            waitFor("Resumed editor never rendered",600_000,::ready)
        } finally {
            first.countDown();second.countDown();main {activity?.finish()};test.waitForIdleSync();field.set(null,previous)
        }
    }
}
