package org.wastaken.kotatsu.api21.explore.ui.adapter

import android.view.View
import org.wastaken.kotatsu.api21.list.ui.adapter.ListHeaderClickListener
import org.wastaken.kotatsu.api21.list.ui.adapter.ListStateHolderListener

interface ExploreListEventListener : ListStateHolderListener, View.OnClickListener, ListHeaderClickListener
