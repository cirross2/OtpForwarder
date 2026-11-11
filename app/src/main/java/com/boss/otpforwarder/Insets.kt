package com.boss.otpforwarder

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * 엣지투엣지(targetSdk 35 기본)에서 시스템 바(상태바/내비바) 영역만큼
 * 뷰에 패딩을 줘, 내용이 바 밑으로 깔리지 않게 한다.
 */
fun View.applySystemBarInsets() {
    ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        val b = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        v.setPadding(b.left, b.top, b.right, b.bottom)
        insets
    }
}
