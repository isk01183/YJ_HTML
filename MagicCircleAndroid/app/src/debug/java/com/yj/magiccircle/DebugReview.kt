package com.yj.magiccircle

import android.app.Activity
import android.content.Intent
import android.view.Gravity
import android.widget.Button
import android.widget.FrameLayout

object DebugReview {
    @JvmStatic fun install(activity: Activity, root: FrameLayout) {
        root.addView(Button(activity).apply {
            setText(R.string.sanctuary_review)
            setOnClickListener { activity.startActivity(Intent(activity, SanctuaryReviewActivity::class.java)) }
        }, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.END))
    }
}
