package com.example.knotes

import android.content.Context
import android.content.ContextWrapper
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.graphics.PointF
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.knotes.reader.*
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.*
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class ReaderTest {
    private lateinit var directory:File
    private lateinit var context:Context
    @Before fun setup() {
        val real=InstrumentationRegistry.getInstrumentation().targetContext
        directory=File(real.cacheDir,"reader-test-${UUID.randomUUID()}").apply{mkdirs()}
        context=object:ContextWrapper(real){
            override fun getApplicationContext():Context=this
            override fun getFilesDir()=File(directory,"files").apply{mkdirs()}
            override fun getDatabasePath(name:String)=File(directory,"db/$name").apply{parentFile!!.mkdirs()}
            override fun openOrCreateDatabase(name:String,mode:Int,factory:SQLiteDatabase.CursorFactory?):SQLiteDatabase=SQLiteDatabase.openDatabase(getDatabasePath(name).path,factory,SQLiteDatabase.CREATE_IF_NECESSARY)
            override fun openOrCreateDatabase(name:String,mode:Int,factory:SQLiteDatabase.CursorFactory?,errorHandler:DatabaseErrorHandler?):SQLiteDatabase=SQLiteDatabase.openDatabase(getDatabasePath(name).path,factory,SQLiteDatabase.CREATE_IF_NECESSARY,errorHandler)
        }
        PDFBoxResourceLoader.init(context)
    }
    @After fun cleanup(){directory.deleteRecursively()}
    private fun pdf():File=File(directory,"source.pdf").also { file->
        PDDocument().use{doc->
            repeat(2){index->val page=PDPage();doc.addPage(page)
                PDPageContentStream(doc,page).use{stream->stream.beginText();stream.setFont(PDType1Font.HELVETICA,20f);stream.newLineAtOffset(40f,700f);stream.showText("Reading with Knotes page ${index+1}");stream.endText()}
            };doc.save(file)
        }
    }
    private fun annotation(document:ReaderDocument,anchor:String)=ReaderAnnotation(UUID.randomUUID().toString(),document.id,AnnotationKind.HIGHLIGHT,anchor,"Reading with Knotes","Remember this",createdAt=123L)
    @Test fun originalPdfIsPreservedAndImportsAreDeduplicated()=runBlocking {
        val original=pdf();val bytes=original.readBytes()
        ReaderStore(context).use{store->
            val imported=store.importDocument(Uri.fromFile(original))
            assertEquals(2,imported.pageCount);assertEquals("pdf",imported.format)
            assertArrayEquals(bytes,File(imported.path).readBytes())
            assertEquals(imported.id,store.importDocument(Uri.fromFile(original)).id)
            assertEquals(1,store.documents().size)
        }
    }
    @Test fun pdfTextSelectionAndAnnotationsSurviveReadingPositionUpdates()=runBlocking {
        val doc=ReaderStore(context).use{it.importDocument(Uri.fromFile(pdf()))}
        val selection=PdfReaderSession(File(doc.path)).use{session->
            assertEquals(1,session.searchPage(0,"Knotes").size)
            assertTrue(session.textLines(0).any { it.selection.text.contains("Knotes") })
            assertTrue(session.searchPage(0,"not present").isEmpty())
            session.select(0,PointF(.08f,.11f),PointF(.35f,.11f))!!
        }
        assertTrue(selection.text.isNotBlank())
        ReaderStore(context).use{store->
            store.saveAnnotation(annotation(doc,selection.anchor))
            store.updatePosition(doc.id,"{\"page\":1}",1.0)
        }
        ReaderStore(context).use{store->
            assertEquals("Remember this",store.annotations(doc.id).single().note)
            assertEquals(1.0,store.document(doc.id)!!.progress,0.001)
            store.deleteDocument(doc.id);assertTrue(store.annotations(doc.id).isEmpty())
        }
    }
    @Test fun annotatedPdfRetainsTextAndStandardEditableHighlights()=runBlocking {
        val source=pdf();val original=source.readBytes()
        ReaderStore(context).use{store->
            val doc=store.importDocument(Uri.fromFile(source))
            val anchor=PdfReaderSession(File(doc.path)).use{it.searchPage(0,"Knotes").single().anchor}
            val output=ReaderExport.annotatedPdf(context,doc,listOf(annotation(doc,anchor)),File(directory,"annotated.pdf"))
            PDDocument.load(output).use{exported->
                assertEquals("Highlight",exported.getPage(0).annotations.single().subtype)
                assertEquals("Remember this",exported.getPage(0).annotations.single().contents)
                assertTrue(com.tom_roush.pdfbox.text.PDFTextStripper().getText(exported).contains("Knotes"))
            }
            assertArrayEquals(original,source.readBytes());assertArrayEquals(original,File(doc.path).readBytes())
        }
    }
    @Test fun libraryBackupRestoresReaderReferencesWithoutBookBytesAndReconnectsOnReimport()=runBlocking {
        val source=pdf()
        val originalFolderId=UUID.randomUUID().toString()
        ReaderStore(context).use{store->
            val doc=store.importDocument(Uri.fromFile(source),originalFolderId)
            val anchor=PdfReaderSession(File(doc.path)).use{it.searchPage(0,"Knotes").single().anchor}
            store.saveAnnotation(annotation(doc,anchor));store.updatePosition(doc.id,"{\"page\":1}",.9)
            val backup=NotebookFiles(context).exportLibraryBackup(emptyList(),com.example.knotes.home.LibrarySettings(folders=listOf(com.example.knotes.home.NoteFolder(originalFolderId,"Research","lavender","papers"))))
            java.util.zip.ZipFile(backup).use { zip -> assertFalse(zip.entries().asSequence().any { it.name.startsWith("assets/") }) }
            store.deleteDocument(doc.id)
            val imported=NotebookFiles(context).importLibraryBackup(Uri.fromFile(backup))
            val restored=store.documents().single()
            val restoredFolder=imported.settings.folders.single()
            assertEquals("Research",restoredFolder.name);assertNotEquals(originalFolderId,restoredFolder.id);assertEquals(restoredFolder.id,restored.folderId)
            assertEquals("lavender", restoredFolder.color); assertEquals("papers", restoredFolder.style)
            assertEquals(doc.contentHash,restored.contentHash);assertTrue(restored.path.isBlank());assertTrue(restored.sourceUri.isNotBlank())
            assertEquals("Remember this",store.annotations(restored.id).single().note)
            assertEquals(.9,restored.progress,.001)
            val reimported=store.importDocument(Uri.fromFile(source))
            assertEquals(restored.id,reimported.id);assertTrue(File(reimported.path).isFile)
            assertEquals(restoredFolder.id,reimported.folderId)
            assertEquals("Remember this",store.annotations(reimported.id).single().note)
        }
    }
    @Test fun epubImportUsesReadiumAndPreservesLocatorAnchors()=runBlocking {
        val epub=epub()
        ReaderStore(context).use{store->
            val doc=store.importDocument(Uri.fromFile(epub));assertEquals("Knotes Sample",doc.title);assertEquals("epub",doc.format)
            val publication=EpubReaderEngine.open(context,File(doc.path))
            try {
                assertEquals(1,publication.readingOrder.size)
                val locator=publication.locatorFromLink(publication.readingOrder.first())!!.toJSON().toString()
                val note=ReaderAnnotation(UUID.randomUUID().toString(),doc.id,AnnotationKind.BOOKMARK,locator,note="Chapter one",createdAt=1L)
                store.saveAnnotation(note);store.updatePosition(doc.id,locator,.25)
                assertEquals(locator,store.annotations(doc.id).single().anchor)
                assertEquals(locator,store.document(doc.id)!!.locator)
            }finally{publication.close()}
        }
    }
    @Test fun scannedPageOcrProducesPersistentHighlightGeometry()=runBlocking {
        val scanned=File(directory,"scan.pdf")
        val bitmap=android.graphics.Bitmap.createBitmap(1200,1600,android.graphics.Bitmap.Config.ARGB_8888)
        val canvas=android.graphics.Canvas(bitmap);canvas.drawColor(android.graphics.Color.WHITE)
        val paint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply{color=android.graphics.Color.BLACK;textSize=64f}
        canvas.drawText("Reading with Knotes",80f,240f,paint)
        PDDocument().use{document->
            val page=PDPage(com.tom_roush.pdfbox.pdmodel.common.PDRectangle(600f,800f));document.addPage(page)
            val image=com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory.createFromImage(document,bitmap)
            PDPageContentStream(document,page).use{it.drawImage(image,0f,0f,600f,800f)};document.save(scanned)
        };bitmap.recycle()
        ReaderStore(context).use{store->
            val doc=store.importDocument(Uri.fromFile(scanned))
            PdfReaderSession(File(doc.path)).use{session->
                assertTrue(session.pageText(0).isBlank())
                assertTrue(session.recognize(0)>0)
                val selection=session.select(0,PointF(.1f,.13f),PointF(.8f,.13f))!!
                assertTrue(selection.text.contains("Reading",ignoreCase=true))
                store.saveAnnotation(annotation(doc,selection.anchor))
                assertTrue(JSONObject(store.annotations(doc.id).single().anchor).getBoolean("ocr"))
            }
        }
    }

    @Test fun rejectsEpubPathTraversalWithoutAddingDocument()=runBlocking {
        val file=File(directory,"bad.epub")
        ZipOutputStream(file.outputStream()).use{zip->listOf("META-INF/container.xml","../escape").forEach{zip.putNextEntry(ZipEntry(it));zip.write("bad".toByteArray());zip.closeEntry()}}
        ReaderStore(context).use{store->
            try{store.importDocument(Uri.fromFile(file));fail("Expected rejection")}catch(expected:Exception){assertTrue(expected is IllegalArgumentException || expected is java.util.zip.ZipException)}
            assertTrue(store.documents().isEmpty());assertFalse(File(directory,"escape").exists())
        }
    }
    private fun epub():File=File(directory,"sample.epub").also{file->
        val entries=linkedMapOf(
            "mimetype" to "application/epub+zip",
            "META-INF/container.xml" to """<?xml version="1.0"?><container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="EPUB/package.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""",
            "EPUB/package.opf" to """<?xml version="1.0"?><package xmlns="http://www.idpf.org/2007/opf" unique-identifier="id" version="3.0"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:identifier id="id">urn:uuid:sample</dc:identifier><dc:title>Knotes Sample</dc:title><dc:language>en</dc:language><dc:creator>Knotes</dc:creator><meta property="dcterms:modified">2026-10-16T00:00:00Z</meta></metadata><manifest><item id="chapter" href="chapter.xhtml" media-type="application/xhtml+xml"/><item id="nav" href="nav.xhtml" properties="nav" media-type="application/xhtml+xml"/></manifest><spine><itemref idref="chapter"/></spine></package>""",
            "EPUB/chapter.xhtml" to """<html xmlns="http://www.w3.org/1999/xhtml"><head><title>Chapter one</title></head><body><h1>Chapter one</h1><p>Reading and highlighting with Knotes.</p></body></html>""",
            "EPUB/nav.xhtml" to """<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><head><title>Contents</title></head><body><nav epub:type="toc"><ol><li><a href="chapter.xhtml">Chapter one</a></li></ol></nav></body></html>"""
        )
        ZipOutputStream(file.outputStream()).use{zip->entries.forEach{(name,text)->zip.putNextEntry(ZipEntry(name));zip.write(text.toByteArray());zip.closeEntry()}}
    }
}
