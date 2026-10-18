package com.scchan.warpplus;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record WarpRecord(DomainName domain, UUID owner, Instant createdAt, Instant updatedAt,
                         Map<MetadataField, String> metadata, WarpLocation location) {
    public WarpRecord {
        Objects.requireNonNull(domain, "domain");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        metadata.forEach((field, value) -> field.validate(value));
        metadata = Map.copyOf(metadata);
    }

    public WarpRecord withField(MetadataField field, String value, Instant now) {
        var fields = new EnumMap<MetadataField, String>(MetadataField.class);
        fields.putAll(metadata);
        String validated = field.validate(value);
        if (validated.isEmpty()) fields.remove(field);
        else fields.put(field, validated);
        return new WarpRecord(domain, owner, createdAt, now, fields, location);
    }

    public WarpRecord withLocation(WarpLocation destination, Instant now) {
        return new WarpRecord(domain, owner, createdAt, now, metadata, destination);
    }

    public WarpRecord withOwner(UUID newOwner, Instant now) {
        return new WarpRecord(domain, Objects.requireNonNull(newOwner), createdAt, now, metadata, location);
    }
}
