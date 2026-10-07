package com.yj.magiccircle

import android.graphics.*
import kotlin.math.*

/** Hand-authored, resolution independent artwork. Paths and paints are prepared, never built in draw(). */
internal class CharacterPart {
    private data class Mark(val path: Path,val paint: Paint)
    private val marks=ArrayList<Mark>()
    fun path(color: Int, stroke: Float=0f, shader: Shader?=null, block: Path.()->Unit) {
        val p=Path().apply(block)
        val ink=Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color=color; style=if(stroke==0f) Paint.Style.FILL else Paint.Style.STROKE
            strokeWidth=stroke; strokeCap=Paint.Cap.ROUND; strokeJoin=Paint.Join.ROUND; this.shader=shader
        }
        marks.add(Mark(p,ink))
    }
    fun oval(x: Float,y: Float,rx: Float,ry: Float,color: Int,stroke: Float=0f) = path(color,stroke) {
        addOval(x-rx,y-ry,x+rx,y+ry,Path.Direction.CW)
    }
    fun draw(canvas: Canvas) { for(i in marks.indices) { val m=marks[i]; canvas.drawPath(m.path,m.paint) } }
}

internal class CharacterParts(private val v: CharacterDefinition) {
    private val outline=0xff382537.toInt()
    private val skin=v.colors.getValue("skin")
    private val skinDark=tint(skin,0xffa95068.toInt(),.27f)
    private val hair=v.colors.getValue("hair")
    private val hairDark=tint(hair,0xff191d36.toInt(),.58f)
    private val hairLight=tint(hair,0xffffbed2.toInt(),.28f)
    private val cloth=v.colors.getValue("torso.base")
    private val accent=v.colors.getValue("torso.accent")
    private val white=0xfff6eee7.toInt()
    private fun gradient(a: Int,b: Int,y: Float=32f) = LinearGradient(-12f,0f,14f,y,a,b,Shader.TileMode.CLAMP)
    private fun CharacterPart.shape(color: Int, shader: Shader?=null, block: Path.()->Unit) {
        path(color,shader=shader,block=block); path(tint(color,outline,.58f),.22f,block=block)
    }
    private fun CharacterPart.line(color: Int,width: Float=.17f,block: Path.()->Unit) = path(color,width,block=block)
    val backHair=CharacterPart().apply {
        if(v.appearance.hair=="tied") {
            for(side in listOf(-1f,1f)) shape(hair,gradient(hairLight,hairDark,64f)) {
                moveTo(side*12f,10f); cubicTo(side*29f,3f,side*29f,31f,side*24f,41f)
                cubicTo(side*19f,49f,side*28f,57f,side*21f,64f)
                cubicTo(side*23f,49f,side*13f,49f,side*16f,32f); close()
            }
        }
        val bottom=if(v.appearance.hair=="long") 68f else 33f
        shape(hair,gradient(hairLight,hairDark,bottom)) {
            moveTo(-14f,26f); cubicTo(-21f,16f,-19f,-5f,-2f,-3f)
            cubicTo(13f,-6f,20f,4f,18f,20f)
            cubicTo(17f,28f,19f,bottom-7f,17.8f,bottom-2f)
            quadTo(14f,bottom+1f,13f,bottom-1f); quadTo(9f,bottom+2f,7f,bottom-.5f)
            quadTo(3f,bottom+1f,1f,bottom-2f); quadTo(-4f,bottom+2f,-8f,bottom-.8f)
            quadTo(-12f,bottom+1f,-13.5f,bottom-1f); quadTo(-20f,bottom+1f,-18f,bottom-6f)
            cubicTo(-17f,bottom-8f,-18f,30f,-14f,26f); close()
        }
        for(side in listOf(-1f,1f)) for(i in 0..6) {
            val x=side*(6f+i*1.6f)
            line(if(i%3==0) hairLight else hairDark,.16f) {
                moveTo(x,5f); cubicTo(x+side*4f,20f,x-side*3f,bottom-8f,x+side*3f,bottom-2f)
            }
        }
    }
    val face=CharacterPart().apply {
        oval(-13.1f,20f,2f,3f,skinDark); oval(13.1f,20f,2f,3f,skinDark)
        val cheek=when(v.appearance.face) { "oval"->12f; "soft-square"->14.4f; else->13.7f }
        shape(skin,gradient(tint(skin,white,.12f),skin)) {
            moveTo(-12f,9f); cubicTo(-17f,16f,-cheek,25f,-9f,28f)
            cubicTo(-5f,31f,-2f,32f,0f,32f); cubicTo(3f,32f,7f,29f,10f,27f)
            cubicTo(cheek+1f,23f,16f,13f,11f,7f); cubicTo(5f,2f,-8f,3f,-12f,9f); close()
        }
        for(side in listOf(-1f,1f)) {
            val blush=tint(skin,0xffe97391.toInt(),.28f)
            path(blush,shader=RadialGradient(side*8.8f,23.5f,3.7f,intArrayOf(blush,skin and 0xffffff),null,Shader.TileMode.CLAMP)) {
                addOval(side*8.8f-3.8f,21.4f,side*8.8f+3.8f,25.5f,Path.Direction.CW)
            }
            for(i in 0..2) line(tint(skin,0xffd47083.toInt(),.35f),.12f) {
                moveTo(side*(9f+i*.65f),23f); lineTo(side*(8.7f+i*.65f),24f)
            }
        }
        when(v.appearance.nose) {
            "small"->line(skinDark,.24f) { moveTo(.7f,22.8f); quadTo(1.2f,23.7f,.2f,23.9f) }
            "soft"-> { oval(.1f,23.3f,1.1f,.5f,skinDark); oval(-.3f,22.9f,.7f,.25f,white) }
            else->line(skinDark,.24f) { moveTo(.4f,20.9f); lineTo(1.2f,23.7f); quadTo(.1f,24.4f,-.9f,23.8f) }
        }
        when(v.appearance.mouth) {
            "smile"->shape(0xffa95264.toInt()) { moveTo(-2.6f,26.2f); quadTo(0f,27.3f,2.6f,26.2f); quadTo(0f,30f,-2.6f,26.2f); close() }
            "straight"->line(0xffba7279.toInt(),.28f) { moveTo(-1.9f,27f); quadTo(0f,26.8f,1.9f,27f) }
            else-> { line(0xffb96778.toInt(),.27f) { moveTo(-2.1f,26.9f); quadTo(0f,28f,2.1f,26.8f) }; line(white,.18f) { moveTo(-.9f,28.1f); lineTo(.8f,28.1f) } }
        }
    }
    val eyes=CharacterPart().apply {
        for(side in listOf(-1f,1f)) {
            val x=side*6.8f
            val ry=when(v.appearance.eyes) { "almond"->2.65f; "soft"->3.15f; else->3.9f }
            shape(white) { moveTo(x-4f,18.8f); cubicTo(x-3.4f,15f,x+3.3f,15.3f,x+4f,18.5f); quadTo(x+3f,20f+ry,x,20f+ry); quadTo(x-3.6f,20f+ry,x-4f,18.8f); close() }
            val iris=v.colors.getValue("eyes")
            path(iris,shader=LinearGradient(x,16.3f,x,23f,hairDark,iris,Shader.TileMode.CLAMP)) { addOval(x-2.65f,16.5f,x+2.65f,20f+ry,Path.Direction.CW) }
            oval(x,19.2f,1.18f,ry*.8f,0xff332239.toInt())
            line(tint(iris,white,.55f),.6f) { moveTo(x-1.8f,20f+ry*.6f); quadTo(x,22f+ry*.5f,x+1.8f,20f+ry*.6f) }
            oval(x-1.2f,17.5f,1.05f,1.15f,white); oval(x+1.2f,21f,.45f,.6f,white)
            line(outline,.58f) { moveTo(x-4.2f,18.7f); cubicTo(x-3.5f,15.6f,x+2.8f,15.6f,x+4.1f,18.5f) }
            val edge=x+side*4f
            line(outline,.4f) { moveTo(edge,18.1f); lineTo(edge+side*1.4f,16.4f); moveTo(edge-side*.7f,17.3f); lineTo(edge+side*.3f,15.8f) }
            line(tint(skinDark,outline,.2f),.18f) { moveTo(x-2.7f,21.3f+ry*.5f); quadTo(x,22f+ry*.5f,x+2.6f,21.3f+ry*.5f) }
        }
    }
    val closedEyes=CharacterPart().apply {
        for(side in listOf(-1f,1f)) line(outline,.48f) { moveTo(side*3f,20.4f); quadTo(side*7f,22.9f,side*10.8f,20f); lineTo(side*12f,19.4f) }
    }
    val brows=CharacterPart().apply {
        for(side in listOf(-1f,1f)) line(hairDark,.5f) {
            moveTo(side*3.5f,14.5f)
            val arch=when(v.appearance.brows) { "arched"->11.3f; "straight"->14f; else->12.8f }
            quadTo(side*6.8f,arch,side*10.1f,14f)
        }
    }
    val frontHair=CharacterPart().apply {
        // Distinct curved locks, not a single helmet-shaped fill.
        for(side in listOf(-1f,1f)) {
            shape(hair,gradient(hairLight,hairDark)) {
                moveTo(side*.2f,-2f); cubicTo(side*11f,-4f,side*18f,1f,side*16f,10f)
                cubicTo(side*17f,18f,side*14f,27f,side*17f,30f)
                cubicTo(side*10f,29f,side*10f,21f,side*12f,15f)
                cubicTo(side*8f,14f,side*3f,8f,side*.2f,-2f); close()
            }
            shape(hair,gradient(hairLight,hairDark,16f)) {
                moveTo(side*.2f,-2f); cubicTo(side*9f,0f,side*8f,9f,side*11.5f,13f)
                cubicTo(side*7f,13.5f,side*4f,9f,side*3f,6f)
                cubicTo(side*4f,10f,side*3.1f,12f,side*1.9f,13f)
                cubicTo(side*2.3f,8f,side*-1f,7f,side*.2f,-2f); close()
            }
            for(i in 0..5) {
                val offset=i*.78f
                line(if(i%2==0) hairLight else hairDark,.13f) {
                    moveTo(side*(2f+offset),.2f); cubicTo(side*(6f+offset),3f,side*(5.6f+offset),8f,side*(8f+offset),11.2f)
                }
            }
            for(i in 0..3) line(hairLight,.16f) { moveTo(side*(13.3f+i*.65f),8f); cubicTo(side*(14f+i*.65f),16f,side*(11.1f+i*.65f),23f,side*(14f+i*.65f),28f) }
            path(hairLight) { moveTo(side*4f,3.7f); quadTo(side*9f,4.3f,side*12.7f,7.2f); lineTo(side*12.2f,8f); quadTo(side*8f,5.4f,side*4.5f,4.4f); close() }
        }
        if(v.appearance.hair=="tied") for(side in listOf(-1f,1f)) {
            oval(side*16f,10f,2.6f,1.1f,accent); line(outline,.25f) { moveTo(side*14f,10f); lineTo(side*18f,10f) }
        }
    }
    val headgear=CharacterPart().apply {
        if(v.outfit[OutfitSlot.HEAD]=="ribbon") {
            val color=v.colors.getValue("head.base")
            shape(color) { moveTo(8f,4f); cubicTo(8f,-4f,19f,-4f,17f,5f); lineTo(11f,7f); cubicTo(19f,7f,20f,14f,13f,11f); close() }
            oval(11f,6f,1.8f,2.1f,v.colors.getValue("head.accent"))
        } else if(v.outfit[OutfitSlot.HEAD]=="star") star(this,12f,8f,3.3f,v.colors.getValue("head.base"))
    }
    val halo=CharacterPart().apply {
        if(v.outfit[OutfitSlot.HALO]!="none") {
            oval(0f,-10f,13f,3.2f,v.colors.getValue("halo.base"),.7f)
            oval(0f,-10f,11.7f,2.5f,v.colors.getValue("halo.accent"),.18f)
            if(v.outfit[OutfitSlot.HALO]=="star") for(i in 0..4) star(this,(i-2)*5f,-10f,1.7f,accent)
        }
    }
    val neck=CharacterPart().apply {
        shape(skin,gradient(skinDark,skin,10f)) { moveTo(-4.2f,-3f); lineTo(-4f,5f); quadTo(-7f,6f,-9f,8f); quadTo(0f,15f,9f,8f); quadTo(4f,6f,4f,5f); lineTo(4.2f,-3f); close() }
        line(outline,.9f) { moveTo(-4f,4.2f); quadTo(0f,6f,4f,4.2f) }
        star(this,0f,6.2f,1.4f,accent)
    }
    val torso=CharacterPart().apply {
        val type=v.outfit[OutfitSlot.TORSO]
        val bottom=if(type=="robe") 67f else 47f
        shape(cloth,gradient(tint(cloth,white,.16f),tint(cloth,outline,.24f),bottom)) {
            moveTo(-9f,-2f); lineTo(-18f,1f); quadTo(-17f,12f,-14f,20f)
            quadTo(-12f,25f,-14f,30f); lineTo(-20f,bottom-2f)
            quadTo(0f,bottom+3f,20f,bottom-2f); lineTo(14f,30f)
            quadTo(12f,25f,14f,20f); lineTo(18f,1f); lineTo(9f,-2f); quadTo(0f,8f,-9f,-2f); close()
        }
        shape(white) { moveTo(-9f,-2f); lineTo(-13f,-.5f); lineTo(-6f,10f); lineTo(0f,6f); lineTo(6f,10f); lineTo(13f,-.5f); lineTo(9f,-2f); quadTo(0f,5f,-9f,-2f); close() }
        shape(tint(cloth,outline,.7f)) { moveTo(-5.3f,6f); lineTo(0f,17f); lineTo(5.3f,6f); quadTo(0f,9f,-5.3f,6f); close() }
        for(i in 0..3) {
            val y=8f+i*1.9f; val x=3.4f-i*.65f
            line(accent,.38f) { moveTo(-x,y); lineTo(x-.3f,y+1.7f); moveTo(x,y); lineTo(-x+.3f,y+1.7f) }
            oval(-x,y,.35f,.35f,white); oval(x,y,.35f,.35f,white)
        }
        if(type=="coat") {
            for(side in listOf(-1f,1f)) shape(accent) { moveTo(side*10f,0f); lineTo(side*5f,18f); lineTo(side*11f,11f); lineTo(side*9f,8f); lineTo(side*14f,5f); close() }
            line(accent,.65f) { moveTo(0f,18f); lineTo(0f,bottom-1f) }
            for(i in 0..4) oval(2f,22f+i*4.2f,.65f,.65f,accent)
        }
        for(side in listOf(-1f,1f)) for(i in 0..2) line(tint(cloth,outline,.28f),.18f) {
            moveTo(side*(9f+i*2f),30f); quadTo(side*(11f+i*2f),37f,side*(12f+i*2.5f),bottom-4f)
        }
        path(accent) { moveTo(-19f,bottom-5f); quadTo(0f,bottom-1f,19f,bottom-5f); lineTo(20f,bottom-2f); quadTo(0f,bottom+2f,-20f,bottom-2f); close() }
        for(i in -4..4) {
            val x=i*3.9f
            line(tint(accent,outline,.45f),.22f) { moveTo(x-2f,bottom-3f); quadTo(x,bottom-6f,x+2f,bottom-3f); quadTo(x,bottom,x-2f,bottom-3f) }
        }
        shape(outline) { moveTo(-13f,24f); quadTo(0f,23f,13f,24f); lineTo(13f,26f); quadTo(0f,25f,-13f,26f); close() }
        oval(-9f,25f,2.1f,1.5f,accent,.45f)
        line(outline,.9f) { moveTo(-9f,25f); cubicTo(-16f,24f,-14f,35f,-10f,30f); cubicTo(-5f,23f,-8f,23f,-9f,25f); lineTo(-12f,41f); moveTo(-9f,25f); quadTo(-6f,29f,-7f,37f) }
        line(accent,.18f) { moveTo(-12.3f,32f); lineTo(-13.2f,40f) }
    }
    val upperArm=CharacterPart().apply {
        shape(white,gradient(white,0xffbfc6d3.toInt(),26f)) { moveTo(-5f,0f); quadTo(-7f,13f,-4.2f,27f); quadTo(0f,30f,4.2f,27f); quadTo(6f,12f,5f,0f); close() }
        shape(cloth,gradient(cloth,tint(cloth,outline,.2f),14f)) { moveTo(-5.8f,-1f); quadTo(-8f,5f,-7.2f,15f); quadTo(0f,17f,7.2f,15f); quadTo(8f,5f,5.8f,-1f); close() }
        line(accent,.45f) { moveTo(-6.9f,13.8f); quadTo(0f,15.6f,6.9f,13.8f) }
        line(tint(cloth,outline,.27f),.2f) { moveTo(-2f,2f); quadTo(-4f,7f,-4f,12f) }
    }
    val lowerArm=CharacterPart().apply {
        val glove=v.outfit[OutfitSlot.GLOVES]
        path(white,shader=gradient(white,0xffc0c4d0.toInt(),26f)) { moveTo(-4.3f,-2f); quadTo(-5f,13f,-3.4f,24f); quadTo(0f,26f,3.4f,24f); quadTo(5f,13f,4.3f,-2f); close() }
        line(0xff9c939f.toInt(),.22f) { moveTo(-4.3f,-1f); quadTo(-5f,13f,-3.4f,24f); moveTo(4.3f,-1f); quadTo(5f,13f,3.4f,24f) }
        for(i in 0..2) line(0xffb9b4bf.toInt(),.2f) { moveTo(-3.5f,16f+i*2f); quadTo(0f,17f+i*2f,3.5f,15f+i*2f) }
        if(glove=="long") shape(v.colors.getValue("gloves.base")) { moveTo(-4.5f,4f); lineTo(-3.6f,25f); lineTo(3.6f,25f); lineTo(4.5f,4f); quadTo(0f,6f,-4.5f,4f); close() }
        shape(if(glove=="none") accent else v.colors.getValue("gloves.accent")) { moveTo(-4f,21f); lineTo(-4f,24.2f); lineTo(4f,24.2f); lineTo(4f,21f); close() }
        line(white,.35f) { moveTo(-3.7f,21.4f); lineTo(3.7f,23.5f); moveTo(3.7f,21.4f); lineTo(-3.7f,23.5f) }
    }
    val hand=CharacterPart().apply {
        val color=if(v.outfit[OutfitSlot.GLOVES]=="none") skin else v.colors.getValue("gloves.base")
        shape(color,gradient(color,tint(color,outline,.15f),10f)) {
            moveTo(-2.8f,-1f); lineTo(-3.1f,4f); quadTo(-5.5f,5f,-4.1f,7.5f)
            lineTo(-2.6f,5.6f); lineTo(-1.8f,10.3f); quadTo(-1f,11f,-.7f,9.5f)
            quadTo(1f,11.7f,1.5f,9.7f); quadTo(3.5f,11f,3.5f,8f); lineTo(3f,0f); close()
        }
        line(tint(color,outline,.28f),.15f) { moveTo(-.7f,5.3f); lineTo(-.3f,8.8f); moveTo(1.1f,5.3f); lineTo(1.5f,9f) }
    }
    val upperLeg=CharacterPart().apply {
        shape(skin,gradient(skin,tint(skin,skinDark,.45f),36f)) { moveTo(-7f,-4f); quadTo(-7.7f,12f,-4.8f,35f); quadTo(0f,38f,4.8f,35f); quadTo(7.7f,12f,7f,-4f); close() }
        path(0xff392a40.toInt()) { moveTo(-7.3f,-4f); lineTo(-7.3f,5f); quadTo(0f,7f,7.3f,5f); lineTo(7.3f,-4f); close() }
        line(0xff7f6c81.toInt(),.23f) { moveTo(-7f,4.3f); quadTo(0f,6.3f,7f,4.3f) }
    }
    val lowerLeg=CharacterPart().apply {
        path(skin,shader=gradient(skin,skinDark,30f)) { moveTo(-4.8f,-2f); quadTo(-6f,10f,-3.3f,31f); lineTo(3.3f,31f); quadTo(6f,10f,4.8f,-2f); close() }
        line(tint(skin,outline,.58f),.22f) { moveTo(-4.8f,-1f); quadTo(-6f,10f,-3.3f,31f); moveTo(4.8f,-1f); quadTo(6f,10f,3.3f,31f) }
        line(tint(skin,white,.4f),.3f) { moveTo(-1.2f,4f); quadTo(-2.7f,14f,-1.5f,24f) }
    }
    val shoe=CharacterPart().apply {
        val boots=v.outfit[OutfitSlot.SHOES]=="boots"
        val top=if(boots) -15f else 0f
        val color=v.colors.getValue("shoes.base"); val trim=v.colors.getValue("shoes.accent")
        shape(color,gradient(tint(color,white,.15f),tint(color,outline,.3f),9f)) {
            moveTo(-4.1f,top); lineTo(4.1f,top); lineTo(4.3f,3f); cubicTo(5.5f,6f,7.2f,7f,6f,8f)
            quadTo(0f,9.3f,-5.6f,8f); lineTo(-5.6f,5f); lineTo(-3.5f,2f); close()
        }
        line(outline,.8f) { moveTo(-5.5f,8f); quadTo(0f,9.1f,6f,8f) }
        line(trim,.7f) { moveTo(-4.1f,top+.8f); quadTo(0f,top+2f,4.1f,top+.8f) }
        if(boots) { for(i in 0..3) line(trim,.25f) { moveTo(-1.6f,-12f+i*3f); lineTo(1.6f,-10f+i*3f); moveTo(1.6f,-12f+i*3f); lineTo(-1.6f,-10f+i*3f) }; oval(0f,-14f,1f,1f,trim) }
        else line(trim,.6f) { moveTo(-3.5f,2f); quadTo(0f,4f,3.5f,2f) }
    }
    val wings=CharacterPart().apply {
        val type=v.outfit[OutfitSlot.WINGS]; val base=v.colors.getValue("wings.base"); val trim=v.colors.getValue("wings.accent")
        if(type=="feather") for(side in listOf(-1f,1f)) {
            shape(base,gradient(base,tint(base,0xff536797.toInt(),.3f),55f)) {
                moveTo(side*10f,10f); cubicTo(side*33f,-5f,side*38f,-21f,side*47f,-29f)
                cubicTo(side*48f,4f,side*33f,44f,side*10f,45f); close()
            }
            for(i in 0..13) {
                val x=side*(14f+i*2.1f); val y=9f-i*2f
                shape(tint(base,white,(i%3)*.09f)) { moveTo(x,y); cubicTo(x+side*9f,y-9f,x+side*10f,y-12f,x+side*10f,y-19f); cubicTo(x+side*13f,y+3f,x+side*3f,y+16f,x,y+17f); quadTo(x+side*3f,y+6f,x,y); close() }
                line(tint(base,0xff607391.toInt(),.35f),.14f) { moveTo(x+side*2f,y+9f); quadTo(x+side*6f,y,x+side*8f,y-8f) }
            }
        } else if(type=="fairy") for(side in listOf(-1f,1f)) {
            for(i in 0..1) {
                val y=if(i==0) -28f else 38f
                shape(tint(base,0xffb2b5ed.toInt(),.4f)) { moveTo(side*9f,14f); cubicTo(side*31f,y,side*65f,y-17f,side*44f,y+21f); quadTo(side*31f,29f,side*9f,14f); close() }
                for(k in 0..4) line(trim,.2f) { moveTo(side*10f,14f); quadTo(side*(30f+k*2f),y+10f,side*(39f+k),y+3f+k*2f) }
            }
        }
    }
    companion object {
        fun tint(a: Int,b: Int,t: Float): Int {
            fun channel(s: Int) = (((a ushr s) and 255)*(1f-t)+((b ushr s) and 255)*t).roundToInt().coerceIn(0,255)
            return (255 shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
        }
        private fun star(part: CharacterPart,x: Float,y: Float,r: Float,color: Int) {
            part.path(color) { for(i in 0..9) { val a=-Math.PI/2+i*Math.PI/5; val rr=if(i%2==0) r else r*.43f; val px=x+cos(a).toFloat()*rr; val py=y+sin(a).toFloat()*rr; if(i==0) moveTo(px,py) else lineTo(px,py) }; close() }
        }
    }
}
