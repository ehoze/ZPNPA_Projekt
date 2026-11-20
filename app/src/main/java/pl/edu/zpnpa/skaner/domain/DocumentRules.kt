package pl.edu.zpnpa.skaner.domain

import java.text.Normalizer
import java.util.Locale

object DocumentRules {
    const val MAX_TITLE = 100
    const val MAX_TEXT = 200_000
    const val MAX_PAGES = 10

    fun titleError(title: String): String? = when {
        title.isBlank() -> "Wpisz nazwę dokumentu."
        title.trim().length > MAX_TITLE -> "Nazwa może mieć maksymalnie $MAX_TITLE znaków."
        else -> null
    }

    fun textError(text: String): String? =
        if (text.length > MAX_TEXT) "Tekst może mieć maksymalnie $MAX_TEXT znaków." else null
}

/** One representation for both the stored index and the search query, including Polish ł. */
object SearchText {
    fun normalize(value: String): String = Normalizer.normalize(
        value.lowercase(Locale.ROOT).replace('ł', 'l'), Normalizer.Form.NFD
    ).replace(Regex("\\p{M}+"), "")

    fun likeQuery(value: String): String = normalize(value.trim())
        .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
}
