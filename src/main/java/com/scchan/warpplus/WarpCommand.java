package com.scchan.warpplus;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;

public final class WarpCommand implements CommandExecutor, TabCompleter {
    private static final List<String> READ_COMMANDS = List.of("go", "whois", "list", "help");
    private static final List<String> WRITE_COMMANDS = List.of("register", "namespace", "set", "setlocation", "clearlocation", "transfer", "delete");
    private static final int PAGE_SIZE = 8;
    private final WarpPlusPlugin plugin;
    private final WarpRegistry registry;

    public WarpCommand(WarpPlusPlugin plugin, WarpRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("warpplus.use")) {
            error(sender, "You do not have permission to use WarpPlus.");
            return true;
        }
        try {
            if (command.getName().equalsIgnoreCase("warpwhois")) {
                arity(args, 1, "/warpwhois <domain>");
                whois(sender, new DomainName(args[0]));
                return true;
            }
            if (args.length == 0) { help(sender); return true; }
            String action = args[0].toLowerCase(Locale.ROOT);
            switch (action) {
                case "help" -> { arity(args, 1, "/warp help"); help(sender); }
                case "go" -> { arity(args, 2, "/warp go <domain>"); teleport(sender, new DomainName(args[1])); }
                case "whois" -> { arity(args, 2, "/warp whois <domain>"); whois(sender, new DomainName(args[1])); }
                case "list" -> list(sender, args);
                case "register", "namespace" -> {
                    arity(args, 2, "/warp " + action + " <domain>");
                    DomainName domain = new DomainName(args[1]);
                    WarpLocation location = action.equals("register") ? position(player(sender)) : null;
                    registry.register(actor(sender), domain, location);
                    success(sender, "Registered " + domain + (location == null ? " as a namespace." : " at your location."));
                }
                case "set" -> {
                    if (args.length < 4) throw new IllegalArgumentException("Usage: /warp set <domain> <field> <value...|->");
                    String value = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
                    registry.setField(actor(sender), new DomainName(args[1]), MetadataField.parse(args[2]), value.equals("-") ? "" : value);
                    success(sender, "Updated " + args[2] + " for " + args[1] + ". WHOIS details are public.");
                }
                case "setlocation", "clearlocation" -> {
                    arity(args, 2, "/warp " + action + " <domain>");
                    registry.setLocation(actor(sender), new DomainName(args[1]),
                            action.equals("setlocation") ? position(player(sender)) : null);
                    success(sender, "Updated the destination for " + args[1] + ".");
                }
                case "transfer" -> {
                    arity(args, 3, "/warp transfer <domain> <online-player|uuid>");
                    UUID owner = resolveOwner(args[2]);
                    registry.transfer(actor(sender), new DomainName(args[1]), owner);
                    success(sender, "Transferred " + args[1] + " to " + owner + ". Ancestor owners retain authority over the subtree.");
                }
                case "delete" -> {
                    arity(args, 2, "/warp delete <domain>");
                    registry.delete(actor(sender), new DomainName(args[1]));
                    success(sender, "Deleted " + args[1] + ".");
                }
                default -> { arity(args, 1, "/warp <domain> or /warp help"); teleport(sender, new DomainName(args[0])); }
            }
        } catch (IllegalArgumentException ex) {
            error(sender, ex.getMessage());
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Registry save failed", ex);
            error(sender, "Could not save the registry. Your change was not applied; ask an administrator to check the logs.");
        }
        return true;
    }

    private void teleport(CommandSender sender, DomainName domain) {
        Player player = player(sender);
        WarpLocation target = registry.require(domain).location();
        if (target == null) throw new IllegalArgumentException(domain + " is a namespace without a destination. Browse it with /warp list " + domain);
        var world = plugin.getServer().getWorld(target.worldId());
        if (world == null) throw new IllegalArgumentException("Destination world is unavailable: " + target.worldName());
        var destination = new Location(world, target.x(), target.y(), target.z(), target.yaw(), target.pitch());
        if (target.y() < world.getMinHeight() || target.y() >= world.getMaxHeight() || !world.getWorldBorder().isInside(destination)) {
            throw new IllegalArgumentException("Destination is outside the world's build height or border. Ask its maintainer to move it.");
        }
        player.teleportAsync(destination, PlayerTeleportEvent.TeleportCause.COMMAND).whenComplete((teleported, failure) -> {
            if (!plugin.isEnabled()) return;
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                if (failure != null) {
                    plugin.getLogger().log(Level.WARNING, "Teleport to " + domain + " failed", failure);
                    error(player, "Teleport failed. Please try again.");
                } else if (Boolean.TRUE.equals(teleported)) {
                    success(player, "Arrived at " + domain + ".");
                } else {
                    error(player, "Teleport was cancelled.");
                }
            });
        });
    }

    private void whois(CommandSender sender, DomainName domain) {
        WarpRecord record = registry.require(domain);
        sender.sendMessage(Component.text("WHOIS " + domain, NamedTextColor.GOLD));
        detail(sender, "Parent", domain.parent().map(DomainName::value).orElse("(root)"));
        String owner = "Server administrators";
        if (record.owner() != null) {
            var offline = plugin.getServer().getOfflinePlayer(record.owner());
            owner = (offline.getName() == null ? "" : offline.getName() + " ") + "(" + record.owner() + ")";
        }
        detail(sender, "Owner", owner);
        for (MetadataField field : MetadataField.values()) detail(sender, field.key(), record.metadata().getOrDefault(field, "(not provided)"));
        detail(sender, "Registered (UTC)", record.createdAt().toString());
        detail(sender, "Updated (UTC)", record.updatedAt().toString());
        detail(sender, "Children", Integer.toString(registry.children(domain).size()));
        WarpLocation location = record.location();
        detail(sender, "Destination", location == null ? "Namespace only" : String.format(Locale.ROOT,
                "%s [%.2f, %.2f, %.2f]", location.worldName(), location.x(), location.y(), location.z()));
    }

    private void list(CommandSender sender, String[] args) {
        if (args.length > 3) throw new IllegalArgumentException("Usage: /warp list [parent|-] [page]");
        DomainName parent = args.length >= 2 && !args[1].equals("-") ? new DomainName(args[1]) : null;
        if (parent != null) registry.require(parent);
        int page;
        try { page = args.length == 3 ? Integer.parseInt(args[2]) : 1; }
        catch (NumberFormatException ex) { throw new IllegalArgumentException("Page must be a positive integer."); }
        List<WarpRecord> children = registry.children(parent);
        int pages = Math.max(1, (children.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        if (page < 1 || page > pages) throw new IllegalArgumentException("Page must be between 1 and " + pages + ".");
        sender.sendMessage(Component.text("Domains under " + (parent == null ? "(root)" : parent) + " • " + page + "/" + pages, NamedTextColor.GOLD));
        if (children.isEmpty()) detail(sender, "Registry", "No children registered.");
        children.stream().skip((long) (page - 1) * PAGE_SIZE).limit(PAGE_SIZE).forEach(record -> {
            String target = record.location() == null ? "list " : "go ";
            Component entry = Component.text(record.domain().value(), NamedTextColor.AQUA)
                    .clickEvent(ClickEvent.suggestCommand("/warpplus:warp " + target + record.domain()))
                    .hoverEvent(HoverEvent.showText(Component.text("Click to " + (record.location() == null ? "browse children" : "prepare teleport"))))
                    .append(Component.text(record.location() == null ? " [namespace]" : " [warp]", NamedTextColor.GRAY));
            sender.sendMessage(entry);
        });
        if (page < pages) detail(sender, "Next", "/warp list " + (parent == null ? "-" : parent) + " " + (page + 1));
    }

    private void help(CommandSender sender) {
        sender.sendMessage(Component.text("WarpPlus • com → com.example → com.example.market", NamedTextColor.GOLD));
        for (String line : List.of(
                "/warp <domain> (or /warp go <domain>) — teleport",
                "/warp whois <domain> (or /warpwhois <domain>) — public registry",
                "/warp list [parent|-] [page] — browse direct children")) detail(sender, "•", line);
        if (sender.hasPermission("warpplus.register") || sender.hasPermission("warpplus.admin")) {
            for (String line : List.of(
                    "/warp register <domain> — register at your position",
                    "/warp namespace <domain> — register without a destination",
                    "/warp set <domain> <field> <value...|-> — edit or clear public details",
                    "Fields: name, organization, poi, maintainer, contact, website, description",
                    "/warp setlocation|clearlocation <domain> — change destination",
                    "/warp transfer <domain> <online-player|uuid> — delegate ownership",
                    "/warp delete <domain> — remove a domain with no children")) detail(sender, "•", line);
        }
    }

    private Actor actor(CommandSender sender) {
        return new Actor(sender instanceof Player player ? player.getUniqueId() : null,
                sender.hasPermission("warpplus.admin"), sender.hasPermission("warpplus.register"),
                sender.hasPermission("warpplus.register.root"));
    }

    private static Player player(CommandSender sender) {
        if (!(sender instanceof Player player)) throw new IllegalArgumentException("This command requires an in-game player.");
        return player;
    }

    private static WarpLocation position(Player player) {
        Location location = player.getLocation();
        return new WarpLocation(location.getWorld().getUID(), location.getWorld().getName(), location.getX(), location.getY(),
                location.getZ(), location.getYaw(), location.getPitch());
    }

    private UUID resolveOwner(String value) {
        Player online = plugin.getServer().getPlayerExact(value);
        if (online != null) return online.getUniqueId();
        try { return UUID.fromString(value); }
        catch (IllegalArgumentException ex) { throw new IllegalArgumentException("Use an online player's exact name or a player's UUID."); }
    }

    private static void arity(String[] args, int count, String usage) {
        if (args.length != count) throw new IllegalArgumentException("Usage: " + usage);
    }

    private static void detail(CommandSender sender, String label, String value) {
        sender.sendMessage(Component.text(label + ": ", NamedTextColor.GRAY).append(Component.text(value, NamedTextColor.WHITE)));
    }

    private static void success(CommandSender sender, String message) { sender.sendMessage(Component.text(message, NamedTextColor.GREEN)); }
    private static void error(CommandSender sender, String message) { sender.sendMessage(Component.text(message, NamedTextColor.RED)); }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("warpplus.use") || args.length == 0) return List.of();
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        var options = new java.util.TreeSet<String>();
        boolean whoisAlias = command.getName().equalsIgnoreCase("warpwhois");
        if (whoisAlias && args.length == 1) registry.names().forEach(name -> options.add(name.value()));
        else if (!whoisAlias) {
            String action = args[0].toLowerCase(Locale.ROOT);
            if (args.length == 1) {
                options.addAll(READ_COMMANDS);
                if (sender.hasPermission("warpplus.register") || sender.hasPermission("warpplus.admin")) options.addAll(WRITE_COMMANDS);
                registry.names().forEach(name -> options.add(name.value()));
            } else if (args.length == 2 && (READ_COMMANDS.contains(action) || WRITE_COMMANDS.contains(action)) && !action.equals("help")) {
                registry.names().stream().filter(name -> !WRITE_COMMANDS.contains(action) || registry.canManage(actor(sender), name))
                        .forEach(name -> options.add(name.value() + (action.equals("register") || action.equals("namespace") ? "." : "")));
                if (action.equals("list")) options.add("-");
            } else if (args.length == 3 && action.equals("set")) {
                for (MetadataField field : MetadataField.values()) options.add(field.key());
            } else if (args.length == 3 && action.equals("transfer")) {
                plugin.getServer().getOnlinePlayers().forEach(player -> options.add(player.getName()));
            }
        }
        return options.stream().filter(option -> option.startsWith(prefix)).limit(50).toList();
    }
}
