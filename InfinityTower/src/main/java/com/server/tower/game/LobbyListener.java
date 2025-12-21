package com.server.tower.game;

import com.server.tower.TowerPlugin;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;



public class LobbyListener implements Listener {

    private final TowerPlugin plugin;

    public LobbyListener(TowerPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null) return;

        Player player = event.getPlayer();

        // 게임 중이면 무시
        if (plugin.getGameManager().isIngame(player)) return;

        // 1. 다이아몬드 블록 -> 던전 입장
        if (block.getType() == Material.DIAMOND_BLOCK) {
            plugin.getGameManager().startGame(player);
            event.setCancelled(true); // 설치/상호작용 방지
        }

        // 2. 에메랄드 블록 -> 상점 열기
        else if (block.getType() == Material.EMERALD_BLOCK) {
            plugin.getShopManager().openStatShop(player);
            event.setCancelled(true);
        }

        // 3. 모루 -> 강화소
        else if (block.getType() == Material.ANVIL || block.getType() == Material.CHIPPED_ANVIL) {
            plugin.getEnhanceUI().open(player); // TowerPlugin에 Getter 추가 필요
            event.setCancelled(true);
        }
        // 인챈트 테이블 -> 보석 장착소
        else if (block.getType() == Material.ENCHANTING_TABLE) {
            plugin.getSocketingUI().open(player); // TowerPlugin에 Getter 추가 필요
            event.setCancelled(true);
        }

        // 숫돌 -> 수리소
        else if (block.getType() == Material.GRINDSTONE) {
            plugin.getRepairUI().open(player);
            event.setCancelled(true);
        }
    }
}