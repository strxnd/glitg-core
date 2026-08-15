# GLITG Core

GLITG Core is an MIT-licensed, clean-room Paper plugin for administering a competitive SMP. It independently implements behavior described in public SMP Core v3.5 materials; it contains no downloaded, decompiled, or copied proprietary code, assets, messages, menus, or branding.

Target: Paper 26.2, Java 25, GLITG Core 1.0.0.

## Install

1. Run `./gradlew clean test build`.
2. Copy `build/libs/glitg-core-1.0.0.jar` into the Paper server's `plugins/` directory.
3. Start Paper once, edit the generated files in `plugins/GLITGCore/`, then run `/glitgcore reload`.
4. Grant specific `glitgcore.*` permissions to staff. Management permissions default to operators, but gameplay bypasses do not. The Gameplay panel has an explicit global operator-bypass toggle.

All ambiguous or potentially destructive mechanics default off. Item rules, limits, enchant rules, cooldowns, caps, combat, grace, kits, recipes, dimensions, rituals, and utilities can be changed independently. `/glitgcore gui` opens a clean Gameplay / Balance / Content control panel. Every control shows its current value and click behavior; detailed editors cover items, potions, enchantments, recipes, kits, unique crafts, altars, and rituals. `/glitgcore recipe <id>` opens the virtual metadata-preserving shaped/shapeless editor. `/glitg` is the short command alias.

## Optional integrations

- WorldGuard: reflective safe-region checks plus the public `RegionProvider` service API.
- PacketEvents or ProtocolLib: detected as soft packet providers. Packet-only defenses are never allowed to prevent startup.
- LifeStealZ, CoreProtect, and Grim: no hard dependency. GLITG Core limits mutations to configured items/events.

The public SMP Core overview conflicts with its April 2026 changelog about whether ProtocolLib or PacketEvents is the current packet provider. GLITG Core therefore treats both as optional and clearly reports inactive packet defenses. See [COMPATIBILITY.md](COMPATIBILITY.md).

## Quick examples

```text
/glitgcore gui
/banitem INTERACT        # uses the exact item in the main hand
/itemlimit 1             # supports potion/custom-model/PDC-aware identity
/dimension lock end
/start 600
/saltar place basic
```

Documentation: [commands](COMMANDS.md), [configuration](CONFIGURATION.md), [architecture](ARCHITECTURE.md), [parity](PARITY.md), [testing](TESTING.md), and [compatibility](COMPATIBILITY.md).

## Clean-room evidence boundary

Research used only public BuiltByBit pages, changelog entries, screenshots/listing text, and official public APIs. No paid JAR was acquired or inspected. Public sources are linked directly in `PARITY.md` and `REMOVED_FEATURES.md`.
