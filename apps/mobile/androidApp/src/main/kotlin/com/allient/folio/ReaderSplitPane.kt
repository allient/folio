@file:OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class)
package com.allient.folio

import android.view.View
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.FragmentManager
import com.allient.folio.reader.ReaderAnnotation
import com.allient.folio.reader.ReaderDocument
import kotlinx.coroutines.*
import org.json.JSONObject
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import java.io.File

/** Independent navigation in the second pane; never overwrites the primary reading position. */
@Composable internal fun ReaderComparisonPane(document: ReaderDocument, startPage: Int, fragments: FragmentManager, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val app = context.applicationContext
    val preferences = remember { app.getSharedPreferences("reader-preferences", android.content.Context.MODE_PRIVATE) }
    var session by remember(document.id) { mutableStateOf<PdfReaderSession?>(null) }
    var book by remember(document.id) { mutableStateOf<Publication?>(null) }
    var annotations by remember(document.id) { mutableStateOf<List<ReaderAnnotation>>(emptyList()) }
    var error by remember(document.id) { mutableStateOf<String?>(null) }
    var page by rememberSaveable(document.id) { mutableIntStateOf(preferences.getInt("split-page.${document.id}", startPage).coerceAtLeast(0)) }
    LaunchedEffect(document.id, page) { preferences.edit().putInt("split-page.${document.id}", page).apply() }
    var nav by remember(document.id) { mutableStateOf<EpubNavigatorFragment?>(null) }
    LaunchedEffect(nav) {
        nav?.currentLocator?.collect { preferences.edit().putString("split-locator.${document.id}", it.toJSON().toString()).apply() }
    }
    val containerId = remember(document.id) { View.generateViewId() }
    DisposableEffect(document.id) {
        val loader = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        var ownedPdf: PdfReaderSession? = null
        var ownedBook: Publication? = null
        loader.launch {
            try {
                // Assign ownership before returning to Main so cancellation cannot leak native handles.
                withContext(Dispatchers.IO) {
                    if (document.format == "pdf") ownedPdf = PdfReaderSession(File(document.path))
                    else ownedBook = EpubReaderEngine.open(app, File(document.path))
                    annotations = ReaderStore(app).use { it.annotations(document.id) }
                }
                ownedPdf?.let { page = page.coerceIn(0, it.pageCount - 1) }
                session = ownedPdf; book = ownedBook
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error = failure.message ?: "Could not open document" }
        }
        onDispose {
            loader.cancel()
            fragments.findFragmentById(containerId)?.let { fragments.beginTransaction().remove(it).commitNowAllowingStateLoss() }
            // Join the loader before releasing resources it may still be creating on IO.
            ReaderWrites.scope.launch { loader.coroutineContext[Job]?.join(); ownedPdf?.close(); ownedBook?.close() }
        }
    }
    Column(modifier) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                error != null -> Text(error.orEmpty(), Modifier.align(Alignment.Center).padding(16.dp))
                session != null -> AndroidView(factory = { PdfPageReaderView(it) }, update = { view ->
                    val pdf = session!!
                    view.show(pdf, page, annotations)
                    view.onPageTurn = { forward -> page = (page + if (forward) 1 else -1).coerceIn(0, pdf.pageCount - 1) }
                    view.onError = { error = it }
                }, modifier = Modifier.fillMaxSize())
                book != null -> AndroidView(factory = { host ->
                    FragmentContainerView(host).apply {
                        id = containerId
                        val factory = EpubNavigatorFactory(book!!).createFragmentFactory(
                            initialLocator = (preferences.getString("split-locator.${document.id}", null) ?: document.locator).takeIf { it.isNotBlank() }?.let { Locator.fromJSON(JSONObject(it)) },
                            initialPreferences = EpubPreferences(),
                        )
                        val fragment = factory.instantiate(host.classLoader, EpubNavigatorFragment::class.java.name) as EpubNavigatorFragment
                        post {
                            if (isAttachedToWindow && !fragments.isDestroyed && !fragments.isStateSaved) {
                                fragments.beginTransaction().replace(id, fragment).commitNow()
                                nav = fragment
                            }
                        }
                    }
                }, modifier = Modifier.fillMaxSize())
                else -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(enabled = session?.let { page > 0 } ?: (nav != null), onClick = { if (session != null) page-- else nav?.goBackward(true) }) { Text("Previous") }
            Text(if (session != null) "${page + 1}/${session!!.pageCount}" else "EPUB", style = MaterialTheme.typography.labelSmall)
            TextButton(enabled = session?.let { page < it.pageCount - 1 } ?: (nav != null), onClick = { if (session != null) page++ else nav?.goForward(true) }) { Text("Next") }
        }
    }
}
