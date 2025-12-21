package com.server.tower.game;

import com.server.tower.TowerPlugin;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

public class DungeonListener implements Listener {

    private final TowerPlugin plugin;

    public DungeonListener(TowerPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block block = event.getClickedBlock();
        if (block == null) return;

        Player player = event.getPlayer();

        // [중요] 게임 중인 플레이어만 체크
        if (!plugin.getGameManager().isIngame(player)) return;

        // 레드스톤 블록 -> 나가기
        if (block.getType() == Material.REDSTONE_BLOCK) {
            event.setCancelled(true); // 설치/상호작용 방지

            player.sendMessage("§c[Tower] 던전을 포기하고 나갑니다...");
            plugin.getGameManager().endGame(player);
        }
    }
}