package com.allient.folio

import androidx.compose.runtime.Composable
import knotes.shared.generated.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

// Display names for the built-in template catalog and infinite paper. The IDs (and the English names kept next to
// them) are stored and compared; only these labels are translated. Unknown IDs fall back to the stored English name.

internal fun templateNameRes(id: String): StringResource? = when (id) {
    "blank" -> Res.string.template_blank; "lined" -> Res.string.template_lined; "wide-ruled" -> Res.string.template_wide_ruled
    "grid" -> Res.string.template_grid; "big-grid" -> Res.string.template_big_grid; "dots" -> Res.string.template_dots
    "graph" -> Res.string.template_graph; "hex-grid" -> Res.string.template_hex_grid; "todo" -> Res.string.template_todo
    "todo-daily" -> Res.string.template_todo_daily; "daily" -> Res.string.template_daily; "time-block" -> Res.string.template_time_block
    "weekly" -> Res.string.template_weekly; "monthly" -> Res.string.template_monthly; "habit" -> Res.string.template_habit
    "priority" -> Res.string.template_priority; "meeting" -> Res.string.template_meeting; "project" -> Res.string.template_project
    "kanban" -> Res.string.template_kanban; "cornell" -> Res.string.template_cornell; "lecture" -> Res.string.template_lecture
    "reading" -> Res.string.template_reading; "revision" -> Res.string.template_revision; "vocabulary" -> Res.string.template_vocabulary
    "lab" -> Res.string.template_lab; "math-grid" -> Res.string.template_math_grid; "english" -> Res.string.template_english
    "isometric" -> Res.string.template_isometric; "music" -> Res.string.template_music; "storyboard" -> Res.string.template_storyboard
    "comic" -> Res.string.template_comic; "mind-map" -> Res.string.template_mind_map; "sketch" -> Res.string.template_sketch
    "custom-grid" -> Res.string.template_custom_grid; "custom-ruled" -> Res.string.template_custom_ruled; "custom-dots" -> Res.string.template_custom_dots
    else -> null
}

internal fun templateCategoryRes(category: String): StringResource? = when (category) {
    "All" -> Res.string.template_category_all; "Recent" -> Res.string.template_category_recent; "General" -> Res.string.template_category_general
    "Planning" -> Res.string.template_category_planning; "Work" -> Res.string.template_category_work; "Study" -> Res.string.template_category_study
    "Creative" -> Res.string.template_category_creative; "Custom" -> Res.string.template_category_custom
    else -> null
}

/** The heading printed at the top of a template preview, or null for templates without one. */
internal fun templateHeadingRes(id: String): StringResource? = when (id) {
    "todo" -> Res.string.template_heading_todo; "meeting" -> Res.string.template_heading_meeting; "daily" -> Res.string.template_heading_daily
    "weekly" -> Res.string.template_heading_weekly; "project" -> Res.string.template_heading_project; "cornell" -> Res.string.template_heading_cornell
    "lecture" -> Res.string.template_heading_lecture; "reading" -> Res.string.template_heading_reading; "time-block" -> Res.string.template_heading_time_block
    "monthly" -> Res.string.template_heading_monthly; "habit" -> Res.string.template_heading_habit; "priority" -> Res.string.template_heading_priority
    "kanban" -> Res.string.template_heading_kanban; "revision" -> Res.string.template_heading_revision; "vocabulary" -> Res.string.template_heading_vocabulary
    "lab" -> Res.string.template_heading_lab; "comic" -> Res.string.template_heading_comic; "mind-map" -> Res.string.template_heading_mind_map
    "sketch" -> Res.string.template_heading_sketch
    else -> null
}

internal fun infinitePaperPatternRes(id: String): StringResource? = when (id) {
    "blank" -> Res.string.template_paper_blank; "graph" -> Res.string.template_paper_graph; "grid" -> Res.string.template_paper_grid
    "lines" -> Res.string.template_paper_lines; "dots" -> Res.string.template_paper_dots; "sheet" -> Res.string.template_paper_sheet
    else -> null
}

internal fun infinitePaperColorRes(id: String): StringResource? = when (id) {
    "white" -> Res.string.template_paper_white; "beige" -> Res.string.template_paper_beige; "dark" -> Res.string.template_paper_dark
    else -> null
}

@Composable
private fun label(res: StringResource?, fallback: String) = res?.let { stringResource(it) } ?: fallback

/** Translated name of a built-in page template. */
@Composable
fun templateName(template: NotebookTemplate): String = label(templateNameRes(template.id), template.name)

/** Translated name of a template by ID, or [fallback] for an unknown ID. */
@Composable
fun templateName(id: String, fallback: String = id): String = label(templateNameRes(id), fallback)

/** Translated name of a [NotebookTemplateCatalog.categories] entry. */
@Composable
fun templateCategoryName(category: String): String = label(templateCategoryRes(category), category)

@Composable
fun templateHeading(id: String): String = templateHeadingRes(id)?.let { stringResource(it) }.orEmpty()

/** Translated name of an [InfinitePaper.patterns] ID. */
@Composable
fun infinitePaperPatternName(id: String): String = label(infinitePaperPatternRes(id), InfinitePaper.patterns.firstOrNull { it.first == id }?.second ?: id)

/** Translated name of an [InfinitePaper.colors] ID. */
@Composable
fun infinitePaperColorName(id: String): String = label(infinitePaperColorRes(id), InfinitePaper.colors.firstOrNull { it.first == id }?.second ?: id)
