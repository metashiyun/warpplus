package com.scchan.warpplus;

import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static com.scchan.warpplus.WarpRegistryTest.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EssentialsCommandRouterTest {
    WarpRegistry registry;
    LegacyWarps legacy;
    EssentialsCommandRouter router;
    Player player;

    @BeforeEach
    void setUp() throws IOException {
        registry = new WarpRegistry(new MemoryStore(), CLOCK);
        registry.register(ADMIN, ROOT, null);
        registry.link(ADMIN, CHILD, "Existing_Market");
        legacy = mock(LegacyWarps.class);
        when(legacy.available()).thenReturn(true);
        router = new EssentialsCommandRouter(registry, legacy);
        player = mock(Player.class);
        when(player.hasPermission("warpplus.use")).thenReturn(true);
    }

    @Test
    void linkedNamesAreRewrittenToTheRealWarpBeforeEssentialsProcessesTheEvent() {
        assertEquals("/essentials:warp Existing_Market", route("/warp COM.Example").getMessage());
        assertEquals("/essentials:warp Existing_Market", route("/warpplus:warp go com.example").getMessage());
        assertEquals("/essentials:warp Existing_Market Alex", route("/warp com.example Alex").getMessage());
        assertEquals("/essentials:warp Existing_Market Alex", route("/warp go com.example Alex").getMessage());
        verify(legacy, times(4)).checkEnabled();
        verify(legacy, never()).execute(any(), any());
    }

    @Test
    void legacyNamesPagesOtherPlayerAndExplicitEscapeKeepTheirArguments() {
        assertEquals("/essentials:warp spawn", route("/warp spawn").getMessage());
        assertEquals("/essentials:warp", route("/warp").getMessage());
        assertEquals("/essentials:warp 2", route("/warp 2").getMessage());
        assertEquals("/essentials:warp old_market Alex", route("/warp old_market Alex").getMessage());
        assertEquals("/essentials:warp help", route("/warp legacy help").getMessage());
        assertEquals("/essentials:warp old_market", route("/warp go old_market").getMessage());
    }

    @Test
    void registryCommandsNamespacesAndOtherPluginsRemainWithTheirOriginalHandler() throws IOException {
        for (String command : new String[]{"/warp help", "/warp list com", "/warp whois com.example", "/warp link com.foo spawn",
                "/warp namespace org", "/warp set com name Test", "/warp com", "/warp go com", "/warpwhois com.example",
                "/ewarp spawn", "/essentials:warp spawn", "/other:warp spawn"}) {
            assertEquals(command, route(command).getMessage());
        }
        registry.register(ADMIN, new DomainName("org"), new WarpLocation(java.util.UUID.randomUUID(), "world", 0, 64, 0, 0, 0));
        assertEquals("/warp org", route("/warp org").getMessage());
    }

    @Test
    void routingNeverBypassesWarpplusUseOrRewritesWhenBackendIsUnavailable() {
        when(player.hasPermission("warpplus.use")).thenReturn(false);
        assertEquals("/warp com.example", route("/warp com.example").getMessage());
        when(player.hasPermission("warpplus.use")).thenReturn(true);
        when(legacy.available()).thenReturn(false);
        assertEquals("/warp com.example", route("/warp com.example").getMessage());
        verify(legacy, never()).checkEnabled();
    }

    @Test
    void disabledEssentialsWarpCancelsInsteadOfFallingBackToLocalTeleport() {
        doThrow(new IllegalArgumentException("warp is disabled")).when(legacy).checkEnabled();
        var event = route("/warp com.example");
        assertTrue(event.isCancelled());
        assertEquals("/warp com.example", event.getMessage());
        verify(player).sendMessage(any(net.kyori.adventure.text.Component.class));
    }

    private PlayerCommandPreprocessEvent route(String message) {
        var event = new PlayerCommandPreprocessEvent(player, message, java.util.Set.of());
        router.onCommand(event);
        return event;
    }
}
