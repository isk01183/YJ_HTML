package com.yj.magiccircle

import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.min

class CharacterPreviewView(context: Context): View(context) {
    private var renderer: CharacterRenderer?=null
    private var started=SystemClock.uptimeMillis()
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private var backdrop: Shader?=null
    private var active=true
    var zoom=1f; private set
    var panX=0f; private set
    var panY=0f; private set
    private var lastX=0f; private var lastY=0f
    private val scaling=ScaleGestureDetector(context,object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean { zoom=(zoom*detector.scaleFactor).coerceIn(.65f,4f); invalidate(); return true }
    })
    init { contentDescription="Character preview"; isFocusable=true }
    fun setCharacter(value: CharacterDefinition) {
        if(renderer==null) renderer=CharacterRenderer(value) else renderer!!.update(value)
        if(width>0 && height>0) renderer!!.prepare(width,height)
        invalidate()
    }
    fun fitToView()=resetView()
    fun resetView() { zoom=1f; panX=0f; panY=0f; invalidate() }
    fun restoreView(z: Float,x: Float,y: Float) { zoom=if(z.isFinite())z.coerceIn(.65f,4f)else 1f; panX=if(x.isFinite())x else 0f; panY=if(y.isFinite())y else 0f; invalidate() }
    fun faceView() { zoom=2.6f; panX=0f; panY=height*.34f; invalidate() }
    fun setActive(value: Boolean) { active=value; if(value)invalidate() }
    override fun onSizeChanged(w: Int,h: Int,oldw: Int,oldh: Int) {
        super.onSizeChanged(w,h,oldw,oldh)
        if(w>0 && h>0) { renderer?.prepare(w,h); backdrop=RadialGradient(w*.5f,h*.43f,h*.9f,intArrayOf(0xff777b9f.toInt(),0xff373c5a.toInt(),0xff202840.toInt()),null,Shader.TileMode.CLAMP) }
    }
    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        paint.shader=backdrop; paint.style=Paint.Style.FILL; c.drawRect(0f,0f,width.toFloat(),height.toFloat(),paint); paint.shader=null
        val scale=min(width/160f,height/220f); val top=(height-220f*scale)/2f+10f*scale
        paint.color=0x387fe3ff; paint.strokeWidth=1f
        for(i in 0..27) { val x=((i*83+17)%101)/101f*width; val y=((i*47+5)%97)/97f*height; c.drawCircle(x,y,if(i%4==0)2f else 1f,paint) }
        paint.color=0x408ddaef; paint.style=Paint.Style.STROKE
        c.drawOval(width*.18f,top+196f*scale,width*.82f,top+207f*scale,paint)
        c.drawOval(width*.22f,top+198f*scale,width*.78f,top+205f*scale,paint)
        paint.style=Paint.Style.FILL; paint.color=0xffd8d8e4.toInt(); paint.textSize=11f*resources.displayMetrics.scaledDensity
        if(zoom==1f && panY==0f) for(cm in 0..200 step 40) {
            val y=top+(200-cm)*scale
            c.drawLine(12f,y,24f,y,paint); c.drawText(cm.toString(),28f,y+4f,paint)
        }
        c.save(); c.translate(panX,panY); c.scale(zoom,zoom,width/2f,height/2f)
        renderer?.draw(c,SystemClock.uptimeMillis()-started,active); c.restore()
        if(active && windowVisibility==VISIBLE && isShown && renderer?.animated==true) postInvalidateDelayed(34)
    }
    override fun onWindowVisibilityChanged(visibility: Int) { super.onWindowVisibilityChanged(visibility); if(visibility==VISIBLE) invalidate() }
    override fun onTouchEvent(e: MotionEvent): Boolean {
        scaling.onTouchEvent(e)
        when(e.actionMasked) {
            MotionEvent.ACTION_DOWN->{lastX=e.x;lastY=e.y;parent?.requestDisallowInterceptTouchEvent(true)}
            MotionEvent.ACTION_MOVE->{if(e.pointerCount==1 && !scaling.isInProgress) {panX=(panX+e.x-lastX).coerceIn(-width.toFloat(),width.toFloat());panY=(panY+e.y-lastY).coerceIn(-height.toFloat(),height.toFloat());invalidate()};lastX=e.x;lastY=e.y}
            MotionEvent.ACTION_POINTER_UP->{val index=if(e.actionIndex==0)1 else 0;lastX=e.getX(index);lastY=e.getY(index)}
            MotionEvent.ACTION_UP->{performClick();parent?.requestDisallowInterceptTouchEvent(false)}
            MotionEvent.ACTION_CANCEL->parent?.requestDisallowInterceptTouchEvent(false)
        }
        return true
    }
    override fun performClick(): Boolean {super.performClick();return true}
    fun close() { active=false; renderer?.close();renderer=null }
}
