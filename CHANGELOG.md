# Changelog

## Unreleased

- Closed audit gaps in recursive bundle/shulker policy enforcement, granular bypasses, offline death-ban administration, exact `/kit load @a`, durable kit reset state, vanish restoration, custom spawn use, ritual world-load recovery, scheduled-dimension cancellation, and custom-duration grace progress.
- Added Locator Bar, one-player sleep, global PvP, PvP damage scaling, XP clumps, shield tweaks, built-in health display, separate invisible kill/death controls, and right-click Warden Heart acquisition.
- Made immortal configured drops invulnerable with unlimited lifetime and made configuration/recipe reloads validate and roll back transactionally.
- Removed inert packet-protection controls and optional packet-provider declarations until real packet transformations can be implemented and verified.
- Added durable 30-minute post-death PvP protection with outgoing-attack revocation and optional loot/container anti-interference.
- Added combat-only Elytra, lava, ice, draining, armour-switching, armour-restocking, container-restocking, and environmental danger-logging controls.
- Added grouped/scoped/stack-based item limits, recoverable insertion audits, and effect/amplifier/duration-aware potion policy.
- Added bed/anchor bombing prevention, doubled Breeze Rod policy, restart-safe dimension unlocks, and invisible-player death-message cutoffs.
- Bundled the deterministic GLITG equipment, potion, combat, grace, End, drop, and inventory policy while leaving hearts, legendary registry, season state, anti-cheat, claims, and moderation external.
- Simplified the admin panel into Gameplay, Balance, and Content categories.
- Shortened labels, help text, chat feedback, prompts, and navigation copy.
- Standardized menu titles, click hints, current-value labels, and enabled-state glints.

## 1.0.0 — 2026-08-14

- Initial clean-room GLITG Core release for Paper 26.2 / Java 25.
- Added modular item/action matching, potion/enchant policies, nested limits, unique crafting, exact custom recipes, protected items, combat, cooldowns, damage caps, PvP protections, grace, death systems, kits, dimensions, administration, altars, rituals, villagers, and competitive QoL mechanics.
- Added WAL SQLite persistence and strict current-schema YAML validation.
- Added original paginated admin GUI and metadata-preserving recipe editor.
- Added JUnit 5/JaCoCo suite and official-Paper smoke-test script.
- Documented 89 parity features, confirmed historical removals, compatibility boundaries, and ambiguous public behavior.
