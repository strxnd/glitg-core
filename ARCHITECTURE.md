# Architecture

## Modules

- `domain`: Paper-free immutable descriptors and deterministic item, enchant, limit, cooldown, damage, combat, and protection logic.
- `config` / `message`: strict YAML loading/validation and Adventure rendering.
- `item` / `crafting`: one exact matcher, bounded nested traversal, policy loaders, exact ItemStack codec, recipe registry.
- `persistence`: one WAL SQLite connection, current-schema initialization, transactional unique allocation, durable timers/bans/altars/ritual runs.
- `listener`: thin Paper adapters for item movement, PvP, lifecycle, crafting, altars, and miscellaneous mechanics.
- `service`: grace, durable post-death protection, restart-safe dimension unlocks, kit, and altar/ritual orchestration.
- `integration`: soft dependency discovery and WorldGuard/public provider boundary.
- `gui` / `command`: three-page operator console, schema-backed setting cards, validated chat input, specialized collection editors, virtual recipe editor, confirmations, permissions, completion, and console-safe dispatch.
- `api`: cancellable `GLITGCombatTagEvent` and pluggable `RegionProvider`.

## Event flow

```text
Paper event -> narrow listener -> ItemStack adapter / source classifier
            -> cached policy service -> pure decision
            -> cancel or minimal scoped mutation -> Adventure feedback
```

Listeners use `HIGH`/`HIGHEST` only where enforcement requires a final decision, respect prior cancellation, and never rewrite a whole inventory/death event unless explicitly configured. CoreProtect, Grim, and LifeStealZ retain their own responsibilities.

## Persistence and transactions

SQLite starts in WAL + FULL synchronous mode with a busy timeout and initializes only the current schema. A unique partial index allows one RUNNING ritual per altar. Ritual input is consumed only after the durable run insert; restart reloads unfinished rows and resumes completion. Global craft allocation is a short synchronized transaction, so concurrent craft events cannot exceed the configured limit.

YAML writes happen only through admin actions/reload, not hot event handlers. Item identity and policy configuration are cached in immutable lists and replaced on reload.

## Thread model

Paper/Bukkit entity, inventory, world, and GUI access remains on the server thread. SQLite initialization and short event transactions are serialized; there is no Bukkit access from arbitrary database threads. Grace, protection, dimension, and ritual state use absolute wall-clock deadlines. The anonymous-death cutoff is an independent timestamp rather than a shared season model.

## Ownership boundary

GLITG Core owns deterministic server-side item, potion, combat, protection, damage, dimension, loot, and message rules. A specialist plugin owns the heart economy and final-day heart/death logic. Anti-cheat, claims/rollback, team classification, and subjective moderation remain external. No legendary-item registry or season timeline is present.

No NMS, CraftBukkit version package, or proprietary packet structure is used. Public Paper registries/components are used for 26.2 sounds, enchantments, custom model data, damage types, and game rules.
