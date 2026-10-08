package com.yj.magiccircle

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import java.util.Locale
import java.util.UUID

object CharacterColorChecks {
    fun run(context: Context) {
        val original=CharacterRules.defaults(UUID.randomUUID().toString(),"Color check")
        val failures=ArrayList<String>()
        fun frame(value: CharacterDefinition,slot: OutfitSlot?,full: Boolean=false): Bitmap {
            val image=Bitmap.createBitmap(640,880,Bitmap.Config.ARGB_8888)
            val canvas=Canvas(image)
            if(full) {
                val renderer=CharacterRenderer(value)
                renderer.prepare(image.width,image.height);renderer.draw(canvas,0,false);renderer.close()
            } else {
                val parts=CharacterParts(value)
                canvas.translate(320f,260f);canvas.scale(4f,4f)
                when(slot) {
                    OutfitSlot.HALO->parts.halo.draw(canvas)
                    OutfitSlot.HEAD->parts.headgear.draw(canvas)
                    OutfitSlot.TORSO->parts.torso.draw(canvas)
                    OutfitSlot.GLOVES->{parts.lowerArm.draw(canvas);canvas.translate(0f,26f);parts.hand.draw(canvas)}
                    OutfitSlot.SHOES->parts.shoe.draw(canvas)
                    OutfitSlot.WINGS->parts.wings.draw(canvas)
                    null->parts.frontHair.draw(canvas)
                }
            }
            return image
        }
        CharacterRules.outfitIds.forEach { (slot,variants)->variants.filter {it!="none"}.forEach { variant->
            val value=original.copy(outfit=original.outfit+(slot to variant))
            val prefix=slot.name.lowercase(Locale.ROOT)+"."
            val before=frame(value,slot);val visible=frame(value,slot,true)
            for(channel in listOf("base","accent")) {
                val key=prefix+channel
                val changed=value.copy(colors=value.colors+(key to 0xff16e92b.toInt()))
                val part=frame(changed,slot);val rendered=frame(changed,slot,true)
                if(before.sameAs(part) || visible.sameAs(rendered)) failures.add("$slot/$variant ignores $key")
                part.recycle();rendered.recycle()
            }
            val unrelated=value.copy(colors=value.colors.mapValues { (key,color)->
                if(key.contains('.') && !key.startsWith(prefix)) 0xffe51de9.toInt() else color
            })
            val other=frame(unrelated,slot)
            if(!before.sameAs(other)) failures.add("$slot/$variant uses another outfit slot's color")
            before.recycle();visible.recycle();other.recycle()
        }}
        val tied=original.copy(appearance=original.appearance.copy(hair="tied"))
        val hair=frame(tied,null)
        val recolored=frame(tied.copy(colors=tied.colors+("torso.accent" to 0xff16e92b.toInt())),null)
        if(!hair.sameAs(recolored)) failures.add("Tied hair uses torso.accent")
        hair.recycle();recolored.recycle()
        val eyes=listOf(original,original.copy(colors=original.colors+("hair" to 0xff16e92b.toInt()))).map { value->
            Bitmap.createBitmap(320,320,Bitmap.Config.ARGB_8888).also { image->
                val canvas=Canvas(image);canvas.translate(160f,20f);canvas.scale(8f,8f)
                CharacterParts(value).eyes.draw(canvas)
            }
        }
        if(!eyes[0].sameAs(eyes[1])) failures.add("Iris uses hair color")
        eyes.forEach {it.recycle()}
        check(failures.isEmpty()) {failures.joinToString("; ")}
    }
}
