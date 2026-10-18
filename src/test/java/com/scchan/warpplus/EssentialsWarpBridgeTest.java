package com.scchan.warpplus;

import com.earth2me.essentials.Essentials;
import net.ess3.api.ISettings;
import com.earth2me.essentials.Warps;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EssentialsWarpBridgeTest {
    Essentials essentials;
    PluginCommand command;
    ISettings settings;
    LegacyWarps bridge;
    CommandSender sender;

    @BeforeEach
    void setUp() {
        essentials = mock(Essentials.class);
        command = mock(PluginCommand.class);
        settings = mock(ISettings.class);
        sender = mock(CommandSender.class);
        when(essentials.isEnabled()).thenReturn(true);
        when(essentials.getCommand("warp")).thenReturn(command);
        when(essentials.getSettings()).thenReturn(settings);
        when(command.getPlugin()).thenReturn(essentials);
        bridge = EssentialsWarpBridge.connect(essentials);
    }

    @Test
    void executesTheRealPluginCommandAsTheOriginalSenderWithANonRecursiveLabel() {
        String[] args = {"market", "Alex"};
        bridge.execute(sender, args);
        verify(command).execute(same(sender), eq("ewarp"), same(args));
        verify(essentials, never()).getUser(any(org.bukkit.entity.Player.class));
    }

    @Test
    void lookupPreservesCanonicalNameAndCompletionComesFromEssentials() {
        Warps warps = mock(Warps.class);
        when(essentials.getWarps()).thenReturn(warps);
        when(warps.getList()).thenReturn(Set.of("Existing_Market", "spawn"));
        assertEquals("Existing_Market", bridge.find("existing_market").orElseThrow());
        assertTrue(bridge.find("missing").isEmpty());
        when(command.tabComplete(sender, "ewarp", new String[]{"s"})).thenReturn(List.of("spawn"));
        assertEquals(List.of("spawn"), bridge.complete(sender, new String[]{"s"}));
    }

    @Test
    void disabledWarpAndPluginUnavailabilityNeverExecuteTheCommand() {
        when(settings.isCommandDisabled("warp")).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> bridge.execute(sender, new String[]{"spawn"}));
        assertTrue(bridge.complete(sender, new String[]{""}).isEmpty());
        when(settings.isCommandDisabled("warp")).thenReturn(false);
        when(settings.isCommandDisabled("ewarp")).thenReturn(true);
        assertThrows(IllegalArgumentException.class, bridge::checkEnabled);
        when(essentials.isEnabled()).thenReturn(false);
        assertFalse(bridge.available());
        assertThrows(IllegalArgumentException.class, () -> bridge.find("spawn"));
        assertThrows(IllegalArgumentException.class, () -> bridge.execute(sender, new String[]{"spawn"}));
        verify(command, never()).execute(any(), anyString(), any());
    }

    @Test
    void rejectsUnrelatedPluginsOrACommandOwnedByAnotherPlugin() {
        assertThrows(IllegalArgumentException.class, () -> EssentialsWarpBridge.connect(mock(Plugin.class)));
        when(command.getPlugin()).thenReturn(mock(Plugin.class));
        assertThrows(IllegalArgumentException.class, () -> EssentialsWarpBridge.connect(essentials));
    }
}
