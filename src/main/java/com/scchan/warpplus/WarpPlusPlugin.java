package com.scchan.warpplus;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.time.Clock;
import java.util.Objects;
import java.util.logging.Level;

public final class WarpPlusPlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        try {
            var registry = new WarpRegistry(new YamlRegistryStore(getDataFolder().toPath().resolve("registry.yml")), Clock.systemUTC());
            var executor = new WarpCommand(this, registry);
            for (String name : new String[]{"warp", "warpwhois"}) {
                var command = Objects.requireNonNull(getCommand(name), "Missing command " + name);
                command.setExecutor(executor);
                command.setTabCompleter(executor);
            }
            getLogger().info("Loaded " + registry.names().size() + " domains. Use /warpplus:warp help if /warp is claimed by another plugin.");
        } catch (IOException ex) {
            getLogger().log(Level.SEVERE, "Cannot load the registry. WarpPlus is disabled; the registry file has been preserved.", ex);
            getServer().getPluginManager().disablePlugin(this);
        }
    }
}
