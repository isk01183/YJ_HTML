package com.yj.magiccircle

data class VrmPlacement(val x: Float=0f,val y: Float=0f,val scale: Float=1f,val blink: Boolean=true) {
    fun normalized()=VrmPlacement(
        if(x.isFinite())x.coerceIn(-.35f,.35f)else 0f,
        if(y.isFinite())y.coerceIn(-.35f,.35f)else 0f,
        if(scale.isFinite())scale.coerceIn(.5f,1.5f)else 1f,blink)
}
