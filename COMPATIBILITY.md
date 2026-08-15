# Compatibility

## LifeStealZ

GLITG Core does not implement lifesteal. It does not blanket-clear drops, clone full inventories, or rewrite all respawns. Exact configured immortal drops remain world entities with invulnerability and unlimited lifetime, so they survive plugin restarts. DeathBan/spectator options are independent and default non-invasive; enable them deliberately when another death plugin is present.

## CoreProtect

No rollback or general logging is duplicated. GLITG Core uses normal Paper placement/break/interact events and persistent altar IDs. CoreProtect may log those world changes normally.

## Grim

No anti-cheat behavior or movement rewriting is implemented. Combat tagging observes uncancelled damage. Safe-region rejection restores the prior move through event cancellation and does not exempt movement checks.

## WorldGuard and other region plugins

WorldGuard is a soft dependency queried reflectively for the public PVP flag. Other plugins can register `dev.glitg.core.api.RegionProvider` through Bukkit's ServicesManager. A provider failure fails open with a clear warning rather than trapping a player.

## PacketEvents / ProtocolLib

Public evidence conflicts about the current packet provider, and no public contract defines the transformations. GLITG Core therefore has no PacketEvents/ProtocolLib dependency and exposes no inert packet-defense switches. It does not claim that a server can make a world seed mathematically unrecoverable after clients receive enough terrain, or that server configuration can enforce arbitrary client minimap behavior.

## Custom items

The matcher considers only configured material, potion key, modern custom-model components, explicitly named PDC string entries, enchant levels, and explicit tags. It never strips metadata, downgrades an arbitrary item in place, or treats every third-party PDC item as an GLITG Core item. Exact ItemStack serialization preserves names, lore, attributes, enchantments, components, model data, and PDC.

## Reloads

`/glitgcore reload` is supported. Server-wide `/reload` remains discouraged by Paper. Running rituals and grace timers are durable; registered recipes and cached policy lists are replaced cleanly by the plugin reload command.
