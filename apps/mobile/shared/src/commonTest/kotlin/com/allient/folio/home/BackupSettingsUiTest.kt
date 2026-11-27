package com.allient.folio.home

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** "No folder" is a state, not a label: the folder card never depends on display text. */
class BackupSettingsUiTest {
    @Test fun noFolderAndOffHidesTheFolderCard() {
        assertFalse(BackupSettingsUi(enabled = false, folder = null).showsFolder)
    }

    @Test fun aChosenFolderShowsWhateverItsTranslatedLabel() {
        // Labels in any language, including the old English placeholder text, count as a chosen folder.
        listOf("Documents/Folio", "Almacenamiento interno", "No folder selected").forEach {
            assertTrue(BackupSettingsUi(enabled = false, folder = it).showsFolder, it)
        }
    }

    @Test fun smartBackupsOnShowTheCardEvenWithoutAFolder() {
        assertTrue(BackupSettingsUi(enabled = true, folder = null).showsFolder)
    }
}
