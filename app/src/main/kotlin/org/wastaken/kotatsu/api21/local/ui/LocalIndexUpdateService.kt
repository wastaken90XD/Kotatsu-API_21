package org.wastaken.kotatsu.api21.local.ui

import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import org.wastaken.kotatsu.api21.core.ui.CoroutineIntentService
import org.wastaken.kotatsu.api21.local.data.index.LocalMangaIndex
import javax.inject.Inject

@AndroidEntryPoint
class LocalIndexUpdateService : CoroutineIntentService() {

	@Inject
	lateinit var localMangaIndex: LocalMangaIndex

	override suspend fun IntentJobContext.processIntent(intent: Intent) {
		localMangaIndex.update()
	}

	override fun IntentJobContext.onError(error: Throwable) = Unit
}
