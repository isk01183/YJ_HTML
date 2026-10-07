package com.yj.magiccircle
import android.content.Context
object ScreenEditorChecks {
 fun run(context: Context) {
  val view=ScreenEditorView(context)
  try {
   val draft=EditorDraft("classic",null,null)
   view.setDraft(draft)
   check(view.currentDraft()==draft)
   view.selectedField=InfoField.BATTERY
   view.modifyField {it.copy(x=.23f,y=.67f)}
   // The same action used by the UI must read the latest draft, not a captured render snapshot.
   view.toggleField(InfoField.BATTERY)
   val changed=view.currentDraft().information!!.single {it.field==InfoField.BATTERY}
   check(changed.x==.23f && changed.y==.67f)
   view.resetInformation()
   check(view.currentDraft().information==null) {"Reset must restore the original renderer"}
   val scene=ScreenScene("scene-11111111-1111-1111-1111-111111111111","Check",ScenePurpose.CHARGING,
    listOf(ImageLayer("a","11111111-1111-1111-1111-111111111111",.5f,.5f,1f,0f,false,true)))
   view.setDraft(EditorDraft(scene.id,scene,null));view.selectedLayer="a"
   view.modifyLayer {it.copy(width=1.8f,angle=45f,flipX=true)}
   view.toggleField(InfoField.BATTERY);view.resetInformation()
   check(view.currentDraft().scene!!.layers.single().let {it.width==1.8f && it.angle==45f && it.flipX})
  } finally {view.close();view.close()}
 }
}
