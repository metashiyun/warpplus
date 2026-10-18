package com.scchan.warpplus;

import com.earth2me.essentials.Essentials;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Optional;

/** Delegate to the real warp command, preserving its authorization, cost, and teleport handling. */
public final class EssentialsWarpBridge implements LegacyWarps {
    private final Essentials essentials;
    private final PluginCommand warp;

    private EssentialsWarpBridge(Essentials essentials, PluginCommand warp) {
        this.essentials = essentials;
        this.warp = warp;
    }

    public static LegacyWarps connect(Plugin plugin) {
        if (!(plugin instanceof Essentials essentials)) {
            throw new IllegalArgumentException("The Essentials plugin is not a compatible EssentialsX installation.");
        }
        PluginCommand warp = essentials.getCommand("warp");
        if (warp == null || warp.getPlugin() != essentials) {
            throw new IllegalArgumentException("EssentialsX's warp command is unavailable.");
        }
        return new EssentialsWarpBridge(essentials, warp);
    }

    @Override
    public boolean available() { return essentials.isEnabled(); }

    @Override
    public Optional<String> find(String name) {
        if (!available()) throw LegacyWarps.unavailable();
        return essentials.getWarps().getList().stream().filter(warpName -> warpName.equalsIgnoreCase(name)).findFirst();
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        LegacyWarps.validateTeleportArguments(args);
        checkEnabled();
        // The e-prefixed label prevents EssentialsX handing this command back to WarpPlus.
        warp.execute(sender, "ewarp", args);
    }

    @Override
    public List<String> complete(CommandSender sender, String[] args) {
        if (!available() || disabled()) return List.of();
        return warp.tabComplete(sender, "ewarp", args);
    }

    @Override
    public void checkEnabled() {
        if (!available()) throw LegacyWarps.unavailable();
        if (disabled()) throw new IllegalArgumentException("EssentialsX's warp command is disabled in its configuration.");
    }

    private boolean disabled() {
        return essentials.getSettings().isCommandDisabled("warp") || essentials.getSettings().isCommandDisabled("ewarp")
                || essentials.getSettings().isCommandDisabled("essentials:warp");
    }
}
