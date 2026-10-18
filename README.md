# WarpPlus

A PaperMC plugin with hierarchical warp domains, delegated ownership, and a public WHOIS registry.

- `/warp <destination>` teleports.
- `/whois <domain>` shows registry details.
- `/warpplus` browses and manages the registry.

Version 1.2 moves the former `/warp` management subcommands to `/warpplus`; records and destinations are unchanged. There is no `/warp go`, `/warp legacy`, or `/warp whois` subcommand.

```text
com                         namespace
└── com.example             organization namespace
    ├── com.example.market  teleport destination
    └── com.example.harbor  teleport destination
```

Names run **from parent to child, left to right**, as in the example above. Each parent must exist before its children can be registered. Every record has its own owner, registration/update timestamps, and optional public name, organization, contact, website, and description. A record may be a namespace, a destination, or a destination with children.

## Build and install

1. Install JDK 21 or newer to build, then run `./gradlew build` (`gradlew.bat build` on Windows).
2. Copy `build/libs/warpplus-1.3.1.jar` into your Paper server's `plugins/` directory, replacing the previous WarpPlus JAR if present.
3. Restart the server.

The plugin compiles against **Paper 1.21.11**, targets Java 21 bytecode, and uses no server internals. Paper 1.21.11 is the minimum supported API; newer Paper versions should be verified on your server before deployment. Use the Java runtime required by your server: Paper 1.21.11 uses Java 21; Paper 26.1+ requires Java 25. See [Paper's requirements](https://docs.papermc.io/paper/getting-started/) and [plugin setup](https://docs.papermc.io/paper/dev/project-setup/).

WarpPlus runs standalone or integrates with **EssentialsX** when its `Essentials` plugin is enabled at startup. The integration compiles against the EssentialsX 2.21.2 API; install an EssentialsX release compatible with your Paper version. There are no external services or runtime library downloads from WarpPlus.

## EssentialsX integration

EssentialsX keeps its warp locations, permissions, and teleport settings. WarpPlus adds hierarchical names, ownership, and WHOIS records that **link to existing EssentialsX warps**. It never copies, creates, moves, or deletes EssentialsX warp locations. A link resolves the target on every visit, so moving `/setwarp market` changes where every linked domain leads. Removing that warp causes EssentialsX to report its normal missing-warp error.

For an existing EssentialsX warp named `market`, run these as a registrar/operator:

```text
/warpplus namespace com
/warpplus namespace com.example
/warpplus link com.example.market market
/warpplus set com.example.market organization Example Guild
/warpplus set com.example.market contact Discord: example-guild
/whois com.example.market
/warp com.example.market
```

Create the target first with EssentialsX's `/setwarp market` if it does not exist (requires EssentialsX's normal permissions). `/warpplus link` creates a domain if its parent exists and you control it, or replaces the destination of a domain you control. Linking does not grant permission to visit the target warp.

With the integration active:

| Command | Behavior |
| --- | --- |
| `/warp spawn` | Visit the existing EssentialsX warp if `spawn` is not a registered WarpPlus domain. |
| `/warp com.example.market` | Visit the EssentialsX warp linked to that domain. |
| `/warp com.example.market Alex` | EssentialsX's teleport-other-player form, with its normal permissions. |
| `/warp` | Show teleport usage; no warp list or registry actions. |
| `/warpplus list com.example` | Browse the WarpPlus hierarchy. |
| `/warpplus help` | Show WarpPlus help. |

Registered WarpPlus names take priority over legacy warp names. `/warp` has no subcommands: names such as `help`, `list`, or `delete` are ordinary teleport destinations. Bare `/warp` reports usage, and numeric EssentialsX page arguments are rejected instead of listing warps. Browse with `/warpplus list`. `/ewarp` and `/essentials:warp` remain direct EssentialsX commands, including EssentialsX’s own listing behavior.

For player-entered legacy and linked teleport commands, WarpPlus rewrites the command to `/essentials:warp <target>` **before EssentialsX's player-command listener** runs. EssentialsX therefore sees the original warp name for command cooldown matching and handles its normal warp permissions, per-warp permissions, costs, warmups, teleport cooldowns, safety checks, and `/back` state. WarpPlus never runs these as console or temporarily grants permissions. Programmatic Bukkit command execution delegates directly to the EssentialsX command; as with normal Bukkit dispatch, it does not synthesize a player-command preprocessing event. Tab suggestions for legacy targets come from EssentialsX's own permission-filtered completer. [EssentialsX warp implementation](https://github.com/EssentialsX/Essentials/blob/2.21.2/Essentials/src/main/java/com/earth2me/essentials/commands/Commandwarp.java), [command listener](https://github.com/EssentialsX/Essentials/blob/2.21.2/Essentials/src/main/java/com/earth2me/essentials/EssentialsPlayerListener.java).

If EssentialsX becomes unavailable, links remain in the registry and visits fail without falling back to a local position. WHOIS shows the target name and backend status, without reading or publishing its world coordinates. `/warpplus clearlocation <domain>` removes a link while preserving the domain, metadata, and children. Deleting a WarpPlus record also leaves the EssentialsX warp untouched.

`/warpplus register` and `/warpplus setlocation` still create **local WarpPlus destinations**, with WarpPlus's standalone teleport behavior. They do not use EssentialsX warp permission/cost/warmup settings. Use `/warpplus link` for an EssentialsX-managed destination. Converting between a link and a local destination replaces the previous destination; there is no hidden fallback.

### Command ownership

EssentialsX normally hands overlapping commands to another plugin that provides them. Keep `warp` and `whois` out of EssentialsX's `overridden-commands` setting to use WarpPlus's versions. `/whois <domain>` reads the public domain registry; use `/essentials:whois <player>` or `/ewhois <player>` for EssentialsX's player lookup. If command ownership on your server differs, use `/warpplus:warp`, `/warpplus:whois`, and `/warpplus:warpplus` explicitly. See [EssentialsX's command conflict guidance](https://essentialsx.net/wiki/faq).

You can also merge this alias into your server-root `commands.yml`, then restart:

```yaml
aliases:
  warp:
    - 'warpplus:warp $1-'
  whois:
    - 'warpplus:whois $1-'
```

Preserve other aliases already in that file. Paper's legacy `commands.yml` aliases have limitations around tab completion; the explicit `/warpplus:warp` command retains WarpPlus's completer. [Paper alias documentation](https://docs.papermc.io/paper/reference/bukkit-commands-configuration/).

## Standalone destination example

As an operator in-game:

```text
/warpplus namespace com
/warpplus namespace com.example
/warpplus set com.example name Example Guild
/warpplus set com.example organization Example Builders Association
/warpplus set com.example contact Discord: example-guild
/warpplus set com.example website https://example.com
/warpplus transfer com.example Alex
```

`Alex` must be online when transferring by name. A UUID also works, including for offline players. The console can create namespaces and transfer them using the same commands; console-created namespaces initially belong to the server administrators.

Alex then stands at the market entrance and runs:

```text
/warpplus register com.example.market
/warpplus set com.example.market name Example Central Market
/warpplus set com.example.market contact Discord: example-guild
/warpplus set com.example.market description Player shops, stalls, and community trading.
```

Anyone with the default use permission can browse, query, and travel:

```text
/warpplus list
/warpplus list com
/warpplus list com.example
/whois com.example.market
/warpwhois com.example.market
/warp com.example.market
```

Metadata belongs to each individual record; it does not inherit from a parent. WHOIS displays missing fields as `(not provided)`. All registry metadata, link names, and local destination coordinates are public to players with `warpplus.use`. Contact fields should contain information intended for public display. Text is literal, without color codes, MiniMessage, or link execution.

## Commands

| Command | Purpose |
| --- | --- |
| `/warp <destination> [player]` | Teleport to a registered domain or legacy EssentialsX warp. The player argument requires EssentialsX; console must supply it. |
| `/whois <domain>` | Show the public registry record. |
| `/warpwhois <domain>` or `/wwhois <domain>` | Compatibility aliases for `/whois`. |
| `/warpplus list [parent\|-] [page]` | List direct children, sorted by name, eight per page. Omit parent or use `-` for roots. |
| `/warpplus register <domain>` | Register a destination at your current position, including yaw and pitch. Players only. |
| `/warpplus namespace <domain>` | Register a name without a destination. |
| `/warpplus link <domain> <essentials-warp>` | Register or update a controlled domain with an existing EssentialsX warp target. |
| `/warpplus set <domain> <field> <value...>` | Set `name`, `organization`, `contact`, `website`, or `description`. Use `-` as the value to clear it. |
| `/warpplus setlocation <domain>` | Set or move a destination to your current position. Players only. |
| `/warpplus clearlocation <domain>` | Remove a local destination or EssentialsX link while keeping its registration and children. |
| `/warpplus transfer <domain> <online-player\|uuid>` | Transfer that record's ownership. |
| `/warpplus delete <domain>` | Delete a record with no children. |
| `/warpplus help` | Show command help. |

Names are case-insensitive ASCII letters/digits with internal hyphens, separated by dots. Labels have 1–63 characters; the full name has at most 253 characters and 16 labels. For example, `COM.Example` resolves to `com.example`. Unicode is supported in descriptive metadata. Tab completion offers names, manageable domains, metadata fields, and online transfer recipients. List entries can suggest the corresponding browse or teleport command when clicked.

Local teleportation uses Paper's asynchronous chunk loading. A namespace without a destination cannot be visited; `/warpplus list <namespace>` shows its children. Local destinations resolve by world UUID, and unloaded/missing worlds, out-of-height positions, border violations, and cancelled teleports return an error. Local warps use the exact registered position, without searching for safe ground; owners should choose suitable arrival points. Linked destinations use EssentialsX's own teleport handling instead.

## Permissions and delegation

| Permission | Default | Grants |
| --- | --- | --- |
| `warpplus.use` | Everyone | Teleport, list, help, and WHOIS. |
| `warpplus.register` | Everyone | Manage owned domains and register children under controlled domains. |
| `warpplus.register.root` | Operators | Register top-level names; also requires `warpplus.register`. |
| `warpplus.admin` | Operators | Manage the entire registry and bypass registration ownership checks. |

Command access always requires `warpplus.use`, including for administrators. Owning a domain or **any ancestor** gives management authority over it, provided the player retains `warpplus.register`. This includes editing details/destinations, delegating ownership, registering descendants, and deleting leaf records. Unrelated players cannot claim names underneath it. A new top-level name requires registrar or administrator permission.

**Delegation preserves parent authority.** If Alice owns `com` and delegates `com.example` to Bob, both can manage `com.example` and its descendants. Bob cannot manage `com` or `com.other`. Transferring a parent changes inherited authority immediately, while explicit owners of child records remain unchanged. Names do not expire. Deletion never cascades: remove children first.

## Storage and recovery

Records are saved to `plugins/WarpPlus/registry.yml`, with schema version 2. Version 1.3 removes the `poi` and `maintainer` fields. Older registries still load: these retired fields are ignored and removed on the next successful save; loading alone does not rewrite the file. Other metadata, ownership, timestamps, and destinations are preserved. Existing version 1 registries load unchanged and upgrade on the next successful edit. Back up your registry before upgrading; WarpPlus 1.0 cannot read version 2 files. Dotted names are stored as literal values in a list. Each successful change writes a temporary file in the same directory and atomically replaces the registry before updating memory. A failed save leaves the current in-memory registry intact. Filesystems without atomic replacement support reject writes. No shutdown-only save is required.

Loading rejects corrupt YAML, unsupported schemas, invalid records, duplicates, and missing parents. WarpPlus disables itself on load failure and preserves the registry. Fix or restore the file while the server is stopped, then restart. Back up `registry.yml` with your server data. There is no live reload; do not edit the file while the server is running. Small registry writes run synchronously on the server thread; this initial implementation is intended for community-sized registries rather than high-volume bulk registration.

## Development

```sh
./gradlew test
./gradlew build
```

Tests exercise name validation, permission boundaries and delegation, timestamps, failed-save rollback, YAML round trips/corruption and migration, command handling, WHOIS, pagination, completion, mocked asynchronous teleport handling, and the optional EssentialsX bridge and command routing. They do not launch a Minecraft server or client.

Before production use, test the JAR on your server version: register a namespace, delegate it to a non-operator, create a child warp, check WHOIS as a different player, teleport, restart, and confirm the record and destination persist. With EssentialsX, also link an existing warp, verify allowed/denied users, warmups/costs/cooldowns, move/delete the backing warp, and restart with EssentialsX absent to confirm links remain unavailable. Check both `/warp` and `/warpplus:warp` on your server's command setup.

## Cutting a release

Run **Actions → Cut release → Run workflow** on GitHub and choose `patch`, `minor`, or `major` (default: `patch`). The workflow always releases `main`. It bumps the version in `build.gradle.kts` and the installation JAR filename in this README, commits the change, builds and tests that exact commit with Java 21, verifies the packaged plugin version, and creates an annotated `vX.Y.Z` tag. It then publishes a GitHub release with generated notes and two downloadable assets:

- `warpplus-X.Y.Z.jar` — copy this into the server's `plugins/` directory.
- `warpplus-X.Y.Z.jar.sha256` — verify with `sha256sum --check warpplus-X.Y.Z.jar.sha256`.

The workflow uses the repository's automatic `GITHUB_TOKEN`; no publishing secret is required. Repository rules must allow GitHub Actions to push the release commit to `main` and create release tags. A tag push alone does not run the release workflow. If the build fails, the version commit remains on `main`, but no release tag is created. Retry failed publishing jobs from the same workflow run so they use the same release commit; starting another dispatch cuts a new version. Existing tags must point to that exact commit. Draft releases can be completed on retry; published release assets are compared and never overwritten.
