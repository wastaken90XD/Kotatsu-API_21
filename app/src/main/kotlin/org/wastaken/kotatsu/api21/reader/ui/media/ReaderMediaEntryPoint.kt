package org.wastaken.kotatsu.api21.reader.ui.media

import coil3.ImageLoader
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Lets the booru media reader components (page view holders are not injectable)
 * reach the app's shared Coil [ImageLoader], which has GifDecoder registered.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ReaderMediaEntryPoint {

	fun imageLoader(): ImageLoader
}
