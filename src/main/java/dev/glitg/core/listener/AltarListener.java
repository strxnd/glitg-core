package dev.glitg.core.listener;

import dev.glitg.core.service.AltarRitualService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;

public final class AltarListener implements Listener {
    private final AltarRitualService service;
    public AltarListener(AltarRitualService service) { this.service = service; }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) return;
        var altar = service.at(event.getClickedBlock().getLocation());
        if (altar != null && service.tryStart(event.getPlayer(), altar)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (service.at(event.getBlock().getLocation()) != null && !event.getPlayer().hasPermission("glitgcore.altar.manage")) event.setCancelled(true);
    }
}
