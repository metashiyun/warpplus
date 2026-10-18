package com.scchan.warpplus;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import static com.scchan.warpplus.WarpRegistryTest.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.Mockito.*;

class WarpCommandTest {
    WarpPlusPlugin plugin;
    Server server;
    WarpRegistry registry;
    WarpRegistryTest.MemoryStore store;
    WarpCommand executor;
    Command command;
    CommandSender console;
    Player player;

    @BeforeEach
    void setUp() throws IOException {
        plugin = mock(WarpPlusPlugin.class);
        server = mock(Server.class);
        when(plugin.getServer()).thenReturn(server);
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        store = new WarpRegistryTest.MemoryStore();
        registry = new WarpRegistry(store, CLOCK);
        registry.register(ADMIN, ROOT, null);
        executor = new WarpCommand(plugin, registry);
        command = mock(Command.class);
        when(command.getName()).thenReturn("warpplus");
        console = mock(CommandSender.class);
        when(console.hasPermission(anyString())).thenReturn(true);
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(BOB);
        when(player.hasPermission("warpplus.use")).thenReturn(true);
        when(player.hasPermission("warpplus.register")).thenReturn(true);
    }

    @Test
    void deniedUsersCannotReadOrMutateRegistry() {
        when(console.hasPermission("warpplus.use")).thenReturn(false);
        run(console, "namespace", "org");
        assertEquals(List.of(ROOT), registry.names());
        assertTrue(messages(console).contains("permission"));
    }

    @Test
    void consoleCanCreateNamespacesAndDelegateToUuid() {
        run(console, "namespace", "com.example");
        assertNull(registry.require(CHILD).owner());
        run(console, "transfer", "com.example", BOB.toString());
        assertEquals(BOB, registry.require(CHILD).owner());
        run(player, "set", "com.example", "organization", "Example", "Guild");
        assertEquals("Example Guild", registry.require(CHILD).metadata().get(MetadataField.ORGANIZATION));
        run(player, "set", "com.example", "organization", "-");
        assertTrue(registry.require(CHILD).metadata().isEmpty());
    }

    @Test
    void consoleCannotRegisterAtAnInventedPosition() {
        run(console, "register", "com.example");
        assertEquals(List.of(ROOT), registry.names());
        assertTrue(messages(console).contains("in-game player"));
    }

    @Test
    void registrationUsesThePlayersWorldPositionAndOrientation() throws IOException {
        registry.transfer(ADMIN, ROOT, BOB);
        World world = mock(World.class);
        UUID worldId = UUID.randomUUID();
        when(world.getUID()).thenReturn(worldId);
        when(world.getName()).thenReturn("world");
        when(player.getLocation()).thenReturn(new Location(world, 4.25, 70, -9.5, 45, 10));
        run(player, "register", "com.example");
        assertEquals(new WarpLocation(worldId, "world", 4.25, 70, -9.5, 45, 10), registry.require(CHILD).location());
        assertEquals(BOB, registry.require(CHILD).owner());
    }

    @Test
    void whoisAliasShowsPublicMetadataOwnerAndNamespaceStatus() throws IOException {
        registry.setField(ADMIN, ROOT, MetadataField.CONTACT, "guild@example.com");
        registry.setField(ADMIN, ROOT, MetadataField.ORGANIZATION, "Example Team");
        var offline = mock(org.bukkit.OfflinePlayer.class);
        when(server.getOfflinePlayer(ALICE)).thenReturn(offline);
        when(offline.getName()).thenReturn("Alice");
        lookup(player, "com");
        String text = messages(player);
        assertTrue(text.contains("WHOIS com"));
        assertTrue(text.contains(ALICE.toString()));
        assertTrue(text.contains("guild@example.com"));
        assertTrue(text.contains("Example Team"));
        assertTrue(text.contains("Namespace only"));
        assertFalse(text.contains("poi:"));
        assertFalse(text.contains("maintainer:"));
    }

    @Test
    void listShowsDirectChildrenAndRejectsInvalidPages() throws IOException {
        registry.register(ADMIN, CHILD, null);
        registry.register(ADMIN, GRANDCHILD, null);
        run(player, "list", "com");
        assertTrue(messages(player).contains("com.example [namespace]"));
        assertFalse(messages(player).contains("com.example.market"));
        clearInvocations(player);
        run(player, "list", "com", "2147483647");
        assertTrue(messages(player).contains("Page must be between"));
        clearInvocations(player);
        run(player, "list", "-", "bad");
        assertTrue(messages(player).contains("positive integer"));
    }

    @Test
    void formerSubcommandNamesAreOrdinaryTeleportTargets() throws IOException {
        registry.register(ADMIN, new DomainName("help"), null);
        visit(player, "help");
        assertTrue(messages(player).contains("help is a namespace"));
    }

    @Test
    void ownershipFailuresAndInvalidArgumentsDoNotMutateRecords() {
        run(player, "set", "com", "name", "Taken");
        assertTrue(registry.require(ROOT).metadata().isEmpty());
        assertTrue(messages(player).contains("owner"));
        clearInvocations(player);
        run(console, "set", "com", "unknown", "value");
        run(console, "transfer", "com", "no-such-user");
        run(console, "namespace", "com..invalid");
        run(console, "delete", "com", "extra");
        assertEquals(List.of(ROOT), registry.names());
        assertEquals(ALICE, registry.require(ROOT).owner());
    }

    @Test
    void saveFailureIsReportedWithoutApplyingChange() {
        store.fail = true;
        run(console, "namespace", "org");
        assertEquals(List.of(ROOT), registry.names());
        assertTrue(messages(console).contains("change was not applied"));
    }

    @Test
    void unavailableWorldDoesNotTriggerTeleport() throws IOException {
        registry.setLocation(ADMIN, ROOT, new WarpLocation(UUID.randomUUID(), "missing-world", 0, 64, 0, 0, 0));
        visit(player, "com");
        assertTrue(messages(player).contains("world is unavailable"));
        verify(player, never()).teleportAsync(any(Location.class), any(PlayerTeleportEvent.TeleportCause.class));
    }

    @Test
    void teleportsAsynchronouslyAndReportsCompletionOnTheServerThread() throws IOException {
        setupDestination();
        var future = new CompletableFuture<Boolean>();
        when(player.teleportAsync(any(Location.class), eq(PlayerTeleportEvent.TeleportCause.COMMAND))).thenReturn(future);
        when(plugin.isEnabled()).thenReturn(true);
        when(player.isOnline()).thenReturn(true);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(server.getScheduler()).thenReturn(scheduler);
        visit(player, "com");
        verify(player).teleportAsync(any(Location.class), eq(PlayerTeleportEvent.TeleportCause.COMMAND));
        verify(player, never()).sendMessage(any(Component.class));
        future.complete(true);
        var task = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runTask(eq(plugin), task.capture());
        task.getValue().run();
        assertTrue(messages(player).contains("Arrived at com"));
    }

    @Test
    void borderAndHeightChecksRejectOutOfBoundsDestinations() throws IOException {
        World world = setupDestination();
        when(world.getMaxHeight()).thenReturn(60);
        visit(player, "com");
        assertTrue(messages(player).contains("outside"));
        verify(player, never()).teleportAsync(any(Location.class), any(PlayerTeleportEvent.TeleportCause.class));
    }

    @Test
    void tabCompletionFiltersManagementOptionsByOwnership() throws IOException {
        registry.register(ADMIN, CHILD, null);
        registry.transfer(ADMIN, CHILD, BOB);
        assertEquals(List.of("com.example"), executor.onTabComplete(player, command, "warp", new String[]{"delete", "com"}));
        when(command.getName()).thenReturn("whois");
        assertEquals(List.of("com", "com.example"), executor.onTabComplete(player, command, "whois", new String[]{"com"}));
        when(command.getName()).thenReturn("warpplus");
        assertEquals(List.of("contact"), executor.onTabComplete(player, command, "warp", new String[]{"set", "com.example", "con"}));
        assertEquals(List.of("com.example."), executor.onTabComplete(player, command, "warp", new String[]{"register", "com"}));
    }

    @Test
    void linkingChecksTheActualEssentialsWarpAndDelegatesVisitsWithoutLocalTeleport() throws IOException {
        registry.transfer(ADMIN, ROOT, BOB);
        LegacyWarps legacy = mock(LegacyWarps.class);
        when(legacy.available()).thenReturn(true);
        when(legacy.find("market")).thenReturn(java.util.Optional.of("Market"));
        executor = new WarpCommand(plugin, registry, legacy);
        run(player, "link", "com.example", "market");
        assertEquals("Market", registry.require(CHILD).essentialsWarp());
        assertNull(registry.require(CHILD).location());
        visit(player, "com.example");
        verify(legacy).execute(eq(player), aryEq(new String[]{"Market"}));
        verify(player, never()).teleportAsync(any(Location.class), any(PlayerTeleportEvent.TeleportCause.class));
    }

    @Test
    void legacyVisitsKeepTheirArgumentsButBareWarpNeverLists() {
        LegacyWarps legacy = mock(LegacyWarps.class);
        when(legacy.available()).thenReturn(true);
        executor = new WarpCommand(plugin, registry, legacy);
        visit(player, "old_market", "Alex");
        visit(player);
        verify(legacy).execute(eq(player), aryEq(new String[]{"old_market", "Alex"}));
        verify(legacy, never()).execute(eq(player), aryEq(new String[]{}));
        assertTrue(messages(player).contains("Usage: /warp <destination>"));
    }

    @Test
    void missingEssentialsWarpCannotBeLinkedAndAnUnavailableBackendCannotBeVisited() throws IOException {
        LegacyWarps legacy = mock(LegacyWarps.class);
        when(legacy.available()).thenReturn(true);
        when(legacy.find("missing")).thenReturn(java.util.Optional.empty());
        executor = new WarpCommand(plugin, registry, legacy);
        run(console, "link", "com.example", "missing");
        assertTrue(registry.find(CHILD).isEmpty());
        registry.link(ADMIN, CHILD, "market");
        executor = new WarpCommand(plugin, registry);
        visit(player, "com.example");
        assertTrue(messages(player).contains("EssentialsX is unavailable"));
        assertEquals("market", registry.require(CHILD).essentialsWarp());
        verify(player, never()).teleportAsync(any(Location.class), any(PlayerTeleportEvent.TeleportCause.class));
    }

    @Test
    void linkedWhoisAndBrowsingExposeTheLinkWithoutAnEssentialsWorldLookup() throws IOException {
        registry.link(ADMIN, CHILD, "market");
        registry.transfer(ADMIN, CHILD, BOB);
        var offline = mock(org.bukkit.OfflinePlayer.class);
        when(server.getOfflinePlayer(BOB)).thenReturn(offline);
        lookup(player, "com.example");
        assertTrue(messages(player).contains("EssentialsX warp: market"));
        assertTrue(messages(player).contains("EssentialsX unavailable"));
        verify(server, never()).getWorld(any(UUID.class));
        run(player, "list", "com");
        assertTrue(messages(player).contains("com.example [EssentialsX]"));
    }

    @Test
    void warpCannotRunRegistryOperationsOrShowWhois() {
        visit(console, "delete", "com");
        visit(console, "namespace", "org");
        visit(console, "set", "com", "name", "Changed");
        visit(player, "whois", "com");
        assertEquals(List.of(ROOT), registry.names());
        assertTrue(registry.require(ROOT).metadata().isEmpty());
        assertFalse(messages(player).contains("WHOIS"));
    }

    @Test
    void formerSubcommandNameCanBeLinkedAndVisitedDirectly() throws IOException {
        registry.link(ADMIN, new DomainName("delete"), "spawn");
        LegacyWarps legacy = mock(LegacyWarps.class);
        when(legacy.available()).thenReturn(true);
        executor = new WarpCommand(plugin, registry, legacy);
        visit(player, "delete");
        verify(legacy).execute(eq(player), aryEq(new String[]{"spawn"}));
        assertTrue(registry.find("delete").isPresent());
    }

    @Test
    void warpCompletesOnlyDestinationsAndWarpplusCompletesRegistryActions() throws IOException {
        registry.link(ADMIN, CHILD, "market");
        when(command.getName()).thenReturn("warp");
        assertEquals(List.of("com.example"), executor.onTabComplete(player, command, "warp", new String[]{""}));
        when(command.getName()).thenReturn("warpplus");
        List<String> actions = executor.onTabComplete(player, command, "warpplus", new String[]{""});
        assertTrue(actions.containsAll(List.of("help", "list", "register", "link")));
        assertFalse(actions.contains("com.example"));
    }

    @Test
    void consoleCannotInvokeEssentialsListingThroughASingleDestinationArgument() {
        LegacyWarps legacy = mock(LegacyWarps.class);
        when(legacy.available()).thenReturn(true);
        executor = new WarpCommand(plugin, registry, legacy);
        visit(console, "spawn");
        verify(legacy, never()).execute(any(), any());
        assertTrue(messages(console).contains("<destination> <player>"));
    }

    @Test
    void retiredFieldsCannotBeSetOrOfferedInCompletion() {
        run(console, "set", "com", "poi", "Market");
        run(console, "set", "com", "maintainer", "Alex");
        assertTrue(registry.require(ROOT).metadata().isEmpty());
        assertTrue(messages(console).contains("Fields: name, organization, contact, website, description."));
        assertEquals(List.of("contact", "description", "name", "organization", "website"),
                executor.onTabComplete(console, command, "warpplus", new String[]{"set", "com", ""}));
    }

    private World setupDestination() throws IOException {
        UUID id = UUID.randomUUID();
        registry.setLocation(ADMIN, ROOT, new WarpLocation(id, "world", 10, 64, 20, 90, 0));
        World world = mock(World.class);
        WorldBorder border = mock(WorldBorder.class);
        when(server.getWorld(id)).thenReturn(world);
        when(world.getMinHeight()).thenReturn(-64);
        when(world.getMaxHeight()).thenReturn(320);
        when(world.getWorldBorder()).thenReturn(border);
        when(border.isInside(any(Location.class))).thenReturn(true);
        return world;
    }

    private void visit(CommandSender sender, String... args) {
        when(command.getName()).thenReturn("warp");
        run(sender, args);
        when(command.getName()).thenReturn("warpplus");
    }

    private void lookup(CommandSender sender, String domain) {
        when(command.getName()).thenReturn("whois");
        run(sender, domain);
        when(command.getName()).thenReturn("warpplus");
    }

    private void run(CommandSender sender, String... args) {
        assertTrue(executor.onCommand(sender, command, command.getName(), args));
    }

    private String messages(CommandSender sender) {
        var captor = ArgumentCaptor.forClass(Component.class);
        verify(sender, atLeastOnce()).sendMessage(captor.capture());
        return String.join("\n", captor.getAllValues().stream().map(PlainTextComponentSerializer.plainText()::serialize).toList());
    }
}
