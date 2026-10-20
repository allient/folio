package com.allient.folio

import android.content.Context
import com.allient.folio.reader.*
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDColor
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceRGB
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.*
import org.json.JSONObject
import java.io.File

/** Export adds editable standard annotations to the original, preserving its text and vectors. */
internal object ReaderExport {
    fun annotatedPdf(context: Context, original: ReaderDocument, annotations: List<ReaderAnnotation>, output: File): File {
        PDFBoxResourceLoader.init(context.applicationContext)
        try {
            val loaded = try { PDDocument.load(File(original.path), MemoryUsageSetting.setupMixed(24L * 1024 * 1024)) }
                catch (locked: com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException) { throw IllegalArgumentException("This PDF is password protected, so it can't be exported with annotations. Export your reading notes instead.", locked) }
            loaded.use { document ->
                require(document.currentAccessPermission.canModifyAnnotations()) { "This PDF does not permit annotations." }
                annotations.filter { it.kind != AnnotationKind.BOOKMARK }.forEach { a ->
                    val anchor = JSONObject(a.anchor); val index = anchor.getInt("page")
                    require(index in 0 until document.numberOfPages) { "Invalid annotation page" }
                    val page = document.getPage(index)
                    val rgb = PDColor(floatArrayOf((a.color shr 16 and 255)/255f,(a.color shr 8 and 255)/255f,(a.color and 255)/255f),PDDeviceRGB.INSTANCE)
                    val annotation: PDAnnotation = when(a.kind) {
                        AnnotationKind.HIGHLIGHT, AnnotationKind.UNDERLINE -> {
                            val boxes=anchor.optJSONArray("rects") ?: return@forEach
                            val quads=mutableListOf<Float>();var left=Float.MAX_VALUE;var bottom=Float.MAX_VALUE;var right=-Float.MAX_VALUE;var top=-Float.MAX_VALUE
                            for(i in 0 until boxes.length()) {
                                val r=boxes.getJSONArray(i)
                                val x1=minOf(r.getDouble(0),r.getDouble(2)).toFloat();val x2=maxOf(r.getDouble(0),r.getDouble(2)).toFloat()
                                val y1=minOf(r.getDouble(1),r.getDouble(3)).toFloat();val y2=maxOf(r.getDouble(1),r.getDouble(3)).toFloat()
                                left=minOf(left,x1);right=maxOf(right,x2);bottom=minOf(bottom,y1);top=maxOf(top,y2)
                                quads.addAll(listOf(x1,y2,x2,y2,x1,y1,x2,y1))
                            }
                            if(quads.isEmpty())return@forEach
                            PDAnnotationTextMarkup(if(a.kind==AnnotationKind.UNDERLINE)PDAnnotationTextMarkup.SUB_TYPE_UNDERLINE else PDAnnotationTextMarkup.SUB_TYPE_HIGHLIGHT).apply {
                                quadPoints=quads.toFloatArray();rectangle=PDRectangle(left,bottom,right-left,top-bottom)
                            }
                        }
                        AnnotationKind.INK -> {
                            val points=anchor.getJSONArray("points");val ink=FloatArray(points.length()*2)
                            for(i in 0 until points.length()){val p=points.getJSONArray(i);ink[i*2]=p.getDouble(0).toFloat();ink[i*2+1]=p.getDouble(1).toFloat()}
                            val xs=ink.filterIndexed { index,_->index%2==0 };val ys=ink.filterIndexed { index,_->index%2==1 }
                            val strokeWidth=anchor.optDouble("width",2.0).toFloat()
                            PDAnnotationMarkup().apply {
                                cosObject.setName(com.tom_roush.pdfbox.cos.COSName.SUBTYPE,"Ink")
                                inkList=arrayOf(ink)
                                rectangle=PDRectangle(xs.min()-strokeWidth,ys.min()-strokeWidth,xs.max()-xs.min()+strokeWidth*2,ys.max()-ys.min()+strokeWidth*2)
                                borderStyle=PDBorderStyleDictionary().apply { width=strokeWidth }
                            }
                        }
                        else -> PDAnnotationText().apply { rectangle=PDRectangle(page.cropBox.lowerLeftX+20,page.cropBox.upperRightY-44,24f,24f);name=PDAnnotationText.NAME_NOTE }
                    }
                    annotation.annotationName=a.id;annotation.color=rgb;annotation.contents=a.note.ifBlank { a.quote };annotation.setPrinted(true);annotation.page=page
                    if(annotation is PDAnnotationMarkup)annotation.titlePopup="Folio"
                    page.annotations.add(annotation)
                    annotation.constructAppearances()
                }
                try { document.save(output) }
                catch (error: java.io.IOException) { if (document.isEncrypted) throw IllegalArgumentException("This PDF is encrypted, so it can't be exported with annotations. Export your reading notes instead.", error) else throw error }
            }
            return output
        } catch(error:Throwable){output.delete();throw error}
    }
}
