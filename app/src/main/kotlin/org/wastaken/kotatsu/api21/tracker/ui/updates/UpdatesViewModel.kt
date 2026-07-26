package org.wastaken.kotatsu.api21.tracker.ui.updates

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.plus
import org.wastaken.kotatsu.api21.R
import org.wastaken.kotatsu.api21.core.parser.MangaDataRepository
import org.wastaken.kotatsu.api21.core.prefs.AppSettings
import org.wastaken.kotatsu.api21.core.prefs.ListMode
import org.wastaken.kotatsu.api21.core.prefs.observeAsFlow
import org.wastaken.kotatsu.api21.core.ui.model.DateTimeAgo
import org.wastaken.kotatsu.api21.core.util.ext.calculateTimeAgo
import org.wastaken.kotatsu.api21.core.util.ext.onFirst
import org.wastaken.kotatsu.api21.list.domain.ListFilterOption
import org.wastaken.kotatsu.api21.list.domain.MangaListMapper
import org.wastaken.kotatsu.api21.list.domain.QuickFilterListener
import org.wastaken.kotatsu.api21.list.ui.MangaListViewModel
import org.wastaken.kotatsu.api21.list.ui.model.EmptyState
import org.wastaken.kotatsu.api21.list.ui.model.ListHeader
import org.wastaken.kotatsu.api21.list.ui.model.ListModel
import org.wastaken.kotatsu.api21.list.ui.model.LoadingState
import org.wastaken.kotatsu.api21.list.ui.model.toErrorState
import org.wastaken.kotatsu.api21.tracker.domain.TrackingRepository
import org.wastaken.kotatsu.api21.tracker.domain.UpdatesListQuickFilter
import org.wastaken.kotatsu.api21.tracker.domain.model.MangaTracking
import javax.inject.Inject

@HiltViewModel
class UpdatesViewModel @Inject constructor(
	private val repository: TrackingRepository,
	settings: AppSettings,
	private val mangaListMapper: MangaListMapper,
	private val quickFilter: UpdatesListQuickFilter,
	mangaDataRepository: MangaDataRepository,
) : MangaListViewModel(settings, mangaDataRepository), QuickFilterListener by quickFilter {

	override val content = combine(
		quickFilter.appliedOptions.flatMapLatest { filterOptions ->
			repository.observeUpdatedManga(
				limit = 0,
				filterOptions = filterOptions,
			)
		},
		quickFilter.appliedOptions,
		settings.observeAsFlow(AppSettings.KEY_UPDATED_GROUPING) { isUpdatedGroupingEnabled },
		observeListModeWithTriggers(),
	) { mangaList, filters, grouping, mode ->
		when {
			mangaList.isEmpty() -> listOfNotNull(
				quickFilter.filterItem(filters),
				EmptyState(
					icon = R.drawable.ic_empty_history,
					textPrimary = R.string.text_history_holder_primary,
					textSecondary = R.string.text_history_holder_secondary,
					actionStringRes = 0,
				),
			)

			else -> mangaList.toUi(mode, filters, grouping)
		}
	}.onStart {
		loadingCounter.increment()
	}.onFirst {
		loadingCounter.decrement()
	}.catch {
		emit(listOf(it.toErrorState(canRetry = false)))
	}.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.Eagerly, listOf(LoadingState))

	init {
		launchJob(Dispatchers.Default) {
			repository.gc()
		}
	}

	override fun onRefresh() = Unit

	override fun onRetry() = Unit

	fun remove(ids: Set<Long>) {
		launchJob(Dispatchers.Default) {
			repository.clearUpdates(ids)
		}
	}

	private suspend fun List<MangaTracking>.toUi(
		mode: ListMode,
		filters: Set<ListFilterOption>,
		grouped: Boolean,
	): List<ListModel> {
		val result = ArrayList<ListModel>(if (grouped) (size * 1.4).toInt() else size + 1)
		quickFilter.filterItem(filters)?.let(result::add)
		var prevHeader: DateTimeAgo? = null
		for (item in this) {
			if (grouped) {
				val header = item.lastChapterDate?.let { calculateTimeAgo(it) }
				if (header != prevHeader) {
					if (header != null) {
						result += ListHeader(header)
					}
					prevHeader = header
				}
			}
			result += mangaListMapper.toListModel(item.manga, mode)
		}
		return result
	}
}
