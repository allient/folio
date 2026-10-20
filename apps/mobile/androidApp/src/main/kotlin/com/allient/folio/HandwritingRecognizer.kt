package com.allient.folio

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.MlKitException
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.recognition.Ink
import com.google.mlkit.vision.digitalink.recognition.RecognitionContext
import com.google.mlkit.vision.digitalink.recognition.WritingArea
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Handwriting to text with ML Kit Digital Ink Recognition. It runs on the device and reads pen
 * strokes (points in order, with timing), which is far more accurate for handwriting than OCR on an
 * image. Each language is a model of about 20 MB, downloaded the first time it is used.
 */
internal object HandwritingRecognizer {
    private const val PREFS = "handwriting"
    private const val LANGUAGE = "language"

    /** Languages ML Kit can read, as (BCP-47 tag, display name), sorted by name. Shapes and emoji models are left out. */
    val languages: List<Pair<String, String>> by lazy {
        DigitalInkRecognitionModelIdentifier.allModelIdentifiers()
            .map { it.languageTag }
            .filterNot { it.startsWith("zxx") }
            .distinct()
            .map { tag -> tag to Locale.forLanguageTag(tag).let { locale -> locale.getDisplayName(locale).replaceFirstChar { it.titlecase(locale) } } }
            .sortedBy { it.second.lowercase(Locale.ROOT) }
    }

    /** The saved language, else the device language when ML Kit supports it, else English. */
    fun language(context: Context): String {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(LANGUAGE, null)?.let { return it }
        val device = Locale.getDefault()
        return listOf(device.toLanguageTag(), device.language).firstOrNull { tag -> DigitalInkRecognitionModelIdentifier.fromLanguageTag(tag) != null } ?: "en-US"
    }

    fun setLanguage(context: Context, tag: String) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(LANGUAGE, tag).apply()

    suspend fun isDownloaded(tag: String): Boolean = model(tag)?.let { RemoteModelManager.getInstance().isModelDownloaded(it).await() } ?: false

    /**
     * Text candidates for [strokes], best first. Strokes use the page's normalized coordinates; they
     * are scaled to document units so the recognizer sees the real proportions of the writing.
     */
    suspend fun recognize(strokes: List<InkStroke>, tag: String): List<String> {
        val model = model(tag) ?: throw IllegalArgumentException("Handwriting in this language isn't supported.")
        val manager = RemoteModelManager.getInstance()
        if (!manager.isModelDownloaded(model).await()) manager.download(model, DownloadConditions.Builder().build()).await()
        val ink = Ink.builder().apply {
            var clock = 0L // Strokes keep their own timing; consecutive strokes get increasing times.
            strokes.filter { it.points.isNotEmpty() }.forEach { stroke ->
                val builder = Ink.Stroke.builder()
                stroke.points.forEach { p -> builder.addPoint(Ink.Point.create(p.x * DOC_WIDTH, p.y * DOC_HEIGHT, clock + p.elapsedMillis)) }
                addStroke(builder.build())
                clock += (stroke.points.last().elapsedMillis) + 200L
            }
        }.build()
        val points = strokes.flatMap { it.points }
        val width = ((points.maxOf { it.x } - points.minOf { it.x }) * DOC_WIDTH).coerceAtLeast(1f)
        val height = ((points.maxOf { it.y } - points.minOf { it.y }) * DOC_HEIGHT).coerceAtLeast(1f)
        val recognizer = DigitalInkRecognition.getClient(DigitalInkRecognizerOptions.builder(model).build())
        try {
            val context = RecognitionContext.builder().setPreContext("").setWritingArea(WritingArea(width, height)).build()
            return recognizer.recognize(ink, context).await().candidates.map { it.text }.filter { it.isNotBlank() }.distinct()
        } finally {
            recognizer.close()
        }
    }

    private fun model(tag: String): DigitalInkRecognitionModel? =
        DigitalInkRecognitionModelIdentifier.fromLanguageTag(tag)?.let { DigitalInkRecognitionModel.builder(it).build() }

    private const val DOC_WIDTH = 600f
    private const val DOC_HEIGHT = 960f
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { continuation.resume(it) }
    addOnFailureListener { continuation.resumeWithException(it) }
    addOnCanceledListener { continuation.cancel() }
}

/**
 * The "Convert to text" sheet: recognizes [strokes], lets the user pick or correct the text, then
 * replaces the handwriting with a text box ([onReplace]) or adds the text next to it ([onAdd]).
 */
@Composable
internal fun ConvertToTextPanel(strokes: List<InkStroke>, onReplace: (String) -> Unit, onAdd: (String) -> Unit, onCopy: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var language by remember { mutableStateOf(HandwritingRecognizer.language(context)) }
    var choosing by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var candidates by remember { mutableStateOf<List<String>>(emptyList()) }
    var text by remember { mutableStateOf("") }
    fun run() = scope.launch {
        candidates = emptyList()
        status = if (HandwritingRecognizer.isDownloaded(language)) "Reading your handwriting…" else "Downloading the ${displayName(language)} handwriting model (about 20 MB, once)…"
        try {
            candidates = HandwritingRecognizer.recognize(strokes, language)
            text = candidates.firstOrNull().orEmpty()
            status = if (candidates.isEmpty()) "No text found. Select clearer handwriting or try another language." else null
            AppAnalytics.event("handwriting_converted", "language" to language.substringBefore('-'))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            status = if (error is MlKitException && error.errorCode == MlKitException.UNAVAILABLE) "Couldn't download the language model. Connect to the internet once and try again."
            else error.message ?: "Couldn't read the handwriting."
        }
    }
    LaunchedEffect(language, strokes) { if (strokes.isNotEmpty()) run() }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Language", Modifier.weight(1f), fontSize = 13.sp, color = ChromeMuted)
            TextButton(onClick = { choosing = true }) { Text(displayName(language), color = ChromeInk, fontWeight = FontWeight.Medium) }
        }
        if (strokes.isEmpty()) Text("Select handwriting with the lasso first.", fontSize = 13.sp, color = ChromeMuted)
        status?.let { message ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (message.endsWith("…")) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Text(message, fontSize = 13.sp, color = ChromeMuted)
            }
        }
        if (candidates.size > 1) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            candidates.take(3).forEach { candidate ->
                FilterChip(selected = candidate == text, onClick = { text = candidate }, label = { Text(candidate, maxLines = 1) })
            }
        }
        // Gboard's pen handwriting works in this field too, for corrections.
        OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth(), label = { Text("Text") }, minLines = 2, enabled = candidates.isNotEmpty() || text.isNotEmpty())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onReplace(text.trim()) }, enabled = text.isNotBlank(), shape = RoundedCornerShape(12.dp)) { Text("Replace handwriting") }
            OutlinedButton(onClick = { onAdd(text.trim()) }, enabled = text.isNotBlank(), shape = RoundedCornerShape(12.dp)) { Text("Add as text") }
        }
        TextButton(onClick = { onCopy(text.trim()) }, enabled = text.isNotBlank()) { Text("Copy text") }
    }

    if (choosing) AlertDialog(onDismissRequest = { choosing = false },
        title = { Text("Handwriting language") },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(HandwritingRecognizer.languages, key = { it.first }) { (tag, name) ->
                    Text(name, Modifier.fillMaxWidth().clickable {
                        choosing = false; language = tag; HandwritingRecognizer.setLanguage(context, tag)
                    }.padding(vertical = 12.dp), fontSize = 15.sp, color = if (tag == language) Color(0xFF2F7A68) else ChromeInk,
                        fontWeight = if (tag == language) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        },
        confirmButton = { TextButton(onClick = { choosing = false }) { Text("Close") } })
}

private fun displayName(tag: String): String =
    HandwritingRecognizer.languages.firstOrNull { it.first == tag }?.second ?: Locale.forLanguageTag(tag).displayName
