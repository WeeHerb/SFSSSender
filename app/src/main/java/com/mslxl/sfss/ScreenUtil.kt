package com.mslxl.sfss

import android.content.Context
import android.content.res.Resources
import android.util.TypedValue


object ScreenUtil {
    fun spToPx(context: Context, spValue: Float): Int {
        val metrics = context.resources.displayMetrics
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, spValue, metrics).toInt()
    }

    val widthPixels: Int
        get() = Resources.getSystem().displayMetrics.widthPixels

    fun dpToPx(context: Context, dipValue: Float): Int {
        val metrics = context.resources.displayMetrics
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dipValue, metrics).toInt()
    }

    fun dip2pix(context: Context, dip: Float): Int {
        val scale = context.resources
            .displayMetrics.density
        return (dip * scale + 0.5f).toInt()
    }
}