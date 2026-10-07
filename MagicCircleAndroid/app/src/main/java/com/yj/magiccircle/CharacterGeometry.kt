package com.yj.magiccircle

data class BodyPoint(val x: Float,val y: Float)
data class BodyFrame(val crownY: Float,val soleY: Float,val headHeight: Float,val neck: BodyPoint,
    val leftShoulder: BodyPoint,val rightShoulder: BodyPoint,val leftElbow: BodyPoint,val rightElbow: BodyPoint,
    val leftWrist: BodyPoint,val rightWrist: BodyPoint,val pelvis: BodyPoint,val leftKnee: BodyPoint,
    val rightKnee: BodyPoint,val leftAnkle: BodyPoint,val rightAnkle: BodyPoint,val scale: Float) {
    val points get() = listOf(neck,leftShoulder,rightShoulder,leftElbow,rightElbow,leftWrist,rightWrist,pelvis,leftKnee,rightKnee,leftAnkle,rightAnkle)
}
object CharacterGeometry {
    fun measure(b: BodyProportions): BodyFrame {
        b.values().forEachIndexed { i,v -> require(v.isFinite() && v in CharacterRules.range(i)) }
        val unit=b.heightCm/(32f*b.head+6f+48f*b.torso+74f*b.legs)
        val crown=200f-b.heightCm; val head=32f*b.head*unit
        val shoulderY=crown+head+6f*unit
        val pelvisY=shoulderY+48f*b.torso*unit
        val shoulderX=17.2f*b.shoulders*unit
        val elbowY=shoulderY+25f*b.arms*unit; val wristY=elbowY+24f*b.arms*unit
        val kneeY=pelvisY+36f*b.legs*unit; val ankleY=200f-8f*unit
        return BodyFrame(crown,200f,head,BodyPoint(0f,crown+head),
            BodyPoint(-shoulderX,shoulderY),BodyPoint(shoulderX,shoulderY),
            BodyPoint(-shoulderX-4f*unit,elbowY),BodyPoint(shoulderX+4f*unit,elbowY),
            BodyPoint(-shoulderX-7f*unit,wristY),BodyPoint(shoulderX+7f*unit,wristY),BodyPoint(0f,pelvisY),
            BodyPoint(-8f*unit,kneeY),BodyPoint(8f*unit,kneeY),BodyPoint(-8f*unit,ankleY),BodyPoint(8f*unit,ankleY),unit)
    }
}
