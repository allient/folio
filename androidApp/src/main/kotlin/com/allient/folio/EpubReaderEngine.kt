@file:OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class)
package com.allient.folio

import android.content.Context
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.isRestricted
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.shared.util.toUrl
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser
import java.io.File

internal object EpubReaderEngine {
    suspend fun open(context: Context, file: File): Publication {
        val http = DefaultHttpClient()
        val assets = AssetRetriever(context.contentResolver, http)
        val asset = assets.retrieve(file.toUrl(isDirectory = false)).getOrElse { throw IllegalArgumentException("Could not read EPUB: ${it.message}") }
        val publication = PublicationOpener(DefaultPublicationParser(context, http, assets, pdfFactory = null))
            .open(asset, allowUserInteraction = false).getOrElse { throw IllegalArgumentException("Could not open EPUB: ${it.message}") }
        if (publication.isRestricted) { publication.close(); throw IllegalArgumentException("This book is DRM protected. Import a DRM-free EPUB.") }
        return publication
    }
}
