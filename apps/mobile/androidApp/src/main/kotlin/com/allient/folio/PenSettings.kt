package com.allient.folio

import android.content.Context
import android.content.SharedPreferences
import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlin.math.abs

/**
 * Pen pressure settings shared by the whole app. They live in the same preferences file the
 * notebook editor already used, so the editor's Pressure panel and the global settings edit
 * one configuration.
 */
internal object PenSettings {
    private const val FILE = "editor"
    private var listener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    /** Contact below this pressure is treated as hovering. Read on every pen touch. */
    @Volatile var threshold: Float = pressureProfiles[1].threshold
        private set(value) { field = value; thresholdState.floatValue = value }

    /** The same value for Compose, provided to every screen as LocalPenThreshold. */
    val thresholdState = mutableFloatStateOf(pressureProfiles[1].threshold)

    fun init(context: Context) {
        if (listener != null) return
        val preferences = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        threshold = preferences.getFloat("threshold", pressureProfiles[1].threshold)
        // SharedPreferences keeps listeners weakly, so hold the reference here.
        listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (key == "threshold") threshold = prefs.getFloat("threshold", pressureProfiles[1].threshold)
        }.also(preferences::registerOnSharedPreferenceChangeListener)
        installDialogFilter()
    }

    /**
     * Dialogs have their own window, which the activity's touch filter never sees. Every Compose
     * root reports itself here; the ones inside a dialog get the same filter on their window.
     */
    private fun installDialogFilter() {
        val previous = androidx.compose.ui.platform.ViewRootForTest.onViewCreatedCallback
        androidx.compose.ui.platform.ViewRootForTest.onViewCreatedCallback = { root ->
            previous?.invoke(root)
            if (root.view.isAttachedToWindow) filterDialogOf(root.view)
            root.view.addOnAttachStateChangeListener(object : android.view.View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(view: android.view.View) = filterDialogOf(view)
                override fun onViewDetachedFromWindow(view: android.view.View) = Unit
            })
        }
    }
}

private fun filterDialogOf(view: android.view.View) {
    var parent = view.parent
    while (parent != null && parent !is androidx.compose.ui.window.DialogWindowProvider) parent = parent.parent
    val window = (parent as? androidx.compose.ui.window.DialogWindowProvider)?.window ?: return
    val callback = window.callback ?: return
    if (callback !is PenFilteringCallback) window.callback = PenFilteringCallback(callback)
}

private class PenFilteringCallback(private val delegate: android.view.Window.Callback) : android.view.Window.Callback by delegate {
    private val filter = PenPressureFilter()
    override fun dispatchTouchEvent(event: MotionEvent): Boolean = filter.dispatch(event) { delegate.dispatchTouchEvent(it) }
}

/**
 * Drops pen contact that is lighter than the global threshold, for any screen: library, reader and
 * notebooks. A touch that starts too lightly is held back and becomes a normal press (ACTION_DOWN)
 * the moment the pen presses firmly enough, so hovering never taps or drags anything.
 */
internal class PenPressureFilter {
    private var engaged = false

    fun dispatch(event: MotionEvent, next: (MotionEvent) -> Boolean): Boolean {
        val tool = event.getToolType(0)
        val pen = tool == MotionEvent.TOOL_TYPE_STYLUS || tool == MotionEvent.TOOL_TYPE_ERASER
        if (!pen || event.pointerCount != 1) {
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) engaged = false
            return next(event)
        }
        val firm = event.pressure > PenSettings.threshold
        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { engaged = firm; if (firm) next(event) else true }
            MotionEvent.ACTION_MOVE -> when {
                engaged -> next(event)
                firm -> {
                    engaged = true
                    val press = MotionEvent.obtain(event)
                    press.setAction(MotionEvent.ACTION_DOWN)
                    try { next(press) } finally { press.recycle() }
                }
                else -> true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { val was = engaged; engaged = false; if (was) next(event) else true }
            else -> next(event)
        }
    }
}

/** The app-wide pen settings, opened from Preferences and from the reader. Same options as the notebook's Pressure panel. */
@Composable
internal fun GlobalPenSettingsDialog(onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val preferences = remember { context.getSharedPreferences("editor", Context.MODE_PRIVATE) }
    var threshold by remember { mutableFloatStateOf(preferences.getFloat("threshold", pressureProfiles[1].threshold)) }
    var pressureEnabled by remember { mutableStateOf(preferences.getBoolean("pressureCurveEnabled", true)) }
    var pressureCurve by remember { mutableStateOf(preferences.loadResponseCurve(CurveChannel.Pressure, defaultPressureCurve)) }
    var velocityEnabled by remember { mutableStateOf(preferences.getBoolean("velocityCurveEnabled", false)) }
    var velocityCurve by remember { mutableStateOf(preferences.loadResponseCurve(CurveChannel.Velocity, defaultVelocityCurve, velocityEnabled)) }
    var tiltEnabled by remember { mutableStateOf(preferences.getBoolean("tiltCurveEnabled", false)) }
    var tiltCurve by remember { mutableStateOf(preferences.loadResponseCurve(CurveChannel.Tilt, defaultTiltCurve, tiltEnabled)) }
    var profileName by remember { mutableStateOf(preferences.getString("profile", "Balanced") ?: "Balanced") }
    fun save() {
        preferences.edit().putFloat("threshold", threshold).putString("profile", profileName)
            .putBoolean("pressureCurveEnabled", pressureEnabled).putResponseCurve(CurveChannel.Pressure, pressureCurve)
            .putBoolean("velocityCurveEnabled", velocityEnabled).putResponseCurve(CurveChannel.Velocity, velocityCurve)
            .putBoolean("tiltCurveEnabled", tiltEnabled).putResponseCurve(CurveChannel.Tilt, tiltCurve).apply()
    }
    PressureDialog(threshold, profileName, pressureEnabled, pressureCurve, velocityEnabled, velocityCurve, tiltEnabled, tiltCurve,
        onProfile = { profileName = it.name; threshold = it.threshold; save() },
        onThreshold = { threshold = it; profileName = pressureProfiles.firstOrNull { p -> abs(p.threshold - it) < .005f }?.name ?: "Custom"; save() },
        onPressureEnabled = { pressureEnabled = it; save() }, onPressureCurve = { pressureCurve = it; save() },
        onVelocityEnabled = { velocityEnabled = it; save() }, onVelocityCurve = { velocityCurve = it; save() },
        onTiltEnabled = { tiltEnabled = it; save() }, onTiltCurve = { tiltCurve = it; save() },
        onDismiss = onDismiss)
}
