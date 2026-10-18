package com.scchan.warpplus;

import java.util.Arrays;
import java.util.Locale;

public enum MetadataField {
    NAME(120), ORGANIZATION(160), POI(160), MAINTAINER(120), CONTACT(240), WEBSITE(240), DESCRIPTION(500);

    private final int maxLength;

    MetadataField(int maxLength) { this.maxLength = maxLength; }

    public String key() { return name().toLowerCase(Locale.ROOT); }

    public String validate(String value) {
        if (value == null || value.length() > maxLength || value.codePoints().anyMatch(Character::isISOControl)
                || value.indexOf('\u00a7') >= 0) {
            throw new IllegalArgumentException(key() + " must be plain text of at most " + maxLength + " characters.");
        }
        return value.strip();
    }

    public static MetadataField parse(String value) {
        return Arrays.stream(values()).filter(field -> field.key().equalsIgnoreCase(value)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Fields: name, organization, poi, maintainer, contact, website, description."));
    }
}
