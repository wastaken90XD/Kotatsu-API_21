package org.wastaken.kotatsu.api21.details.domain

import org.wastaken.kotatsu.api21.core.util.LocaleStringComparator
import org.wastaken.kotatsu.api21.details.ui.model.MangaBranch

class BranchComparator : Comparator<MangaBranch> {

	private val delegate = LocaleStringComparator()

	override fun compare(o1: MangaBranch, o2: MangaBranch): Int = delegate.compare(o1.name, o2.name)
}
