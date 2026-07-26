package org.wastaken.kotatsu.api21.suggestions.data

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation
import org.wastaken.kotatsu.api21.core.db.entity.MangaEntity
import org.wastaken.kotatsu.api21.core.db.entity.MangaTagsEntity
import org.wastaken.kotatsu.api21.core.db.entity.TagEntity

data class SuggestionWithManga(
	@Embedded val suggestion: SuggestionEntity,
	@Relation(
		parentColumn = "manga_id",
		entityColumn = "manga_id"
	)
	val manga: MangaEntity,
	@Relation(
		parentColumn = "manga_id",
		entityColumn = "tag_id",
		associateBy = Junction(MangaTagsEntity::class)
	)
	val tags: List<TagEntity>,
)