package com.allient.folio

import kotlin.test.Test
import kotlin.test.assertEquals

class AppLanguagesTest {
    @Test fun systemLanguageIsUsedWhenSupported() {
        assertEquals("es", AppLanguages.resolve(listOf("es-MX")))
        assertEquals("pt", AppLanguages.resolve(listOf("pt-BR")))
        assertEquals("en", AppLanguages.resolve(listOf("en-GB")))
    }

    @Test fun newerLanguagesAreUsedFromTheSystem() {
        assertEquals("fr", AppLanguages.resolve(listOf("fr-CA")))
        assertEquals("de", AppLanguages.resolve(listOf("de-AT")))
        assertEquals("hi", AppLanguages.resolve(listOf("hi-IN")))
        assertEquals("zh", AppLanguages.resolve(listOf("zh-Hans-CN")))
    }

    @Test fun unsupportedSystemLanguageFallsBackToEnglish() {
        assertEquals("en", AppLanguages.resolve(listOf("it-IT")))
        assertEquals("en", AppLanguages.resolve(emptyList()))
    }

    @Test fun firstSupportedLanguageInThePreferenceListWins() {
        assertEquals("pt", AppLanguages.resolve(listOf("it-IT", "pt-PT", "es-ES")))
    }

    @Test fun tagsAreMatchedByLanguageWhateverTheirCaseOrSeparator() {
        assertEquals("es", AppLanguages.resolve(listOf("ES_ar")))
    }

    @Test fun choiceMeansSystemWhenEmptyOrUnknown() {
        assertEquals("", AppLanguages.choice(""))
        assertEquals("", AppLanguages.choice("it"))
        assertEquals("pt", AppLanguages.choice("pt-BR"))
        assertEquals("es", AppLanguages.choice("es"))
    }

    @Test fun everyLanguageHasAFlag() {
        assertEquals(listOf("🇺🇸", "🇪🇸", "🇧🇷", "🇫🇷", "🇩🇪", "🇮🇳", "🇨🇳"), AppLanguages.supported.map { it.flag })
    }

    @Test fun everyLanguageHasItsOwnName() {
        assertEquals(listOf("en", "es", "pt", "fr", "de", "hi", "zh"), AppLanguages.supported.map { it.tag })
        assertEquals(listOf("English", "Español", "Português (Brasil)", "Français", "Deutsch", "हिन्दी", "简体中文"), AppLanguages.supported.map { it.nativeName })
    }
}
