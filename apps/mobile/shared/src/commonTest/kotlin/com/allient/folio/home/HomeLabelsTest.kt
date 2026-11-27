package com.allient.folio.home

import com.allient.folio.InfinitePaper
import com.allient.folio.NotebookTemplateCatalog
import com.allient.folio.infinitePaperColorRes
import com.allient.folio.infinitePaperPatternRes
import com.allient.folio.templateCategoryRes
import com.allient.folio.templateNameRes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Stored IDs stay English; every ID the UI shows has a translatable label. */
class HomeLabelsTest {
    @Test fun everyTemplateAndCategoryHasALabel() {
        NotebookTemplateCatalog.all.forEach { assertNotNull(templateNameRes(it.id), it.id) }
        NotebookTemplateCatalog.categories.forEach { assertNotNull(templateCategoryRes(it), it) }
        assertNull(templateNameRes("not-a-template"))
    }

    @Test fun everyInfinitePaperOptionHasALabel() {
        InfinitePaper.patterns.forEach { (id, _) -> assertNotNull(infinitePaperPatternRes(id), id) }
        InfinitePaper.colors.forEach { (id, _) -> assertNotNull(infinitePaperColorRes(id), id) }
    }

    @Test fun everyPreferenceChoiceHasALabel() {
        AccentPresets.forEach { assertNotNull(accentNameRes(it.id), it.id) }
        CornerStyles.forEach { (id, _) -> assertNotNull(cornerStyleRes(id), id) }
        HomeSort.all.forEach { (id, _) -> assertNotNull(sortLabelRes(id), id) }
        FolderStyleIds.forEach { assertNotNull(folderStyleRes(it), it) }
        FolderColorIds.forEach { assertNotNull(folderColorRes(it), it) }
    }

    @Test fun libraryCountsUseThePluralOfTheActiveTab() {
        assertEquals("home_count_notebooks", libraryCountPlural("notes").key)
        assertEquals("home_count_canvases", libraryCountPlural("canvas").key)
        assertEquals("home_count_books", libraryCountPlural("books").key)
        assertEquals("home_count_items", libraryCountPlural("all").key)
    }
}
