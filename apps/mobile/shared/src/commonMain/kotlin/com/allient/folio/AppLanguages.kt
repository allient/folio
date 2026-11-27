package com.allient.folio

/** A language Folio ships translations for. [tag] is the BCP 47 language (the `values-<tag>` resource folders). */
data class AppLanguage(val tag: String, val nativeName: String)

/**
 * The languages Folio is translated into. English is the default and the fallback for any other system language.
 *
 * Adding a language: add `values-<tag>/strings.xml` to `shared/src/commonMain/composeResources` and
 * `androidApp/src/main/res`, then add it here. Names stay in their own language (they are never translated).
 */
object AppLanguages {
    const val DEFAULT = "en"

    val supported = listOf(
        AppLanguage("en", "English"),
        AppLanguage("es", "Español"),
        AppLanguage("pt", "Português (Brasil)"),
    )

    private fun language(tag: String) = tag.replace('_', '-').substringBefore('-').lowercase()

    /** The language the app shows for the user's preferred languages, in order: the first one Folio has, else English. */
    fun resolve(preferred: List<String>): String =
        preferred.map(::language).firstOrNull { tag -> supported.any { it.tag == tag } } ?: DEFAULT

    /** A stored choice, normalised: a supported language, or "" for "follow the system". */
    fun choice(stored: String): String = language(stored).takeIf { tag -> supported.any { it.tag == tag } } ?: ""
}
