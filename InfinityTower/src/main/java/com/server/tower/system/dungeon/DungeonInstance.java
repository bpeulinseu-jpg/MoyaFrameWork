package com.server.tower.system.dungeon;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.UUID;

public class DungeonInstance {
    private final int slotId;
    private final Location centerLocation;
    private final UUID ownerId;

    public DungeonInstance(int slotId, Location centerLocation, Player owner) {
        this.slotId = slotId;
        this.centerLocation = centerLocation;
        this.ownerId = owner.getUniqueId();
    }

    public int getSlotId() { return slotId; }
    public Location getCenter() { return centerLocation; }
    public UUID getOwnerId() { return ownerId; }
}