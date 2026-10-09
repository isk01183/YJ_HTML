package com.yj.magiccircle

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import java.io.Closeable
import kotlin.math.atan2
import kotlin.math.hypot

class ScreenEditorView(context: Context): FrameLayout(context),Closeable {
    private lateinit var draft: EditorDraft
    private var host: ChargingSceneView?=null
    private var vrmHost: VrmSceneView?=null
    private var lease: AutoCloseable?=null
    private var rendering=true
    private var listener: ((EditorDraft)->Unit)?=null
    var selectedLayer: String?=null
        set(value) {if(field!=value)stopGesture();field=value;if(value!=null){selectedField=null;selectedCharacter=false;selectedVrm=false};handles.invalidate()}
    var selectedField: InfoField?=null
        set(value) {if(field!=value)stopGesture();field=value;if(value!=null){selectedLayer=null;selectedCharacter=false;selectedVrm=false};handles.invalidate()}
    var selectedCharacter=false
        set(value) {if(field!=value)stopGesture();field=value;if(value){selectedLayer=null;selectedField=null;selectedVrm=false};handles.invalidate()}
    var selectedVrm=false
        set(value) {if(field!=value)stopGesture();field=value;if(value){selectedLayer=null;selectedField=null;selectedCharacter=false};handles.invalidate()}
    var onSelectionChanged: (() -> Unit)?=null
    var onError: (() -> Unit)?=null
    private var downX=0f;private var downY=0f;private var originX=0f;private var originY=0f
    private var gestureLayer: String?=null
    private var gestureField: InfoField?=null
    private var gestureCharacter=false
    private var gestureVrm=false
    private var firstPointer=-1;private var secondPointer=-1
    private var originWidth=1f;private var originAngle=0f
    private var pointerDistance=0f;private var pointerAngle=0f
    private var closed=false
    private var previewScale=1f
    private var previewLeft=0f
    private var previewTop=0f
    private var surfaceWidth=1
    private var surfaceHeight=1
    private var previewProgress=.5f
    private val ink=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=0xffeac985.toInt();style=Paint.Style.STROKE;strokeWidth=2f*resources.displayMetrics.density }
    private val handles: View=object:View(context) {
        override fun onDraw(c: Canvas) {
            if(!::draft.isInitialized)return
            val l=draft.scene?.layers?.find { it.id==selectedLayer }
            val p=information().find { it.field==selectedField }
            val a=if(selectedCharacter)draft.scene?.character else null
            val v=if(selectedVrm)draft.scene?.vrm?.placement else null
            val x=l?.x ?: a?.x ?: v?.screenX() ?: p?.x ?: return;val y=l?.y ?: a?.y ?: v?.screenY() ?: p?.y ?: return
            c.drawCircle(x*width,y*height,12f*resources.displayMetrics.density,ink)
        }
    }
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
        val structure= { d: EditorDraft? -> Triple(d?.scene?.layers?.map { Triple(it.id,it.mediaId,it.visible) },d?.scene?.character?.definition,d?.scene?.vrm?.modelId) }
        if(old?.key!=value.key || structure(old)!=structure(value))stopGesture()
        draft=value
        if(!rendering)return
        if(host==null && vrmHost==null || old?.key!=value.key || structure(old)!=structure(value) || (old?.information==null)!=(value.information==null)) {
            releaseRenderers()
            if(value.scene?.vrm!=null) {
                try {
                    val library=MediaLibrary.get(context);val model=value.scene.vrm.modelId
                    lease=library.leaseMedia(value.scene.layers.map {it.mediaId})
                    val current=VrmSceneView(context,value.scene,{VrmModelStore.get(context).openModel(model)},{library.open(it,false)}, {}, {releaseRenderers();onError?.invoke()})
                    vrmHost=current;addView(current,LayoutParams(-1,-1))
                } catch(_: Exception){lease?.close();lease=null;onError?.invoke()}
            } else {
                host=ChargingSceneView(context,value.key,value.information,value.scene)
                addView(host,LayoutParams(-1,-1))
                val current=host!!
                current.prepare(Runnable { if(host===current)current.showEditorFrame(previewProgress) },Runnable { if(host===current)onError?.invoke() })
            }
            addView(handles,LayoutParams(-1,-1))
        } else { value.scene?.let { vrmHost?.updateScene(it);host?.updateLayers(it.layers);host?.updateCharacter(it.character) };host?.setInformation(value.information) }
        handles.invalidate()
    }
    fun showEditorStage(progress: Float) {if(closed)return;previewProgress=progress.coerceIn(0f,1f);host?.showEditorFrame(previewProgress)}
    fun change(value: EditorDraft) {
        val s=value.scene
        val safe=if(s==null)value else value.copy(scene=s.copy(
            character=s.character?.let {it.copy(beforeImage=it.beforeImage.coerceIn(0,s.layers.size))},
            vrm=s.vrm?.let {it.copy(beforeImage=it.beforeImage.coerceIn(0,s.layers.size))}))
        setDraft(safe);listener?.invoke(safe)
    }
    fun modifyLayer(block: (ImageLayer)->ImageLayer) { val s=draft.scene ?: return;change(draft.copy(scene=s.copy(layers=s.layers.map {if(it.id==selectedLayer)block(it)else it}))) }
    fun modifyCharacter(block: (CharacterLayer)->CharacterLayer) {val s=draft.scene ?: return;val c=s.character ?: return;change(draft.copy(scene=s.copy(character=block(c))))}
    fun modifyVrm(block: (VrmSceneLayer)->VrmSceneLayer) {val s=draft.scene ?: return;val v=s.vrm ?: return;change(draft.copy(scene=s.copy(vrm=block(v))))}
    fun modifyField(block: (InfoPlacement)->InfoPlacement) {
        change(draft.copy(information=information().map {if(it.field==selectedField)block(it)else it}))
    }
    fun toggleField(field: InfoField) {change(draft.copy(information=information().map {if(it.field==field)it.copy(visible=!it.visible)else it}))}
    fun resetInformation() {change(draft.copy(information=null))}
    override fun onInterceptTouchEvent(e: MotionEvent)=true
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if(closed || !::draft.isInitialized)return false
        when(e.actionMasked) {
            MotionEvent.ACTION_DOWN->{
                stopGesture()
                if(selectedVrm)gestureVrm=draft.scene?.vrm?.visible==true
                else if(selectedCharacter)gestureCharacter=draft.scene?.character?.visible==true
                else if(selectedLayer!=null)gestureLayer=draft.scene?.layers?.find {it.id==selectedLayer && it.visible}?.id
                else gestureField=information().find {it.field==selectedField && it.visible}?.field
                if(gestureLayer!=null || gestureField!=null || gestureCharacter || gestureVrm) {
                    rebasePointers(e);parent?.requestDisallowInterceptTouchEvent(true)
                }
            }
            MotionEvent.ACTION_POINTER_DOWN->rebasePointers(e)
            MotionEvent.ACTION_POINTER_UP->rebasePointers(e,e.actionIndex)
            MotionEvent.ACTION_MOVE->movePointers(e)
            MotionEvent.ACTION_UP->{stopGesture();performClick();onSelectionChanged?.invoke()}
            MotionEvent.ACTION_CANCEL->stopGesture()
        }
        return true
    }
    private fun stopGesture() {
        gestureLayer=null;gestureField=null;gestureCharacter=false;gestureVrm=false;firstPointer=-1;secondPointer=-1
        parent?.requestDisallowInterceptTouchEvent(false)
    }
    private fun rebasePointers(e: MotionEvent, excluded: Int=-1) {
        if(gestureLayer==null && gestureField==null && !gestureCharacter && !gestureVrm)return
        val l=gestureArtwork()
        val p=if(gestureField!=null)information().find {it.field==gestureField && it.visible}else null
        if(l==null && p==null) {stopGesture();return}
        val ids=(0 until e.pointerCount).filter {it!=excluded}.map {e.getPointerId(it)}
        firstPointer=if(firstPointer in ids)firstPointer else if(secondPointer in ids)secondPointer else ids.firstOrNull() ?: -1
        secondPointer=if(l==null)-1 else if(secondPointer in ids && secondPointer!=firstPointer)secondPointer else ids.firstOrNull {it!=firstPointer} ?: -1
        val first=e.findPointerIndex(firstPointer);val second=e.findPointerIndex(secondPointer)
        if(first<0) {stopGesture();return}
        originX=l?.x ?: p!!.x;originY=l?.y ?: p!!.y
        originWidth=l?.width ?: 1f;originAngle=l?.angle ?: 0f
        downX=e.getX(first);downY=e.getY(first);pointerDistance=0f
        if(second>=0) {
            val dx=e.getX(second)-downX;val dy=e.getY(second)-downY
            pointerDistance=hypot(dx,dy);pointerAngle=atan2(dy,dx)*180f/Math.PI.toFloat()
            downX=(downX+e.getX(second))/2;downY=(downY+e.getY(second))/2
        }
    }
    private fun movePointers(e: MotionEvent) {
        if(firstPointer<0 || previewScale<=0f)return
        val l=gestureArtwork()
        val p=if(gestureField!=null)information().find {it.field==gestureField && it.visible}else null
        if(l==null && p==null) {stopGesture();return}
        val first=e.findPointerIndex(firstPointer);val second=e.findPointerIndex(secondPointer)
        if(first<0 || (secondPointer>=0 && second<0)) {rebasePointers(e);return}
        var px=e.getX(first);var py=e.getY(first)
        var size=originWidth;var angle=originAngle
        if(second>=0) {
            val dx=e.getX(second)-px;val dy=e.getY(second)-py
            val distance=hypot(dx,dy)
            if(pointerDistance<=0f) {rebasePointers(e);return}
            size=(originWidth*(distance/pointerDistance)).coerceIn(.05f,4f)
            if(distance>0f)angle=originAngle+atan2(dy,dx)*180f/Math.PI.toFloat()-pointerAngle
            angle=((angle+180f)%360f+360f)%360f-180f
            px=(px+e.getX(second))/2;py=(py+e.getY(second))/2
        }
        val x=(originX+(px-downX)/(surfaceWidth*previewScale)).coerceIn(0f,1f)
        val y=(originY+(py-downY)/(surfaceHeight*previewScale)).coerceIn(0f,1f)
        if(gestureVrm)modifyVrm {it.copy(placement=it.placement.withScreenPosition(x,y).copy(scale=size).normalized())}
        else if(gestureCharacter)modifyCharacter {it.copy(x=x,y=y,width=size,angle=angle)}
        else if(l!=null)modifyLayer {it.copy(x=x,y=y,width=size,angle=angle)}
        else modifyField {it.copy(x=x,y=y)}
    }
    private fun gestureArtwork(): ImageLayer? = if(gestureVrm)draft.scene?.vrm?.takeIf {it.visible}?.let {
        ImageLayer("vrm","",it.placement.screenX(),it.placement.screenY(),it.placement.scale,0f,false,true)
    } else if(gestureCharacter)draft.scene?.character?.takeIf {it.visible}?.let {
        ImageLayer("character","",it.x,it.y,it.width,it.angle,it.flipX,it.visible)
    } else draft.scene?.layers?.find {it.id==gestureLayer && it.visible}
    override fun performClick(): Boolean {super.performClick();return true}
    fun setRenderingEnabled(enabled: Boolean) {
        if(closed)return
        rendering=enabled
        if(!enabled)releaseRenderers() else if(::draft.isInitialized)setDraft(draft)
    }
    private fun releaseRenderers() {host?.close();host=null;vrmHost?.close();vrmHost=null;lease?.close();lease=null;removeAllViews()}
    override fun close() {if(closed)return;stopGesture();closed=true;releaseRenderers()}
}
