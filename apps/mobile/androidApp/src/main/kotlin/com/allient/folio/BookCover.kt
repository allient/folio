package com.allient.folio

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.allient.folio.reader.ReaderDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Generated cover for a reader book, or its format and title when there is no cover image. */
@Composable
internal fun BookCover(doc: ReaderDocument, modifier: Modifier) {
    val cover by produceState<android.graphics.Bitmap?>(null, doc.coverPath) {
        value = withContext(Dispatchers.IO) { if (doc.coverPath.isBlank()) null else runCatching { android.graphics.BitmapFactory.decodeFile(doc.coverPath) }.getOrNull() }
    }
    val bitmap = cover
    if (bitmap != null) Image(bitmap.asImageBitmap(), contentDescription = null, modifier = modifier, contentScale = ContentScale.Crop)
    else Column(modifier.padding(10.dp)) {
        Text(doc.format.uppercase(), fontSize = 9.sp, color = ChromeMuted, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(10.dp))
        Text(doc.title, fontSize = 12.sp, lineHeight = 15.sp, color = Color(0xFF292929), maxLines = 5, overflow = TextOverflow.Ellipsis)
    }
}
