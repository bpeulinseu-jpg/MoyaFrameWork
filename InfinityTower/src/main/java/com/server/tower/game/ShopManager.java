package com.server.tower.game;

import com.server.core.api.CoreProvider;
import com.server.core.api.builder.ItemBuilder;
import com.server.tower.TowerPlugin;
import com.server.tower.user.TowerUserData;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class ShopManager {

    private final TowerPlugin plugin;

    public ShopManager(TowerPlugin plugin) {
        this.plugin = plugin;
    }

    public void openStatShop(Player player) {
        TowerUserData data = plugin.getUserManager().getUser(player);
        if (data == null) return;

        Inventory gui = CoreProvider.openGui(player, "infinity_tower:menu_bg", "스탯 강화소", -16, 4, true);

        // 1. 힘 강화 (STR)
        setupUpgradeButton(gui, 10, player, data, "str", "힘 (STR)", Material.IRON_SWORD, data.statStr);

        // 2. 체력 강화 (VIT)
        setupUpgradeButton(gui, 12, player, data, "vit", "체력 (VIT)", Material.GOLDEN_APPLE, data.statVit);

        // 3. 민첩 강화 (DEX)
        setupUpgradeButton(gui, 14, player, data, "dex", "민첩 (DEX)", Material.FEATHER, data.statDex);

        // 4. 지능 강화 (INT)
        setupUpgradeButton(gui, 16, player, data, "int", "지능 (INT)", Material.BLAZE_ROD, data.statInt);

        // 5. 행운 강화 (LUK)
        setupUpgradeButton(gui, 22, player, data, "luk", "운 (LUK)", Material.EMERALD, data.statLuk);
    }

    private void setupUpgradeButton(Inventory gui, int slot, Player player, TowerUserData data, String statType, String statName, Material icon, int currentLevel) {
        // 비용 공식: 100 * (현재레벨 + 1)
        long cost = 100L * (currentLevel + 1);

        ItemStack btn = new ItemBuilder(icon)
                .name("§6§l" + statName + " 강화")
                .lore(
                        "§7현재 레벨: §f" + currentLevel,
                        "§7강화 비용: §e" + cost + " G",
                        "",
                        "§e[클릭하여 강화]"
                )
                .build();

        CoreProvider.setGuiButton(gui, slot, btn, (e) -> {
            // 1. 돈 체크
            if (data.gold >= cost) {
                // 2. 차감 및 스탯 증가
                data.gold -= cost;

                switch (statType) {
                    case "str" -> data.statStr++;
                    case "vit" -> data.statVit++;
                    case "dex" -> data.statDex++;
                    case "int" -> data.statInt++;
                    case "luk" -> data.statLuk++;
                }

                // 3. 저장 및 갱신
                plugin.getUserManager().saveUser(player); // DB 저장
                plugin.getUserManager().applyStatsToCore(player, data); // 스탯 적용
                plugin.getUserManager().updateSidebar(player); // 스코어보드 갱신

                // 4. 피드백
                player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1f, 1f);
                player.sendMessage("§a" + statName + " 강화 성공! (Lv." + (currentLevel + 1) + ")");

                // 5. GUI 새로고침 (가격 변동 반영)
                openStatShop(player);
            } else {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                player.sendMessage("§c골드가 부족합니다.");
            }
        });
    }
}