package com.allient.folio

/** Paper settings of an infinite canvas, stored in the page template as `inf-<pattern>:<color>:<spacing>`. */
data class InfinitePaper(val pattern: String = "dots", val color: String = "white", val spacing: Int = 3) {
    val template: String get() = "inf-$pattern:$color:$spacing"
    val background: Int get() = colorArgb(color)
    val dark: Boolean get() = color == "dark"
    /** Grid gap as a fraction of canvas width. Spacing 1..8. */
    val gapFraction: Float get() = (.012f + .006f * spacing.coerceIn(1, 8)) * when (pattern) { "graph" -> .6f; "grid" -> 1.5f; else -> 1f }

    companion object {
        val patterns = listOf("blank" to "Blank paper", "graph" to "Graph paper", "grid" to "Wide grid paper", "lines" to "Line paper", "dots" to "Dot paper", "sheet" to "Sheet")
        val colors = listOf("white" to "White", "beige" to "Beige", "dark" to "Dark gray")
        fun colorArgb(id: String): Int = when (id) { "beige" -> 0xFFFFF4E0.toInt(); "dark" -> 0xFF4A4A4A.toInt(); else -> 0xFFFFFFFF.toInt() }
        fun parse(template: String): InfinitePaper? {
            if (!template.startsWith("inf-")) return null
            val parts = template.removePrefix("inf-").split(':')
            val pattern = parts[0].let { p -> if (patterns.any { it.first == p }) p else if (p == "cross") "dots" else "dots" }
            return InfinitePaper(pattern, parts.getOrNull(1)?.takeIf { c -> colors.any { it.first == c } } ?: "white", parts.getOrNull(2)?.toIntOrNull()?.coerceIn(1, 8) ?: 3)
        }
    }
}
