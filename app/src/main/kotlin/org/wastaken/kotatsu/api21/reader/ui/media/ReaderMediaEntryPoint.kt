package org.wastaken.kotatsu.api21.reader.ui.media

import coil3.ImageLoader
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.wastaken.kotatsu.api21.core.network.OriginalImageDownloader
import org.wastaken.kotatsu.api21.core.prefs.AppSettings

/**
 * Lets the booru media reader components (page view holders are not injectable)
 * reach singleton services:
 * - the app's shared Coil [ImageLoader] (with GifDecoder registered),
 * - [AppSettings] (save-bytes preference and configured download folder),
 * - [OriginalImageDownloader] (raw-byte downloads that honor source headers).
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ReaderMediaEntryPoint {

	fun imageLoader(): ImageLoader

	fun settings(): AppSettings

	fun originalImageDownloader(): OriginalImageDownloader
}
