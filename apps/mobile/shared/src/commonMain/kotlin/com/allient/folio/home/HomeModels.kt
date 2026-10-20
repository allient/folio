package com.allient.folio.home

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Platform-neutral presentation models. Persistence and ink rendering are supplied by the host.
/** [deletedAt] > 0 means the folder is in Trash; its notebooks and books keep their folderId and show as unfiled. */
data class NoteFolder(val id: String, val name: String, val color: String = "sage", val style: String = "classic", val deletedAt: Long = 0)
data class LibrarySettings(val folders: List<NoteFolder> = emptyList(), val downloaded: Set<String> = emptySet()) {
    val activeFolders get() = folders.filter { it.deletedAt == 0L }
}
data class HomeNotebook(
    val id: String, val title: String, val pageCount: Int, val folderId: String?,
    val favorite: Boolean, val openedAt: Long, val updatedAt: Long, val dateLabel: String,
    val tags: List<String>, val searchableText: String, val currentPage: Int = 1,
    val infinite: Boolean = false, val createdAt: Long = updatedAt, val deletedAt: Long = 0,
)
data class HomeBook(
    val id: String, val title: String, val author: String, val format: String, val folderId: String?,
    val favorite: Boolean, val openedAt: Long, val createdAt: Long, val dateLabel: String,
    val progress: Float, val pageCount: Int, val missing: Boolean, val deletedAt: Long = 0,
)

object HomeSort { const val Edited = "edited"; const val Created = "created"; const val Type = "type"; const val Name = "name"
    val all = listOf(Edited to "Last edit", Created to "Date created", Type to "File type", Name to "Name") }

/** Persisted by the host. The home menu and the Preferences screen edit the same values. */
data class HomePreferences(
    val accent: String = "sage", val corners: String = "rounded", val viewMode: String = "grid",
    val sort: String = HomeSort.Edited, val startFullscreen: Boolean = false, val trashRetentionDays: Int = 30,
    /** Anonymous usage statistics (Google Analytics). Stored by the platform analytics layer, not in home preferences. */
    val shareUsageStats: Boolean = true,
)

@Immutable data class AccentPreset(val id: String, val name: String, val primary: Color, val soft: Color)
val AccentPresets = listOf(
    AccentPreset("sage", "Sage", Color(0xFF2F7A68), Color(0xFFE3EFEA)),
    AccentPreset("morning", "Morning", Color(0xFFC9803A), Color(0xFFFBEEDD)),
    AccentPreset("evening", "Evening", Color(0xFF5A5A9C), Color(0xFFE9E8F5)),
    AccentPreset("sea", "Sea", Color(0xFF2E6E9C), Color(0xFFE2EDF6)),
    AccentPreset("forest", "Forest", Color(0xFF3E6A36), Color(0xFFE5EEE1)),
    AccentPreset("lavender", "Lavender", Color(0xFF8768AE), Color(0xFFF0EAF7)),
    AccentPreset("desert", "Desert", Color(0xFFAE6140), Color(0xFFF6E7DF)),
    AccentPreset("graphite", "Graphite", Color(0xFF3E4347), Color(0xFFE8E9EA)),
)
val CornerStyles = listOf("sharp" to "Sharp", "rounded" to "Rounded", "soft" to "Soft")

@Immutable data class HomeStyle(val accent: Color, val soft: Color, val card: Dp, val item: Dp, val pill: Dp)
fun homeStyle(preferences: HomePreferences): HomeStyle {
    val preset = AccentPresets.firstOrNull { it.id == preferences.accent } ?: AccentPresets.first()
    return when (preferences.corners) {
        "sharp" -> HomeStyle(preset.primary, preset.soft, 3.dp, 4.dp, 6.dp)
        "soft" -> HomeStyle(preset.primary, preset.soft, 24.dp, 22.dp, 28.dp)
        else -> HomeStyle(preset.primary, preset.soft, 14.dp, 12.dp, 22.dp)
    }
}
val LocalHomeStyle = staticCompositionLocalOf { homeStyle(HomePreferences()) }

/** One deleted library item, as listed on the Trash screen. kind is "note", "book" or "folder". */
data class TrashItem(val kind: String, val id: String, val title: String, val detail: String, val deletedAt: Long)

object TrashRules {
    const val DAY = 86_400_000L
    val retentionOptions = listOf(7 to "7 days", 30 to "30 days", 0 to "Never auto-delete")
    /** retentionDays <= 0 means "never auto-delete". */
    fun expired(deletedAt: Long, now: Long, retentionDays: Int) = deletedAt > 0 && retentionDays > 0 && now - deletedAt >= retentionDays * DAY
    fun daysLeft(deletedAt: Long, now: Long, retentionDays: Int): Int? =
        if (retentionDays <= 0) null else ((deletedAt + retentionDays * DAY - now + DAY - 1) / DAY).toInt().coerceAtLeast(0)
}

internal val HomeInk = com.allient.folio.FolioBrand.Ink
internal val HomeMuted = com.allient.folio.FolioBrand.Muted
internal val HomeLine = Color(0xFFCBC5B8)
internal val HomeHairline = com.allient.folio.FolioBrand.Hairline
/** Screen and sidebar backgrounds: warm paper tones from the brand kit; cards and pages stay white. */
internal val HomeBackground = com.allient.folio.FolioBrand.Paper
internal val HomeSidebarBackground = com.allient.folio.FolioBrand.Cream
internal val HomeDanger = Color(0xFFB3261E)
