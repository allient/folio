package com.allient.folio

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.net.Uri
import android.os.LocaleList
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException
import java.util.UUID

/** Errors shown to people come from string resources in the app language; exception text stays English for logs. */
@RunWith(AndroidJUnit4::class)
class UserMessageTest {
    private val target: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val id = UUID.randomUUID().toString()
    private val sandbox = File(target.cacheDir, "user-message-$id").apply { mkdirs() }
    private val preferences: SharedPreferences = target.getSharedPreferences("test-$id-backup", Context.MODE_PRIVATE)

    private fun resources(tag: String) = target.createConfigurationContext(Configuration(target.resources.configuration)
        .apply { setLocales(LocaleList.forLanguageTags(tag)) }).resources

    @After fun tearDown() { preferences.edit().clear().commit(); sandbox.deleteRecursively() }

    @Test fun typedErrorShowsItsResourceInTheAppLanguageAndKeepsEnglishForLogs() {
        val error = UserInputException(R.string.error_epub_too_long, "This book is too long (more than 600 pages).", listOf(600))
        assertTrue(error is IllegalArgumentException)
        assertEquals("This book is too long (more than 600 pages).", error.message)
        assertEquals(resources("es").getString(R.string.error_epub_too_long, 600), error.userMessage(resources("es"), R.string.error_import_file))
        assertTrue(error.userMessage(resources("pt"), R.string.error_import_file).contains("600"))
        assertNotEquals(error.userMessage(resources("en"), R.string.error_import_file), error.userMessage(resources("es"), R.string.error_import_file))
    }

    @Test fun libraryAndPlatformMessagesAreNeverShown() {
        val raw = IllegalStateException("org.readium.SomeInternalFailure: zip entry 42")
        val shown = raw.userMessage(resources("es"), R.string.error_import_file)
        assertEquals(resources("es").getString(R.string.error_import_file), shown)
        assertFalse(shown.contains("readium"))
    }

    @Test fun aTypedCauseIsFoundThroughWrappers() {
        val wrapped = IOException("Backup archive could not be read back", UserIoException(R.string.error_backup_verify, "mismatch"))
        assertEquals(resources("pt").getString(R.string.error_backup_verify), wrapped.userMessage(resources("pt"), R.string.error_backup_failed))
    }

    @Test fun requireAndCheckKeepTheirStandardExceptionTypes() {
        try { requireUser(false, R.string.error_file_too_large) { "Selected file is too large." }; fail() }
        catch (expected: IllegalArgumentException) { assertEquals("Selected file is too large.", expected.message); assertEquals(R.string.error_file_too_large, expected.findUserMessage()?.messageRes) }
        try { checkUser(false, R.string.error_recording_running) { "A recording is already running." }; fail() }
        catch (expected: IllegalStateException) { assertEquals(R.string.error_recording_running, expected.findUserMessage()?.messageRes) }
    }

    @Test fun invalidImageImportFailsWithATranslatedMessage() {
        val files = NotebookFiles(target)
        val source = File(sandbox, "not-an-image.png").apply { writeText("Invalid image") }
        val error = runCatching { files.importImages(Uri.fromFile(source)) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertEquals(resources("es").getString(R.string.error_image_unsupported), error!!.userMessage(resources("es"), R.string.error_import_file))
    }

    @Test fun backupFolderNamesFollowTheAppLanguageAndReadOldEnglishLabels() {
        val store = LocalBackupStore(target, "test-$id-backup")
        assertNull("No folder chosen", store.read().folderName)
        val documents = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3ADocuments%2FFolio")
        store.configure(documents, "Folio", enabled = true)
        assertEquals(BackupFolderName.Named("Documents/Folio"), store.read().folderName)
        val root = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3A")
        assertEquals(BackupFolderName.InternalStorage, LocalBackupStore.folderName(root))
        assertEquals(resources("es").getString(R.string.backup_label_internal_storage), BackupFolderName.InternalStorage.label(resources("es")))
        assertEquals(BackupFolderName.SdCard("Backups"), LocalBackupStore.folderName(Uri.parse("content://com.android.externalstorage.documents/tree/1234-ABCD%3ABackups")))
        // Other providers: older versions stored English placeholders as the label; they read as "no name".
        val drive = Uri.parse("content://com.example.provider/tree/folder-1")
        preferences.edit().putString("folderUri", drive.toString()).putString("folderLabel", "Selected folder").commit()
        assertEquals(BackupFolderName.Selected, store.read().folderName)
        assertEquals(resources("pt").getString(R.string.backup_label_selected_folder), store.read().folderName!!.label(resources("pt")))
        preferences.edit().putString("folderLabel", "Mis copias").commit()
        assertEquals(BackupFolderName.Named("Mis copias"), store.read().folderName)
    }

    @Test fun backupFailuresAreStoredAsCodesAndOldEnglishErrorsShowTheGenericMessage() {
        val store = LocalBackupStore(target, "test-$id-backup")
        store.configure(Uri.parse("content://com.android.externalstorage.documents/tree/primary%3ADocuments"), null, enabled = true)
        assertNull(BackupProblems.message(resources("es"), store.read()))
        store.error(store.read(), "Folder access is no longer available.", BackupProblems.code(SecurityException("revoked")))
        assertEquals(resources("es").getString(R.string.error_backup_folder_access), BackupProblems.message(resources("es"), store.read()))
        store.error(store.read(), "Could not create a backup in the selected folder.", BackupProblems.code(UserIoException(R.string.error_backup_create_file, "x")))
        assertEquals(resources("pt").getString(R.string.error_backup_create_file), BackupProblems.message(resources("pt"), store.read()))
        // An error written by an older version: English text, no code.
        preferences.edit().putString("error", "Could not finish the backup.").remove("errorCode").commit()
        assertEquals(resources("es").getString(R.string.error_backup_failed), BackupProblems.message(resources("es"), store.read()))
        assertEquals("failed", BackupProblems.code(RuntimeException("disk full")))
    }
}
