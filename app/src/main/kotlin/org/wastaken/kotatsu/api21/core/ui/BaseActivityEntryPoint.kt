package org.wastaken.kotatsu.api21.core.ui

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.wastaken.kotatsu.api21.core.exceptions.resolve.ExceptionResolver
import org.wastaken.kotatsu.api21.core.prefs.AppSettings

@EntryPoint
@InstallIn(SingletonComponent::class)
interface BaseActivityEntryPoint {

	val settings: AppSettings

	val exceptionResolverFactory: ExceptionResolver.Factory
}
