package com.allient.folio

import android.content.SharedPreferences

internal data class ReaderGesturePreferences(
    val stylusWriting: Boolean = false,
    val clickLongPress: Boolean = true,
    val slide: Boolean = true,
    val pinch: Boolean = true,
    val systemBars: Boolean = true,
    val bottom: Boolean = true,
    val sides: Boolean = true,
    val back: Boolean = true,
) {
    fun save(preferences: SharedPreferences) {
        preferences.edit().putBoolean("gestureStylus", stylusWriting).putBoolean("gestureClick", clickLongPress)
            .putBoolean("gestureSlide", slide).putBoolean("gesturePinch", pinch).putBoolean("gestureSystemBars", systemBars)
            .putBoolean("gestureBottom", bottom).putBoolean("gestureSides", sides).putBoolean("gestureBack", back).apply()
    }
    companion object {
        fun read(preferences: SharedPreferences) = ReaderGesturePreferences(
            preferences.getBoolean("gestureStylus", false), preferences.getBoolean("gestureClick", true),
            preferences.getBoolean("gestureSlide", true), preferences.getBoolean("gesturePinch", true),
            preferences.getBoolean("gestureSystemBars", true), preferences.getBoolean("gestureBottom", true),
            preferences.getBoolean("gestureSides", true), preferences.getBoolean("gestureBack", true),
        )
    }
}
