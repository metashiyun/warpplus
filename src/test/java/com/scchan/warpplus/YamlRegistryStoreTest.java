package com.scchan.warpplus;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.scchan.warpplus.WarpRegistryTest.*;
import static org.junit.jupiter.api.Assertions.*;

class YamlRegistryStoreTest {
    @TempDir Path directory;

    @Test
    void roundTripsDottedDomainsMetadataWorldAndNullableOwner() throws IOException {
        var file = directory.resolve("plugin/registry.yml");
        var store = new YamlRegistryStore(file);
        assertTrue(store.load().isEmpty());
        var child = new WarpRecord(CHILD, BOB, NOW, NOW,
                Map.of(MetadataField.NAME, "Market: 商店", MetadataField.CONTACT, "discord: example#1234"),
                new WarpLocation(UUID.randomUUID(), "world_nether", -12.5, 64, 33, 45, -12));
        var root = new WarpRecord(ROOT, null, NOW, NOW, Map.of(), null);
        store.save(List.of(root, child));
        assertEquals(List.of(root, child), store.load());
        assertTrue(Files.readString(file).contains("domain: com.example"));
        store.save(List.of(root));
        assertEquals(List.of(root), store.load());
        try (var files = Files.list(file.getParent())) { assertEquals(1, files.count()); }
    }

    @Test
    void malformedYamlIsPreservedAndRejected() throws IOException {
        Path file = directory.resolve("registry.yml");
        String broken = "schema-version: 1\nwarps: [unterminated";
        Files.writeString(file, broken);
        assertThrows(IOException.class, () -> new YamlRegistryStore(file).load());
        assertEquals(broken, Files.readString(file));
    }

    @Test
    void rejectsUnknownSchemaWrongTypesAndInvalidDates() throws IOException {
        Path file = directory.resolve("registry.yml");
        var store = new YamlRegistryStore(file);
        for (String invalid : List.of("schema-version: 99\nwarps: []", "schema-version: 1\nwarps: {}", "")) {
            Files.writeString(file, invalid);
            assertThrows(IOException.class, store::load);
        }
        store.save(List.of(record(ROOT)));
        String valid = Files.readString(file);
        Files.writeString(file, valid.replace(NOW.toString(), "not-a-date"));
        assertThrows(IOException.class, store::load);
        Files.writeString(file, valid.replace("domain: com", "domain: 123"));
        assertThrows(IOException.class, store::load);
    }

    @Test
    void failedReplacementLeavesOriginalRegistryUntouchedAndCleansTemporaryFile() throws IOException {
        // A directory at the destination makes replacement fail on every supported filesystem.
        Path destination = Files.createDirectory(directory.resolve("registry.yml"));
        Files.writeString(destination.resolve("sentinel"), "preserve");
        assertThrows(IOException.class, () -> new YamlRegistryStore(destination).save(List.of(record(ROOT))));
        assertEquals("preserve", Files.readString(destination.resolve("sentinel")));
        try (var files = Files.list(directory)) { assertEquals(1, files.count()); }
    }

    @Test
    void migratesVersionOneOnlyOnSaveAndRoundTripsLinksInVersionTwo() throws IOException {
        Path file = directory.resolve("registry.yml");
        var store = new YamlRegistryStore(file);
        store.save(List.of(record(ROOT)));
        String versionOne = Files.readString(file).replace("schema-version: 2", "schema-version: 1");
        Files.writeString(file, versionOne);
        assertEquals(List.of(record(ROOT)), store.load());
        assertEquals(versionOne, Files.readString(file));
        var registry = new WarpRegistry(store, CLOCK);
        registry.link(ADMIN, CHILD, "Existing_Market");
        assertTrue(Files.readString(file).contains("schema-version: 2"));
        assertEquals("Existing_Market", new WarpRegistry(store, CLOCK).require(CHILD).essentialsWarp());
        registry.setField(ADMIN, CHILD, MetadataField.CONTACT, "Alex");
        registry.transfer(ADMIN, CHILD, BOB);
        assertEquals("Existing_Market", new WarpRegistry(store, CLOCK).require(CHILD).essentialsWarp());
    }

    @Test
    void refusesRecordsWithBothBackendsOrWrongLinkTypes() throws IOException {
        Path file = directory.resolve("registry.yml");
        var store = new YamlRegistryStore(file);
        var destination = new WarpLocation(UUID.randomUUID(), "world", 1, 64, 2, 0, 0);
        store.save(List.of(record(ROOT).withLocation(destination, NOW)));
        String local = Files.readString(file);
        Files.writeString(file, local.replace("  location:", "  essentials-warp: spawn\n  location:"));
        assertThrows(IOException.class, store::load);
        store.save(List.of(record(ROOT).withEssentialsWarp("spawn", NOW)));
        String linked = Files.readString(file);
        Files.writeString(file, linked.replace("essentials-warp: spawn", "essentials-warp: []"));
        assertThrows(IOException.class, store::load);
        Files.writeString(file, linked.replace("schema-version: 2", "schema-version: 1"));
        assertThrows(IOException.class, store::load);
    }

    @Test
    void oldRegistriesLoadWithoutRetiredFieldsAndRemoveThemOnlyOnSave() throws IOException {
        Path file = directory.resolve("registry.yml");
        var store = new YamlRegistryStore(file);
        var expected = record(ROOT).withField(MetadataField.NAME, "Example", NOW)
                .withField(MetadataField.ORGANIZATION, "Example Guild", NOW);
        for (int schema : List.of(1, 2)) {
            store.save(List.of(expected));
            String legacy = Files.readString(file).replace("schema-version: 2", "schema-version: " + schema)
                    .replace("    name: Example", "    poi: Old landmark\n    maintainer: Alex\n    name: Example");
            assertTrue(legacy.contains("maintainer: Alex"));
            Files.writeString(file, legacy);
            assertEquals(List.of(expected), store.load());
            assertEquals(legacy, Files.readString(file));
            store.save(store.load());
            String saved = Files.readString(file);
            assertFalse(saved.contains("poi:"));
            assertFalse(saved.contains("maintainer:"));
            assertEquals(List.of(expected), store.load());
        }
    }
}
