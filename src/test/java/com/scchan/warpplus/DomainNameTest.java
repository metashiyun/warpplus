package com.scchan.warpplus;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class DomainNameTest {
    @Test
    void normalizesNamesAndUsesLeftToRightHierarchy() {
        var domain = new DomainName("COM.Example.Market");
        assertEquals("com.example.market", domain.value());
        assertEquals(new DomainName("com.example"), domain.parent().orElseThrow());
        assertTrue(new DomainName("com").parent().isEmpty());
        assertTrue(domain.isDescendantOf(new DomainName("com.example")));
        assertFalse(new DomainName("com.examples").isDescendantOf(new DomainName("com.example")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", ".com", "com.", "com..shop", "com.-shop", "com.shop-", "com.a_b", "com.a b", "com/market", "com.商店"})
    void rejectsMalformedNames(String value) {
        assertThrows(IllegalArgumentException.class, () -> new DomainName(value));
    }

    @Test
    void enforcesLabelDomainAndDepthLimits() {
        assertDoesNotThrow(() -> new DomainName("a".repeat(63)));
        assertThrows(IllegalArgumentException.class, () -> new DomainName("a".repeat(64)));
        assertThrows(IllegalArgumentException.class, () -> new DomainName(("a".repeat(63) + ".").repeat(3) + "a".repeat(62)));
        assertThrows(IllegalArgumentException.class, () -> new DomainName("a.".repeat(16) + "a"));
        assertThrows(IllegalArgumentException.class, () -> new DomainName(null));
    }

    @Test
    void normalizationIsIndependentOfServerLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr"));
            assertEquals("info", new DomainName("INFO").value());
        } finally {
            Locale.setDefault(previous);
        }
    }
}
