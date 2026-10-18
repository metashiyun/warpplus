package com.scchan.warpplus;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/** A left-to-right hierarchy: com is the parent of com.example. */
public record DomainName(String value) implements Comparable<DomainName> {
    private static final Pattern LABEL = Pattern.compile("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?");

    public DomainName {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Enter a domain, for example com.example.");
        }
        value = value.toLowerCase(Locale.ROOT);
        String[] labels = value.split("\\.", -1);
        if (value.length() > 253 || labels.length > 16) {
            throw new IllegalArgumentException("Domains may contain at most 253 characters and 16 labels.");
        }
        for (String label : labels) {
            if (!LABEL.matcher(label).matches()) {
                throw new IllegalArgumentException("Use dot-separated labels of 1-63 letters, digits, or internal hyphens.");
            }
        }
    }

    public Optional<DomainName> parent() {
        int dot = value.lastIndexOf('.');
        return dot < 0 ? Optional.empty() : Optional.of(new DomainName(value.substring(0, dot)));
    }

    public boolean isDescendantOf(DomainName ancestor) {
        return value.startsWith(ancestor.value + ".");
    }

    @Override
    public int compareTo(DomainName other) { return value.compareTo(other.value); }

    @Override
    public String toString() { return value; }
}
