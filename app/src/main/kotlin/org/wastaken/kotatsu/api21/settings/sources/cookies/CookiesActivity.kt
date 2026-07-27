package org.wastaken.kotatsu.api21.settings.sources.cookies

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import androidx.activity.viewModels
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.filterNotNull
import org.wastaken.kotatsu.api21.R
import org.wastaken.kotatsu.api21.core.exceptions.resolve.SnackbarErrorObserver
import org.wastaken.kotatsu.api21.core.model.getTitle
import org.wastaken.kotatsu.api21.core.ui.BaseActivity
import org.wastaken.kotatsu.api21.core.ui.util.ReversibleActionObserver
import org.wastaken.kotatsu.api21.core.util.ext.consumeAllSystemBarsInsets
import org.wastaken.kotatsu.api21.core.util.ext.end
import org.wastaken.kotatsu.api21.core.util.ext.observe
import org.wastaken.kotatsu.api21.core.util.ext.observeEvent
import org.wastaken.kotatsu.api21.core.util.ext.systemBarsInsets
import org.wastaken.kotatsu.api21.databinding.ActivityCookiesBinding
import org.wastaken.kotatsu.api21.list.ui.adapter.TypedListSpacingDecoration
import org.wastaken.kotatsu.api21.list.ui.model.ListModel
import org.wastaken.kotatsu.api21.settings.sources.cookies.adapter.CookiesAdapter
import org.wastaken.kotatsu.api21.settings.sources.cookies.model.CookieListModel

@AndroidEntryPoint
class CookiesActivity :
	BaseActivity<ActivityCookiesBinding>(),
	CookiesListListener,
	View.OnClickListener {

	private val viewModel by viewModels<CookiesViewModel>()

	private lateinit var adapter: CookiesAdapter

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(ActivityCookiesBinding.inflate(layoutInflater))
		setDisplayHomeAsUp(isEnabled = true, showUpAsClose = false)
		supportActionBar?.subtitle = viewModel.source.getTitle(this)
		adapter = CookiesAdapter(this)
		viewBinding.recyclerView.setHasFixedSize(true)
		viewBinding.recyclerView.adapter = adapter
		viewBinding.recyclerView.addItemDecoration(TypedListSpacingDecoration(this, false))
		viewBinding.fabAdd.setOnClickListener(this)
		supportFragmentManager.setFragmentResultListener(
			CookieEditDialogFragment.REQUEST_KEY,
			this,
		) { _, bundle -> onCookieResult(bundle) }

		viewModel.content.filterNotNull().observe(this, ::onContentChanged)
		viewModel.onError.observeEvent(this, SnackbarErrorObserver(viewBinding.recyclerView, null))
		viewModel.onActionDone.observeEvent(this, ReversibleActionObserver(viewBinding.recyclerView))
	}

	override fun onResume() {
		super.onResume()
		// refresh: cookies may have changed after a browser/login flow
		viewModel.onResume()
	}

	override fun onApplyWindowInsets(
		v: View,
		insets: WindowInsetsCompat
	): WindowInsetsCompat {
		val barsInsets = insets.systemBarsInsets
		viewBinding.recyclerView.updatePadding(
			left = barsInsets.left,
			right = barsInsets.right,
			bottom = barsInsets.bottom,
		)
		viewBinding.appbar.updatePadding(
			left = barsInsets.left,
			right = barsInsets.right,
			top = barsInsets.top,
		)
		viewBinding.fabAdd.updateLayoutParams<MarginLayoutParams> {
			marginEnd = topMargin + barsInsets.end(v)
			bottomMargin = topMargin + barsInsets.bottom
		}
		return insets.consumeAllSystemBarsInsets()
	}

	override fun onCreateOptionsMenu(menu: Menu): Boolean {
		menuInflater.inflate(R.menu.opt_cookies, menu)
		return super.onCreateOptionsMenu(menu)
	}

	override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
		R.id.action_clear_all -> {
			viewModel.clearAllCookies()
			true
		}

		else -> super.onOptionsItemSelected(item)
	}

	override fun onClick(v: View) {
		when (v.id) {
			R.id.fab_add -> CookieEditDialogFragment.showAdd(
				supportFragmentManager,
				viewModel.sourceDomain,
			)
		}
	}

	override fun onCookieClick(item: CookieListModel) {
		CookieEditDialogFragment.showEdit(supportFragmentManager, item.name, item.value)
	}

	override fun onCookieDeleteClick(item: CookieListModel) {
		viewModel.deleteCookie(item)
	}

	private fun onCookieResult(bundle: Bundle) {
		viewModel.setCookie(
			name = CookieEditDialogFragment.getName(bundle),
			value = CookieEditDialogFragment.getValue(bundle),
			domain = CookieEditDialogFragment.getDomain(bundle),
			replaced = if (CookieEditDialogFragment.isEdit(bundle)) {
				CookieListModel(
					name = CookieEditDialogFragment.getOldName(bundle),
					value = CookieEditDialogFragment.getOldValue(bundle),
					domain = "",
					path = "/",
					expiresAt = 0L,
					isPersistent = false,
					isSecure = false,
					isHttpOnly = false,
				)
			} else {
				null
			},
		)
	}

	private suspend fun onContentChanged(list: List<ListModel>) {
		adapter.emit(list)
	}
}
