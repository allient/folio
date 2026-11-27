package com.allient.folio.home

import androidx.compose.runtime.Composable
import knotes.shared.generated.resources.*
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

// Labels for library preference values. The IDs ("sage", "rounded", "edited", "classic"…) are what Folio stores;
// only these display names are translated.

internal fun accentNameRes(id: String): StringResource? = when (id) {
    "sage" -> Res.string.accent_sage; "morning" -> Res.string.accent_morning; "evening" -> Res.string.accent_evening
    "sea" -> Res.string.accent_sea; "forest" -> Res.string.accent_forest; "lavender" -> Res.string.accent_lavender
    "desert" -> Res.string.accent_desert; "graphite" -> Res.string.accent_graphite
    else -> null
}

internal fun cornerStyleRes(id: String): StringResource? = when (id) {
    "sharp" -> Res.string.prefs_corners_sharp; "rounded" -> Res.string.prefs_corners_rounded; "soft" -> Res.string.prefs_corners_soft
    else -> null
}

internal fun sortLabelRes(id: String): StringResource? = when (id) {
    HomeSort.Edited -> Res.string.sort_edited; HomeSort.Created -> Res.string.sort_created
    HomeSort.Type -> Res.string.sort_type; HomeSort.Name -> Res.string.sort_name
    else -> null
}

internal fun folderStyleRes(id: String): StringResource? = when (id) {
    "classic" -> Res.string.folder_style_classic; "papers" -> Res.string.folder_style_papers; "tabbed" -> Res.string.folder_style_tabbed
    "open" -> Res.string.folder_style_open; "mosaic" -> Res.string.folder_style_mosaic
    else -> null
}

internal fun folderColorRes(id: String): StringResource? = when (id) {
    "sage" -> Res.string.folder_color_sage; "blue" -> Res.string.folder_color_blue; "lavender" -> Res.string.folder_color_lavender
    "rose" -> Res.string.folder_color_rose; "peach" -> Res.string.folder_color_peach; "sand" -> Res.string.folder_color_sand
    else -> null
}

/** "3 notebooks", "1 canvas"… for the library tab being shown ("notes", "canvas", "books" or "all"). */
internal fun libraryCountPlural(segment: String): PluralStringResource = when (segment) {
    "notes" -> Res.plurals.home_count_notebooks
    "canvas" -> Res.plurals.home_count_canvases
    "books" -> Res.plurals.home_count_books
    else -> Res.plurals.home_count_items
}

@Composable
private fun label(res: StringResource?, fallback: String) = res?.let { stringResource(it) } ?: fallback

@Composable internal fun accentName(preset: AccentPreset): String = label(accentNameRes(preset.id), preset.name)
@Composable internal fun cornerStyleName(id: String): String = label(cornerStyleRes(id), CornerStyles.firstOrNull { it.first == id }?.second ?: id)
@Composable internal fun sortName(id: String): String = label(sortLabelRes(id), HomeSort.all.firstOrNull { it.first == id }?.second ?: id)
@Composable internal fun folderStyleName(id: String): String = label(folderStyleRes(id), id)
@Composable internal fun folderColorName(id: String): String = label(folderColorRes(id), id.replaceFirstChar { it.uppercase() })

/** A [TrashRules.retentionOptions] choice: "7 days", "30 days" or "Never auto-delete". */
@Composable
internal fun retentionName(days: Int): String =
    if (days > 0) pluralStringResource(Res.plurals.prefs_retention_days, days, days) else stringResource(Res.string.prefs_retention_never)
