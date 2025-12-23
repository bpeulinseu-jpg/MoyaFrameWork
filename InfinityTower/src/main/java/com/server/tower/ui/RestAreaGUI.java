package com.server.tower.ui;

import com.server.core.api.CoreProvider;
import com.server.core.api.builder.ItemBuilder;
import com.server.tower.TowerPlugin;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

public class RestAreaGUI {

    private final TowerPlugin plugin;

    public RestAreaGUI(TowerPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        // 배경: 어두운 느낌의 메뉴
        Inventory gui = CoreProvider.openGui(player, "infinity_tower:menu_bg", "휴식: 선택의 시간", -16, 3, true);

        // 1. [도전] 다음 층으로 이동
        ItemBuilder nextBtn = new ItemBuilder(Material.IRON_SWORD)
                .name("§a§l[ 다음 층으로 도전 ]")
                .lore("§7휴식을 마치고 더 깊은 곳으로 나아갑니다.", "", "§c⚠ 주의: 난이도가 상승합니다.", "§e보상 기대값: ▲▲▲")
                .glow();

        CoreProvider.setGuiButton(gui, 11, nextBtn.build(), e -> {
            player.closeInventory();
            player.playSound(player.getLocation(), Sound.BLOCK_IRON_DOOR_OPEN, 1f, 1f);
            player.sendMessage("§a굳은 결의를 다지고 다음 층으로 향합니다...");

            // GameManager에게 다음 층 진행 요청
            plugin.getGameManager().forceNextFloor(player);
        });

        // 2. [귀환] 로비로 복귀
        ItemBuilder leaveBtn = new ItemBuilder(Material.RED_BED)
                .name("§e§l[ 로비로 귀환 ]")
                .lore("§7지금까지 얻은 전리품을 가지고 돌아갑니다.", "", "§a안전하게 보상을 챙길 수 있습니다.")
                .glow();

        CoreProvider.setGuiButton(gui, 15, leaveBtn.build(), e -> {
            player.closeInventory();
            // GameManager에게 게임 종료 요청
            plugin.getGameManager().endGame(player);
        });
    }
}