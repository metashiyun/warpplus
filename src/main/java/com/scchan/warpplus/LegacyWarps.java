package com.scchan.warpplus;

import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.Optional;

/** Optional integration boundary: loading the standalone plugin needs no EssentialsX classes. */
public interface LegacyWarps {
    LegacyWarps NONE = new LegacyWarps() {
        public boolean available() { return false; }
        public Optional<String> find(String name) { return Optional.empty(); }
        public void execute(CommandSender sender, String[] args) { throw unavailable(); }
        public List<String> complete(CommandSender sender, String[] args) { return List.of(); }
    };

    boolean available();
    default void checkEnabled() { if (!available()) throw unavailable(); }
    Optional<String> find(String name);
    void execute(CommandSender sender, String[] args);
    List<String> complete(CommandSender sender, String[] args);

    static IllegalArgumentException unavailable() {
        return new IllegalArgumentException("EssentialsX is unavailable. Linked destinations cannot be visited until it is enabled.");
    }
}
