# WarpPlus

A PaperMC plugin for hierarchical warp names, delegated ownership, and public WHOIS records.

```text
com
└── com.example
    ├── com.example.market
    └── com.example.harbor
```

Names run from parent to child, left to right. Each domain has an owner and can serve as a namespace, a teleport destination, or both.

## Install

Requires Paper and the Java version required by your server. EssentialsX integration is optional.

1. Download the plugin JAR from [Releases](https://github.com/metashiyun/warpplus/releases), or build it from source:

   ```sh
   ./gradlew build
   ```

   On Windows, use `gradlew.bat build`. The built JAR is in `build/libs/`.

2. Place the JAR in your server's `plugins/` directory, replacing the previous WarpPlus JAR when upgrading.
3. Restart the server.

## Create your first warp

As an operator, create a namespace and delegate it to an online player:

```text
/warpplus namespace com
/warpplus namespace com.example
/warpplus transfer com.example Alex
```

Alex stands at the destination and registers a warp:

```text
/warpplus register com.example.market
/warpplus set com.example.market name Example Market
/warpplus set com.example.market description Player shops and community trading.
```

Players can then browse, look up the record, and teleport:

```text
/warpplus list com.example
/whois com.example.market
/warp com.example.market
```

Create each parent before registering its children. Names are case-insensitive and use dot-separated labels of ASCII letters, digits, and internal hyphens.

## Use EssentialsX warps

With EssentialsX installed, link a domain to an existing warp. For an EssentialsX warp named `market`, using the namespace above:

```text
/warpplus link com.example.market market
/warp com.example.market
```

Create the target with EssentialsX's `/setwarp market` if needed. Linked destinations use EssentialsX's permissions, costs, warmups, cooldowns, and teleport handling. Moving the EssentialsX warp updates every domain linked to it.

`/warp market` also accepts existing EssentialsX warp names. Registered WarpPlus domains take priority when names overlap. `/warpplus register` and `/warpplus setlocation` save a local destination at the player's exact position and facing; `/warpplus link` switches a domain to an EssentialsX destination.

Keep `warp` and `whois` out of EssentialsX's `overridden-commands` setting so WarpPlus handles them. Explicit commands are `/warpplus:warp` and `/warpplus:whois`. For EssentialsX player lookup, use `/essentials:whois <player>`.

## Commands

| Command | Purpose |
| --- | --- |
| `/warp <destination> [player]` | Teleport to a domain or EssentialsX warp. The optional player argument uses EssentialsX; console supplies a player. |
| `/whois <domain>` | Show ownership, timestamps, public details, and destination. Aliases: `/warpwhois`, `/wwhois`. |
| `/warpplus list [parent\|-] [page]` | Browse direct children, eight per page. Omit the parent or use `-` for top-level domains. |
| `/warpplus namespace <domain>` | Create a namespace. |
| `/warpplus register <domain>` | Create a warp at your current position. |
| `/warpplus link <domain> <essentials-warp>` | Create or update a domain linked to an EssentialsX warp. |
| `/warpplus set <domain> <field> <value...>` | Set public details. Use `-` as the value to clear a field. |
| `/warpplus setlocation <domain>` | Set the destination to your current position. |
| `/warpplus clearlocation <domain>` | Remove the destination, keeping the domain and its children. |
| `/warpplus transfer <domain> <online-player\|uuid>` | Transfer ownership. Use a UUID for an offline player. |
| `/warpplus delete <domain>` | Delete a domain after removing its children. |
| `/warpplus help` | Show command help. |

Public detail fields are `name`, `organization`, `contact`, `website`, and `description`. Each domain stores its own details, which players can read through WHOIS.

## Ownership and permissions

Owners can edit their domains, create children, and delegate ownership. A parent owner retains authority over all descendants after delegation. For example, if Alice owns `com` and transfers `com.example` to Bob, both can manage `com.example` and its children.

| Permission | Default | Grants |
| --- | --- | --- |
| `warpplus.use` | Everyone | Teleport, browse, and read WHOIS. Required for all commands. |
| `warpplus.register` | Everyone | Manage owned domains and register children under controlled domains. |
| `warpplus.register.root` | Operators | Create top-level domains, together with `warpplus.register`. |
| `warpplus.admin` | Operators | Manage the entire registry. |

## Data

The registry is stored in `plugins/WarpPlus/registry.yml` and saved after each change. Stop the server before editing it manually, then restart to load changes.

## Development and releases

```sh
./gradlew test
./gradlew build
```

To publish, open **Actions → Cut release → Run workflow** on GitHub and choose `patch`, `minor`, or `major`. The workflow bumps the version on `main`, builds and tests the plugin, and publishes a tagged release with the JAR and its SHA-256 checksum.

## License

[MIT](LICENSE) © 2026 Sicheng Chen.
