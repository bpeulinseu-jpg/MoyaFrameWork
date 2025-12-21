package com.server.tower.user;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;

import com.server.core.api.CoreProvider;

public class UserListener implements Listener {

    private final UserManager userManager;

    public UserListener(UserManager userManager) {
        this.userManager = userManager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        userManager.loadUser(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        userManager.saveUser(event.getPlayer());
    }

    // 스탯이 부여 된 아이템 들었을 때 스코어보드 갱신
    @EventHandler
    public void onItemChange(PlayerItemHeldEvent event) {
        // Core가 스탯 재계산을 비동기/다음 틱에 할 수 있으므로 1틱 뒤에 갱신
        event.getPlayer().getServer().getScheduler().runTask(com.server.tower.TowerPlugin.getInstance(), () -> {
            // 1. 스탯 재계산 (Core)
            CoreProvider.recalculateStats(event.getPlayer());
            // 2. 스코어보드 갱신 (Tower)
            userManager.updateSidebar(event.getPlayer());
        });
    }
}