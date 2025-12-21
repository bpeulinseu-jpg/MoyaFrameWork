package com.server.tower.item;

import com.server.tower.user.TowerUserData;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

public class RepairManager {

    private static final int COST_PER_DURABILITY = 10; // 내구도 1당 10골드

    public long calculateRepairCost(ItemStack item) {
        if (item == null || !(item.getItemMeta() instanceof Damageable meta)) return 0;
        return (long) meta.getDamage() * COST_PER_DURABILITY;
    }

    public ItemStack repairItem(Player player, ItemStack item, TowerUserData userData) {
        if (item == null || !(item.getItemMeta() instanceof Damageable meta)) return item;

        // 1. 수리 필요 여부 확인
        if (!meta.hasDamage()) {
            player.sendMessage("§a수리할 필요가 없는 아이템입니다.");
            return item;
        }

        // 2. 비용 계산
        long cost = calculateRepairCost(item);

        // 3. 골드 확인
        if (userData.gold < cost) {
            player.sendMessage("§c골드가 부족합니다. (필요: " + cost + " G)");
            return item; // 수리 실패
        }

        // 4. 수리 진행
        userData.gold -= cost;
        meta.setDamage(0); // 내구도 완전 회복
        item.setItemMeta(meta);

        player.playSound(player.getLocation(), Sound.BLOCK_GRINDSTONE_USE, 1f, 1f);
        player.sendMessage("§a수리 완료! (-" + cost + " G)");

        return item;
    }
}