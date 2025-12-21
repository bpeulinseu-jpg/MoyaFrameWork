package com.server.tower.game;

import com.server.core.api.CoreProvider;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class RegenTask extends BukkitRunnable {

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.isDead()) continue;

            double regen = CoreProvider.getStat(player, "hp_regen");
            if (regen > 0) {
                double maxHp = player.getAttribute(Attribute.MAX_HEALTH).getValue();
                double currentHp = player.getHealth();

                if (currentHp < maxHp) {
                    player.setHealth(Math.min(maxHp, currentHp + regen));
                }
            }
        }
    }
}