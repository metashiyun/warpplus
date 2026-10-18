package com.scchan.warpplus;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Domains are values in a YAML list, never paths (dots must remain literal). */
public final class YamlRegistryStore implements RegistryStore {
    private final Path file;

    public YamlRegistryStore(Path file) { this.file = file.toAbsolutePath(); }

    @Override
    public List<WarpRecord> load() throws IOException {
        if (!Files.exists(file)) return List.of();
        var yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(Files.readString(file, StandardCharsets.UTF_8));
            if (!Integer.valueOf(1).equals(yaml.get("schema-version"))) {
                throw new IllegalArgumentException("Unsupported or missing schema-version.");
            }
            Object entries = yaml.get("warps");
            if (!(entries instanceof List<?> list)) throw new IllegalArgumentException("warps must be a list.");
            var result = new ArrayList<WarpRecord>();
            for (Object entry : list) result.add(decode(map(entry)));
            return List.copyOf(result);
        } catch (InvalidConfigurationException | RuntimeException ex) {
            throw new IOException("Invalid registry " + file + ": " + ex.getMessage(), ex);
        }
    }

    @Override
    public void save(Collection<WarpRecord> records) throws IOException {
        var yaml = new YamlConfiguration();
        yaml.set("schema-version", 1);
        yaml.set("warps", records.stream().map(this::encode).toList());
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), "registry-", ".tmp");
        try {
            Files.writeString(temporary, yaml.saveToString(), StandardCharsets.UTF_8);
            // If atomic replacement is unsupported, fail rather than risk truncating the live registry.
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private Map<String, Object> encode(WarpRecord record) {
        var entry = new LinkedHashMap<String, Object>();
        entry.put("domain", record.domain().value());
        entry.put("owner", record.owner() == null ? null : record.owner().toString());
        entry.put("created-at", record.createdAt().toString());
        entry.put("updated-at", record.updatedAt().toString());
        var metadata = new LinkedHashMap<String, Object>();
        for (MetadataField field : MetadataField.values()) {
            if (record.metadata().containsKey(field)) metadata.put(field.key(), record.metadata().get(field));
        }
        entry.put("metadata", metadata);
        WarpLocation location = record.location();
        if (location != null) {
            var destination = new LinkedHashMap<String, Object>();
            destination.put("world-id", location.worldId().toString());
            destination.put("world-name", location.worldName());
            destination.put("x", location.x());
            destination.put("y", location.y());
            destination.put("z", location.z());
            destination.put("yaw", location.yaw());
            destination.put("pitch", location.pitch());
            entry.put("location", destination);
        }
        return entry;
    }

    private WarpRecord decode(Map<?, ?> entry) {
        DomainName domain = new DomainName(string(entry, "domain"));
        UUID owner = entry.get("owner") == null ? null : UUID.fromString(string(entry, "owner"));
        var metadata = new EnumMap<MetadataField, String>(MetadataField.class);
        for (var field : map(entry.get("metadata")).entrySet()) {
            if (!(field.getKey() instanceof String key) || !(field.getValue() instanceof String value)) {
                throw new IllegalArgumentException("Metadata keys and values must be text.");
            }
            metadata.put(MetadataField.parse(key), value);
        }
        WarpLocation location = null;
        if (entry.get("location") != null) {
            Map<?, ?> destination = map(entry.get("location"));
            location = new WarpLocation(UUID.fromString(string(destination, "world-id")), string(destination, "world-name"),
                    number(destination, "x"), number(destination, "y"), number(destination, "z"),
                    (float) number(destination, "yaw"), (float) number(destination, "pitch"));
        }
        return new WarpRecord(domain, owner, Instant.parse(string(entry, "created-at")),
                Instant.parse(string(entry, "updated-at")), metadata, location);
    }

    private static Map<?, ?> map(Object value) {
        if (!(value instanceof Map<?, ?> result)) throw new IllegalArgumentException("Expected a registry object.");
        return result;
    }

    private static String string(Map<?, ?> map, String key) {
        if (!(map.get(key) instanceof String value)) throw new IllegalArgumentException("Expected text for " + key);
        return value;
    }

    private static double number(Map<?, ?> map, String key) {
        if (!(map.get(key) instanceof Number value)) throw new IllegalArgumentException("Expected number for " + key);
        return value.doubleValue();
    }
}
