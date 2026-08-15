package dev.glitg.core.gui;

import org.bukkit.Material;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class GuiCatalog {
    private GuiCatalog() {}

    enum Category {
        RULES("Gameplay", Material.DIAMOND_SWORD),
        BALANCING("Balance", Material.REPEATER),
        OTHER("Content", Material.CRAFTING_TABLE);

        final String label;
        final Material icon;
        Category(String label, Material icon) { this.label = label; this.icon = icon; }
    }

    enum Special {
        NONE, ITEM_RULES, ITEM_LIMITS, POTIONS, ENCHANTMENTS, UNIQUE_ITEMS,
        PROTECTED_ITEMS, RECIPES, KITS, ALTARS, RITUALS, ADMIN_UTILITIES
    }

    enum ValueType { BOOLEAN, INTEGER, DOUBLE, DURATION, ENUM, STRING, STRING_LIST }

    record Setting(String file, String path, String label, Material icon, ValueType type,
                   List<String> options, List<String> description) {
        Setting(String file, String path, String label, Material icon, ValueType type, String... description) {
            this(file, path, label, icon, type, List.of(), List.of(description));
        }

        Setting(String file, String path, String label, Material icon, List<String> options, String... description) {
            this(file, path, label, icon, ValueType.ENUM, List.copyOf(options), List.of(description));
        }

        Setting under(String prefix) {
            return new Setting(file, prefix + path, label, icon, type, options, description);
        }
    }

    record Feature(String key, String label, Material icon, Category category, Special special,
                   List<String> description, List<Setting> settings) {}

    static final Map<String, Feature> FEATURES = buildFeatures();

    static List<Feature> features(Category category) {
        return FEATURES.values().stream().filter(feature -> feature.category == category).toList();
    }

    private static Map<String, Feature> buildFeatures() {
        Map<String, Feature> features = new LinkedHashMap<>();

        add(features, "operator-bypass", "Operator bypass", Material.GOLDEN_HELMET, Category.RULES, Special.NONE,
                List.of("Let operators ignore gameplay restrictions."), List.of());
        add(features, "item-rules", "Item restrictions", Material.BARRIER, Category.RULES, Special.ITEM_RULES,
                List.of("Control exactly how selected items may be used."), List.of(
                        bool("config.yml", "items.traverse-shulkers", "Inspect shulker boxes", Material.SHULKER_BOX, "Prevent nested-storage bypasses."),
                        bool("config.yml", "items.traverse-bundles", "Inspect bundles", Material.BUNDLE, "Prevent bundle bypasses."),
                        bool("config.yml", "items.grandfather-existing-mythical", "Grandfather existing items", Material.NAME_TAG, "Mark pre-enforcement mythical items once per player.")));
        add(features, "item-limits", "Item limits", Material.HOPPER, Category.RULES, Special.ITEM_LIMITS,
                List.of("Set how many of an item each player may carry."), List.of(
                        bool("config.yml", "items.include-ender-chest", "Count ender chest", Material.ENDER_CHEST, "Include ender-chest contents in limits."),
                        bool("config.yml", "items.audit-insertions", "Audit inserted items", Material.ENDER_EYE, "Periodically drop restricted or excess externally inserted items."),
                        integer("config.yml", "items.audit-interval-ticks", "Audit interval", Material.CLOCK, "Ticks between online inventory audits."),
                        enumeration("config.yml", "items.limit-scope", "New-limit scope", Material.COMPASS, List.of("CARRIED", "STORED", "COMBAT_LOADOUT"), "Scope assigned to newly created quantity limits."),
                        enumeration("config.yml", "items.overflow", "Overflow action", Material.DROPPER, List.of("BLOCK"), "How excess items are handled.")));
        add(features, "potion-policy", "Potion policy", Material.POTION, Category.RULES, Special.POTIONS,
                List.of("Ban individual potion types or complete tiers."), List.of(
                        bool("items.yml", "potion-policy.ban-tier-1", "Ban all Tier I", Material.POTION, "Blocks every Tier I potion."),
                        bool("items.yml", "potion-policy.ban-tier-2", "Ban all Tier II", Material.SPLASH_POTION, "Blocks every Tier II potion."),
                        list("items.yml", "potion-policy.banned-effects", "Banned effects", Material.FERMENTED_SPIDER_EYE, "Comma-separated effect keys banned in base and custom potions.")));
        add(features, "enchant-policy", "Enchant policy", Material.ENCHANTED_BOOK, Category.RULES, Special.ENCHANTMENTS,
                List.of("Ban enchantments and set maximum accepted levels."), List.of());
        add(features, "unique-items", "Unique crafts", Material.MACE, Category.RULES, Special.UNIQUE_ITEMS,
                List.of("Cap successful crafts globally with durable atomic counters."), List.of());
        add(features, "protected-items", "Special items", Material.TOTEM_OF_UNDYING, Category.RULES, Special.PROTECTED_ITEMS,
                List.of("Configure immortal, glowing, and stop-storage items."), List.of());
        add(features, "combat-tag", "Combat tagging", Material.DIAMOND_SWORD, Category.RULES, Special.NONE,
                List.of("Manage PvP tags, commands, safe zones, and logout penalties."), List.of(
                        integer("config.yml", "combat.duration-seconds", "Tag duration", Material.CLOCK, "Seconds players remain tagged."),
                        bool("config.yml", "combat.block-commands", "Block commands", Material.COMMAND_BLOCK, "Block non-whitelisted commands during combat."),
                        list("config.yml", "combat.whitelisted-commands", "Allowed commands", Material.PAPER, "Comma-separated commands allowed during combat."),
                        bool("config.yml", "combat.block-safe-regions", "Block safe-region entry", Material.SHIELD, "Prevent tagged players entering safe regions."),
                        enumeration("config.yml", "combat.disconnect-action", "Logout action", Material.SKELETON_SKULL, List.of("KILL", "NONE"), "Penalty for disconnecting while tagged."),
                        bool("config.yml", "combat.danger-logging.enabled", "Danger logging", Material.FLINT_AND_STEEL, "Penalize logout shortly after environmental damage."),
                        integer("config.yml", "combat.danger-logging.duration-seconds", "Danger duration", Material.CLOCK, "Seconds environmental danger remains active."),
                        enumeration("config.yml", "combat.danger-logging.disconnect-action", "Danger logout action", Material.SKELETON_SKULL, List.of("KILL", "NONE"), "Penalty for disconnecting in danger."),
                        bool("config.yml", "combat.restrictions.elytra", "Block Elytra", Material.ELYTRA, "Block gliding and armour changes during combat."),
                        bool("config.yml", "combat.restrictions.lava", "Block lava", Material.LAVA_BUCKET, "Block emptying lava buckets during combat."),
                        bool("config.yml", "combat.restrictions.ice", "Block ice", Material.PACKED_ICE, "Block placing ice during combat."),
                        bool("config.yml", "combat.restrictions.draining", "Block draining", Material.SPONGE, "Block bucket filling and sponge placement during combat."),
                        bool("config.yml", "combat.restrictions.armour-switching", "Block armour switching", Material.DIAMOND_CHESTPLATE, "Block armour-slot changes during combat."),
                        bool("config.yml", "combat.restrictions.armour-restocking", "Block armour pickup", Material.ARMOR_STAND, "Block acquiring backup armour during combat."),
                        bool("config.yml", "combat.restrictions.container-restocking", "Block containers", Material.CHEST, "Block opening restock containers during combat.")));
        add(features, "protections", "PvP protections", Material.SHIELD, Category.RULES, Special.NONE,
                List.of("AFK, naked, and new-player protection with anti-abuse rules."), List.of(
                        bool("config.yml", "protections.afk.enabled", "AFK protection", Material.CLOCK, "Protect genuinely inactive players."),
                        integer("config.yml", "protections.afk.activation-seconds", "AFK activation", Material.REPEATER, "Seconds before AFK protection activates."),
                        bool("config.yml", "protections.naked.enabled", "Naked protection", Material.LEATHER_CHESTPLATE, "Protect players without armor."),
                        bool("config.yml", "protections.naked.require-empty-armor", "Require empty armor", Material.ARMOR_STAND, "Require all armor slots to be empty."),
                        bool("config.yml", "protections.new-player.enabled", "New-player protection", Material.PLAYER_HEAD, "Protect players during their first play period."),
                        integer("config.yml", "protections.new-player.duration-seconds", "New-player duration", Material.CLOCK, "Protection duration in seconds."),
                        bool("config.yml", "protections.post-death.enabled", "Post-death protection", Material.TOTEM_OF_UNDYING, "Protect players after every death."),
                        integer("config.yml", "protections.post-death.duration-seconds", "Post-death duration", Material.CLOCK, "Protection duration in seconds."),
                        bool("config.yml", "protections.post-death.block-item-pickup", "Block protected pickup", Material.HOPPER, "Prevent protected players interfering with ground loot."),
                        bool("config.yml", "protections.post-death.block-container-access", "Block protected containers", Material.CHEST, "Prevent protected players restocking or moving stored loot.")));
        add(features, "dimensions", "Dimension locks", Material.END_PORTAL_FRAME, Category.RULES, Special.NONE,
                List.of("Open or close the Nether and End."), List.of(
                        bool("config.yml", "dimensions.nether-locked", "Nether locked", Material.NETHERRACK, "Block travel into the Nether."),
                        bool("config.yml", "dimensions.end-locked", "End locked", Material.END_STONE, "Block travel into the End.")));
        add(features, "locator-bar", "Locator Bar", Material.COMPASS, Category.RULES, Special.NONE,
                List.of("Apply the vanilla Locator Bar game rule to every loaded world."), List.of(
                        bool("config.yml", "locator-bar.enabled", "Locator Bar enabled", Material.COMPASS, "Show or hide the player locator overlay.")));

        add(features, "cooldowns", "Cooldowns", Material.CLOCK, Category.BALANCING, Special.NONE,
                List.of("Tune individual combat-item cooldowns."), List.of(
                        duration("config.yml", "cooldowns.shield", "Shield", Material.SHIELD),
                        duration("config.yml", "cooldowns.ender_pearl", "Ender pearl", Material.ENDER_PEARL),
                        duration("config.yml", "cooldowns.wind_charge", "Wind charge", Material.WIND_CHARGE),
                        duration("config.yml", "cooldowns.trident", "Trident", Material.TRIDENT),
                        duration("config.yml", "cooldowns.enchanted_golden_apple", "Enchanted apple", Material.ENCHANTED_GOLDEN_APPLE),
                        duration("config.yml", "cooldowns.mace", "Mace", Material.MACE),
                        duration("config.yml", "cooldowns.spear", "Spear", Material.DIAMOND_SPEAR),
                        duration("config.yml", "cooldowns.lunge", "Lunge", Material.FEATHER)));
        add(features, "damage-caps", "Damage caps", Material.IRON_SWORD, Category.BALANCING, Special.NONE,
                List.of("Set maximum final damage in health points; two points equal one heart."), List.of(
                        decimal("config.yml", "damage-caps.mace", "Mace", Material.MACE),
                        decimal("config.yml", "damage-caps.spear", "Spear", Material.DIAMOND_SPEAR),
                        decimal("config.yml", "damage-caps.end_crystal", "End crystal", Material.END_CRYSTAL),
                        decimal("config.yml", "damage-caps.respawn_anchor", "Respawn anchor", Material.RESPAWN_ANCHOR),
                        decimal("config.yml", "damage-caps.bed_explosion", "Bed explosion", Material.RED_BED),
                        decimal("config.yml", "damage-caps.tnt_minecart", "TNT minecart", Material.TNT_MINECART),
                        decimal("config.yml", "damage-caps.tnt", "TNT", Material.TNT),
                        decimal("config.yml", "damage-caps.projectile", "Projectile", Material.ARROW),
                        decimal("config.yml", "damage-caps.fall", "Fall", Material.FEATHER)));
        add(features, "global-pvp", "Global PvP", Material.IRON_SWORD, Category.BALANCING, Special.NONE,
                List.of("Apply one PvP state to every loaded world."), List.of(
                        bool("config.yml", "pvp.enabled", "PvP enabled", Material.IRON_SWORD, "Allow player-versus-player damage.")));
        add(features, "pvp-damage", "PvP damage", Material.DIAMOND_SWORD, Category.BALANCING, Special.NONE,
                List.of("Scale general player-versus-player damage."), List.of(
                        decimal("config.yml", "pvp.damage-multiplier", "Damage multiplier", Material.DIAMOND_SWORD, "Multiply uncancelled PvP damage.")));
        add(features, "shield-tweaks", "Shield fixes", Material.SHIELD, Category.BALANCING, Special.NONE,
                List.of("Correct shield sound, disable delay, and damage-tick behavior."), List.of(
                        bool("config.yml", "shield-tweaks.correct-block-sound", "Correct block sound", Material.NOTE_BLOCK, "Play the shield block sound reliably."),
                        integer("config.yml", "shield-tweaks.disable-cooldown-ticks", "Disable cooldown", Material.CLOCK, "Ticks before a disabled shield may be raised."),
                        bool("config.yml", "shield-tweaks.skip-vanilla-damage-ticks", "Skip damage immunity", Material.REDSTONE, "Clear vanilla damage immunity while blocking.")));
        add(features, "xp-clumps", "XP clumps", Material.EXPERIENCE_BOTTLE, Category.BALANCING, Special.NONE,
                List.of("Merge nearby experience into larger orbs."), List.of(
                        decimal("config.yml", "xp-clumps.radius", "Merge radius", Material.ENDER_EYE, "Distance used to collect nearby XP orbs."),
                        integer("config.yml", "xp-clumps.maximum-experience", "Maximum clump", Material.EXPERIENCE_BOTTLE, "Maximum experience stored in one clump.")));
        add(features, "one-player-sleep", "One-player sleep", Material.RED_BED, Category.BALANCING, Special.NONE,
                List.of("Configure the vanilla sleeping percentage in every world."), List.of(
                        integer("config.yml", "sleep.players-sleeping-percentage", "Sleeping percentage", Material.RED_BED, "Use 1 for practical one-player sleep.")));
        add(features, "health-indicator", "Health indicator", Material.GOLDEN_APPLE, Category.BALANCING, Special.NONE,
                List.of("Show vanilla health below player names."), List.of(
                        string("config.yml", "health-indicator.label", "Indicator label", Material.NAME_TAG, "MiniMessage label shown below names.")));
        add(features, "grace", "Grace period", Material.BELL, Category.BALANCING, Special.NONE,
                List.of("Set the opening grace period."), List.of(
                        bool("config.yml", "grace.active-on-startup", "Start automatically", Material.REDSTONE_TORCH, "Start a fresh grace period when the server boots."),
                        integer("config.yml", "grace.duration-seconds", "Default duration", Material.CLOCK, "Grace duration in seconds."),
                        list("config.yml", "grace.start-actions", "Start actions", Material.COMMAND_BLOCK, "Comma-separated console commands run when grace starts.")));
        add(features, "death-system", "Death system", Material.SKELETON_SKULL, Category.BALANCING, Special.NONE,
                List.of("Configure spectator mode, death bans, messages, and sounds."), List.of(
                        bool("config.yml", "death.spectator-on-death", "Spectator on death", Material.ENDER_EYE, "Move players to spectator after respawn."),
                        integer("config.yml", "death.death-ban-seconds", "Death-ban duration", Material.IRON_DOOR, "Zero disables death bans."),
                        string("config.yml", "death.custom-message", "Death message", Material.OAK_SIGN, "MiniMessage text; use clear to remove."),
                        string("config.yml", "death.sound", "Death sound", Material.NOTE_BLOCK, "Namespaced sound key; use clear to remove.")));
        add(features, "villagers", "Villager controls", Material.EMERALD, Category.BALANCING, Special.NONE,
                List.of("Restocking, anchoring, and direct-kill protection."), List.of(
                        bool("config.yml", "villagers.infinite-restock", "Infinite restock", Material.EMERALD, "Reset trades through events rather than tick scans."),
                        bool("config.yml", "villagers.anchor-on-click", "Anchor on click", Material.LEAD, "Right-click disables villager AI."),
                        bool("config.yml", "villagers.prevent-killing", "Prevent killing", Material.SHIELD, "Cancel direct player damage to villagers.")));
        add(features, "golden-heads", "Golden heads", Material.PLAYER_HEAD, Category.BALANCING, Special.NONE,
                List.of("Configure the tagged golden-head recipe and effects."), List.of(
                        bool("items.yml", "golden-head.enabled", "Recipe enabled", Material.GOLDEN_APPLE, "Allow crafting golden heads."),
                        integer("items.yml", "golden-head.absorption-hearts", "Absorption hearts", Material.GOLDEN_APPLE, "Temporary absorption granted."),
                        integer("items.yml", "golden-head.regeneration-seconds", "Regeneration duration", Material.POTION, "Regeneration duration in seconds.")));
        add(features, "warden-heart", "Warden heart", Material.ECHO_SHARD, Category.BALANCING, Special.NONE,
                List.of("Configure the tagged Warden drop."), List.of(
                        bool("items.yml", "warden-heart.enabled", "Drop enabled", Material.ECHO_SHARD, "Allow Wardens to drop the configured heart."),
                        enumeration("items.yml", "warden-heart.acquisition", "Acquisition", Material.ECHO_SHARD, List.of("RIGHT_CLICK", "DROP", "BOTH"), "Choose the public right-click path, death drop, or both."),
                        decimal("items.yml", "warden-heart.drop-chance", "Drop chance", Material.WARDEN_SPAWN_EGG, "Probability from 0.0 to 1.0."),
                        string("items.yml", "warden-heart.material", "Result material", Material.ECHO_SHARD, "Minecraft material name.")));
        add(features, "miscellaneous", "Fight mechanics", Material.REDSTONE, Category.BALANCING, Special.NONE,
                List.of("Configure additional combat rules."), List.of(
                        string("config.yml", "misc.hide-invisible-deaths-until", "Hide invisible deaths until", Material.POTION, "Optional ISO-8601 UTC cutoff timestamp."),
                        bool("config.yml", "misc.hide-invisible-kills", "Hide invisible killers", Material.IRON_SWORD, "Hide the death message when an invisible player kills."),
                        bool("config.yml", "misc.hide-invisible-deaths", "Hide invisible deaths", Material.SKELETON_SKULL, "Hide the death message when the dead player is invisible."),
                        bool("config.yml", "misc.ban-bed-bombing", "Ban bed bombing", Material.RED_BED, "Block explosive bed use outside the Overworld."),
                        bool("config.yml", "misc.ban-respawn-anchor-bombing", "Ban anchor bombing", Material.RESPAWN_ANCHOR, "Block explosive anchor use outside the Nether."),
                        integer("config.yml", "misc.breeze-rod-drop-multiplier", "Breeze Rod multiplier", Material.BREEZE_ROD, "Multiply Breeze Rod drops; one keeps vanilla behavior."),
                        decimal("config.yml", "misc.happy-ghast-speed-multiplier", "Happy Ghast speed", Material.HAPPY_GHAST_SPAWN_EGG, "Base flying-speed multiplier."),
                        bool("config.yml", "misc.ban-tipped-arrows", "Ban tipped arrows", Material.TIPPED_ARROW, "Block potion arrows at launch."),
                        bool("config.yml", "misc.ban-breach-swapping", "Ban Breach swapping", Material.MACE, "Block Breach hand swaps."),
                        bool("config.yml", "misc.prevent-string-duper", "Prevent string duping", Material.TRIPWIRE_HOOK, "Block the tripwire-piston exploit path."),
                        bool("config.yml", "misc.anti-draining", "Prevent draining", Material.WATER_BUCKET, "Block water-source pickup during fights."),
                        bool("config.yml", "misc.anti-dura", "Prevent durability loss", Material.ANVIL, "Cancel item durability damage."),
                        bool("config.yml", "misc.attribute-swapping", "Prevent attribute swapping", Material.NETHERITE_SWORD, "Block swaps involving attribute-bearing items."),
                        bool("config.yml", "misc.better-pearl-catching", "Pearl catching", Material.ENDER_PEARL, "Return pearls caught by another player.")));

        add(features, "custom-crafting", "Custom crafting", Material.CRAFTING_TABLE, Category.OTHER, Special.RECIPES,
                List.of("Create and manage custom recipes."), List.of());
        add(features, "altars", "Altars", Material.LODESTONE, Category.OTHER, Special.ALTARS,
                List.of("Manage persistent ritual locations."), List.of());
        add(features, "rituals", "Rituals", Material.SOUL_LANTERN, Category.OTHER, Special.RITUALS,
                List.of("Manage ritual inputs, timing, effects, and rewards."), List.of());
        add(features, "join-kit", "Join kit", Material.CHEST, Category.OTHER, Special.KITS,
                List.of("Configure the first-join kit."), List.of());
        add(features, "admin-utilities", "Admin utilities", Material.COMMAND_BLOCK, Category.OTHER, Special.ADMIN_UTILITIES,
                List.of("View staff commands and utilities."), List.of());
        return Map.copyOf(features);
    }

    private static void add(Map<String, Feature> map, String key, String label, Material icon, Category category,
                            Special special, List<String> description, List<Setting> settings) {
        map.put(key, new Feature(key, label, icon, category, special, List.copyOf(description), List.copyOf(settings)));
    }

    private static Setting bool(String file, String path, String label, Material icon, String description) {
        return new Setting(file, path, label, icon, ValueType.BOOLEAN, description);
    }

    private static Setting integer(String file, String path, String label, Material icon, String description) {
        return new Setting(file, path, label, icon, ValueType.INTEGER, description);
    }

    private static Setting decimal(String file, String path, String label, Material icon, String description) {
        return new Setting(file, path, label, icon, ValueType.DOUBLE, description);
    }

    private static Setting decimal(String file, String path, String label, Material icon) {
        return decimal(file, path, label, icon, "Maximum final damage in health points.");
    }

    private static Setting duration(String file, String path, String label, Material icon) {
        return new Setting(file, path, label, icon, ValueType.DURATION, "Use ms, s, m, h, or d (for example 15s)." );
    }

    private static Setting string(String file, String path, String label, Material icon, String description) {
        return new Setting(file, path, label, icon, ValueType.STRING, description);
    }

    private static Setting list(String file, String path, String label, Material icon, String description) {
        return new Setting(file, path, label, icon, ValueType.STRING_LIST, description);
    }

    private static Setting enumeration(String file, String path, String label, Material icon, List<String> options, String description) {
        return new Setting(file, path, label, icon, options, description);
    }
}
