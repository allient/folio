package com.allient.folio.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Centered segmented control; the selected segment is filled with the accent colour. */
@Composable
internal fun SegmentPill(options: List<Pair<String, String>>, selected: String, compact: Boolean, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val style = LocalHomeStyle.current
    Surface(modifier, shape = RoundedCornerShape(style.pill), color = Color.White, shadowElevation = 3.dp, border = BorderStroke(1.dp, HomeHairline)) {
        Row(Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            options.forEach { (id, label) ->
                val on = id == selected
                Box(Modifier.clip(RoundedCornerShape((style.pill - 4.dp).coerceAtLeast(2.dp))).background(if (on) style.accent else Color.Transparent)
                    .clickable { onSelect(id) }.heightIn(min = 40.dp).padding(horizontal = if (compact) 13.dp else 22.dp)
                    .testTag("segment-$id").semantics { contentDescription = label; this.selected = on }, contentAlignment = Alignment.Center) {
                    Text(label, color = if (on) Color.White else HomeInk, fontSize = 14.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
internal fun SectionHeader(key: String, title: String, count: Int, foldable: Boolean, open: Boolean, onToggle: () -> Unit) {
    val style = LocalHomeStyle.current
    Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 2.dp).testTag("section-$key"), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold, color = HomeInk)
        Spacer(Modifier.weight(1f))
        if (foldable) Row(Modifier.clip(RoundedCornerShape(style.item)).clickable(onClick = onToggle).padding(horizontal = 8.dp, vertical = 6.dp).testTag("fold-$key"),
            verticalAlignment = Alignment.CenterVertically) {
            Text(if (open) "Fold" else "Unfold ($count)", color = style.accent, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            if (!open) HomeSymbol(Symbol.ChevronRight, Modifier.padding(start = 2.dp).size(14.dp), tint = style.accent)
        }
    }
}

@Composable
internal fun MenuCaret(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.size(40.dp).clip(CircleShape).clickable(onClick = onClick).semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        HomeSymbol(Symbol.ChevronDown, Modifier.size(15.dp))
    }
}

@Composable
internal fun SelectionMark(selected: Boolean, modifier: Modifier = Modifier) {
    val style = LocalHomeStyle.current
    Box(modifier.size(24.dp).clip(CircleShape).background(if (selected) style.accent else Color.White).border(1.5.dp, if (selected) style.accent else HomeLine, CircleShape),
        contentAlignment = Alignment.Center) { if (selected) HomeSymbol(Symbol.Check, Modifier.size(14.dp), tint = Color.White) }
}

/** One library item as a grid card or a list row. [selected] is null outside selection mode. */
@Composable
internal fun LibraryTile(
    grid: Boolean, title: String, subtitle: String, menuLabel: String, onMenu: () -> Unit, modifier: Modifier,
    selected: Boolean?, highlighted: Boolean = false, subtitleColor: Color = HomeMuted, previewBackground: Color = Color.White,
    preview: @Composable BoxScope.() -> Unit,
) {
    val style = LocalHomeStyle.current
    val marked = highlighted || selected == true
    if (grid) Column(modifier.fillMaxWidth()) {
        val shape = RoundedCornerShape(style.card)
        Box(Modifier.fillMaxWidth().aspectRatio(.75f).clip(shape).border(if (marked) 2.dp else 1.dp, if (marked) style.accent else HomeLine, shape)
            .background(if (highlighted) style.soft else previewBackground), contentAlignment = Alignment.Center) {
            preview()
            if (selected != null) SelectionMark(selected, Modifier.align(Alignment.TopEnd).padding(7.dp))
        }
        Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.Medium, color = HomeInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (selected == null) MenuCaret(menuLabel, onMenu, Modifier.offset(x = 10.dp))
        }
        Text(subtitle, fontSize = 10.sp, lineHeight = 13.sp, color = subtitleColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
    } else {
        val shape = RoundedCornerShape(style.item)
        Row(Modifier.fillMaxWidth().clip(shape).background(if (marked) style.soft else Color(0xFFF8F8F8)).then(modifier).padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            if (selected != null) SelectionMark(selected, Modifier.padding(end = 10.dp))
            Box(Modifier.width(44.dp).aspectRatio(.75f).clip(RoundedCornerShape(style.item / 2)).border(1.dp, HomeLine, RoundedCornerShape(style.item / 2)).background(previewBackground),
                contentAlignment = Alignment.Center) { preview() }
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(title, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium, color = HomeInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, Modifier.padding(top = 2.dp), fontSize = 11.sp, lineHeight = 14.sp, color = subtitleColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (selected == null) MenuCaret(menuLabel, onMenu)
        }
    }
}

/** Small rounded label drawn over a card thumbnail. */
@Composable
internal fun TileBadge(text: String, modifier: Modifier, filled: Boolean = false, tag: String? = null) {
    val style = LocalHomeStyle.current
    Box(modifier.padding(6.dp).clip(RoundedCornerShape(10.dp)).background(if (filled) style.accent else Color.White).border(1.dp, if (filled) style.accent else HomeHairline, RoundedCornerShape(10.dp))
        .padding(horizontal = 7.dp, vertical = 2.dp).then(if (tag != null) Modifier.testTag(tag) else Modifier)) {
        Text(text, fontSize = 9.sp, lineHeight = 12.sp, fontWeight = FontWeight.Medium, color = if (filled) Color.White else HomeInk)
    }
}
