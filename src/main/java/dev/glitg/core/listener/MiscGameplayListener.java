package dev.glitg.core.listener;

import dev.glitg.core.config.ConfigService;
import dev.glitg.core.message.MessageService;
import dev.glitg.core.permission.BypassPolicy;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.GameRule;
import org.bukkit.GameRules;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.entity.VillagerReplenishTradeEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class MiscGameplayListener implements Listener {
    private final JavaPlugin plugin;
    private final ConfigService configs;
    private final MessageService messages;
    private final NamespacedKey speedAdjusted;

    public MiscGameplayListener(JavaPlugin plugin, ConfigService configs, MessageService messages) {
        this.plugin=plugin;this.configs=configs;this.messages=messages;speedAdjusted=new NamespacedKey(plugin,"happy_ghast_speed_adjusted");
        applyLocatorRule();
        if (configs.enabled("golden-heads") && configs.file("items.yml").getBoolean("golden-head.enabled", false)) {
            NamespacedKey key = new NamespacedKey(plugin, "golden_head");
            ShapedRecipe recipe = new ShapedRecipe(key, new ItemStack(Material.PLAYER_HEAD));
            recipe.shape("GGG", "GHG", "GGG"); recipe.setIngredient('G', Material.GOLD_INGOT); recipe.setIngredient('H', Material.PLAYER_HEAD);
            plugin.getServer().addRecipe(recipe);
        }
    }

    @EventHandler public void onJoin(PlayerJoinEvent event){applyLocatorRule();}

    private void applyLocatorRule(){if(!configs.enabled("miscellaneous"))return;boolean value=configs.main().getBoolean("misc.locator-bar",true);plugin.getServer().getWorlds().forEach(world->world.setGameRule(GameRules.LOCATOR_BAR,value));}

    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
    public void onVillagerInteract(PlayerInteractEntityEvent event){if(!(event.getRightClicked() instanceof Villager villager)||!configs.enabled("villagers"))return;if(configs.main().getBoolean("villagers.infinite-restock",false))villager.getRecipes().forEach(recipe->recipe.setUses(0));if(configs.main().getBoolean("villagers.anchor-on-click",false)){villager.setAI(false);playerNote(event.getPlayer(),"Villager anchored.");}}

    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
    public void onRestock(VillagerReplenishTradeEvent event){if(configs.enabled("villagers")&&configs.main().getBoolean("villagers.infinite-restock",false)){var recipe=event.getRecipe();recipe.setUses(0);recipe.setMaxUses(Integer.MAX_VALUE);event.setRecipe(recipe);}}

    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
    public void onVillagerDamage(EntityDamageByEntityEvent event){if(event.getEntity() instanceof Villager&&event.getDamager() instanceof Player&&configs.enabled("villagers")&&configs.main().getBoolean("villagers.prevent-killing",false))event.setCancelled(true);}

    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
    public void onTippedArrow(ProjectileLaunchEvent event){if(!(event.getEntity() instanceof Arrow arrow)||!configs.enabled("miscellaneous")||!configs.main().getBoolean("misc.ban-tipped-arrows",false))return;if(arrow.getBasePotionType()!=null||arrow.hasCustomEffects()){event.setCancelled(true);if(arrow.getShooter() instanceof Player player)playerNote(player,"Tipped arrows are disabled.");}}

    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
    public void onSwap(PlayerSwapHandItemsEvent event){if(!configs.enabled("miscellaneous"))return;if(configs.main().getBoolean("misc.ban-breach-swapping",false)&&(hasEnchant(event.getMainHandItem(),"breach")||hasEnchant(event.getOffHandItem(),"breach")))event.setCancelled(true);else if(configs.main().getBoolean("misc.attribute-swapping",false)&&(hasAttributes(event.getMainHandItem())||hasAttributes(event.getOffHandItem())))event.setCancelled(true);}

    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
    public void onPiston(BlockPistonExtendEvent event){if(configs.enabled("miscellaneous")&&configs.main().getBoolean("misc.prevent-string-duper",false)&&event.getBlocks().stream().anyMatch(block->block.getType()==Material.TRIPWIRE||block.getType()==Material.TRIPWIRE_HOOK))event.setCancelled(true);}

    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
    public void onDrain(PlayerBucketFillEvent event){if(configs.enabled("miscellaneous")&&configs.main().getBoolean("misc.anti-draining",false)&&!BypassPolicy.bypasses(configs,event.getPlayer(),"glitgcore.bypass.misc"))event.setCancelled(true);}

    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
    public void onDurability(PlayerItemDamageEvent event){if(configs.enabled("miscellaneous")&&configs.main().getBoolean("misc.anti-dura",false))event.setCancelled(true);}

    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void onGhast(EntitySpawnEvent event){if(!event.getEntityType().name().equals("HAPPY_GHAST")||!configs.enabled("miscellaneous"))return;Entity entity=event.getEntity();if(entity.getPersistentDataContainer().has(speedAdjusted))return;var attribute=entity instanceof org.bukkit.entity.LivingEntity living?living.getAttribute(Attribute.FLYING_SPEED):null;if(attribute!=null){double multiplier=configs.main().getDouble("misc.happy-ghast-speed-multiplier",1.0);attribute.setBaseValue(attribute.getBaseValue()*multiplier);entity.getPersistentDataContainer().set(speedAdjusted,PersistentDataType.BYTE,(byte)1);}}

    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
    public void onPearlHit(ProjectileHitEvent event){if(!configs.enabled("miscellaneous")||!configs.main().getBoolean("misc.better-pearl-catching",false)||!event.getEntityType().name().equals("ENDER_PEARL")||!(event.getHitEntity() instanceof Player catcher))return;event.getEntity().remove();catcher.getInventory().addItem(new ItemStack(Material.ENDER_PEARL)).values().forEach(item->catcher.getWorld().dropItemNaturally(catcher.getLocation(),item));}

    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
    public void onGoldenHead(PlayerInteractEvent event){if(!configs.enabled("golden-heads")||!configs.file("items.yml").getBoolean("golden-head.enabled",false))return;ItemStack item=event.getItem();if(item==null||item.getType()!=Material.PLAYER_HEAD||!item.getItemMeta().getPersistentDataContainer().has(new NamespacedKey(plugin,"golden_head")))return;event.setCancelled(true);Player player=event.getPlayer();player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION,configs.file("items.yml").getInt("golden-head.regeneration-seconds",10)*20,1));player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION,120*20,Math.max(0,configs.file("items.yml").getInt("golden-head.absorption-hearts",4)/2-1)));item.setAmount(item.getAmount()-1);}

    @EventHandler(priority=EventPriority.HIGH)
    public void onGoldenHeadCraft(PrepareItemCraftEvent event){if(!configs.enabled("golden-heads")||!configs.file("items.yml").getBoolean("golden-head.enabled",false))return;int heads=0,gold=0;for(ItemStack item:event.getInventory().getMatrix()){if(item==null)continue;if(item.getType()==Material.PLAYER_HEAD)heads++;else if(item.getType()==Material.GOLD_INGOT)gold++;else return;}if(heads==1&&gold==8){ItemStack result=new ItemStack(Material.PLAYER_HEAD);result.editMeta(meta->{meta.itemName(messages.raw("<gold>Golden Head</gold>").decoration(TextDecoration.ITALIC, false));meta.getPersistentDataContainer().set(new NamespacedKey(plugin,"golden_head"),PersistentDataType.BYTE,(byte)1);meta.setEnchantmentGlintOverride(true);});event.getInventory().setResult(result);}}

    private static boolean hasEnchant(ItemStack item,String key){return item!=null&&item.getEnchantments().keySet().stream().anyMatch(enchantment->enchantment.getKey().getKey().equals(key));}
    private static boolean hasAttributes(ItemStack item){return item!=null&&item.hasItemMeta()&&item.getItemMeta().hasAttributeModifiers();}
    private void playerNote(Player player,String text){player.sendMessage(messages.raw("<gray>"+text+"</gray>"));}
}
