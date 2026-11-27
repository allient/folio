package com.allient.folio

import kotlin.test.Test
import kotlin.test.assertEquals

class AppLanguagesTest {
    @Test fun systemLanguageIsUsedWhenSupported() {
        assertEquals("es", AppLanguages.resolve(listOf("es-MX")))
        assertEquals("pt", AppLanguages.resolve(listOf("pt-BR")))
        assertEquals("en", AppLanguages.resolve(listOf("en-GB")))
    }

    @Test fun unsupportedSystemLanguageFallsBackToEnglish() {
        assertEquals("en", AppLanguages.resolve(listOf("fr-FR")))
        assertEquals("en", AppLanguages.resolve(emptyList()))
    }

    @Test fun firstSupportedLanguageInThePreferenceListWins() {
        assertEquals("pt", AppLanguages.resolve(listOf("fr-FR", "pt-PT", "es-ES")))
    }

    @Test fun tagsAreMatchedByLanguageWhateverTheirCaseOrSeparator() {
        assertEquals("es", AppLanguages.resolve(listOf("ES_ar")))
    }

    @Test fun choiceMeansSystemWhenEmptyOrUnknown() {
        assertEquals("", AppLanguages.choice(""))
        assertEquals("", AppLanguages.choice("fr"))
        assertEquals("pt", AppLanguages.choice("pt-BR"))
        assertEquals("es", AppLanguages.choice("es"))
    }

    @Test fun everyLanguageHasItsOwnName() {
        assertEquals(listOf("en", "es", "pt"), AppLanguages.supported.map { it.tag })
        assertEquals(listOf("English", "Español", "Português (Brasil)"), AppLanguages.supported.map { it.nativeName })
    }
}
