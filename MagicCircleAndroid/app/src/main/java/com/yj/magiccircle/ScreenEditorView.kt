package com.yj.magiccircle

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.FrameLayout
import java.io.Closeable

class ScreenEditorView(context: Context): FrameLayout(context),Closeable {
    private lateinit var draft: EditorDraft
    private var host: ChargingSceneView?=null
    private var listener: ((EditorDraft)->Unit)?=null
    var selectedLayer: String?=null
    var selectedField: InfoField?=null
    var onSelectionChanged: (() -> Unit)?=null
    var onError: (() -> Unit)?=null
    private var downX=0f;private var downY=0f;private var originX=0f;private var originY=0f
    private var closed=false
    private var previewScale=1f
    private var previewLeft=0f
    private var previewTop=0f
    private var surfaceWidth=1
    private var surfaceHeight=1
    private val ink=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=0xffeac985.toInt();style=Paint.Style.STROKE;strokeWidth=2f*resources.displayMetrics.density }
    private val handles: View=object:View(context) {
        override fun onDraw(c: Canvas) {
            if(!::draft.isInitialized)return
            val l=draft.scene?.layers?.find { it.id==selectedLayer }
            val p=information().find { it.field==selectedField }
            val x=l?.x ?: p?.x ?: return;val y=l?.y ?: p?.y ?: return
            c.drawCircle(x*width,y*height,12f*resources.displayMetrics.density,ink)
        }
    }
    private val pinch=ScaleGestureDetector(context,object:ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(d: ScaleGestureDetector): Boolean {
            modifyLayer { it.copy(width=(it.width*d.scaleFactor).coerceIn(.05f,4f)) };return true
        }
    })
    fun setOnDraftChanged(listener: (EditorDraft)->Unit) {this.listener=listener}
    override fun onMeasure(w: Int,h: Int) {
        setMeasuredDimension(MeasureSpec.getSize(w),MeasureSpec.getSize(h))
        surfaceWidth=resources.displayMetrics.widthPixels
        surfaceHeight=resources.displayMetrics.heightPixels
        for(i in 0 until childCount)getChildAt(i).measure(MeasureSpec.makeMeasureSpec(surfaceWidth,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(surfaceHeight,MeasureSpec.EXACTLY))
    }
    override fun onLayout(changed: Boolean,l: Int,t: Int,r: Int,b: Int) {
        previewScale=minOf(width.toFloat()/surfaceWidth,height.toFloat()/surfaceHeight)
        previewLeft=(width-surfaceWidth*previewScale)/2;previewTop=(height-surfaceHeight*previewScale)/2
        for(i in 0 until childCount)getChildAt(i).apply {
            layout(0,0,surfaceWidth,surfaceHeight);pivotX=0f;pivotY=0f;scaleX=previewScale;scaleY=previewScale;translationX=previewLeft;translationY=previewTop
        }
    }
    fun currentDraft()=draft
    fun information(): List<InfoPlacement> = ChargeInfoView.defaultInformation(draft.key).map { original ->
        if(draft.information==null) original else draft.information!!.find {it.field==original.field} ?: original.copy(visible=false)
    }
    fun setDraft(value: EditorDraft) {
        if(closed)return
        val old=if(::draft.isInitialized)draft else null
        draft=value
        val structure= { d: EditorDraft? -> d?.scene?.layers?.map { Triple(it.id,it.mediaId,it.visible) } }
        if(host==null || old?.key!=value.key || structure(old)!=structure(value) || (old?.information==null)!=(value.information==null)) {
            host?.close();removeAllViews()
            host=ChargingSceneView(context,value.key,value.information,value.scene)
            addView(host,LayoutParams(-1,-1));addView(handles,LayoutParams(-1,-1))
            val current=host!!
            current.prepare(Runnable { if(host===current)current.showEditorFrame() },Runnable { if(host===current)onError?.invoke() })
        } else { value.scene?.let { host?.updateLayers(it.layers) };host?.setInformation(value.information) }
        handles.invalidate()
    }
    fun change(value: EditorDraft) {setDraft(value);listener?.invoke(value)}
    fun modifyLayer(block: (ImageLayer)->ImageLayer) { val s=draft.scene ?: return;change(draft.copy(scene=s.copy(layers=s.layers.map {if(it.id==selectedLayer)block(it)else it}))) }
    fun modifyField(block: (InfoPlacement)->InfoPlacement) {
        change(draft.copy(information=information().map {if(it.field==selectedField)block(it)else it}))
    }
    fun toggleField(field: InfoField) {change(draft.copy(information=information().map {if(it.field==field)it.copy(visible=!it.visible)else it}))}
    fun resetInformation() {change(draft.copy(information=null))}
    override fun onInterceptTouchEvent(e: MotionEvent)=true
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if(closed || !::draft.isInitialized)return false
        pinch.onTouchEvent(e)
        when(e.actionMasked) {
            MotionEvent.ACTION_DOWN->{
                val px=(e.x-previewLeft)/previewScale;val py=(e.y-previewTop)/previewScale
                selectedField=host?.informationView?.placementAt(px,py)
                selectedLayer=if(selectedField==null)host?.imageAt(px,py)else null
                val l=draft.scene?.layers?.find {it.id==selectedLayer};val p=information().find {it.field==selectedField}
                originX=l?.x ?: p?.x ?: .5f;originY=l?.y ?: p?.y ?: .5f;downX=e.x;downY=e.y
                parent.requestDisallowInterceptTouchEvent(true);onSelectionChanged?.invoke();handles.invalidate()
            }
            MotionEvent.ACTION_MOVE->if(!pinch.isInProgress && e.pointerCount==1) {
                val x=(originX+(e.x-downX)/(surfaceWidth*previewScale)).coerceIn(0f,1f);val y=(originY+(e.y-downY)/(surfaceHeight*previewScale)).coerceIn(0f,1f)
                if(selectedLayer!=null)modifyLayer {it.copy(x=x,y=y)}else if(selectedField!=null)modifyField {it.copy(x=x,y=y)}
            }
            MotionEvent.ACTION_UP->{performClick();parent.requestDisallowInterceptTouchEvent(false);onSelectionChanged?.invoke()}
            MotionEvent.ACTION_CANCEL->parent.requestDisallowInterceptTouchEvent(false)
        }
        return true
    }
    override fun performClick(): Boolean {super.performClick();return true}
    override fun close() {if(closed)return;closed=true;host?.close();host=null}
}
