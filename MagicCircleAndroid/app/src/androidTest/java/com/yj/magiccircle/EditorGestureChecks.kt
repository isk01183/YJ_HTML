package com.yj.magiccircle

import android.content.Context
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import kotlin.math.abs

/** Injects real pointer IDs, including reordered indices and a lifted primary pointer. */
object EditorGestureChecks {
    private data class Finger(val id: Int, val x: Float, val y: Float)
    private fun near(actual: Float, expected: Float, label: String) {
        check(abs(actual-expected)<.001f) { "$label: expected $expected, got $actual" }
    }
    private class Touch(private val view: ScreenEditorView) {
        private val start=SystemClock.uptimeMillis()
        private var time=start
        fun send(action: Int, vararg fingers: Finger, index: Int=0) {
            val properties=fingers.map { f -> MotionEvent.PointerProperties().apply {id=f.id;toolType=MotionEvent.TOOL_TYPE_FINGER} }.toTypedArray()
            val coords=fingers.map { f -> MotionEvent.PointerCoords().apply {x=f.x;y=f.y;pressure=1f;size=1f} }.toTypedArray()
            val event=MotionEvent.obtain(start,time,action or (index shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                fingers.size,properties,coords,0,0,1f,1f,0,0,android.view.InputDevice.SOURCE_TOUCHSCREEN,0)
            try { view.onTouchEvent(event) } finally {event.recycle();time+=20}
        }
    }
    fun run(context: Context) {
        val w=context.resources.displayMetrics.widthPixels
        val h=context.resources.displayMetrics.heightPixels
        val a=ImageLayer("selected","11111111-1111-1111-1111-111111111111",.25f,.35f,.4f,0f,false,true)
        val b=a.copy(id="other",x=.7f,y=.75f,width=.8f,angle=30f)
        val scene=ScreenScene("scene-11111111-1111-1111-1111-111111111111","Gestures",ScenePurpose.CHARGING,listOf(a,b))
        val original=EditorDraft(scene.id,scene,emptyList())
        val view=ScreenEditorView(context)
        val parent=FrameLayout(context).apply {addView(view)}
        fun layer(id: String="selected")=view.currentDraft().scene!!.layers.single {it.id==id}
        fun reset(draft: EditorDraft=original) {view.setDraft(draft);view.selectedLayer="selected";view.selectedField=null}
        try {
            reset()
            view.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY))
            view.layout(0,0,w,h)
            val t=Touch(view)
            t.send(MotionEvent.ACTION_DOWN,Finger(7,w*.7f,h*.75f))
            check(view.selectedLayer=="selected") {"Canvas DOWN replaced the explicitly selected layer"}
            t.send(MotionEvent.ACTION_MOVE,Finger(7,w*.8f,h*.8f))
            near(layer().x,.35f,"Selected layer drag X");near(layer().y,.4f,"Selected layer drag Y")
            check(layer("other")==b) {"Drag changed another layer"}
            t.send(MotionEvent.ACTION_UP,Finger(7,w*.8f,h*.8f))

            reset()
            val cx=w*.25f;val cy=h*.35f
            t.send(MotionEvent.ACTION_DOWN,Finger(7,cx-40,cy))
            t.send(MotionEvent.ACTION_POINTER_DOWN,Finger(7,cx-40,cy),Finger(19,cx+40,cy),index=1)
            t.send(MotionEvent.ACTION_MOVE,Finger(19,cx,cy+80),Finger(7,cx,cy-80))
            near(layer().width,.8f,"Two-finger 2x scale");near(layer().angle,90f,"Two-finger rotation")
            near(layer().x,.25f,"Pinch center X");near(layer().y,.35f,"Pinch center Y")
            check(layer("other")==b) {"Pinch changed another layer"}
            t.send(MotionEvent.ACTION_POINTER_UP,Finger(19,cx,cy+80),Finger(7,cx,cy-80),index=0)
            t.send(MotionEvent.ACTION_MOVE,Finger(7,cx,cy-80))
            near(layer().x,.25f,"Pointer UP stationary X");near(layer().y,.35f,"Pointer UP stationary Y")
            t.send(MotionEvent.ACTION_MOVE,Finger(7,cx+20,cy-50))
            near(layer().x,.25f+20f/w,"Remaining finger X");near(layer().y,.35f+30f/h,"Remaining finger Y")
            near(layer().width,.8f,"Remaining finger keeps scale");near(layer().angle,90f,"Remaining finger keeps rotation")
            t.send(MotionEvent.ACTION_UP,Finger(7,cx+20,cy-50))

            reset()
            t.send(MotionEvent.ACTION_DOWN,Finger(7,cx-40,cy))
            t.send(MotionEvent.ACTION_POINTER_DOWN,Finger(7,cx-40,cy),Finger(19,cx+40,cy),index=1)
            t.send(MotionEvent.ACTION_POINTER_DOWN,Finger(3,cx+150,cy+150),Finger(19,cx+40,cy),Finger(7,cx-40,cy),index=0)
            t.send(MotionEvent.ACTION_MOVE,Finger(19,cx+40,cy),Finger(3,cx+250,cy+250),Finger(7,cx-40,cy))
            near(layer().x,.25f,"Third pointer X");near(layer().y,.35f,"Third pointer Y")
            near(layer().width,.4f,"Third pointer scale");near(layer().angle,0f,"Third pointer rotation")
            t.send(MotionEvent.ACTION_POINTER_UP,Finger(19,cx+40,cy),Finger(3,cx+250,cy+250),Finger(7,cx-40,cy),index=2)
            t.send(MotionEvent.ACTION_MOVE,Finger(3,cx+250,cy+250),Finger(19,cx+40,cy))
            near(layer().x,.25f,"Replacement pointer X");near(layer().y,.35f,"Replacement pointer Y")
            near(layer().width,.4f,"Replacement pointer scale");near(layer().angle,0f,"Replacement pointer rotation")
            check(layer("other")==b) {"Pointer replacement changed another layer"}
            t.send(MotionEvent.ACTION_CANCEL,Finger(3,cx+250,cy+250),Finger(19,cx+40,cy))

            reset(original.copy(scene=scene.copy(layers=listOf(a.copy(angle=179f),b))))
            t.send(MotionEvent.ACTION_DOWN,Finger(7,cx-40,cy))
            t.send(MotionEvent.ACTION_POINTER_DOWN,Finger(7,cx-40,cy),Finger(19,cx+40,cy),index=1)
            t.send(MotionEvent.ACTION_MOVE,Finger(7,cx+20,cy-80),Finger(19,cx+20,cy+80))
            near(layer().angle,-91f,"Rotation wraps into [-180,180)")
            near(layer().x,.25f+20f/w,"Pinch midpoint translation")
            t.send(MotionEvent.ACTION_MOVE,Finger(7,cx-1000,cy),Finger(19,cx+1000,cy))
            near(layer().width,4f,"Maximum width")
            t.send(MotionEvent.ACTION_MOVE,Finger(7,cx-1,cy),Finger(19,cx+1,cy))
            near(layer().width,.05f,"Minimum width")
            t.send(MotionEvent.ACTION_CANCEL,Finger(7,cx-1,cy),Finger(19,cx+1,cy))
            val canceled=view.currentDraft()
            t.send(MotionEvent.ACTION_MOVE,Finger(7,cx+200,cy+200))
            check(view.currentDraft()==canceled) {"MOVE after CANCEL modified the draft"}

            reset()
            t.send(MotionEvent.ACTION_DOWN,Finger(7,cx,cy))
            view.selectedLayer="other"
            t.send(MotionEvent.ACTION_MOVE,Finger(7,cx+100,cy+100))
            check(layer("other")==b) {"Selection switch redirected an active gesture"}
            t.send(MotionEvent.ACTION_CANCEL,Finger(7,cx+100,cy+100))

            for(selection in listOf(null,"missing","selected")) {
                reset(original.copy(scene=scene.copy(layers=listOf(a.copy(visible=false),b))))
                view.selectedLayer=selection
                val before=view.currentDraft()
                t.send(MotionEvent.ACTION_DOWN,Finger(7,cx,cy))
                t.send(MotionEvent.ACTION_MOVE,Finger(7,cx+100,cy+100))
                t.send(MotionEvent.ACTION_UP,Finger(7,cx+100,cy+100))
                check(view.currentDraft()==before) {"Absent or hidden selection changed the draft"}
            }

            val field=InfoPlacement(InfoField.BATTERY,.4f,.4f,true)
            reset(original.copy(information=listOf(field)))
            view.selectedLayer=null;view.selectedField=InfoField.BATTERY
            t.send(MotionEvent.ACTION_DOWN,Finger(7,10f,10f))
            t.send(MotionEvent.ACTION_MOVE,Finger(7,30f,40f))
            val moved=view.currentDraft().information!!.single {it.field==InfoField.BATTERY}
            near(moved.x,.4f+20f/w,"Information drag X");near(moved.y,.4f+30f/h,"Information drag Y")
            check(view.selectedField==InfoField.BATTERY && view.currentDraft().scene==scene)
            t.send(MotionEvent.ACTION_UP,Finger(7,30f,40f))

            parent.removeView(view)
            reset()
            t.send(MotionEvent.ACTION_DOWN,Finger(7,cx,cy))
            t.send(MotionEvent.ACTION_CANCEL,Finger(7,cx,cy))
            view.close()
            val closed=view.currentDraft()
            t.send(MotionEvent.ACTION_MOVE,Finger(7,cx+100,cy+100))
            check(view.currentDraft()==closed) {"Closed editor changed its draft"}
        } finally {view.close();view.close()}
    }
}
