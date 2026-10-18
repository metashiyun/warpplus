package com.scchan.warpplus;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record WarpRecord(DomainName domain, UUID owner, Instant createdAt, Instant updatedAt,
                         Map<MetadataField, String> metadata, WarpLocation location, String essentialsWarp) {
    public WarpRecord {
        Objects.requireNonNull(domain, "domain");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        metadata.forEach((field, value) -> field.validate(value));
        metadata = Map.copyOf(metadata);
        if (essentialsWarp != null) {
            essentialsWarp = validateEssentialsWarp(essentialsWarp);
            if (location != null) throw new IllegalArgumentException("A domain cannot have both a local destination and an EssentialsX link.");
        }
    }

    public WarpRecord(DomainName domain, UUID owner, Instant createdAt, Instant updatedAt,
                      Map<MetadataField, String> metadata, WarpLocation location) {
        this(domain, owner, createdAt, updatedAt, metadata, location, null);
    }

    public static String validateEssentialsWarp(String name) {
        if (name.isEmpty() || name.length() > 255 || name.codePoints().anyMatch(code -> Character.isWhitespace(code)
                || Character.isSpaceChar(code) || Character.isISOControl(code)) || name.contains("/") || name.contains("\\")) {
            throw new IllegalArgumentException("An EssentialsX warp name must be a single name without spaces or slashes (1-255 characters).");
        }
        return name;
    }

    public boolean hasDestination() { return location != null || essentialsWarp != null; }

    public WarpRecord withField(MetadataField field, String value, Instant now) {
        var fields = new EnumMap<MetadataField, String>(MetadataField.class);
        fields.putAll(metadata);
        String validated = field.validate(value);
        if (validated.isEmpty()) fields.remove(field);
        else fields.put(field, validated);
        return new WarpRecord(domain, owner, createdAt, now, fields, location, essentialsWarp);
    }

    public WarpRecord withLocation(WarpLocation destination, Instant now) {
        return new WarpRecord(domain, owner, createdAt, now, metadata, destination);
    }

    public WarpRecord withOwner(UUID newOwner, Instant now) {
        return new WarpRecord(domain, Objects.requireNonNull(newOwner), createdAt, now, metadata, location, essentialsWarp);
    }

    public WarpRecord withEssentialsWarp(String warp, Instant now) {
        return new WarpRecord(domain, owner, createdAt, now, metadata, null, warp);
    }
}
