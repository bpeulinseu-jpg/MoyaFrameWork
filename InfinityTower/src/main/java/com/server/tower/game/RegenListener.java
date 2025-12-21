package com.server.tower.game;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityRegainHealthEvent;

public class RegenListener implements Listener {
    @EventHandler
    public void onRegen(EntityRegainHealthEvent event) {
        // 배고픔(SATIATED)이나 자연 회복(REGEN)으로 인한 회복 차단
        if (event.getRegainReason() == EntityRegainHealthEvent.RegainReason.SATIATED ||
                event.getRegainReason() == EntityRegainHealthEvent.RegainReason.REGEN) {
            event.setCancelled(true);
        }
    }
}