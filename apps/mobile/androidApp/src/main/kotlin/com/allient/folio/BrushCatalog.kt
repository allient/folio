package com.allient.folio

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalResources

/** Sizes are canvas pixels at 100% (600px document width), never device pixels or DPI. */
internal object BrushCatalog {
    val presets = listOf(
        BrushStyle("Pencil", 2f, 0xFF55514C.toInt(), label = "2H · Technical", variant = "2h", texture = .75f, sensitivity = .35f),
        BrushStyle("Pencil", 3f, 0xFF4B4742.toInt(), label = "HB · Everyday", variant = "hb", texture = .72f),
        BrushStyle("Pencil", 5f, 0xFF37332F.toInt(), label = "2B · Sketch", variant = "2b", texture = .58f, sensitivity = .6f),
        BrushStyle("Pencil", 9f, 0xFF292623.toInt(), label = "4B · Soft", variant = "4b", texture = .48f, grain = 1.2f, sensitivity = .65f),
        BrushStyle("Pencil", 16f, 0xFF25221F.toInt(), label = "6B · Shading", variant = "6b", texture = .4f, grain = 1.4f, sensitivity = .7f),
        BrushStyle("Pencil", 24f, 0xFF3C3732.toInt(), label = "Coarse graphite", variant = "coarse", texture = .9f, grain = 2.2f),
        BrushStyle("Pen", 2f, 0xFF232927.toInt(), label = "Ballpoint · Fine", variant = "ballpoint"),
        BrushStyle("Pen", 3.5f, 0xFF232927.toInt(), label = "Ballpoint · Medium", variant = "medium"),
        BrushStyle("Pen", 5f, 0xFF232927.toInt(), label = "Ballpoint · Bold", variant = "bold"),
        BrushStyle("Gel pen", 3f, 0xFF192A45.toInt(), label = "Gel · Smooth", variant = "gel"),
        BrushStyle("Gel pen", 4.5f, 0xFF192A45.toInt(), label = "Gel · Bold", variant = "bold"),
        BrushStyle("Fineliner", 1f, 0xFF252525.toInt(), label = "Fineliner · 1 px", variant = "fine"),
        BrushStyle("Fineliner", 2f, 0xFF252525.toInt(), label = "Fineliner · 2 px", variant = "medium"),
        BrushStyle("Fountain pen", 3f, 0xFF242629.toInt(), label = "Fountain · Fine", angle = -35f, variant = "fine"),
        BrushStyle("Fountain pen", 6f, 0xFF242629.toInt(), label = "Fountain · Broad", angle = -35f, variant = "broad"),
        BrushStyle("Calligraphy", 8f, 0xFF252525.toInt(), label = "Italic · 35°", angle = -35f, variant = "italic"),
        BrushStyle("Calligraphy", 14f, 0xFF252525.toInt(), label = "Broad edge · 45°", angle = -45f, variant = "broad"),
        BrushStyle("Brush pen", 8f, 0xFF252525.toInt(), label = "Brush · Lettering", variant = "lettering", sensitivity = .65f),
        BrushStyle("Brush pen", 12f, 0xFF252525.toInt(), label = "Brush · Bold", variant = "bold", sensitivity = .7f),
        BrushStyle("Marker", 10f, 0xCC345C91.toInt(), label = "Marker · Fine", variant = "fine"),
        BrushStyle("Marker", 22f, 0xAA345C91.toInt(), label = "Marker · Broad", variant = "broad"),
        BrushStyle("Highlighter", 20f, 0x55EAC243, label = "Highlighter · Text", variant = "text"),
        BrushStyle("Highlighter", 32f, 0x55EAC243, label = "Highlighter · Wide", variant = "wide"),
        BrushStyle("Chalk", 14f, 0xFF64605A.toInt(), label = "Chalk · Soft", texture = .85f, grain = 2f, variant = "soft"),
        BrushStyle("Chalk", 28f, 0xFF64605A.toInt(), label = "Chalk · Block", texture = .95f, grain = 3f, variant = "block"),
        BrushStyle("Paint brush", 10f, 0xFF2F5D8A.toInt(), label = "Round · Wet", texture = .15f, grain = 1f, variant = "round", sensitivity = .6f),
        BrushStyle("Paint brush", 18f, 0xFF8A4B2F.toInt(), label = "Dry brush", texture = .75f, grain = 1.6f, variant = "dry", sensitivity = .6f),
        BrushStyle("Spray", 24f, 0xAA345C91.toInt(), label = "Spray · Fine mist", texture = .7f, grain = .7f, variant = "mist"),
        BrushStyle("Spray", 36f, 0xAA345C91.toInt(), label = "Spray · Coarse", texture = .8f, grain = 1.7f, variant = "coarse"),
    )
    /** Usable width range per brush, in canvas px. Pressure brushes can draw up to ~2x this at full pressure. */
    fun widthRange(name: String): ClosedFloatingPointRange<Float> = when (name) {
        "Pen" -> .5f..6f
        "Gel pen" -> 1f..6f
        "Fineliner" -> .3f..4f
        "Fountain pen" -> 1f..10f
        "Calligraphy" -> 3f..24f
        "Brush pen" -> 2f..16f
        "Marker" -> 4f..30f
        "Highlighter" -> 10f..40f
        "Pencil" -> 1f..24f
        "Chalk" -> 6f..40f
        "Spray" -> 10f..48f
        "Paint brush" -> 3f..40f
        else -> .5f..64f
    }
    fun widthStep(name: String): Float = widthRange(name).let { if (it.endInclusive <= 10f) .1f else if (it.endInclusive <= 24f) .5f else 1f }
    /** Marker-family tips keep a constant width; their pressure setting has no effect. */
    fun pressureResponsive(name: String) = name !in setOf("Fineliner", "Marker", "Highlighter", "Gel pen")
    /** Brushes whose nib or grain uses the angle setting. */
    fun angled(name: String) = name in setOf("Calligraphy", "Fountain pen", "Pencil", "Chalk")
    fun clampWidth(style: BrushStyle) = style.copy(width = style.width.coerceIn(widthRange(style.name)))
    fun forType(name: String) = presets.filter { it.name == name }
    fun default(name: String) = forType(name).let { options ->
        if (name == "Pencil") options.first { it.variant == "hb" } else options.firstOrNull()
    } ?: brushes.first { it.name == name }
    fun reset(style: BrushStyle): BrushStyle = (presets.firstOrNull { it.name == style.name && it.variant == style.variant } ?: default(style.name))
        .copy(presetId = style.presetId)

    /** Translated name of a brush type. [name] is the stored id ("Fountain pen") and is never translated itself. */
    @StringRes fun typeLabel(name: String): Int? = when (name) {
        "Pen" -> R.string.brush_type_pen
        "Pencil" -> R.string.brush_type_pencil
        "Marker" -> R.string.brush_type_marker
        "Fountain pen" -> R.string.brush_type_fountain_pen
        "Brush pen" -> R.string.brush_type_brush_pen
        "Fineliner" -> R.string.brush_type_fineliner
        "Highlighter" -> R.string.brush_type_highlighter
        "Gel pen" -> R.string.brush_type_gel_pen
        "Calligraphy" -> R.string.brush_type_calligraphy
        "Spray" -> R.string.brush_type_spray
        "Chalk" -> R.string.brush_type_chalk
        "Paint brush" -> R.string.brush_type_paint_brush
        "Eraser" -> R.string.brush_type_eraser
        else -> null
    }

    /** Catalog labels are saved in English inside brush presets; they are shown through these resources. */
    private val presetLabels = mapOf(
        "2H · Technical" to R.string.brush_preset_pencil_2h, "HB · Everyday" to R.string.brush_preset_pencil_hb,
        "2B · Sketch" to R.string.brush_preset_pencil_2b, "4B · Soft" to R.string.brush_preset_pencil_4b,
        "6B · Shading" to R.string.brush_preset_pencil_6b, "Coarse graphite" to R.string.brush_preset_pencil_coarse,
        "Ballpoint · Fine" to R.string.brush_preset_pen_fine, "Ballpoint · Medium" to R.string.brush_preset_pen_medium,
        "Ballpoint · Bold" to R.string.brush_preset_pen_bold, "Gel · Smooth" to R.string.brush_preset_gel_smooth,
        "Gel · Bold" to R.string.brush_preset_gel_bold, "Fineliner · 1 px" to R.string.brush_preset_fineliner_1,
        "Fineliner · 2 px" to R.string.brush_preset_fineliner_2, "Fountain · Fine" to R.string.brush_preset_fountain_fine,
        "Fountain · Broad" to R.string.brush_preset_fountain_broad, "Italic · 35°" to R.string.brush_preset_calligraphy_italic,
        "Broad edge · 45°" to R.string.brush_preset_calligraphy_broad, "Brush · Lettering" to R.string.brush_preset_brush_pen_lettering,
        "Brush · Bold" to R.string.brush_preset_brush_pen_bold, "Marker · Fine" to R.string.brush_preset_marker_fine,
        "Marker · Broad" to R.string.brush_preset_marker_broad, "Highlighter · Text" to R.string.brush_preset_highlighter_text,
        "Highlighter · Wide" to R.string.brush_preset_highlighter_wide, "Chalk · Soft" to R.string.brush_preset_chalk_soft,
        "Chalk · Block" to R.string.brush_preset_chalk_block, "Round · Wet" to R.string.brush_preset_paint_round,
        "Dry brush" to R.string.brush_preset_paint_dry, "Spray · Fine mist" to R.string.brush_preset_spray_mist,
        "Spray · Coarse" to R.string.brush_preset_spray_coarse,
    )
    private val numbered = Regex("^(.+) (\\d+)$")

    /**
     * What a brush is called on screen. Catalog labels, type names and the generated "Pen 2" labels are stored in English
     * and translated here; a label the user typed is shown as saved.
     */
    fun displayLabel(resources: Resources, brush: BrushStyle): String = displayLabel(resources, brush.label)

    fun displayLabel(resources: Resources, label: String): String {
        presetLabels[label]?.let { return resources.getString(it) }
        typeLabel(label)?.let { return resources.getString(it) }
        numbered.matchEntire(label)?.let { match ->
            val type = typeLabel(match.groupValues[1])
            val number = match.groupValues[2].toIntOrNull()
            if (type != null && number != null) return resources.getString(R.string.brush_numbered, resources.getString(type), number)
        }
        return label
    }

    fun displayType(resources: Resources, name: String): String = typeLabel(name)?.let { resources.getString(it) } ?: name
}

/** [BrushCatalog.displayLabel] for composables. */
@Composable
internal fun brushLabel(brush: BrushStyle): String {
    return BrushCatalog.displayLabel(LocalResources.current, brush)
}

/** [BrushCatalog.displayType] for composables. */
@Composable
internal fun brushTypeLabel(name: String): String {
    return BrushCatalog.displayType(LocalResources.current, name)
}
