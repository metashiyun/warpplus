package com.scchan.warpplus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WarpRegistryTest {
    static final Instant NOW = Instant.parse("2026-10-17T12:00:00Z");
    static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    static final UUID ALICE = UUID.fromString("b55a6251-af2e-4152-bd41-b35916890c13");
    static final UUID BOB = UUID.fromString("5f6e541a-5f60-4f61-a124-a7105b408661");
    static final Actor ADMIN = new Actor(ALICE, true, true, true);
    static final Actor ALICE_USER = new Actor(ALICE, false, true, false);
    static final Actor BOB_USER = new Actor(BOB, false, true, false);
    static final DomainName ROOT = new DomainName("com");
    static final DomainName CHILD = new DomainName("com.example");
    static final DomainName GRANDCHILD = new DomainName("com.example.market");
    MemoryStore store;
    WarpRegistry registry;

    @BeforeEach
    void setUp() throws IOException {
        store = new MemoryStore();
        registry = new WarpRegistry(store, CLOCK);
    }

    @Test
    void ordinaryPlayersNeedDelegationAndCannotClaimRoots() {
        assertThrows(IllegalArgumentException.class, () -> registry.register(BOB_USER, ROOT, null));
        registryRoot();
        assertThrows(IllegalArgumentException.class, () -> registry.register(BOB_USER, CHILD, null));
        assertDoesNotThrow(() -> registry.register(ALICE_USER, CHILD, null));
    }

    @Test
    void registrarCanCreateRootsWithoutBeingGlobalAdmin() throws IOException {
        registry.register(new Actor(BOB, false, true, true), ROOT, null);
        assertEquals(BOB, registry.require(ROOT).owner());
        assertThrows(IllegalArgumentException.class, () -> registry.register(new Actor(BOB, false, false, true), new DomainName("org"), null));
    }

    @Test
    void requiresParentsAndRejectsCaseInsensitiveDuplicates() throws IOException {
        assertThrows(IllegalArgumentException.class, () -> registry.register(ADMIN, CHILD, null));
        registry.register(ADMIN, ROOT, null);
        assertThrows(IllegalArgumentException.class, () -> registry.register(ADMIN, new DomainName("COM"), null));
    }

    @Test
    void delegationGrantsSubtreeAuthorityButNeverParentOrSiblingAuthority() throws IOException {
        registry.register(ADMIN, ROOT, null);
        registry.register(ALICE_USER, CHILD, null);
        registry.register(ALICE_USER, new DomainName("com.examples"), null);
        registry.transfer(ALICE_USER, CHILD, BOB);
        registry.register(BOB_USER, GRANDCHILD, null);
        assertTrue(registry.canManage(BOB_USER, GRANDCHILD));
        assertTrue(registry.canManage(ALICE_USER, GRANDCHILD));
        assertFalse(registry.canManage(BOB_USER, ROOT));
        assertFalse(registry.canManage(BOB_USER, new DomainName("com.examples")));
        assertThrows(IllegalArgumentException.class, () -> registry.setField(BOB_USER, ROOT, MetadataField.NAME, "Taken"));
    }

    @Test
    void revokedRegisterPermissionPreventsEditsEvenForOwners() throws IOException {
        registry.register(ADMIN, ROOT, null);
        assertFalse(registry.canManage(new Actor(ALICE, false, false, true), ROOT));
        assertThrows(IllegalArgumentException.class, () -> registry.delete(new Actor(ALICE, false, false, true), ROOT));
    }

    @Test
    void consoleNamespacesAreAdminManagedUntilTransferred() throws IOException {
        registry.register(new Actor(null, true, true, true), ROOT, null);
        assertNull(registry.require(ROOT).owner());
        assertFalse(registry.canManage(new Actor(null, false, true, true), ROOT));
        registry.transfer(ADMIN, ROOT, BOB);
        assertTrue(registry.canManage(BOB_USER, ROOT));
    }

    @Test
    void transferringParentChangesInheritedAuthorityButPreservesChildOwnership() throws IOException {
        registry.register(ADMIN, ROOT, null);
        registry.register(ALICE_USER, CHILD, null);
        registry.transfer(ADMIN, ROOT, BOB);
        assertTrue(registry.canManage(BOB_USER, CHILD));
        assertTrue(registry.canManage(ALICE_USER, CHILD));
        assertFalse(registry.canManage(ALICE_USER, ROOT));
        assertEquals(ALICE, registry.require(CHILD).owner());
    }

    @Test
    void onlyLeafDomainsCanBeDeleted() throws IOException {
        registry.register(ADMIN, ROOT, null);
        registry.register(ADMIN, CHILD, null);
        assertThrows(IllegalArgumentException.class, () -> registry.delete(ADMIN, ROOT));
        registry.delete(ADMIN, CHILD);
        registry.delete(ADMIN, ROOT);
        assertTrue(registry.names().isEmpty());
    }

    @Test
    void childrenAreDirectAndSorted() throws IOException {
        registry.register(ADMIN, ROOT, null);
        registry.register(ADMIN, new DomainName("com.zoo"), null);
        registry.register(ADMIN, CHILD, null);
        registry.register(ADMIN, GRANDCHILD, null);
        assertEquals(List.of(ROOT), registry.children(null).stream().map(WarpRecord::domain).toList());
        assertEquals(List.of(CHILD, new DomainName("com.zoo")), registry.children(ROOT).stream().map(WarpRecord::domain).toList());
    }

    @Test
    void metadataIsPlainTextAndCanBeClearedWithoutChangingCreationTime() throws IOException {
        registry.register(ADMIN, ROOT, null);
        var later = new WarpRegistry(store, Clock.offset(CLOCK, java.time.Duration.ofDays(1)));
        later.setField(ADMIN, ROOT, MetadataField.NAME, "  Market 商店  ");
        assertEquals("Market 商店", later.require(ROOT).metadata().get(MetadataField.NAME));
        assertEquals(NOW, later.require(ROOT).createdAt());
        assertEquals(NOW.plusSeconds(86400), later.require(ROOT).updatedAt());
        assertThrows(IllegalArgumentException.class, () -> later.setField(ADMIN, ROOT, MetadataField.CONTACT, "a\nb"));
        assertThrows(IllegalArgumentException.class, () -> later.setField(ADMIN, ROOT, MetadataField.NAME, "§4Admin"));
        assertThrows(IllegalArgumentException.class, () -> later.setField(ADMIN, ROOT, MetadataField.NAME, "x".repeat(121)));
        later.setField(ADMIN, ROOT, MetadataField.NAME, "");
        assertFalse(later.require(ROOT).metadata().containsKey(MetadataField.NAME));
    }

    @Test
    void locationAndMetadataSurviveRegistryReconstruction() throws IOException {
        registry.register(ADMIN, ROOT, null);
        var location = new WarpLocation(UUID.randomUUID(), "world", 1.25, 65, -30.5, 90, -5);
        registry.setLocation(ADMIN, ROOT, location);
        registry.setField(ADMIN, ROOT, MetadataField.ORGANIZATION, "Example Guild");
        var reloaded = new WarpRegistry(store, CLOCK);
        assertEquals(registry.require(ROOT), reloaded.require(ROOT));
        reloaded.setLocation(ADMIN, ROOT, null);
        assertNull(reloaded.require(ROOT).location());
    }

    @Test
    void failedSavesNeverPublishRegistrationEditTransferOrDeletion() throws IOException {
        registry.register(ADMIN, ROOT, null);
        WarpRecord original = registry.require(ROOT);
        store.fail = true;
        assertThrows(IOException.class, () -> registry.register(ADMIN, CHILD, null));
        assertThrows(IOException.class, () -> registry.setField(ADMIN, ROOT, MetadataField.NAME, "Lost"));
        assertThrows(IOException.class, () -> registry.transfer(ADMIN, ROOT, BOB));
        assertThrows(IOException.class, () -> registry.setLocation(ADMIN, ROOT, new WarpLocation(UUID.randomUUID(), "world", 0, 64, 0, 0, 0)));
        assertThrows(IOException.class, () -> registry.delete(ADMIN, ROOT));
        assertEquals(List.of(ROOT), registry.names());
        assertEquals(original, registry.require(ROOT));
        assertEquals(List.of(original), store.saved);
    }

    @Test
    void rejectsDuplicateAndOrphanedRecordsDuringStartup() {
        var root = record(ROOT);
        store.saved = List.of(root, root);
        assertThrows(IOException.class, () -> new WarpRegistry(store, CLOCK));
        store.saved = List.of(record(CHILD));
        assertThrows(IOException.class, () -> new WarpRegistry(store, CLOCK));
    }

    @Test
    void refusesNonFiniteDestinations() {
        assertThrows(IllegalArgumentException.class, () -> new WarpLocation(UUID.randomUUID(), "world", Double.NaN, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new WarpLocation(UUID.randomUUID(), "world", 0, 0, 0, Float.POSITIVE_INFINITY, 0));
    }

    static WarpRecord record(DomainName name) { return new WarpRecord(name, ALICE, NOW, NOW, Map.of(), null); }

    private void registryRoot() { assertDoesNotThrow(() -> registry.register(ADMIN, ROOT, null)); }

    static class MemoryStore implements RegistryStore {
        List<WarpRecord> saved = List.of();
        boolean fail;
        public List<WarpRecord> load() { return saved; }
        public void save(Collection<WarpRecord> records) throws IOException {
            if (fail) throw new IOException("Simulated full disk");
            saved = List.copyOf(records);
        }
    }
}
