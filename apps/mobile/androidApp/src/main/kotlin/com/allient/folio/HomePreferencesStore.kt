package com.allient.folio

import android.content.Context
import com.allient.folio.home.AccentPresets
import com.allient.folio.home.CornerStyles
import com.allient.folio.home.HomePreferences
import com.allient.folio.home.HomeSort

/** App-private preferences `home-preferences`. Unknown stored values fall back to the defaults. */
internal class HomePreferencesStore(context: Context) {
    private val preferences = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun read(): HomePreferences {
        val defaults = HomePreferences()
        return HomePreferences(
            accent = preferences.getString(ACCENT, null)?.takeIf { id -> AccentPresets.any { it.id == id } } ?: defaults.accent,
            corners = preferences.getString(CORNERS, null)?.takeIf { id -> CornerStyles.any { it.first == id } } ?: defaults.corners,
            viewMode = preferences.getString(VIEW, null)?.takeIf { it in setOf("grid", "list") } ?: defaults.viewMode,
            sort = preferences.getString(SORT, null)?.takeIf { id -> HomeSort.all.any { it.first == id } } ?: defaults.sort,
            startFullscreen = preferences.getBoolean(FULLSCREEN, defaults.startFullscreen),
            trashRetentionDays = preferences.getInt(RETENTION, defaults.trashRetentionDays).takeIf { it in setOf(0, 7, 30) } ?: defaults.trashRetentionDays,
        )
    }

    fun write(value: HomePreferences) {
        preferences.edit().putString(ACCENT, value.accent).putString(CORNERS, value.corners).putString(VIEW, value.viewMode)
            .putString(SORT, value.sort).putBoolean(FULLSCREEN, value.startFullscreen).putInt(RETENTION, value.trashRetentionDays).apply()
    }

    companion object {
        const val NAME = "home-preferences"
        const val ACCENT = "accent"
        const val CORNERS = "corners"
        const val VIEW = "view_mode"
        const val SORT = "sort"
        const val FULLSCREEN = "start_fullscreen"
        const val RETENTION = "trash_retention_days"
    }
}
