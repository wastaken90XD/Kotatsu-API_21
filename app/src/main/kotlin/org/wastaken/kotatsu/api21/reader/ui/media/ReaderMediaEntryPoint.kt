package org.wastaken.kotatsu.api21.reader.ui.media

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import org.wastaken.kotatsu.api21.core.network.MangaHttpClient
import org.wastaken.kotatsu.api21.core.network.OriginalImageDownloader
import org.wastaken.kotatsu.api21.core.prefs.AppSettings

/**
 * Lets the booru media reader components (page view holders are not injectable)
 * reach singleton services:
 * - the [MangaHttpClient]-qualified OkHttp client (source headers per request),
 *   used to stream GIF/video bytes directly with no image-proxy and zero disk
 *   caching,
 * - [AppSettings] (configured download folder),
 * - [OriginalImageDownloader] (chunked raw-byte downloads that honor source headers).
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ReaderMediaEntryPoint {

	@MangaHttpClient
	fun okHttpClient(): OkHttpClient

	fun settings(): AppSettings

	fun originalImageDownloader(): OriginalImageDownloader
}
