package com.yj.magiccircle
import android.content.Context
object ChargeInfoChecks {
 fun run(context: Context) {
  ThemeSelection.IDS.forEach { SceneRules.validateInformation(ChargeInfoView.defaultInformation(it)) }
  val v=ChargeInfoView(context)
  v.setInformation(listOf(InfoPlacement(InfoField.BATTERY,.5f,.5f,true)))
  v.update(ChargeSnapshot(100,32.5f,2,2,2),"ko",.5f)
  check(v.contentDescription.contains("100%"))
  v.setInformation(emptyList())
  check(v.contentDescription.isNullOrEmpty())
  val scene=ScreenScene("scene-11111111-1111-1111-1111-111111111111","Check",ScenePurpose.CHARGING,emptyList())
  val layerHost=ChargingSceneView(context,scene.id,null,scene)
  try {
   layerHost.setInformation(null)
   check(!layerHost.informationView.contentDescription.isNullOrEmpty()) {"Moving a layer must retain default scene information"}
  } finally {layerHost.close()}
  listOf("native-N01","ref-W03","ref-R01","classic","premium","ref-U04").forEach { id ->
   val host=ChargingSceneView(context,id,emptyList())
   host.setInformation(emptyList()); host.close(); host.close()
  }
  val language=WebViews.selectedLanguage(context)
  try {
   for((code,title) in listOf("en" to "Sanctuary of Stars","ja" to "星を読む聖域")) {
    WebViews.selectLanguage(context,code)
    val native=MainMagicChargeView(context).apply {customInformation=true}
    try {
     native.start()
     val field=MainMagicChargeView::class.java.getDeclaredField("panel").apply {isAccessible=true}
     check((field.get(native) as ChargeStatusPanelRenderer).description().startsWith(title)) {"Custom N01 lost its selected language"}
     check(native.contentDescription.isNullOrEmpty()) {"Hidden native information must not be announced"}
    } finally {native.stop()}
   }
  } finally {WebViews.selectLanguage(context,language)}
 }
}
