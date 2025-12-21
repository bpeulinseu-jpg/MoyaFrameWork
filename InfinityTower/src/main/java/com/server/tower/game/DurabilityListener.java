package com.server.tower.game;

import com.server.core.api.CoreProvider;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.ItemStack;

public class DurabilityListener implements Listener {

    @EventHandler
    public void onItemDamage(PlayerItemDamageEvent event) {
        ItemStack item = event.getItem();

        // 커스텀 아이템인 경우 내구도 감소 방지
        // (모든 아이템을 막고 싶다면 조건문 제거)
        // 여기서는 "infinity_tower" 네임스페이스를 가진 아이템만 보호
        String customId = CoreProvider.getCustomId(item);
        if (customId != null && customId.startsWith("infinity_tower")) {
            event.setCancelled(true);
        }
    }
}