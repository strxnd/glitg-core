# Commands and permissions

All dangerous permissions default to operators. `<player>` means an online player unless noted.

| Command | Purpose | Permission |
|---|---|---|
| `/glitgcore gui` | Open the Gameplay / Balance / Content control panel | `glitgcore.admin` |
| `/glitgcore reload` | Validate and live-reload YAML/services | `glitgcore.admin` |
| `/glitgcore status|debug|version` | Runtime diagnostics | `glitgcore.admin` |
| `/glitgcore feature <key> <on|off>` | Persist a feature toggle | `glitgcore.admin` |
| `/glitgcore recipe <id>` | Open the virtual shaped/shapeless recipe editor | `glitgcore.admin` |
| `/banitem [ALL|CRAFT|INTERACT|DROPPING|PICKUP|INVENTORY_MOVE|STORAGE|TRADE|EQUIP]` | With no mode, open the appropriate held-item/potion/enchantment GUI; with a mode, add the rule directly | `glitgcore.items.manage` |
| `/itemlimit <amount|remove>` | Manage held-item quantity limit | `glitgcore.items.manage` |
| `/combat` | Remaining combat time | `glitgcore.combat.status` |
| `/protection` | Remaining post-death protection time | `glitgcore.protection.status` |
| `/cooldown status [key]` | Remaining cooldown | `glitgcore.cooldown.status` |
| `/cooldown reset [player] [key]` | Reset one/all cooldowns | `glitgcore.cooldown.reset` |
| `/grace`, `/start [seconds]`, `/stopgrace` | Grace status/control | `glitgcore.grace.status`, `.manage` |
| `/kit save|load [player|@a]|clear|resetplayer <player>|join [on|off]|give <player|@a>` | Exact slot-restoring kit operations and durable eligibility reset | `glitgcore.kit.manage` |
| `/invsee <player>`, `/endersee <player>` | Live inventory editors | `glitgcore.admin.invsee`, `.endersee` |
| `/vanish [player]` | Toggle vanish | `glitgcore.admin.vanish` |
| `/sbroadcast <message>` | Adventure broadcast | `glitgcore.admin.broadcast` |
| `/smsg <player> <message>`, `/reply <message>` | Private messaging | `glitgcore.message.send`, `.reply` |
| `/worldtp <world> [player]` | Loaded-world spawn teleport | `glitgcore.admin.worldtp` |
| `/setrespawnspawn`, `/setcustomspawn` | Save spawn locations | `glitgcore.admin.setspawn` |
| `/dimension status|lock|unlock|schedule <nether|end> [seconds]` | Dimension controls | `glitgcore.dimension.status`, `.manage` |
| `/anonymousdeaths status|start|stop [seconds]` | Independent invisible-player death-message timer | `glitgcore.timers.status`, `.manage` |
| `/uniqueitem query|set|reset <id> [value]` | Atomic craft-counter admin | `glitgcore.unique.manage` |
| `/deathban status|clear [player]` | Death-ban admin | `glitgcore.deathban.manage` |
| `/saltar place|remove|list|info [id]` | Persistent altar admin | `glitgcore.altar.manage` |
| `/enchant <player|@s|@a> <key> [level|remove]` | Policy-aware advanced enchant | `glitgcore.admin.enchant` |

Bypasses: `glitgcore.bypass.itemrules`, `.itemlimits`, `.potions`, `.enchants`, `.protecteditems`, `.cooldowns`, `.damagecaps`, `.combat`, `.protection`, `.dimensions`, `.misc`, and umbrella `glitgcore.bypass.*`. Each policy checks only its own bypass. For operators, **Gameplay → Operator bypass** is the sole bypass authority. With it disabled, operators obey restrictions even if the server implicitly reports a bypass node. Granular nodes are intended for non-operator staff.

Every command has console-safe validation, usage text, tab completion, and exact permission declaration in `plugin.yml`.
