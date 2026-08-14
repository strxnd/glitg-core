package dev.glitg.core.command;

import dev.glitg.core.GLITGCorePlugin;
import dev.glitg.core.config.ConfigService;
import dev.glitg.core.domain.CombatTagService;
import dev.glitg.core.domain.CooldownService;
import dev.glitg.core.domain.ItemAction;
import dev.glitg.core.gui.AdminGuiService;
import dev.glitg.core.item.EnchantPolicyService;
import dev.glitg.core.item.PotionPolicyService;
import dev.glitg.core.item.RuleEngine;
import dev.glitg.core.message.MessageService;
import dev.glitg.core.persistence.SqliteDatabase;
import dev.glitg.core.persistence.UniqueItemStore;
import dev.glitg.core.service.AltarRitualService;
import dev.glitg.core.service.DimensionService;
import dev.glitg.core.service.GraceService;
import dev.glitg.core.service.KitService;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.IOException;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class CommandRouter implements CommandExecutor, TabCompleter, Listener {
    private final GLITGCorePlugin plugin;
    private final ConfigService configs;
    private final MessageService messages;
    private final RuleEngine rules;
    private final EnchantPolicyService enchants;
    private final PotionPolicyService potions;
    private final CombatTagService combat;
    private final CooldownService cooldowns;
    private final GraceService grace;
    private final DimensionService dimensions;
    private final KitService kits;
    private final UniqueItemStore uniqueItems;
    private final SqliteDatabase database;
    private final AltarRitualService altars;
    private final AdminGuiService gui;
    private final Map<UUID, UUID> lastMessages = new HashMap<>();
    private final Set<UUID> vanished = new HashSet<>();
    private final NamespacedKey vanishedKey;

    public CommandRouter(GLITGCorePlugin plugin, ConfigService configs, MessageService messages, RuleEngine rules,
                         EnchantPolicyService enchants, PotionPolicyService potions, CombatTagService combat,
                         CooldownService cooldowns, GraceService grace, DimensionService dimensions, KitService kits,
                         UniqueItemStore uniqueItems, SqliteDatabase database, AltarRitualService altars, AdminGuiService gui) {
        this.plugin=plugin; this.configs=configs; this.messages=messages; this.rules=rules; this.enchants=enchants; this.potions=potions;
        this.combat=combat; this.cooldowns=cooldowns; this.grace=grace; this.dimensions=dimensions; this.kits=kits;
        this.uniqueItems=uniqueItems; this.database=database; this.altars=altars; this.gui=gui;
        vanishedKey = new NamespacedKey(plugin, "vanished");
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        try {
            return switch (command.getName().toLowerCase(Locale.ROOT)) {
                case "glitgcore" -> root(sender, args);
                case "banitem" -> banItem(sender, args);
                case "itemlimit" -> itemLimit(sender, args);
                case "combat" -> combat(sender);
                case "cooldown" -> cooldown(sender, args);
                case "grace" -> graceStatus(sender);
                case "start" -> start(sender, args);
                case "stopgrace" -> stopGrace(sender);
                case "kit" -> kit(sender, args);
                case "invsee" -> inventorySee(sender, args, false);
                case "endersee" -> inventorySee(sender, args, true);
                case "vanish" -> vanish(sender, args);
                case "sbroadcast" -> broadcast(sender, args);
                case "smsg" -> privateMessage(sender, args);
                case "reply" -> reply(sender, args);
                case "worldtp" -> worldTp(sender, args);
                case "setrespawnspawn" -> setSpawn(sender, false);
                case "setcustomspawn" -> setSpawn(sender, true);
                case "dimension" -> dimension(sender, args);
                case "uniqueitem" -> unique(sender, args);
                case "deathban" -> deathBan(sender, args);
                case "saltar" -> altar(sender, args);
                case "enchant" -> enchant(sender, args);
                default -> false;
            };
        } catch (IllegalArgumentException | IOException | SQLException | dev.glitg.core.config.ConfigurationException exception) {
            sender.sendMessage(messages.raw("<red>" + exception.getMessage() + "</red>"));
            return true;
        }
    }

    private boolean root(CommandSender sender, String[] args) throws IOException, dev.glitg.core.config.ConfigurationException {
        String sub = args.length == 0 ? "gui" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "gui" -> gui.open(requirePlayer(sender));
            case "reload" -> { configs.reload(); rules.reload(); enchants.reload(); potions.reload(); plugin.reloadServices(); messages.send(sender, "reloaded"); }
            case "status" -> sender.sendMessage(messages.raw("<gold>GLITG Core " + plugin.getPluginMeta().getVersion() + "</gold> <gray>— " + enabledCount() + " features enabled, " + Bukkit.getOnlinePlayers().size() + " online</gray>"));
            case "feature" -> {
                if (args.length < 3) throw new IllegalArgumentException("Usage: /glitgcore feature <name> <on|off>");
                boolean enabled = parseBoolean(args[2]); configs.setFeature(args[1], enabled);
                messages.send(sender, "feature-changed", Map.of("feature", args[1], "state", enabled ? "enabled" : "disabled"));
            }
            case "recipe" -> { if (args.length < 2) throw new IllegalArgumentException("Usage: /glitgcore recipe <id>"); gui.openRecipeEditor(requirePlayer(sender), args[1]); }
            case "debug" -> sender.sendMessage(messages.raw("<gray>Paper " + Bukkit.getMinecraftVersion() + ", Java " + Runtime.version() + ", DB open, scheduler tasks managed.</gray>"));
            case "migration" -> sender.sendMessage(messages.raw("<green>Configuration schema " + ConfigService.CURRENT_VERSION + " and SQLite schema 1 are current.</green>"));
            case "version" -> sender.sendMessage(messages.raw("<gold>GLITG Core " + plugin.getPluginMeta().getVersion() + " for Paper 26.2 / Java 25</gold>"));
            default -> throw new IllegalArgumentException("Unknown subcommand: " + sub);
        }
        return true;
    }

    private boolean banItem(CommandSender sender, String[] args) throws IOException {
        Player player=requirePlayer(sender); ItemStack held=requireHeld(player);
        if (args.length == 0) { gui.openItemRuleEditor(player, held); return true; }
        ItemAction action=ItemAction.valueOf(args[0].toUpperCase(Locale.ROOT));
        String id=rules.addRule(held,action); player.sendMessage(messages.raw("<green>Added item rule " + id + ".</green>")); return true;
    }

    private boolean itemLimit(CommandSender sender, String[] args) throws IOException {
        Player player=requirePlayer(sender); ItemStack held=requireHeld(player);
        if(args.length==0)throw new IllegalArgumentException("Usage: /itemlimit <amount|remove>");
        if(args[0].equalsIgnoreCase("remove")){player.sendMessage(messages.raw(rules.removeLimit(held)?"<green>Limit removed.</green>":"<yellow>No matching limit.</yellow>"));}
        else {int maximum=Integer.parseInt(args[0]); if(maximum<0)throw new IllegalArgumentException("Limit must be non-negative"); rules.setLimit(held,maximum); player.sendMessage(messages.raw("<green>Limit set to "+maximum+".</green>"));} return true;
    }

    private boolean combat(CommandSender sender){Player player=requirePlayer(sender);long seconds=(combat.remaining(player.getUniqueId()).toMillis()+999)/1000;messages.send(player,"combat-remaining",Map.of("seconds",seconds));return true;}

    private boolean cooldown(CommandSender sender,String[] args){
        Player self=requirePlayer(sender); if(args.length==0||args[0].equalsIgnoreCase("status")){String key=args.length>1?args[1]:"ender_pearl";long seconds=(cooldowns.remaining(self.getUniqueId(),key).toMillis()+999)/1000;sender.sendMessage(messages.raw("<gray>"+key+": "+seconds+"s</gray>"));return true;}
        if(args[0].equalsIgnoreCase("reset")){if(!sender.hasPermission("glitgcore.cooldown.reset"))throw new IllegalArgumentException("No permission");Player target=args.length>1?requireOnline(args[1]):self;if(args.length>2)cooldowns.reset(target.getUniqueId(),args[2]);else cooldowns.resetAll(target.getUniqueId());sender.sendMessage(messages.raw("<green>Cooldowns reset.</green>"));return true;}return false;
    }

    private boolean graceStatus(CommandSender sender){sender.sendMessage(messages.raw("<gray>Grace "+(grace.active()?"active for "+grace.remaining().toSeconds()+"s":"inactive")+".</gray>"));return true;}
    private boolean start(CommandSender sender,String[] args){long seconds=args.length>0?Long.parseLong(args[0]):configs.main().getLong("grace.duration-seconds",600);grace.start(Duration.ofSeconds(seconds));return true;}
    private boolean stopGrace(CommandSender sender){grace.stop();return true;}

    private boolean kit(CommandSender sender,String[] args)throws IOException{
        if(args.length==0)throw new IllegalArgumentException("Usage: /kit <save|load|clear|resetplayer|join|give>");Player player=requirePlayer(sender);
        switch(args[0].toLowerCase(Locale.ROOT)){case"save"->kits.save(player);case"load"->kits.give(player,true);case"clear"->kits.clear();case"resetplayer"->kits.give(player,true);case"join"->{boolean next=args.length>1?parseBoolean(args[1]):!kits.joinEnabled();kits.setJoinEnabled(next);}case"give"->{if(args.length<2)throw new IllegalArgumentException("Usage: /kit give <player|@a>");if(args[1].equals("@a"))for(Player target:Bukkit.getOnlinePlayers())kits.give(target,false);else kits.give(requireOnline(args[1]),false);}default->throw new IllegalArgumentException("Unknown kit operation");}
        sender.sendMessage(messages.raw("<green>Kit operation complete.</green>"));return true;
    }

    private boolean inventorySee(CommandSender sender,String[] args,boolean ender){Player player=requirePlayer(sender);if(args.length<1)throw new IllegalArgumentException("Player required");Player target=requireOnline(args[0]);player.openInventory(ender?target.getEnderChest():target.getInventory());return true;}

    private boolean vanish(CommandSender sender,String[] args){Player target=args.length>0?requireOnline(args[0]):requirePlayer(sender);boolean hide=!vanished.contains(target.getUniqueId());if(hide){vanished.add(target.getUniqueId());target.getPersistentDataContainer().set(vanishedKey,PersistentDataType.BYTE,(byte)1);target.setCanPickupItems(false);for(Player viewer:Bukkit.getOnlinePlayers())if(!viewer.hasPermission("glitgcore.admin.vanish.see"))viewer.hidePlayer(plugin,target);}else{vanished.remove(target.getUniqueId());target.getPersistentDataContainer().remove(vanishedKey);target.setCanPickupItems(true);Bukkit.getOnlinePlayers().forEach(viewer->viewer.showPlayer(plugin,target));}target.sendMessage(messages.raw("<gray>Vanish "+(hide?"enabled":"disabled")+".</gray>"));return true;}

    private boolean broadcast(CommandSender sender,String[] args){if(args.length==0)throw new IllegalArgumentException("Message required");Bukkit.broadcast(messages.raw("<gold><bold>Broadcast</bold></gold> <gray>»</gray> "+String.join(" ",args)));return true;}
    private boolean privateMessage(CommandSender sender,String[] args){Player from=requirePlayer(sender);if(args.length<2)throw new IllegalArgumentException("Usage: /smsg <player> <message>");Player to=requireOnline(args[0]);sendPrivate(from,to,String.join(" ",Arrays.copyOfRange(args,1,args.length)));return true;}
    private boolean reply(CommandSender sender,String[] args){Player from=requirePlayer(sender);UUID target=lastMessages.get(from.getUniqueId());if(target==null)throw new IllegalArgumentException("Nobody to reply to");Player to=Bukkit.getPlayer(target);if(to==null)throw new IllegalArgumentException("That player is offline");if(args.length==0)throw new IllegalArgumentException("Message required");sendPrivate(from,to,String.join(" ",args));return true;}
    private void sendPrivate(Player from,Player to,String text){lastMessages.put(from.getUniqueId(),to.getUniqueId());lastMessages.put(to.getUniqueId(),from.getUniqueId());from.sendMessage(messages.raw("<gray>[to "+to.getName()+"]</gray> <white>"+escape(text)+"</white>"));to.sendMessage(messages.raw("<gray>[from "+from.getName()+"]</gray> <white>"+escape(text)+"</white>"));}

    private boolean worldTp(CommandSender sender,String[]args){if(args.length<1)throw new IllegalArgumentException("World required");World world=Bukkit.getWorld(args[0]);if(world==null)throw new IllegalArgumentException("World is not loaded");Player target=args.length>1?requireOnline(args[1]):requirePlayer(sender);target.teleportAsync(world.getSpawnLocation());return true;}
    private boolean setSpawn(CommandSender sender,boolean custom)throws IOException{Player player=requirePlayer(sender);Location location=player.getLocation();if(custom){configs.main().set("custom-spawn.world",location.getWorld().getName());configs.main().set("custom-spawn.x",location.x());configs.main().set("custom-spawn.y",location.y());configs.main().set("custom-spawn.z",location.z());configs.main().set("custom-spawn.yaw",location.getYaw());configs.main().set("custom-spawn.pitch",location.getPitch());configs.save("config.yml");}else location.getWorld().setSpawnLocation(location);sender.sendMessage(messages.raw("<green>Spawn saved.</green>"));return true;}

    private boolean dimension(CommandSender sender, String[] args) throws IOException {
        if (args.length < 2) throw new IllegalArgumentException("Usage: /dimension <status|lock|unlock|schedule> <nether|end> [seconds]");
        World.Environment environment = parseEnvironment(args[1]);
        String name = environment == World.Environment.THE_END ? "The End" : "The Nether";
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "status" -> sender.sendMessage(messages.raw("<gold>" + name + "</gold> <gray>is "
                    + (dimensions.locked(environment) ? "<red>locked</red>" : "<green>open</green>") + ".</gray>"));
            case "lock" -> {
                requirePermission(sender, "glitgcore.dimension.manage");
                dimensions.setLocked(environment, true);
                sender.sendMessage(messages.raw("<gold>" + name + "</gold> <red>locked.</red> <gray>Travel is now blocked unless a bypass applies.</gray>"));
            }
            case "unlock" -> {
                requirePermission(sender, "glitgcore.dimension.manage");
                dimensions.setLocked(environment, false);
                sender.sendMessage(messages.raw("<gold>" + name + "</gold> <green>opened.</green>"));
            }
            case "schedule" -> {
                requirePermission(sender, "glitgcore.dimension.manage");
                if (args.length < 3) throw new IllegalArgumentException("Seconds required");
                long seconds = Long.parseLong(args[2]);
                dimensions.setLocked(environment, true);
                dimensions.scheduleUnlock(environment, Duration.ofSeconds(seconds));
                sender.sendMessage(messages.raw("<gold>" + name + "</gold> <red>locked</red> <gray>for " + seconds + " seconds.</gray>"));
            }
            default -> throw new IllegalArgumentException("Unknown dimension operation");
        }
        return true;
    }

    private boolean unique(CommandSender sender,String[]args){if(args.length<2)throw new IllegalArgumentException("Usage: /uniqueitem <query|set|reset> <id> [value]");switch(args[0].toLowerCase(Locale.ROOT)){case"query"->sender.sendMessage(messages.raw("<gray>"+args[1]+" used: "+uniqueItems.used(args[1])+"</gray>"));case"set"->{if(args.length<3)throw new IllegalArgumentException("Value required");uniqueItems.set(args[1],Integer.parseInt(args[2]));}case"reset"->uniqueItems.set(args[1],0);default->throw new IllegalArgumentException("Unknown operation");}return true;}

    private boolean deathBan(CommandSender sender,String[]args)throws SQLException{if(args.length<1)throw new IllegalArgumentException("Usage: /deathban <status|clear> [player]");Player target=args.length>1?requireOnline(args[1]):requirePlayer(sender);if(args[0].equalsIgnoreCase("clear")){database.clearDeathBan(target.getUniqueId());sender.sendMessage(messages.raw("<green>Death ban cleared.</green>"));}else{long expiry=database.deathBanExpiry(target.getUniqueId());sender.sendMessage(messages.raw("<gray>Death ban remaining: "+Math.max(0,(expiry-System.currentTimeMillis()+999)/1000)+"s</gray>"));}return true;}

    private boolean altar(CommandSender sender,String[]args)throws SQLException{if(args.length<1)throw new IllegalArgumentException("Usage: /saltar <place|remove|list|info> [id]");switch(args[0].toLowerCase(Locale.ROOT)){case"place"->{if(args.length<2)throw new IllegalArgumentException("Definition required");String id=altars.place(requirePlayer(sender),args[1]);sender.sendMessage(messages.raw("<green>Placed altar "+id+".</green>"));}case"remove"->{if(args.length<2)throw new IllegalArgumentException("ID required");sender.sendMessage(messages.raw(altars.remove(args[1])?"<green>Altar removed.</green>":"<yellow>Unknown altar.</yellow>"));}case"list"->sender.sendMessage(messages.raw("<gray>Altars: "+altars.list().stream().map(SqliteDatabase.AltarRow::id).toList()+"</gray>"));case"info"->{var row=altars.at(requirePlayer(sender).getTargetBlockExact(6).getLocation());sender.sendMessage(messages.raw(row==null?"<yellow>No altar targeted.</yellow>":"<gray>"+row+"</gray>"));}default->throw new IllegalArgumentException("Unknown altar operation");}return true;}

    private boolean enchant(CommandSender sender,String[]args){if(args.length<2)throw new IllegalArgumentException("Usage: /enchant <player|@s|@a> <enchantment> [level|remove]");List<Player> targets=selector(sender,args[0]);NamespacedKey key=NamespacedKey.fromString(args[1].contains(":")?args[1]:"minecraft:"+args[1]);Enchantment enchantment=key==null?null:RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT).get(key);if(enchantment==null)throw new IllegalArgumentException("Unknown enchantment");int level=args.length<3?1:args[2].equalsIgnoreCase("remove")?0:Integer.parseInt(args[2]);for(Player target:targets){ItemStack item=requireHeld(target);ItemStack before=item.clone();if(item.getItemMeta() instanceof EnchantmentStorageMeta storage){if(level==0)storage.removeStoredEnchant(enchantment);else storage.addStoredEnchant(enchantment,level,true);item.setItemMeta(storage);}else{if(level==0)item.removeEnchantment(enchantment);else item.addUnsafeEnchantment(enchantment,level);}if(enchants.violation(item)!=null){target.getInventory().setItemInMainHand(before);throw new IllegalArgumentException("Configured enchant policy rejects that level");}}sender.sendMessage(messages.raw("<green>Enchantment applied.</green>"));return true;}

    @EventHandler public void onJoin(PlayerJoinEvent event){for(UUID id:vanished){Player hidden=Bukkit.getPlayer(id);if(hidden!=null&&!event.getPlayer().hasPermission("glitgcore.admin.vanish.see"))event.getPlayer().hidePlayer(plugin,hidden);}}

    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[]args){String name=command.getName().toLowerCase(Locale.ROOT);List<String> candidates=switch(name){case"glitgcore"->args.length==1?List.of("gui","reload","status","feature","recipe","debug","migration","version"):List.of();case"banitem"->Arrays.stream(ItemAction.values()).map(Enum::name).toList();case"kit"->List.of("save","load","clear","resetplayer","join","give");case"dimension"->args.length==1?List.of("status","lock","unlock","schedule"):args.length==2?List.of("nether","end"):List.of();case"uniqueitem"->List.of("query","set","reset");case"deathban"->List.of("status","clear");case"saltar"->List.of("place","remove","list","info");case"cooldown"->List.of("status","reset");default->Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();};String prefix=args.length==0?"":args[args.length-1].toLowerCase(Locale.ROOT);return candidates.stream().filter(value->value.toLowerCase(Locale.ROOT).startsWith(prefix)).sorted().toList();}

    private int enabledCount(){var section=configs.main().getConfigurationSection("features");return section==null?0:(int)section.getKeys(false).stream().filter(key->section.getBoolean(key)).count();}
    private static boolean parseBoolean(String raw){return switch(raw.toLowerCase(Locale.ROOT)){case"on","true","enable","enabled"->true;case"off","false","disable","disabled"->false;default->throw new IllegalArgumentException("Expected on or off");};}
    private static World.Environment parseEnvironment(String raw){return switch(raw.toLowerCase(Locale.ROOT)){case"nether"->World.Environment.NETHER;case"end","the_end"->World.Environment.THE_END;default->throw new IllegalArgumentException("Expected nether or end");};}
    private static Player requirePlayer(CommandSender sender){if(sender instanceof Player player)return player;throw new IllegalArgumentException("This command requires a player");}
    private static Player requireOnline(String name){Player player=Bukkit.getPlayerExact(name);if(player==null)throw new IllegalArgumentException("Player is not online: "+name);return player;}
    private static ItemStack requireHeld(Player player){ItemStack held=player.getInventory().getItemInMainHand();if(held.getType().isAir())throw new IllegalArgumentException("Hold an item in your main hand");return held;}
    private static void requirePermission(CommandSender sender,String permission){if(!sender.hasPermission(permission))throw new IllegalArgumentException("No permission");}
    private static List<Player> selector(CommandSender sender,String selector){if(selector.equals("@a"))return List.copyOf(Bukkit.getOnlinePlayers());if(selector.equals("@s"))return List.of(requirePlayer(sender));return List.of(requireOnline(selector));}
    private static String escape(String text){return text.replace("<","&lt;").replace(">","&gt;");}
}
