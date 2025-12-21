package com.server.core.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class SessionLevelUpEvent extends Event {

    private static final HandlerList handlers = new HandlerList();
    private final Player player;
    private final int newLevel;
    private final int levelsGained;

    public SessionLevelUpEvent(Player player, int newLevel, int levelsGained) {
        this.player = player;
        this.newLevel = newLevel;
        this.levelsGained = levelsGained;
    }

    public Player getPlayer() { return player; }
    public int getNewLevel() { return newLevel; }
    public int getLevelsGained() { return levelsGained; }

    @Override
    public @NotNull HandlerList getHandlers() { return handlers; }
    public static HandlerList getHandlerList() { return handlers; }
}