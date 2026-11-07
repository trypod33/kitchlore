package com.trypod33.kitchlore.data

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.util.zip.ZipInputStream

// Text extraction for imported documents: PDF (pdfbox), DOCX (zip + xml pull),
// plain text.

object DocTextExtractor {

    @Volatile
    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (!initialized) {
                PDFBoxResourceLoader.init(context.applicationContext)
                initialized = true
            }
        }
    }

    fun extract(file: File): String = when (file.extension.lowercase()) {
        "pdf" -> extractPdfText(file)
        "docx" -> extractDocxText(file)
        else -> file.readText()
    }

    fun extractPdfText(file: File): String {
        PDDocument.load(file).use { doc ->
            return PDFTextStripper().getText(doc)
        }
    }

    private fun extractDocxText(file: File): String {
        val sb = StringBuilder()
        ZipInputStream(file.inputStream().buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == "word/document.xml") {
                    val parser = XmlPullParserFactory.newInstance().newPullParser()
                    parser.setInput(zip, "UTF-8")
                    var inText = false
                    var event = parser.eventType
                    while (event != XmlPullParser.END_DOCUMENT) {
                        when (event) {
                            XmlPullParser.START_TAG -> when (parser.name) {
                                "t" -> inText = true
                                "p" -> sb.append('\n')
                            }
                            XmlPullParser.END_TAG -> when (parser.name) {
                                "t" -> inText = false
                                "p" -> sb.append('\n')
                            }
                            XmlPullParser.TEXT -> if (inText) sb.append(parser.text)
                        }
                        event = parser.next()
                    }
                    break
                }
                entry = zip.nextEntry
            }
        }
        return sb.toString()
    }
}
