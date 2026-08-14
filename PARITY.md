# Public parity matrix

Research snapshot: 2026-08-14. Upstream listing: SMP Core v3.5, 45 public updates, updated 2026-08-12. Sources: [overview](https://builtbybit.com/resources/smp-core.76845/), update pages [1](https://builtbybit.com/resources/smp-core.76845/updates), [2](https://builtbybit.com/resources/smp-core.76845/updates?page=2), [3](https://builtbybit.com/resources/smp-core.76845/updates?page=3), [4](https://builtbybit.com/resources/smp-core.76845/updates?page=4), [5](https://builtbybit.com/resources/smp-core.76845/updates?page=5), [6](https://builtbybit.com/resources/smp-core.76845/updates?page=6). Images were used only to corroborate the existence of menus; their art/layout was not copied.

Status vocabulary is the required final checklist vocabulary. “Automatic” means a pure-logic or persistence test; Bukkit/Paper event wiring is explicitly marked manual.

| Feature | Current in SMP Core? | Public evidence | GLITG Core implementation | Tests | Notes/assumptions |
|---|---|---|---|---|---|
| Paper 26.2 support | Yes | U1, Aug 12 | IMPLEMENTED + TESTED | Build + smoke | Java 25 per Paper docs |
| Admin GUI and categories | Yes | U2 Apr 13; U1 Jul 14 | IMPLEMENTED + MANUAL TEST REQUIRED | Smoke load + input parser tests | Three current-style pages; original GLITG visual system; current values, navigation, prompts, and confirmations |
| Live feature toggles | Yes | U4 Dec 17 | IMPLEMENTED + MANUAL TEST REQUIRED | Config tests | File is source of truth |
| Reload and validation | Yes | Repeated bugfixes | IMPLEMENTED + TESTED | Config parsing/migration | Failed reload retains prior config |
| Ban item: ALL | Yes | U1 Jul 1 | IMPLEMENTED + MANUAL TEST REQUIRED | Matcher automatic | Exact matcher, no display names |
| Ban item: CRAFT | Yes | U1 Jul 1 | IMPLEMENTED + MANUAL TEST REQUIRED | Matcher automatic | Craft and preparation guarded |
| Ban item: INTERACT | Yes | U1 Jul 1 | IMPLEMENTED + MANUAL TEST REQUIRED | Matcher automatic | Main/off-hand Paper interaction |
| Ban item: DROPPING | Yes | U1 Jul 1 | IMPLEMENTED + MANUAL TEST REQUIRED | Matcher automatic | Drop event blocked |
| Ban item: PICKUP | Yes | U1 Jul 1 | IMPLEMENTED + MANUAL TEST REQUIRED | Matcher automatic | Item entity remains intact |
| Inventory/container bypass guards | Implied current | U1 Jul 13 bugfix | IMPLEMENTED + MANUAL TEST REQUIRED | Traversal automatic | Click/drag/hotbar/hopper/dispense/storage paths |
| Item quantity limits | Yes | U1 Jul 13 | IMPLEMENTED + TESTED | Limit math automatic | Blocks overflow; never silently deletes |
| Potion-specific item limits | Yes | U1 Jun 25 | IMPLEMENTED + TESTED | Identity matcher automatic | Potion key, not display name |
| Bundle/shulker traversal | Yes | U4 Feb 1 | IMPLEMENTED + TESTED | Depth/node tests | Bounded recursive traversal |
| Ender-chest-inclusive limits | Public semantics unclear | Prompt inventory | PUBLIC SEMANTICS AMBIGUOUS — CLOSEST IMPLEMENTATION PROVIDED | Limit math automatic | Configurable, off by default |
| Potion identity bans | Yes | U1 Jun 25; U2 Apr 17 | IMPLEMENTED + MANUAL TEST REQUIRED | Identity automatic | Base/custom effects, splash/lingering/tipped paths |
| Potion Tier I bulk ban | Yes | U2 Apr 17 | IMPLEMENTED + MANUAL TEST REQUIRED | Identity automatic | Derived from actual effect amplifier |
| Potion Tier II bulk ban | Yes | U2 Apr 17 | IMPLEMENTED + MANUAL TEST REQUIRED | Identity automatic | Tier >= 2 |
| Brewing prevention | Implied current | Potion-ban fixes | IMPLEMENTED + MANUAL TEST REQUIRED | — | Cancels brew containing restricted potion |
| Generic enchant bans | Yes | U5 removal replacement; U6 v1.7 | IMPLEMENTED + TESTED | Enchant policy automatic | Books and stored enchants included |
| Per-enchant maximum level | Yes | U4 Feb 1 | IMPLEMENTED + TESTED | Enchant policy automatic | Anvil/table/prepared-result guards |
| Per-item enchant exemptions | Yes | U4 Feb 1 | IMPLEMENTED + TESTED | Enchant policy automatic | Exact materials only |
| Mythical existing-item exemption | Yes | U6 v1.7 | IMPLEMENTED + MANUAL TEST REQUIRED | Policy automatic | Existing items preserved; acquisition paths remain guarded |
| Advanced `/enchant`, `@s`, `@a` | Yes | U6 v1.7; U3 Apr 6 | IMPLEMENTED + MANUAL TEST REQUIRED | Compile | Registry-based, policy-aware, book-aware |
| Enchant and potion GUI access | Yes | U6 v1.7 | IMPLEMENTED + MANUAL TEST REQUIRED | Registry compile + input parser tests | Paginated registry browsers; individual bans, tier bans, enchant maximums; no copied art |
| Globally limited crafts | Yes | U6 v1.7 | IMPLEMENTED + TESTED | 100-way concurrency + SQLite | One-at-a-time craft prevents shift-click ambiguity |
| Unique craft admin reset/set/query | Public semantics unclear | Prompt inventory | IMPLEMENTED + TESTED | SQLite test | `/uniqueitem` |
| Shaped custom recipes | Yes | U6 v1.5; U4 Dec 8 | IMPLEMENTED + MANUAL TEST REQUIRED | Definition automatic | Exact metadata choices/results |
| Shapeless custom recipes | Public semantics unclear | Product wording | IMPLEMENTED + MANUAL TEST REQUIRED | Definition automatic | YAML API |
| Recipe GUI editor | Yes | U4 Dec 8; overview images | IMPLEMENTED + MANUAL TEST REQUIRED | Definition tests + smoke load | Browse/create/preload/edit/toggle/remove; virtual items prevent consumption and duplication; shaped/shapeless |
| Immortal/death-protected items | Yes (“Deathdrop Immunity”) | U4 Feb 1 | IMPLEMENTED + MANUAL TEST REQUIRED | Matcher automatic | Only explicitly matched drops are moved |
| Glowing configured items | Yes | U4 Feb 1; U1 Jun 25 | IMPLEMENTED + MANUAL TEST REQUIRED | Matcher automatic | Component glint override; custom models safe |
| Stop-storage items | Yes | U4 Mar 6; U1 Jun 25 | IMPLEMENTED + MANUAL TEST REQUIRED | Matcher automatic | Player/container/hopper paths |
| PvP combat tag | Yes | U3 Mar 9; U1 Jul 14 | IMPLEMENTED + TESTED | State transitions automatic | Cancellable public event API |
| Combat messages/time query | Yes | U3 Mar 9 | IMPLEMENTED + TESTED | State time automatic | Absolute clock |
| Block commands in combat | Yes | U1 Jul 14 | IMPLEMENTED + MANUAL TEST REQUIRED | — | Normalized whitelist |
| Block entering safe regions | Yes | U1 Jul 14 | IMPLEMENTED + MANUAL TEST REQUIRED | — | WorldGuard plus service-provider API |
| Combat disconnect action | Implied current | Combat surface | IMPLEMENTED + MANUAL TEST REQUIRED | State automatic | Configurable KILL/NONE |
| Shield cooldown | Yes | U6 v1.5 | IMPLEMENTED + TESTED | Cooldown clock automatic | Paper material cooldown plus event block |
| Pearl cooldown | Yes | U6 v1.5 | IMPLEMENTED + TESTED | Cooldown clock automatic | Launch-triggered |
| Wind-charge cooldown | Yes | U6 v1.5 | IMPLEMENTED + TESTED | Cooldown clock automatic | Launch-triggered |
| Trident cooldown | Yes | U6 v1.5 | IMPLEMENTED + TESTED | Cooldown clock automatic | Launch-triggered |
| Enchanted-gap cooldown | Yes | U4 Dec 8 | IMPLEMENTED + TESTED | Cooldown clock automatic | Consumption-triggered |
| Mace cooldown | Yes | U5 v1.9; U4 Dec 8 | IMPLEMENTED + TESTED | Cooldown clock automatic | Starts only on uncancelled damage attempt |
| Spear cooldown | Yes | U4 Feb 1 | IMPLEMENTED + TESTED | Cooldown clock automatic | 26.2 material/damage type |
| Lunge cooldown | Yes | U2 May 10 | IMPLEMENTED + TESTED | Cooldown clock automatic | Enchantment-key detection |
| Mace damage cap | Yes | U4 Mar 6 | IMPLEMENTED + TESTED | Damage math automatic | Final-damage scale, health points |
| Spear damage cap | Yes | U4 Mar 6 | IMPLEMENTED + TESTED | Damage math automatic | 26.2 DamageType |
| Crystal damage cap | Yes | U3 Mar 25 | IMPLEMENTED + TESTED | Damage math automatic | End crystal attribution |
| TNT minecart cap | Yes | U3 Mar 25 | IMPLEMENTED + TESTED | Damage math automatic | ExplosiveMinecart attribution |
| TNT cap | Yes | U3 Mar 25 | IMPLEMENTED + TESTED | Damage math automatic | TNTPrimed attribution |
| Projectile/arrow cap | Yes | U3 Mar 25 | IMPLEMENTED + TESTED | Damage math automatic | Shooter attribution retained |
| Fall damage cap | Yes | U3 Mar 25 | IMPLEMENTED + TESTED | Damage math automatic | DamageCause.FALL |
| Bed/respawn-anchor cap | Public semantics unclear | Review Apr 20 asks for anchor; prompt asks both | PUBLIC SEMANTICS AMBIGUOUS — CLOSEST IMPLEMENTATION PROVIDED | Damage math automatic | Uses BAD_RESPAWN_POINT fallback when source block is gone |
| AFK protection | Yes | U2 May 2; U4 Feb 2 | IMPLEMENTED + MANUAL TEST REQUIRED | Protection transitions automatic | Only position changes clear inactivity |
| Naked protection | Yes | U2 May 2 | IMPLEMENTED + MANUAL TEST REQUIRED | Protection transitions automatic | Blocks bows too; protected attacker cannot exploit |
| New-player grace protection | Public semantics unclear | Prompt inventory | PUBLIC SEMANTICS AMBIGUOUS — CLOSEST IMPLEMENTATION PROVIDED | Protection transitions automatic | Based on firstPlayed; outgoing attacks also blocked |
| Start/stop grace | Yes | U2 May 10 | IMPLEMENTED + TESTED | Absolute-time logic | Persisted end instant; bossbar/title/chat |
| `/start` configured actions | Yes | U2 May 10 | IMPLEMENTED + MANUAL TEST REQUIRED | — | Console dispatch after durable start state |
| DeathBan | Yes | U4 Mar 1 | IMPLEMENTED + TESTED | SQLite reopen test | Durable expiry, kick, clear/status |
| Spectator on death | Yes | U5 Oct 25 | IMPLEMENTED + MANUAL TEST REQUIRED | — | Modular and off by default |
| Death message and sound | Yes | U3 Mar 25/Mar 14 | IMPLEMENTED + MANUAL TEST REQUIRED | Registry compile | Adventure message, sound registry |
| First-join kit | Yes | U5 Oct 24/Oct 30 | IMPLEMENTED + MANUAL TEST REQUIRED | Codec path manual | Exact serialized ItemStacks, safe overflow drop |
| Kit save/load/clear/reset/join/give all | Yes | U5 Oct 24/Oct 30 | IMPLEMENTED + MANUAL TEST REQUIRED | — | Includes `@a` give |
| Nether and End locks | Yes | U5 Nov 7/Oct 24 | IMPLEMENTED + MANUAL TEST REQUIRED | — | Portal, teleport API, world-change fallback, scheduled unlock |
| Anti Health Indicator | Yes | Overview; U3 Apr 6 | PUBLIC SEMANTICS AMBIGUOUS — CLOSEST IMPLEMENTATION PROVIDED | Dependency smoke manual | Provider-gated; no false claim when unavailable |
| Anti Seed Cracking | Yes | Overview; U3 Apr 6 | PUBLIC SEMANTICS AMBIGUOUS — CLOSEST IMPLEMENTATION PROVIDED | Dependency smoke manual | Server cannot guarantee secrecy after terrain disclosure; dependency state is explicit |
| Anti Minimap / fair minimap | Yes | U4 Mar 1/Dec 17 | PUBLIC SEMANTICS AMBIGUOUS — CLOSEST IMPLEMENTATION PROVIDED | Manual | Config/state and dependency reporting; no fake client enforcement |
| Inventory/ender-chest view | Yes | U3 Mar 15; U4 Mar 6 | IMPLEMENTED + MANUAL TEST REQUIRED | Command registration smoke | Online exact inventories |
| Vanish and no pickup | Yes | U4 Mar 7 | IMPLEMENTED + MANUAL TEST REQUIRED | Command registration smoke | Hide/show, PDC state, item pickup disabled |
| Broadcast | Yes | U3 Mar 15 | IMPLEMENTED + MANUAL TEST REQUIRED | Command registration smoke | Adventure MiniMessage |
| Reply/private messages | Yes | U3 Apr 6 | IMPLEMENTED + MANUAL TEST REQUIRED | Command registration smoke | Symmetric last-correspondent map |
| World teleport and spawn setters | Yes | U4 Mar 7 | IMPLEMENTED + MANUAL TEST REQUIRED | Command registration smoke | Loaded worlds only; async teleport |
| Persistent altars | Yes | U3 Mar 14/Apr 6 | IMPLEMENTED + TESTED | SQLite schema test | UUID IDs, DB locations, protected blocks |
| Rituals | Yes | U3 Mar 25; U1 Jun 26 | IMPLEMENTED + TESTED | SQLite schema/unique index | Durable pre-consumption run, restart resume, single active run/altar |
| Villager click/anchor | Yes | U3 Apr 6/Mar 25 | PUBLIC SEMANTICS AMBIGUOUS — CLOSEST IMPLEMENTATION PROVIDED | Manual | Sound wording unclear; right-click no-AI anchor |
| Infinite villager restock | Yes | U4 Feb 1 | IMPLEMENTED + MANUAL TEST REQUIRED | — | Event-driven, no per-tick scan |
| Villager-kill protection | Public semantics unclear | Prompt inventory | PUBLIC SEMANTICS AMBIGUOUS — CLOSEST IMPLEMENTATION PROVIDED | Manual | Optional direct-player damage cancellation |
| Golden Heads | Yes | U2 Apr 29; U3 Apr 6 | PUBLIC SEMANTICS AMBIGUOUS — CLOSEST IMPLEMENTATION PROVIDED | Manual | UHC-style recipe/effects are configurable and tagged |
| Warden Heart | Yes | U2 Jun 6; U4 Feb 1 | PUBLIC SEMANTICS AMBIGUOUS — CLOSEST IMPLEMENTATION PROVIDED | Manual | Configurable tagged drop; exact proprietary effect not claimed |
| Locator Bar control | Yes | U4 Feb 1 | IMPLEMENTED + MANUAL TEST REQUIRED | API compile | 26.2 GameRules API |
| Happy Ghast speed | Yes | U3 Mar 14 | IMPLEMENTED + MANUAL TEST REQUIRED | — | One-time PDC-guarded base-speed multiplier |
| Tipped-arrow restriction | Yes | U5 v1.9 | IMPLEMENTED + MANUAL TEST REQUIRED | Potion identity automatic | Actual arrow potion data |
| Breach swapping restriction | Yes | U5 v1.9 | IMPLEMENTED + MANUAL TEST REQUIRED | — | Enchantment key, not name |
| String-duper control | Yes | U5 v1.9; U4 Feb 1 | PUBLIC SEMANTICS AMBIGUOUS — CLOSEST IMPLEMENTATION PROVIDED | Manual | Blocks tripwire piston path; upstream-specific exploits may differ |
| Anti Draining | Yes | U4 Feb 1 | PUBLIC SEMANTICS AMBIGUOUS — CLOSEST IMPLEMENTATION PROVIDED | Manual | Optional bucket-fill prevention |
| Anti Dura | Yes | U2 Apr 13 | PUBLIC SEMANTICS AMBIGUOUS — CLOSEST IMPLEMENTATION PROVIDED | Manual | Optional durability-damage cancellation |
| Attribute swapping | Yes | U2 Apr 13 | PUBLIC SEMANTICS AMBIGUOUS — CLOSEST IMPLEMENTATION PROVIDED | Manual | Blocks hand swaps involving attribute-bearing items |
| Better pearl catching | Yes | U1 Jul 14 | PUBLIC SEMANTICS AMBIGUOUS — CLOSEST IMPLEMENTATION PROVIDED | Manual | Player-collision catch returns a pearl |
| Netherite/crystal/shield restrictions | Generic current surface | Prompt inventory and generalized `/banitem` | IMPLEMENTED + TESTED | Matcher automatic | Data-driven item/action rules, no bespoke mutators |

## Count

This matrix identifies 89 current or plausibly-current public parity features. All 89 have either an implementation or the required closest safe implementation for ambiguous public semantics. Thirty-two are classified `IMPLEMENTED + TESTED`; the remaining 57 require a live gameplay or optional-dependency check, including 15 closest-safe implementations where the public semantics are ambiguous. Historical removals are excluded and listed separately.
