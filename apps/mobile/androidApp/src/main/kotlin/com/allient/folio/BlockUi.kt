package com.allient.folio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.util.UUID

/** Tables, sticky notes and emoji stickers. Geometry lives in InkBlock; see docs/tables-stickers-notes.md. */
internal data class TableDraft(
    val rows: Int = 3, val columns: Int = 3, val headerRow: Boolean = true, val headerColumn: Boolean = true,
    val borderWidth: Float = 3f, val borderColor: Int = 0xFF8E8E93.toInt(), val fill: Int = 0,
)

internal val TableBorderColors = listOf(0xFF1C1C1E.toInt(), 0xFF8E8E93.toInt(), 0xFFD32F2F.toInt(), 0xFF1976D2.toInt(), 0xFF2E7D32.toInt(), 0xFF7B1FA2.toInt())
internal val TableFillColors = listOf(0, 0xFFFFFFFF.toInt(), 0xFFFFF4C2.toInt(), 0xFFDDEBFF.toInt(), 0xFFDFF5E1.toInt(), 0xFFFFE1E1.toInt())
internal val StickyColors = listOf(0xFFFDDC8C.toInt(), 0xFFA8D8FF.toInt(), 0xFFFFA6D2.toInt(), 0xFFB6EBB0.toInt(), 0xFFD9C7FF.toInt(), 0xFFFFC9A0.toInt())

internal fun equalFractions(count: Int) = List(count.coerceAtLeast(1)) { 1f / count.coerceAtLeast(1) }

/** Grow or shrink a list of fractions (summing to 1) to [count], keeping existing proportions. */
internal fun resizeFractions(old: List<Float>, count: Int): List<Float> {
    val current = old.ifEmpty { listOf(1f) }
    val target = count.coerceIn(1, 20)
    if (target == current.size) return current
    return if (target > current.size) {
        val added = target - current.size
        current.map { it * (target - added) / target } + List(added) { 1f / target }
    } else current.take(target).let { kept -> val sum = kept.sum().coerceAtLeast(.001f); kept.map { it / sum } }
}

internal fun newTable(draft: TableDraft, x: Float, y: Float, width: Float, height: Float, layerId: String) = InkBlock(
    kind = InkBlock.Table, x = x, y = y, width = width, height = height, layerId = layerId, fill = draft.fill,
    rows = equalFractions(draft.rows), columns = equalFractions(draft.columns), headerRow = draft.headerRow, headerColumn = draft.headerColumn,
    borderWidth = draft.borderWidth, borderColor = draft.borderColor,
)

internal fun newSticky(x: Float, y: Float, width: Float, height: Float, layerId: String, color: Int = StickyColors.first()) =
    InkBlock(kind = InkBlock.Sticky, x = x, y = y, width = width, height = height, layerId = layerId, fill = color, textSize = 16f)

/** Render an emoji to a transparent PNG inside the notebook media folder so it behaves like any inserted image. */
internal fun renderStickerFile(context: Context, emoji: String): File {
    val size = 512
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size * .78f; textAlign = Paint.Align.CENTER }
    canvas.drawText(emoji, size / 2f, size / 2f - (paint.ascent() + paint.descent()) / 2f, paint)
    val media = File(context.filesDir, "media").apply { mkdirs() }
    return File(media, "sticker-${UUID.randomUUID()}.png").also { file ->
        try { file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) } } catch (error: Throwable) { file.delete(); throw error } finally { bitmap.recycle() }
    }
}

internal val StickerCategories: List<Pair<String, List<String>>> = listOf(
    "😀" to "😀😃😄😁😆😅😂🤣😊😇🙂😉😍🥰😘😋😎🤓🤔😴🥳😭😡🤯😱🤗🙄😬🤩".chunkedEmoji(),
    "🐶" to "🐶🐱🐭🐹🐰🦊🐻🐼🐨🐯🦁🐮🐷🐸🐵🐔🐧🐦🦉🦄🐝🦋🐢🐙🐬🐳🦒🐾".chunkedEmoji(),
    "🍎" to "🍎🍊🍋🍌🍉🍇🍓🍒🥑🍕🍔🍟🌮🍣🍦🍩🍪🎂☕🍵🥤🍷".chunkedEmoji(),
    "📚" to "📚📖✏️🖊️📝📎📌✂️📏🧮💡🔬🔭🧪🎒🎓🏆🥇⏰📅💻📱🎧🎨🎸🎯".chunkedEmoji(),
    "⭐" to "⭐🌟✨💫🔥💥💯✅❌❗❓💬💭🚀🌈☀️🌙⚡❄️🌸🌻🍀❤️🧡💛💚💙💜🖤👍👏🙌".chunkedEmoji(),
)

/** Splits a string of emoji into grapheme-ish units (a base character plus any variation selectors). */
private fun String.chunkedEmoji(): List<String> {
    val result = mutableListOf<String>(); var i = 0
    while (i < length) {
        var end = i + Character.charCount(codePointAt(i))
        while (end < length && (codePointAt(end) == 0xFE0F || codePointAt(end) == 0x200D)) end += Character.charCount(codePointAt(end))
        result += substring(i, end); i = end
    }
    return result
}


// --- Table structure: insert/delete rows and columns, merge cells. Merges are [firstRow, firstColumn, lastRow, lastColumn]. ---
private fun insertFraction(old: List<Float>, index: Int): List<Float> {
    val current = old.ifEmpty { listOf(1f) }; val n = current.size + 1
    return current.map { it * (n - 1) / n }.toMutableList().also { it.add(index.coerceIn(0, current.size), 1f / n) }
}
private fun deleteFraction(old: List<Float>, index: Int): List<Float> {
    val current = old.ifEmpty { listOf(1f) }
    if (current.size <= 1) return current
    return current.filterIndexed { i, _ -> i != index }.let { kept -> val sum = kept.sum().coerceAtLeast(.001f); kept.map { it / sum } }
}
private fun List<List<Int>>.cleaned() = filter { (r0, c0, r1, c1) -> r1 > r0 || c1 > c0 }

/** Insert a row so it becomes row [index] (0 = above the first row, rows.size = after the last). */
internal fun InkBlock.insertRow(index: Int): InkBlock = copy(rows = insertFraction(rows, index), merges = merges.map { (r0, c0, r1, c1) ->
    when { index <= r0 -> listOf(r0 + 1, c0, r1 + 1, c1); index <= r1 -> listOf(r0, c0, r1 + 1, c1); else -> listOf(r0, c0, r1, c1) } })
internal fun InkBlock.insertColumn(index: Int): InkBlock = copy(columns = insertFraction(columns, index), merges = merges.map { (r0, c0, r1, c1) ->
    when { index <= c0 -> listOf(r0, c0 + 1, r1, c1 + 1); index <= c1 -> listOf(r0, c0, r1, c1 + 1); else -> listOf(r0, c0, r1, c1) } })
internal fun InkBlock.deleteRow(index: Int): InkBlock = if (rows.size <= 1) this else copy(rows = deleteFraction(rows, index), merges = merges.map { (r0, c0, r1, c1) ->
    when { index < r0 -> listOf(r0 - 1, c0, r1 - 1, c1); index <= r1 -> listOf(r0, c0, r1 - 1, c1); else -> listOf(r0, c0, r1, c1) } }.filter { it[2] >= it[0] }.cleaned())
internal fun InkBlock.deleteColumn(index: Int): InkBlock = if (columns.size <= 1) this else copy(columns = deleteFraction(columns, index), merges = merges.map { (r0, c0, r1, c1) ->
    when { index < c0 -> listOf(r0, c0 - 1, r1, c1 - 1); index <= c1 -> listOf(r0, c0, r1, c1 - 1); else -> listOf(r0, c0, r1, c1) } }.filter { it[3] >= it[1] }.cleaned())

/** Grow a cell range until it no longer cuts through a merged range. */
internal fun InkBlock.expandToMerges(range: List<Int>): List<Int> {
    var r0 = minOf(range[0], range[2]); var c0 = minOf(range[1], range[3]); var r1 = maxOf(range[0], range[2]); var c1 = maxOf(range[1], range[3])
    var changed = true
    while (changed) {
        changed = false
        for ((mr0, mc0, mr1, mc1) in merges) if (mr0 <= r1 && mr1 >= r0 && mc0 <= c1 && mc1 >= c0 && (mr0 < r0 || mr1 > r1 || mc0 < c0 || mc1 > c1)) {
            r0 = minOf(r0, mr0); r1 = maxOf(r1, mr1); c0 = minOf(c0, mc0); c1 = maxOf(c1, mc1); changed = true
        }
    }
    return listOf(r0, c0, r1, c1)
}
internal fun InkBlock.mergeRange(range: List<Int>): InkBlock {
    val area = expandToMerges(range)
    if (area[2] == area[0] && area[3] == area[1]) return this
    val kept = merges.filterNot { (r0, c0, r1, c1) -> r0 >= area[0] && r1 <= area[2] && c0 >= area[1] && c1 <= area[3] }
    return copy(merges = kept + listOf(area))
}
internal fun InkBlock.mergeContaining(row: Int, column: Int): List<Int>? = merges.firstOrNull { (r0, c0, r1, c1) -> row in r0..r1 && column in c0..c1 }
internal fun InkBlock.unmerge(row: Int, column: Int): InkBlock = copy(merges = merges.filterNot { it == mergeContaining(row, column) })

/** The cell under a page-normalised point, or null outside the table. */
internal fun InkBlock.cellAt(x: Float, y: Float): Pair<Int, Int>? {
    if (x < this.x || x > this.x + width || y < this.y || y > this.y + height) return null
    fun index(fractions: List<Float>, position: Float): Int {
        val list = fractions.ifEmpty { listOf(1f) }; val sum = list.sum().coerceAtLeast(.001f); var edge = 0f
        list.forEachIndexed { i, f -> edge += f / sum; if (position <= edge + 1e-4f) return i }
        return list.lastIndex
    }
    return index(rows, (y - this.y) / height) to index(columns, (x - this.x) / width)
}

@Composable
private fun ColorDot(color: Int, selected: Boolean, label: String, diameter: Int = 28, onClick: () -> Unit) {
    Box(Modifier.size(diameter.dp).clip(CircleShape).background(if (color == 0) Color.White else Color(color))
        .border(if (selected) 3.dp else 1.5.dp, if (selected) Color(0xFF1976D2) else Color(0xFF9A9AA0), CircleShape)
        .clickable(onClick = onClick).semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        if (color == 0) Canvas(Modifier.fillMaxSize()) { drawLine(Color(0xFFD32F2F), Offset(size.width * .2f, size.height * .8f), Offset(size.width * .8f, size.height * .2f), 3f, StrokeCap.Round) }
    }
}

@Composable
internal fun ColorMenuDot(color: Int, label: String, options: List<Int>, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        ColorDot(color, false, label) { open = true }
        DropdownMenu(open, { open = false }) {
            Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { option -> ColorDot(option, option == color, "$label ${option.toUInt().toString(16)}") { onPick(option); open = false } }
            }
        }
    }
}

@Composable
private fun Stepper(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontSize = 14.sp)
        FilledTonalIconButton({ onChange((value - 1).coerceIn(range)) }, Modifier.size(36.dp).semantics { contentDescription = "Fewer $label" }) { Text("−", fontSize = 18.sp) }
        Text("$value", Modifier.width(44.dp), fontSize = 15.sp, fontWeight = FontWeight.Medium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        FilledTonalIconButton({ onChange((value + 1).coerceIn(range)) }, Modifier.size(36.dp).semantics { contentDescription = "More $label" }) { Text("+", fontSize = 18.sp) }
    }
}

@Composable
internal fun TableSheet(draft: TableDraft, onDraft: (TableDraft) -> Unit, onInsert: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Canvas(Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFF4F4F6)).testTag("table-preview")) {
            val block = newTable(draft, 0f, 0f, 1f, 1f, "ink")
            val inset = 14.dp.toPx(); val w = size.width - inset * 2; val h = size.height - inset * 2
            val xs = block.columns.runningFold(0f) { a, f -> a + f }; val ys = block.rows.runningFold(0f) { a, f -> a + f }
            val header = Color(block.headerFill)
            if (block.fill != 0) drawRect(Color(block.fill), Offset(inset, inset), androidx.compose.ui.geometry.Size(w, h))
            if (block.headerRow) drawRect(header, Offset(inset, inset), androidx.compose.ui.geometry.Size(w, h * ys[1]))
            if (block.headerColumn) drawRect(header, Offset(inset, inset), androidx.compose.ui.geometry.Size(w * xs[1], h))
            val stroke = (block.borderWidth * .7f).dp.toPx().coerceAtLeast(1f); val line = Color(block.borderColor)
            ys.forEach { drawLine(line, Offset(inset, inset + h * it), Offset(inset + w, inset + h * it), stroke) }
            xs.forEach { drawLine(line, Offset(inset + w * it, inset), Offset(inset + w * it, inset + h), stroke) }
        }
        ToolToggle("Header column", "Shade the first column", draft.headerColumn) { onDraft(draft.copy(headerColumn = it)) }
        ToolToggle("Header row", "Shade the first row", draft.headerRow) { onDraft(draft.copy(headerRow = it)) }
        Stepper("Columns", draft.columns, 1..12) { onDraft(draft.copy(columns = it)) }
        Stepper("Rows", draft.rows, 1..20) { onDraft(draft.copy(rows = it)) }
        HorizontalDivider()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Border width", Modifier.weight(1f), fontSize = 14.sp); Text("${draft.borderWidth.toInt()}", fontSize = 15.sp, fontWeight = FontWeight.Medium)
        }
        Slider(draft.borderWidth, { onDraft(draft.copy(borderWidth = it.toInt().toFloat())) }, valueRange = 0f..8f, steps = 7, modifier = Modifier.semantics { contentDescription = "Border width" })
        Row(verticalAlignment = Alignment.CenterVertically) { Text("Border color", Modifier.weight(1f), fontSize = 14.sp); ColorMenuDot(draft.borderColor, "Border color", TableBorderColors) { onDraft(draft.copy(borderColor = it)) } }
        Row(verticalAlignment = Alignment.CenterVertically) { Text("Fill color", Modifier.weight(1f), fontSize = 14.sp); ColorMenuDot(draft.fill, "Fill color", TableFillColors) { onDraft(draft.copy(fill = it)) } }
        Button(onInsert, Modifier.fillMaxWidth().testTag("insert-table"), shape = RoundedCornerShape(14.dp)) { Text("Insert table") }
    }
}

@Composable
internal fun StickerSheet(onPick: (String) -> Unit) {
    var category by remember { mutableIntStateOf(0) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StickerCategories.forEachIndexed { index, (icon, _) ->
                Surface(onClick = { category = index }, shape = RoundedCornerShape(20.dp), color = if (category == index) Color(0xFFE5EFEC) else Color.White,
                    border = BorderStroke(if (category == index) 2.dp else 1.dp, if (category == index) Color(0xFF357568) else Color(0xFFCCCCCC)),
                    modifier = Modifier.semantics { contentDescription = "Sticker category ${index + 1}" }) { Text(icon, Modifier.padding(horizontal = 16.dp, vertical = 6.dp), fontSize = 24.sp) }
            }
        }
        StickerCategories[category].second.chunked(6).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { emoji -> Box(Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(10.dp)).clickable { onPick(emoji) }.semantics { contentDescription = "Sticker $emoji" }, contentAlignment = Alignment.Center) { Text(emoji, fontSize = 30.sp) } }
                repeat(6 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun ToolChip(label: String, selected: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(16.dp), color = if (selected) Color(0xFFDCEBFF) else Color(0xFFF2F2F5),
        border = BorderStroke(1.dp, if (selected) Color(0xFF1976D2) else Color.Transparent)) {
        Text(label, Modifier.padding(horizontal = 12.dp, vertical = 7.dp), fontSize = 12.sp, color = if (enabled) Color(0xFF222226) else Color(0xFFAAAAAA))
    }
}

/** Floating toolbar for the selected table or sticky note. */
@Composable
internal fun BlockToolbar(block: InkBlock, cells: List<Int>?, onChange: (InkBlock) -> Unit, onCells: (List<Int>?) -> Unit, onEditText: () -> Unit, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(26.dp), shadowElevation = 4.dp, color = Color.White) {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (block.kind == InkBlock.Sticky) {
                StickyColors.forEach { color -> ColorDot(color, block.fill == color, "Note color ${color.toUInt().toString(16)}") { onChange(block.copy(fill = color)) } }
                ToolChip("Edit text", onClick = onEditText)
                ToolChip("A−") { onChange(block.copy(textSize = (block.textSize - 2f).coerceAtLeast(8f))) }
                Text("${block.textSize.toInt()}", fontSize = 12.sp)
                ToolChip("A+") { onChange(block.copy(textSize = (block.textSize + 2f).coerceAtMost(48f))) }
            } else if (cells != null && cells.size == 4) {
                val first = cells[0]; val last = cells[2]; val left = cells[1]; val right = cells[3]
                val single = first == last && left == right
                ToolChip("Row above") { onChange(block.insertRow(first)); onCells(listOf(first + 1, left, first + 1, right)) }
                ToolChip("Row below") { onChange(block.insertRow(last + 1)); onCells(null) }
                ToolChip("Column left") { onChange(block.insertColumn(left)); onCells(listOf(first, left + 1, last, left + 1)) }
                ToolChip("Column right") { onChange(block.insertColumn(right + 1)); onCells(null) }
                ToolChip("Delete row", enabled = block.rows.size > 1) { var next = block; for (r in last downTo first) next = next.deleteRow(r); onChange(next); onCells(null) }
                ToolChip("Delete column", enabled = block.columns.size > 1) { var next = block; for (c in right downTo left) next = next.deleteColumn(c); onChange(next); onCells(null) }
                ToolChip("Merge", enabled = !single) { onChange(block.mergeRange(cells)); onCells(block.expandToMerges(cells)) }
                ToolChip("Unmerge", enabled = block.mergeContaining(first, left) != null) { onChange(block.unmerge(first, left)) }
                ToolChip("Done") { onCells(null) }
            } else {
                Text("Tap a cell to edit rows, columns and merges", fontSize = 11.sp, color = Color(0xFF666670))
                ToolChip("Header row", block.headerRow) { onChange(block.copy(headerRow = !block.headerRow)) }
                ToolChip("Header column", block.headerColumn) { onChange(block.copy(headerColumn = !block.headerColumn)) }
                ToolChip("Rows −", enabled = block.rows.size > 1) { onChange(block.copy(rows = resizeFractions(block.rows, block.rows.size - 1))) }
                Text("${block.rows.size.coerceAtLeast(1)}", fontSize = 12.sp)
                ToolChip("Rows +") { onChange(block.copy(rows = resizeFractions(block.rows, block.rows.size + 1))) }
                ToolChip("Columns −", enabled = block.columns.size > 1) { onChange(block.copy(columns = resizeFractions(block.columns, block.columns.size - 1))) }
                Text("${block.columns.size.coerceAtLeast(1)}", fontSize = 12.sp)
                ToolChip("Columns +") { onChange(block.copy(columns = resizeFractions(block.columns, block.columns.size + 1))) }
                ToolChip("Equalize") { onChange(block.copy(rows = equalFractions(block.rows.size), columns = equalFractions(block.columns.size))) }
                ToolChip("Border −") { onChange(block.copy(borderWidth = (block.borderWidth - 1f).coerceAtLeast(0f))) }
                Text("${block.borderWidth.toInt()}", fontSize = 12.sp)
                ToolChip("Border +") { onChange(block.copy(borderWidth = (block.borderWidth + 1f).coerceAtMost(8f))) }
                ColorMenuDot(block.borderColor, "Border color", TableBorderColors) { onChange(block.copy(borderColor = it)) }
                ColorMenuDot(block.fill, "Fill color", TableFillColors) { onChange(block.copy(fill = it)) }
            }
            ToolChip("Delete", onClick = onDelete)
        }
    }
}

@Composable
internal fun StickyTextDialog(initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Sticky note") },
        text = { OutlinedTextField(value, { value = it }, Modifier.fillMaxWidth().testTag("sticky-text"), minLines = 3, maxLines = 8, placeholder = { Text("Write a note") }) },
        confirmButton = { TextButton({ onSave(value) }) { Text("Save") } }, dismissButton = { TextButton(onDismiss) { Text("Cancel") } })
}
