package com.scchan.warpplus;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

/** Rewrite before EssentialsX's MONITOR listener so its command cooldowns see the real target. */
public final class EssentialsCommandRouter implements Listener {
    private static final Set<String> COMMANDS = Set.of("warp", "warpplus:warp");
    private final WarpRegistry registry;
    private final LegacyWarps legacy;

    public EssentialsCommandRouter(WarpRegistry registry, LegacyWarps legacy) {
        this.registry = registry;
        this.legacy = legacy;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage();
        if (!message.startsWith("/") || !event.getPlayer().hasPermission("warpplus.use")) return;
        String[] tokens = message.substring(1).strip().split("\\s+");
        if (tokens.length == 0 || !COMMANDS.contains(tokens[0].toLowerCase(Locale.ROOT)) || !legacy.available()) return;
        String[] args = Arrays.copyOfRange(tokens, 1, tokens.length);
        String[] forwarded = WarpCommand.legacyArguments(registry, args);
        if (forwarded != null) {
            try {
                legacy.checkEnabled();
                event.setMessage("/essentials:warp" + (forwarded.length == 0 ? "" : " " + String.join(" ", forwarded)));
            } catch (IllegalArgumentException ex) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(Component.text(ex.getMessage(), NamedTextColor.RED));
            }
        }
    }
}
