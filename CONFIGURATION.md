# Configuration reference

Every file must use `config-version: 1`. GLITG Core rejects any other schema and never rewrites or upgrades configuration. If reload validation fails, the prior in-memory configuration remains active. Static definitions stay in YAML; dynamic allocation, ban, protection, altar, ritual, and timer state stays in SQLite.

## In-game control console

`/glitgcore gui` exposes the configuration as three operator-focused pages: **Rules**, **Balancing**, and **Content & tools**. Left-click a feature to open its complete editor; right-click its card to toggle the master switch. Boolean values toggle immediately, enum values cycle in either direction, and arbitrary numbers/text/lists use a validated chat prompt (`cancel` exits without saving). Destructive removals always require a separate confirmation screen.

Specialized screens manage exact item rules and limits, potion types and tiers, enchant bans and maximums, death-protected/glowing/stop-storage items, unique craft definitions, virtual shaped/shapeless recipes, join kits, altars, and rituals. Recipe ingredients are virtual copies: editing never consumes player items and never returns duplicated display items. YAML remains a supported source of truth; the GUI writes the same documented schema and refreshes affected services immediately.

The interface uses a shared obsidian, antique-gold, champagne, ivory, silk, and smoke palette. Emerald and garnet are reserved for success/enabled and danger/disabled states. This same visual hierarchy applies to menu titles, navigation, chat prompts, system messages, boss bars, and plugin-owned item names.

## `config.yml`

| Path | Default | Meaning/unit |
|---|---:|---|
| `config-version` | `1` | Exact supported schema version |
| `locale` | `en` | Message locale identifier |
| `update-checker` | `false` | Reserved public update notice toggle; no auto-download |
| `features.<key>` | mixed | Independent master switch; uncertain/destructive mechanics default off |
| `items.limit-scope` | `CARRIED` | Scope assigned to new limits: `CARRIED`, `STORED`, or `COMBAT_LOADOUT` |
| `items.include-ender-chest` | `false` | Include ender chest in player quantity count |
| `items.overflow` | `BLOCK` | Deterministic action block; no silent deletion |
| `items.audit-insertions`, `.audit-interval-ticks` | policy / `100` | Recoverably drop prohibited or excess items inserted outside normal event paths |
| `features.operator-bypass` | `false` | Sole authority for operator gameplay bypass; granular bypass permissions remain available to non-operators |
| `items.traverse-shulkers`, `.traverse-bundles` | `true` | Nested bypass checks |
| `combat.duration-seconds` | `15` | Absolute combat-tag duration |
| `combat.block-commands` | `true` | Block non-whitelisted commands while tagged |
| `combat.whitelisted-commands` | messaging/status | Command labels without leading slash |
| `combat.block-safe-regions` | `true` | Prevent entry to provider-reported safe regions |
| `combat.disconnect-action` | `KILL` | `KILL` or `NONE` |
| `combat.danger-logging.*` | policy | Recent environmental-damage timer and disconnect action |
| `combat.restrictions.*` | policy | Tagged-player Elytra, lava, ice, draining, armour, pickup, and container restrictions |
| `cooldowns.<action>` | `3s`–`60s` | Duration string: `ms`, `s`, `m`, `h`, `d` |
| `damage-caps.<source>` | `16.0`–`24.0` | Maximum final health points; 2 points = 1 heart |
| `protections.afk.enabled` | `false` | AFK incoming/outgoing protection |
| `protections.afk.activation-seconds` | `300` | Still-position time |
| `protections.afk.command-delay-seconds` | `10` | Reserved explicit-AFK delay |
| `protections.naked.enabled` | `false` | Empty-armor incoming/outgoing protection |
| `protections.naked.require-empty-armor` | `true` | Naked predicate |
| `protections.new-player.enabled` | `false` | First-played protection |
| `protections.new-player.duration-seconds` | `3600` | New-player protection duration |
| `protections.post-death.*` | policy / `1800` | Durable protection, outgoing-attack revocation, and protected loot/container restrictions |
| `grace.active-on-startup` | `false` | Start a fresh grace timer on enable if none is persisted |
| `grace.duration-seconds` | `3600` | Default timer and bossbar scale |
| `grace.start-actions` | `[]` | Console commands dispatched by `/start`; optional leading slash |
| `death.spectator-on-death` | `false` | Set spectator on respawn |
| `death.death-ban-seconds` | `0` | Durable death-ban length; zero disables |
| `death.custom-message` | empty | MiniMessage template, supports `<player>` |
| `death.sound` | empty | Namespaced sound key |
| `dimensions.nether-locked`, `.end-locked` | `false` | Portal/API/world-change locks |
| `packet-protections.*` | `false` | Provider-gated health/seed/minimap controls |
| `villagers.infinite-restock` | `false` | Event-driven recipe reset |
| `villagers.anchor-on-click` | `false` | Right-click disables AI |
| `villagers.prevent-killing` | `false` | Cancel direct player damage |
| `misc.hide-invisible-deaths-until` | empty | Independent ISO-8601 cutoff for invisible killer/victim death messages |
| `misc.ban-bed-bombing`, `.ban-respawn-anchor-bombing` | policy | Cancel explosive use in unsafe dimensions |
| `misc.breeze-rod-drop-multiplier` | `2` | Event-driven Breeze Rod drop multiplier |
| `misc.happy-ghast-speed-multiplier` | `1.0` | One-time base flying-speed multiplier |
| `misc.ban-tipped-arrows` | `false` | Cancel potion arrows at launch |
| `misc.ban-breach-swapping` | `false` | Block Breach hand swap |
| `misc.prevent-string-duper` | `false` | Block tripwire piston path |
| `misc.anti-draining` | `false` | Block bucket source pickup |
| `misc.anti-dura` | `false` | Cancel item durability damage |
| `misc.attribute-swapping` | `false` | Block hand swap involving attribute items |
| `misc.better-pearl-catching` | `false` | Catch pearls that collide with players |
| `custom-spawn.*` | empty | World, x/y/z, yaw/pitch written by command |

Feature keys are: `item-rules`, `item-limits`, `potion-policy`, `enchant-policy`, `unique-items`, `custom-crafting`, `protected-items`, `combat-tag`, `cooldowns`, `damage-caps`, `protections`, `grace`, `death-system`, `join-kit`, `dimensions`, `packet-protections`, `admin-utilities`, `altars`, `rituals`, `villagers`, `golden-heads`, `warden-heart`, and `miscellaneous`.

## `items.yml`

`rules.<id>` accepts `enabled`, `actions`, `material`, `potion`, `custom-model-data`, `persistent-data`, `enchantments`, and `tags`. Omitted matcher fields are wildcards; all present fields must match. Actions are `ALL`, `CRAFT`, `INTERACT`, `DROPPING`, `PICKUP`, `INVENTORY_MOVE`, `STORAGE`, `TRADE`, and `EQUIP`.

`limits.<id>` uses the same matcher plus `maximum` or `maximum-stacks`, optional shared `group`, and `scope` (`CARRIED`, `STORED`, or `COMBAT_LOADOUT`). Limits sharing a group are counted together. `protected.<id>` uses the same matcher plus `immortal`, `glowing`, and `stop-storage`. `unique.<id>` accepts `enabled`, `recipe-key` (or `recipe-id`), and global craft `limit`.

`potion-policy` accepts banned base potion keys, banned effect keys, tier bans, per-effect `maximum-amplifier`, and `duration-rules.<id>` with `effect`, optional `amplifier`, and minimum/maximum ticks. Validation applies to base and custom effects on drinkable, splash, lingering, and tipped-arrow ItemStacks. `golden-head` and `warden-heart` expose their material/effect/drop defaults.

Modern custom-model-data components are fingerprinted into safe matcher tags (`cmd-float:`, `cmd-string:`, `cmd-flag:`, `cmd-color:`); the integer field matches an integral first float component. PDC matching reads only explicitly configured string keys and never rewrites unrelated data.

## `enchants.yml`

- `banned`: namespaced enchantment keys.
- `maximum-levels.<key>`: maximum accepted integer level.
- `exempt-materials`: exact material exemptions.

## `recipes.yml`

Each `recipes.<id>` has `enabled`, `type` (`SHAPED`/`SHAPELESS`), result as `result-material` or `result-item: base64:<payload>`, `result-amount`, optional `shape`, and `ingredients`. Ingredients are materials or exact `base64:` ItemStacks. The GUI can browse, create, preload, edit, enable/disable, and remove definitions while preserving exact payloads; validation rejects unknown materials, malformed shapes, and registration conflicts.

## `rituals.yml`

`altars.<definition>`: `enabled`, altar `block`, `interaction-radius`. `rituals.<id>`: `enabled`, `altar`, `input-material`, `input-amount`, `duration-seconds`, particle `radius`, `particle`, `result-material`, `result-amount`, and completion `commands` supporting `<x>`, `<y>`, `<z>`.

## `kits.yml` and `messages.yml`

`kits.yml` stores `join-enabled` and a slot-preserving list of exact ItemStack payloads. `messages.yml` contains every player-facing MiniMessage template: prefix, permission/player errors, reload/config errors, feature/item/enchant/cooldown/combat/grace/dimension/death-ban, and dependency notices. Placeholders are escaped before insertion.

The named MiniMessage colours `gold`, `yellow`, `white`, `gray`/`grey`, `dark_gray`/`dark_grey`, `green`, and `red` are semantic roles and render through the shared luxury palette. Explicit hex colours such as `<#4A90E2>` are preserved when a message needs a deliberate custom colour.
