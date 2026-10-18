# WarpPlus

A PaperMC plugin that gives `/warp` a domain-style hierarchy and a public WHOIS registry.

```text
com                         namespace
└── com.example             organization namespace
    ├── com.example.market  teleport destination
    └── com.example.harbor  teleport destination
```

Names run **from parent to child, left to right**, as in the example above. Each parent must exist before its children can be registered. Every record has its own owner, registration/update timestamps, and optional public name, organization, POI, maintainer, contact, website, and description. A record may be a namespace, a destination, or a destination with children.

## Build and install

1. Install JDK 21 or newer to build, then run `./gradlew build` (`gradlew.bat build` on Windows).
2. Copy `build/libs/warpplus-1.0.0.jar` into your Paper server's `plugins/` directory.
3. Restart the server.

The plugin compiles against **Paper 1.21.11**, targets Java 21 bytecode, and uses no server internals. Paper 1.21.11 is the minimum supported API; newer Paper versions should be verified on your server before deployment. Use the Java runtime required by your server: Paper 1.21.11 uses Java 21; Paper 26.1+ requires Java 25. See [Paper's requirements](https://docs.papermc.io/paper/getting-started/) and [plugin setup](https://docs.papermc.io/paper/dev/project-setup/).

This plugin supplies its own `/warp`; it does not import or modify EssentialsX or another plugin's warp registry. If another plugin owns `/warp`, use **`/warpplus:warp`** (and `/warpplus:warpwhois`) explicitly, or configure your server's command aliases. There are no external services or runtime library downloads from WarpPlus.

## Example setup

As an operator in-game:

```text
/warp namespace com
/warp namespace com.example
/warp set com.example name Example Guild
/warp set com.example organization Example Builders Association
/warp set com.example maintainer Alex and the build team
/warp set com.example contact Discord: example-guild
/warp set com.example website https://example.com
/warp transfer com.example Alex
```

`Alex` must be online when transferring by name. A UUID also works, including for offline players. The console can create namespaces and transfer them using the same commands; console-created namespaces initially belong to the server administrators.

Alex then stands at the market entrance and runs:

```text
/warp register com.example.market
/warp set com.example.market name Example Central Market
/warp set com.example.market poi Public market and trading hall
/warp set com.example.market maintainer Alex
/warp set com.example.market contact Discord: example-guild
/warp set com.example.market description Player shops, stalls, and community trading.
```

Anyone with the default use permission can browse, query, and travel:

```text
/warp list
/warp list com
/warp list com.example
/warp whois com.example.market
/warpwhois com.example.market
/warp com.example.market
```

Metadata belongs to each individual record; it does not inherit from a parent. WHOIS displays missing fields as `(not provided)`. All registry metadata and destination coordinates are public to players with `warpplus.use`. Contact fields should contain information intended for public display. Text is literal, without color codes, MiniMessage, or link execution.

## Commands

| Command | Purpose |
| --- | --- |
| `/warp <domain>` | Teleport to an exact registered name. |
| `/warp go <domain>` | Explicit teleport, including names such as `help` that match subcommands. |
| `/warp whois <domain>` | Show the public registry record. |
| `/warpwhois <domain>` or `/wwhois <domain>` | WHOIS shortcut. |
| `/warp list [parent\|-] [page]` | List direct children, sorted by name, eight per page. Omit parent or use `-` for roots. |
| `/warp register <domain>` | Register a destination at your current position, including yaw and pitch. Players only. |
| `/warp namespace <domain>` | Register a name without a destination. |
| `/warp set <domain> <field> <value...>` | Set `name`, `organization`, `poi`, `maintainer`, `contact`, `website`, or `description`. Use `-` as the value to clear it. |
| `/warp setlocation <domain>` | Set or move a destination to your current position. Players only. |
| `/warp clearlocation <domain>` | Remove a destination while keeping its registration and children. |
| `/warp transfer <domain> <online-player\|uuid>` | Transfer that record's ownership. |
| `/warp delete <domain>` | Delete a record with no children. |
| `/warp help` | Show command help. |

Names are case-insensitive ASCII letters/digits with internal hyphens, separated by dots. Labels have 1–63 characters; the full name has at most 253 characters and 16 labels. For example, `COM.Example` resolves to `com.example`. Unicode is supported in descriptive metadata. Tab completion offers names, manageable domains, metadata fields, and online transfer recipients. List entries can suggest the corresponding browse or teleport command when clicked.

Teleportation uses Paper's asynchronous chunk loading. A namespace without a destination cannot be visited; `/warp list <namespace>` shows its children. Destinations resolve by world UUID, and unloaded/missing worlds, out-of-height positions, border violations, and cancelled teleports return an error. The plugin uses the exact registered position, without searching for safe ground; maintainers should choose suitable arrival points.

## Permissions and delegation

| Permission | Default | Grants |
| --- | --- | --- |
| `warpplus.use` | Everyone | Teleport, list, help, and WHOIS. |
| `warpplus.register` | Everyone | Manage owned domains and register children under controlled domains. |
| `warpplus.register.root` | Operators | Register top-level names; also requires `warpplus.register`. |
| `warpplus.admin` | Operators | Manage the entire registry and bypass registration ownership checks. |

Command access always requires `warpplus.use`, including for administrators. Owning a domain or **any ancestor** gives management authority over it, provided the player retains `warpplus.register`. This includes editing details/destinations, delegating ownership, registering descendants, and deleting leaf records. Unrelated players cannot claim names underneath it. A new top-level name requires registrar or administrator permission.

**Delegation preserves parent authority.** If Alice owns `com` and delegates `com.example` to Bob, both can manage `com.example` and its descendants. Bob cannot manage `com` or `com.other`. Transferring a parent changes inherited authority immediately, while explicit owners of child records remain unchanged. Names do not expire. `maintainer` is public descriptive text; it grants no permissions. Deletion never cascades: remove children first.

## Storage and recovery

Records are saved to `plugins/WarpPlus/registry.yml`, with schema version 1. Dotted names are stored as literal values in a list. Each successful change writes a temporary file in the same directory and atomically replaces the registry before updating memory. A failed save leaves the current in-memory registry intact. Filesystems without atomic replacement support reject writes. No shutdown-only save is required.

Loading rejects corrupt YAML, unsupported schemas, invalid records, duplicates, and missing parents. WarpPlus disables itself on load failure and preserves the registry. Fix or restore the file while the server is stopped, then restart. Back up `registry.yml` with your server data. There is no live reload; do not edit the file while the server is running. Small registry writes run synchronously on the server thread; this initial implementation is intended for community-sized registries rather than high-volume bulk registration.

## Development

```sh
./gradlew test
./gradlew build
```

Tests exercise name validation, permission boundaries and delegation, timestamps, failed-save rollback, YAML round trips/corruption, command handling, WHOIS, pagination, completion, and mocked asynchronous teleport handling. They do not launch a Minecraft server or client.

Before production use, test the JAR on your server version: register a namespace, delegate it to a non-operator, create a child warp, check WHOIS as a different player, teleport, restart, and confirm the record and destination persist. Also verify `/warpplus:warp` if another warp plugin is installed.
