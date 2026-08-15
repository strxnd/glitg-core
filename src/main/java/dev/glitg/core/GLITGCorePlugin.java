package dev.glitg.core;

import dev.glitg.core.command.CommandRouter;
import dev.glitg.core.config.ConfigService;
import dev.glitg.core.config.ConfigurationException;
import dev.glitg.core.crafting.CraftingService;
import dev.glitg.core.domain.CombatTagService;
import dev.glitg.core.domain.CooldownService;
import dev.glitg.core.gui.AdminGuiService;
import dev.glitg.core.integration.IntegrationManager;
import dev.glitg.core.item.BukkitItemAdapter;
import dev.glitg.core.item.EnchantPolicyService;
import dev.glitg.core.item.PotionPolicyService;
import dev.glitg.core.item.RuleEngine;
import dev.glitg.core.listener.AltarListener;
import dev.glitg.core.listener.CombatProtectionListener;
import dev.glitg.core.listener.CombatRestrictionListener;
import dev.glitg.core.listener.ItemPolicyListener;
import dev.glitg.core.listener.LifecycleGameplayListener;
import dev.glitg.core.listener.MiscGameplayListener;
import dev.glitg.core.listener.ParityGameplayListener;
import dev.glitg.core.listener.UniqueCraftListener;
import dev.glitg.core.message.MessageService;
import dev.glitg.core.persistence.SqliteDatabase;
import dev.glitg.core.persistence.SqliteUniqueItemStore;
import dev.glitg.core.service.AltarRitualService;
import dev.glitg.core.service.DimensionService;
import dev.glitg.core.service.GraceService;
import dev.glitg.core.service.KitService;
import dev.glitg.core.service.PostDeathProtectionService;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.time.Clock;
import java.util.List;

public final class GLITGCorePlugin extends JavaPlugin {
    private ConfigService configs;
    private SqliteDatabase database;
    private RuleEngine rules;
    private EnchantPolicyService enchants;
    private PotionPolicyService potions;
    private CraftingService crafting;
    private CombatProtectionListener combatListener;
    private GraceService grace;
    private AltarRitualService altars;
    private ParityGameplayListener parityGameplay;
    private MiscGameplayListener miscGameplay;

    @Override public void onEnable() {
        try {
            configs = new ConfigService(this);
            configs.load();
            database = new SqliteDatabase(getDataFolder().toPath().resolve("state.db"));
            Clock clock = Clock.systemUTC();
            MessageService messages = new MessageService(configs);
            BukkitItemAdapter adapter = new BukkitItemAdapter();
            rules = new RuleEngine(configs, adapter);
            enchants = new EnchantPolicyService(configs, adapter);
            potions = new PotionPolicyService(configs);
            crafting = new CraftingService(this, configs);
            crafting.reload();
            var uniqueItems = new SqliteUniqueItemStore(database);
            var combat = new CombatTagService(clock);
            var cooldowns = new CooldownService(clock);
            var integrations = new IntegrationManager(this);
            grace = new GraceService(this, configs, messages, database, clock);
            var dimensions = new DimensionService(this, configs, database, clock);
            var postDeath = new PostDeathProtectionService(this, database, clock);
            var kits = new KitService(configs, database, clock);
            altars = new AltarRitualService(this, configs, messages, database, clock);
            parityGameplay = new ParityGameplayListener(this, configs, messages);
            miscGameplay = new MiscGameplayListener(this, configs, messages);
            var gui = new AdminGuiService(this, configs, messages, crafting, rules, enchants, potions, kits);
            getLogger().info("Validated " + gui.validate() + " in-game configuration controls.");
            combatListener = new CombatProtectionListener(configs, messages, combat, cooldowns, grace, integrations, postDeath, clock);
            var router = new CommandRouter(this, configs, messages, rules, enchants, potions, combat, cooldowns,
                    grace, dimensions, kits, uniqueItems, database, altars, gui, postDeath);

            registerListeners(gui, router,
                    new ItemPolicyListener(this, configs, messages, rules, enchants, potions, adapter, combat, postDeath, database, clock),
                    combatListener,
                    new CombatRestrictionListener(configs, messages, combat),
                    new LifecycleGameplayListener(this, configs, messages, rules, dimensions, kits, database, postDeath, clock),
                    new UniqueCraftListener(configs, messages, uniqueItems),
                    new AltarListener(altars), altars, miscGameplay, parityGameplay);
            registerCommands(router);

            getLogger().info("GLITG Core " + getPluginMeta().getVersion() + " enabled for Paper " + getServer().getMinecraftVersion());
        } catch (ConfigurationException exception) {
            getLogger().severe("GLITG Core configuration is invalid: " + exception.getMessage());
            getServer().getPluginManager().disablePlugin(this);
        } catch (SQLException | RuntimeException exception) {
            getLogger().severe("GLITG Core could not start safely: " + exception.getMessage());
            exception.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    private void registerListeners(org.bukkit.event.Listener... listeners) {
        for (var listener : listeners) getServer().getPluginManager().registerEvents(listener, this);
    }

    private void registerCommands(CommandRouter router) {
        List<String> names = List.of("glitgcore","banitem","itemlimit","combat","protection","cooldown","grace","start","stopgrace",
                "kit","invsee","endersee","vanish","sbroadcast","smsg","reply","worldtp","setrespawnspawn","setcustomspawn",
                "dimension","anonymousdeaths","uniqueitem","deathban","saltar","enchant");
        for (String name : names) {
            PluginCommand command = getCommand(name);
            if (command == null) throw new IllegalStateException("command missing from plugin.yml: " + name);
            command.setExecutor(router); command.setTabCompleter(router);
        }
    }

    public void reloadServices() {
        rules.reload(); enchants.reload(); potions.reload(); crafting.reload(); combatListener.reload();
        parityGameplay.reload(); miscGameplay.reload(); altars.reload(); grace.reload();
    }

    public void reloadConfiguration() throws ConfigurationException {
        configs.reload();
        try {
            reloadServices();
            configs.commitReload();
        } catch (RuntimeException exception) {
            configs.rollbackReload();
            reloadServices();
            throw new IllegalArgumentException("Reload failed and the prior configuration was restored: " + exception.getMessage(), exception);
        }
    }

    @Override public void onDisable() {
        if (grace != null) grace.close();
        if (altars != null) altars.close();
        if (parityGameplay != null) parityGameplay.close();
        if (database != null) try { database.close(); } catch (SQLException exception) { getLogger().warning("Database close failed: " + exception.getMessage()); }
        HandlerList.unregisterAll(this);
    }
}
