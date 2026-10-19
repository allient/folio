package com.allient.folio

/** Built-in page layouts. IDs are persisted in NotebookPage.template; keep existing IDs stable. */
data class NotebookTemplate(val id: String, val name: String, val category: String)

object NotebookTemplateCatalog {
    val categories = listOf("All", "Recent", "General", "Planning", "Work", "Study", "Creative", "Custom")

    val all = listOf(
        NotebookTemplate("blank", "Blank", "General"),
        NotebookTemplate("lined", "Ruled", "General"),
        NotebookTemplate("wide-ruled", "Wide ruled", "General"),
        NotebookTemplate("grid", "Square grid", "General"),
        NotebookTemplate("big-grid", "Large grid", "General"),
        NotebookTemplate("dots", "Dot grid", "General"),
        NotebookTemplate("graph", "Graph paper", "General"),
        NotebookTemplate("hex-grid", "Hexagonal grid", "General"),
        NotebookTemplate("todo", "To-do list", "Planning"),
        NotebookTemplate("todo-daily", "Daily to-do", "Planning"),
        NotebookTemplate("daily", "Daily planner", "Planning"),
        NotebookTemplate("time-block", "Time blocking", "Planning"),
        NotebookTemplate("weekly", "Weekly planner", "Planning"),
        NotebookTemplate("monthly", "Monthly planner", "Planning"),
        NotebookTemplate("habit", "Habit tracker", "Planning"),
        NotebookTemplate("priority", "Priority matrix", "Planning"),
        NotebookTemplate("meeting", "Meeting notes", "Work"),
        NotebookTemplate("project", "Project planner", "Work"),
        NotebookTemplate("kanban", "Kanban board", "Work"),
        NotebookTemplate("cornell", "Cornell notes", "Study"),
        NotebookTemplate("lecture", "Lecture notes", "Study"),
        NotebookTemplate("reading", "Reading log", "Study"),
        NotebookTemplate("revision", "Revision notes", "Study"),
        NotebookTemplate("vocabulary", "Vocabulary list", "Study"),
        NotebookTemplate("lab", "Lab report", "Study"),
        NotebookTemplate("math-grid", "Math grid", "Study"),
        NotebookTemplate("english", "English practice", "Study"),
        NotebookTemplate("isometric", "Isometric grid", "Creative"),
        NotebookTemplate("music", "Music staff", "Creative"),
        NotebookTemplate("storyboard", "Storyboard", "Creative"),
        NotebookTemplate("comic", "Comic panels", "Creative"),
        NotebookTemplate("mind-map", "Mind map", "Creative"),
        NotebookTemplate("sketch", "Sketch page", "Creative"),
        NotebookTemplate("custom-grid", "Custom grid", "Custom"),
        NotebookTemplate("custom-ruled", "Custom ruled", "Custom"),
        NotebookTemplate("custom-dots", "Custom dots", "Custom"),
    )

    fun inCategory(category: String, recentIds: List<String> = emptyList()): List<NotebookTemplate> = when (category) {
        "All" -> all
        "Recent" -> recentIds.mapNotNull { id -> all.firstOrNull { it.id == id } }.distinctBy { it.id }
        else -> all.filter { it.category == category }
    }
}
