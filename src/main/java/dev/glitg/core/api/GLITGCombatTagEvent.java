package dev.glitg.core.api;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.time.Duration;

public final class GLITGCombatTagEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player attacker;
    private final Player victim;
    private Duration duration;
    private boolean cancelled;

    public GLITGCombatTagEvent(Player attacker, Player victim, Duration duration) {
        this.attacker=attacker;this.victim=victim;this.duration=duration;
    }
    public Player attacker(){return attacker;} public Player victim(){return victim;}
    public Duration duration(){return duration;}
    public void duration(Duration duration){if(duration.isNegative()||duration.isZero())throw new IllegalArgumentException("duration must be positive");this.duration=duration;}
    @Override public boolean isCancelled(){return cancelled;}
    @Override public void setCancelled(boolean cancelled){this.cancelled=cancelled;}
    @Override public HandlerList getHandlers(){return HANDLERS;}
    public static HandlerList getHandlerList(){return HANDLERS;}
}
