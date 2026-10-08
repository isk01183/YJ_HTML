package com.yj.magiccircle
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import java.io.File
import java.util.UUID

object CharacterRenderingChecks {
    fun run(context: Context) {
        CharacterColorChecks.run(context)
        val base=CharacterRules.defaults(UUID.randomUUID().toString(),"별빛")
        val renderer=CharacterRenderer(base)
        renderer.prepare(720,1080)
        fun frame(t: Long, animated: Boolean) = Bitmap.createBitmap(720,1080,Bitmap.Config.ARGB_8888).also { renderer.draw(Canvas(it),t,animated) }
        val still=frame(0,false); val same=frame(5000,false); val initial=frame(0,true)
        check(still.sameAs(same) && still.sameAs(initial))
        check(still.getPixel(0,0)==0)
        val live=frame(900,true); check(!still.sameAs(live)); live.recycle()
        val reference=File(context.getExternalFilesDir(null),"character-reference.png")
        reference.outputStream().use { check(still.compress(Bitmap.CompressFormat.PNG,100,it)) }
        same.recycle(); initial.recycle()
        CharacterRules.appearanceIds.forEachIndexed { index,ids ->
            val rendered=ids.map { id ->
                val parts=CharacterRules.appearanceValues(base.appearance).toMutableList(); parts[index]=id
                renderer.update(base.copy(appearance=CharacterRules.appearance(parts)))
                frame(0,false)
            }
            check(rendered.zipWithNext().all { (a,b) -> !a.sameAs(b) }) { "Appearance $index does not change shape" }
            rendered.forEach { it.recycle() }
        }
        CharacterRules.outfitIds.forEach { (slot,ids) ->
            val rendered=ids.map { id -> renderer.update(base.copy(outfit=base.outfit+(slot to id))); frame(0,false) }
            check(rendered.zipWithNext().all { (a,b) -> !a.sameAs(b) }) { "Outfit $slot does not change shape" }
            rendered.forEach { it.recycle() }
        }
        renderer.update(base.copy(motion=CharacterMotion.WAVE))
        val wave=frame(1100,true); check(!still.sameAs(wave)); wave.recycle()
        renderer.update(base)
        val reset=frame(0,false); check(still.sameAs(reset)); reset.recycle(); still.recycle()
        val sheet=Bitmap.createBitmap(1200,900,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(sheet); canvas.drawColor(0xffebe7f1.toInt())
        renderer.prepare(400,900)
        listOf("bob","long","tied").forEachIndexed { i,hair ->
            renderer.update(base.copy(appearance=base.appearance.copy(hair=hair),outfit=base.outfit+(OutfitSlot.WINGS to listOf("none","feather","fairy")[i])))
            canvas.save(); canvas.translate(i*400f,0f); renderer.draw(canvas,0,false); canvas.restore()
        }
        File(context.getExternalFilesDir(null),"character-variants.png").outputStream().use { check(sheet.compress(Bitmap.CompressFormat.PNG,100,it)) }; sheet.recycle()
        val extremes=Bitmap.createBitmap(1200,900,Bitmap.Config.ARGB_8888)
        val ec=Canvas(extremes);ec.drawColor(0xffebe7f1.toInt())
        val bodies=listOf(BodyProportions(heightCm=120f,head=1.2f,shoulders=1.2f,torsoWidth=.7f,armWidth=.7f),BodyProportions(),BodyProportions(heightCm=200f,head=.8f,shoulders=.8f,torso=1.2f,arms=1.2f,legs=1.2f,torsoWidth=1.3f,legWidth=1.3f))
        bodies.forEachIndexed {i,body->renderer.update(base.copy(body=body,motion=CharacterMotion.WAVE));ec.save();ec.translate(i*400f,0f);renderer.draw(ec,1100,true);ec.restore()}
        File(context.getExternalFilesDir(null),"character-extremes.png").outputStream().use {check(extremes.compress(Bitmap.CompressFormat.PNG,100,it))};extremes.recycle()
        renderer.close(); renderer.close()
    }
}
