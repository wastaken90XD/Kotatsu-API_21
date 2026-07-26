package org.wastaken.kotatsu.api21.reader.ui

import org.wastaken.kotatsu.api21.reader.ui.pager.ReaderPage

data class ReaderContent(
	val pages: List<ReaderPage>,
	val state: ReaderState?
)