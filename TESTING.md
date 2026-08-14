# Testing

## Automated suite

Run:

```bash
./gradlew clean test build
```

JUnit 5 tests cover exact/potion/PDC/model/enchant matching, enchant maximums and exemptions, nested traversal limits, quantity/overflow math, absolute cooldowns, combat transitions, protection revocation, damage cap units, config durations/validation/migration, 100-way unique allocation, SQLite migration/reopen, death-ban/state persistence, and recipe definition validation. JaCoCo HTML/XML reports are generated under `build/reports/jacoco/test/`.

## Real Paper smoke test

```bash
./scripts/smoke-test.sh
```

The script uses Paper's official Fill v3 service with a descriptive User-Agent (and Python 3 only to parse its JSON), downloads the latest stable 26.2 server, builds/copies the fat JAR, creates a disposable `build/smoke-server`, accepts the EULA only there, boots with Java 25+, runs version/status console commands, stops cleanly, and scans the log for enable failures and exceptions. The retained log is `build/smoke-server/server.log`.

## Manual adversarial checklist

- Open `/glitgcore gui` and traverse Rules, Balancing, and Content & tools. Verify every feature card reports the correct master state, left-click opens its editor, right-click toggles it, back controls preserve context, and destructive removals require confirmation.
- Exercise boolean, enum, integer, decimal, duration, string, and comma-separated chat inputs. Test invalid values and `cancel`; the previous menu must reopen without changing YAML.
- From the item, potion, and enchant screens add/toggle/remove entries and restart. Verify exact custom metadata/PDC/enchantments persist. Test `/banitem` with an ordinary item, a potion, and an enchanted item to confirm it routes to the appropriate editor.
- Create, edit, toggle, remove, and recreate shaped and shapeless recipes. Verify the virtual editor neither consumes nor duplicates inventory items and preloads existing exact ingredients/results safely.
- Save/preview/give/clear the join kit and create/edit/remove unique craft, altar, and ritual definitions. Restart between operations and confirm state is durable.
- Test click, shift-click, drag, double-click, hotbar/offhand swap, hopper/dropper/dispenser, chest minecart, bundle, shulker, crafting/recipe book, anvil, grindstone, smithing, enchanting, brewing, merchant, death/respawn, and item pickup paths.
- Test potion base/custom effects and splash/lingering/tipped forms; books and unusual enchant combinations.
- Race the last unique craft with two real players; restart during a ritual and grace timer.
- Exercise combat logout, commands, WorldGuard safe-region entry, arrows against Naked Protection, outgoing attacks from every protected state, portals/pearls/boats/API teleports.
- Verify exact custom ItemStack names/lore/components/attributes/PDC survive kit, recipe, immortal-item, and GUI workflows.
- Repeat with LifeStealZ, CoreProtect, Grim, PacketEvents/ProtocolLib, and WorldGuard separately and together.
