package com.yj.magiccircle

import android.graphics.Canvas
import kotlin.math.*

class CharacterRenderer(definition: CharacterDefinition) {
    private var definition=definition.also { CharacterRules.validate(it) }
    private var parts=CharacterParts(definition)
    private var frame=CharacterGeometry.measure(definition.body)
    private var width=1
    private var height=1
    private var closed=false
    val animated get() = !closed && definition.motion!=CharacterMotion.STILL
    fun update(definition: CharacterDefinition) {
        check(!closed); CharacterRules.validate(definition)
        if(this.definition==definition) return
        if(this.definition.appearance!=definition.appearance || this.definition.outfit!=definition.outfit || this.definition.colors!=definition.colors || this.definition.body.shoulders!=definition.body.shoulders || this.definition.body.torsoWidth!=definition.body.torsoWidth) parts=CharacterParts(definition)
        if(this.definition.body!=definition.body) frame=CharacterGeometry.measure(definition.body)
        this.definition=definition
    }
    fun prepare(width: Int,height: Int) { require(width>0 && height>0); this.width=width; this.height=height }
    fun draw(canvas: Canvas,elapsedMs: Long,animated: Boolean) {
        if(closed) return
        val t=if(animated && this.animated) elapsedMs.coerceAtLeast(0).toDouble()/1000.0 else 0.0
        val breath=(sin(t*1.65)*.24).toFloat()
        val wave=if(definition.motion==CharacterMotion.WAVE) ((1-cos(t*2.2))*.5).toFloat() else 0f
        val s=min(width/160f,height/220f)
        canvas.save(); canvas.translate(width/2f,(height-220f*s)/2f+10f*s); canvas.scale(s,s)
        val f=frame; val b=definition.body
        // Feet stay at y=200 when height changes; camera scale is separate.
        for(side in -1..1 step 2) {
            val knee=if(side<0) f.leftKnee else f.rightKnee; val ankle=if(side<0) f.leftAnkle else f.rightAnkle
            at(canvas,knee.x,f.pelvis.y,b.legWidth*f.scale,(knee.y-f.pelvis.y)/36f,parts.upperLeg)
            at(canvas,knee.x,knee.y,b.legWidth*f.scale,(ankle.y-knee.y)/31f,parts.lowerLeg)
            at(canvas,ankle.x,ankle.y,b.legWidth*f.scale,f.scale,parts.shoe)
        }
        canvas.save(); canvas.translate(0f,breath)
        at(canvas,0f,f.leftShoulder.y+8f*f.scale,f.scale,f.scale,parts.wings)
        at(canvas,0f,f.crownY,f.headHeight/32f,f.headHeight/32f,parts.backHair)
        for(side in -1..1 step 2) arm(canvas,side,if(side>0) wave else 0f)
        val bodyWidth=b.torsoWidth*f.scale*(if(definition.appearance.bodyType=="masculine") 1.13f else 1f)
        at(canvas,0f,f.leftShoulder.y,bodyWidth,(f.pelvis.y-f.leftShoulder.y)/48f,parts.torso)
        at(canvas,0f,f.neck.y,f.scale,f.scale,parts.neck)
        canvas.save(); canvas.translate(0f,f.crownY); canvas.scale(f.headHeight/32f,f.headHeight/32f)
        parts.face.draw(canvas); parts.brows.draw(canvas)
        if(t>0 && (t%4.3) in 3.9..4.05) parts.closedEyes.draw(canvas) else parts.eyes.draw(canvas)
        parts.frontHair.draw(canvas); parts.headgear.draw(canvas); parts.halo.draw(canvas)
        canvas.restore(); canvas.restore(); canvas.restore()
    }
    private fun at(c: Canvas,x: Float,y: Float,sx: Float,sy: Float,p: CharacterPart) {
        c.save(); c.translate(x,y); c.scale(sx,sy); p.draw(c); c.restore()
    }
    private fun arm(c: Canvas,side: Int,wave: Float) {
        val f=frame; val b=definition.body
        val shoulder=if(side<0) f.leftShoulder else f.rightShoulder
        val elbow=if(side<0) f.leftElbow else f.rightElbow
        val wrist=if(side<0) f.leftWrist else f.rightWrist
        val upperLength=hypot(elbow.x-shoulder.x,elbow.y-shoulder.y)
        val lowerLength=hypot(wrist.x-elbow.x,wrist.y-elbow.y)
        val angle=-Math.toDegrees(atan2((elbow.x-shoulder.x).toDouble(),(elbow.y-shoulder.y).toDouble())).toFloat()
        c.save(); c.translate(shoulder.x,shoulder.y); c.rotate(angle-wave*65f)
        at(c,0f,0f,b.armWidth*f.scale,upperLength/26f,parts.upperArm)
        c.translate(0f,upperLength); c.rotate(-wave*100f)
        at(c,0f,0f,b.armWidth*f.scale,lowerLength/24f,parts.lowerArm)
        at(c,0f,lowerLength,b.armWidth*f.scale,f.scale,parts.hand)
        c.restore()
    }
    fun close() { closed=true }
}
