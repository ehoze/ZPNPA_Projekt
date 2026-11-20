package pl.edu.zpnpa.skaner

import org.junit.Assert.*
import org.junit.Test
import pl.edu.zpnpa.skaner.domain.DocumentRules
import pl.edu.zpnpa.skaner.domain.SearchText

class DocumentRulesTest {
    @Test fun blankTitleIsRejected() { assertNotNull(DocumentRules.titleError(" \n\t")) }
    @Test fun titleAtLimitIsAllowed() { assertNull(DocumentRules.titleError("a".repeat(100))) }
    @Test fun titleAboveLimitIsRejected() { assertNotNull(DocumentRules.titleError("a".repeat(101))) }
    @Test fun surroundingSpacesAreIgnored() { assertNull(DocumentRules.titleError("  " + "a".repeat(100) + "  ")) }
    @Test fun emptyOcrTextIsAllowed() { assertNull(DocumentRules.textError("")) }
    @Test fun largeTextIsRejected() { assertNotNull(DocumentRules.textError("a".repeat(200_001))) }
    @Test fun polishSearchIsCaseAndAccentInsensitive() {
        assertEquals("zazolc gesla jazn", SearchText.normalize("ZAŻÓŁĆ GĘŚLĄ JAŹŃ"))
    }
    @Test fun wildcardCharactersAreEscaped() {
        assertEquals("12\\%\\_vat\\\\2026", SearchText.likeQuery("12%_VAT\\2026"))
    }
}
