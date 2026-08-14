# Confirmed removed or superseded behavior

The authoritative public removal entry is [SMP Core updates page 2, Apr 13 2026](https://builtbybit.com/resources/smp-core.76845/updates?page=2); the lifesteal removal is [page 5, Oct 13 2025](https://builtbybit.com/resources/smp-core.76845/updates?page=5); AntiAdminAbuse removal is [page 3, Mar 18 2026](https://builtbybit.com/resources/smp-core.76845/updates?page=3).

| Historical feature | Decision | Reason/current replacement |
|---|---|---|
| Built-in Lifesteal | Not reimplemented | Explicitly removed for plugin conflicts; use LifeStealZ or another specialist plugin |
| `/sban+` | Not reimplemented | Explicitly removed |
| Custom join message | Not reimplemented | Explicitly removed |
| Easy Recipes | Not reimplemented | Explicitly replaced by Custom Crafting |
| Standalone Pearl Ban | Not reimplemented | Explicitly replaced by generic `/banitem` |
| Old Potion Ban | Not reimplemented | Explicitly replaced by Potion Policy and potion-aware item matching |
| Sharpness/Protection-only limiter | Not reimplemented | Explicitly replaced by generic enchant policy |
| Paper dupe fix | Not reimplemented | Explicitly described as fixed upstream since 1.21.1 |
| AntiAdminAbuse webhook logger | Not reimplemented | Explicitly removed for noticeable lag; GLITG Core does not transmit staff activity externally |
| Generic golden-apple cooldown | Superseded | Public Dec 8 update replaced it with enchanted-golden-apple cooldown |

No architecture prevents a future independent extension, but none of these are counted as current parity.
