package com.example.knotes.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp

/** Preview for the additional built-in planner, study, and creative paper layouts. */
@Composable
fun PaperTemplatePreview(template: String, modifier: Modifier = Modifier) {
    val text = rememberTextMeasurer()
    val title = remember(template) {
        when (template) {
            "daily" -> "DAILY PLANNER"; "time-block" -> "TIME BLOCKS"; "weekly" -> "WEEKLY PLANNER"
            "monthly" -> "MONTHLY PLANNER"; "habit" -> "HABIT TRACKER"; "priority" -> "PRIORITY MATRIX"
            "kanban" -> "KANBAN BOARD"; "revision" -> "REVISION NOTES"; "vocabulary" -> "VOCABULARY"
            "lab" -> "LAB REPORT"; "comic" -> "COMIC PANELS"; "mind-map" -> "MIND MAP"
            "sketch" -> "SKETCH PAGE"
            else -> ""
        }
    }
    Canvas(modifier.background(Color.White)) {
        val ink = Color(0xFFB6BDBA)
        val left = size.width * .07f; val right = size.width * .93f
        val top = size.height * .14f; val bottom = size.height * .94f
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(ink, Offset(x1, y1), Offset(x2, y2), 1f)
        fun box(l: Float, t: Float, r: Float, b: Float) = drawRect(ink, Offset(l, t), Size(r-l, b-t), style = Stroke(1f))
        if (title.isNotEmpty()) drawText(text, title, Offset(left, size.height * .035f), style = TextStyle(color = Color(0xFF52595B), fontSize = (size.width*.065f).sp, fontWeight = FontWeight.Medium))
        when (template) {
            "daily" -> {
                box(left, top, right, size.height*.23f); box(left, size.height*.25f, size.width*.59f, size.height*.49f)
                box(size.width*.62f, size.height*.25f, right, size.height*.49f); box(left, size.height*.51f, size.width*.59f, size.height*.73f)
                box(size.width*.62f, size.height*.51f, right, size.height*.73f); box(left, size.height*.75f, right, bottom)
                for (i in 0..4) { val y=size.height*(.30f+i*.042f); line(left+size.width*.06f,y,size.width*.56f,y); line(size.width*.66f,y,right-size.width*.02f,y) }
                for (i in 0..3) line(left,size.height*(.79f+i*.04f),right,size.height*(.79f+i*.04f))
            }
            "time-block" -> for (i in 0..13) { val y=top+(bottom-top)*i/13; line(left+size.width*.12f,y,right,y) }
            "weekly", "monthly" -> {
                val cols = if (template == "weekly") 5 else 7
                for (i in 0..cols) line(left+(right-left)*i/cols,top,left+(right-left)*i/cols,bottom)
                for (i in 0..5) line(left,top+(bottom-top)*i/5,right,top+(bottom-top)*i/5)
            }
            "habit" -> { val split=size.width*.4f; line(split,top,split,bottom); for(i in 0..7) line(split+(right-split)*i/7,top,split+(right-split)*i/7,bottom); for(i in 0..12) line(left,top+(bottom-top)*i/12,right,top+(bottom-top)*i/12) }
            "priority" -> { box(left,top,size.width*.49f,size.height*.52f); box(size.width*.51f,top,right,size.height*.52f); box(left,size.height*.56f,size.width*.49f,bottom); box(size.width*.51f,size.height*.56f,right,bottom) }
            "kanban" -> { for(i in 0..3) line(left+(right-left)*i/3,top,left+(right-left)*i/3,bottom); for(col in 0..2) for(row in 0..3) { val x=left+col*(right-left)/3+size.width*.01f; val y=top+size.height*.07f+row*size.height*.18f; box(x,y,x+(right-left)/3-size.width*.02f,y+size.height*.13f) } }
            "revision" -> { box(left,top,right,size.height*.28f); box(left,size.height*.31f,size.width*.49f,size.height*.59f); box(size.width*.51f,size.height*.31f,right,size.height*.59f); box(left,size.height*.62f,right,bottom); for(i in 0..4) line(left,size.height*(.67f+i*.05f),right,size.height*(.67f+i*.05f)) }
            "vocabulary" -> { line(size.width*.34f,top,size.width*.34f,bottom); line(size.width*.68f,top,size.width*.68f,bottom); for(i in 0..12) line(left,top+(bottom-top)*i/12,right,top+(bottom-top)*i/12) }
            "lab" -> { var y=top; val heights=listOf(.11f,.13f,.12f,.18f,.22f,.12f); for(h in heights) { y+=size.height*h; line(left,y,right,y) } }
            "comic", "storyboard" -> for(row in 0..2) for(col in 0..1) { val x=left+col*size.width*.44f; val y=size.height*.13f+row*size.height*.26f; box(x,y,x+size.width*.4f,y+size.height*.22f) }
            "mind-map" -> { val cx=size.width*.5f; val cy=size.height*.54f; for((x,y) in listOf(.2f to .23f,.5f to .16f,.8f to .23f,.2f to .82f,.5f to .88f,.8f to .82f)) { val px=size.width*x; val py=size.height*y; line(cx,cy,px,py); drawCircle(ink,size.width*.06f,Offset(px,py),style=Stroke(1f)) }; drawCircle(ink,size.width*.1f,Offset(cx,cy),style=Stroke(1f)) }
            "sketch" -> box(left,size.height*.08f,right,size.height*.92f)
            "graph" -> { val step=size.width/12; var y=0f; while(y<size.height) { line(0f,y,size.width,y); y+=step }; var x=0f; while(x<size.width) { line(x,0f,x,size.height); x+=step } }
            "hex-grid" -> {
                val radius = size.width / 22f
                val horizontal = kotlin.math.sqrt(3f) * radius
                val vertical = 1.5f * radius
                var row = 0
                var cy = -radius
                while (cy < size.height + radius) {
                    var cx = if (row % 2 == 0) 0f else horizontal / 2f
                    while (cx < size.width + horizontal) {
                        val points = (0..5).map { side ->
                            val angle = (side * 60f - 90f) * kotlin.math.PI.toFloat() / 180f
                            Offset(cx + radius * kotlin.math.cos(angle), cy + radius * kotlin.math.sin(angle))
                        }
                        for (side in 0..5) line(points[side].x, points[side].y, points[(side + 1) % 6].x, points[(side + 1) % 6].y)
                        cx += horizontal
                    }
                    cy += vertical; row++
                }
            }
            else -> { val gap=size.width/18; var y=top; while(y<bottom) { line(left,y,right,y); y+=gap } }
        }
    }
}
