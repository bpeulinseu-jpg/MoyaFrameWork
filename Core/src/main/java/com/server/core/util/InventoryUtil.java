package com.server.core.util;

import com.server.core.CorePlugin;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class InventoryUtil {

    /**
     * 플레이어 인벤토리에 특정 커스텀 아이템이 충분한지 확인
     */
    public static boolean hasItem(Player player, String customId, int amount) {
        int count = 0;
        Inventory inv = player.getInventory();

        for (ItemStack item : inv.getContents()) {
            if (item == null || item.getType().isAir()) continue;

            // 커스텀 ID 확인
            if (CorePlugin.getItemManager().isCustomItem(item, customId)) {
                count += item.getAmount();
            }
        }
        return count >= amount;
    }

    /**
     * 플레이어 인벤토리에서 특정 커스텀 아이템을 수량만큼 제거
     * @return 성공 여부 (수량이 부족하면 제거하지 않고 false 반환)
     */
    public static boolean removeItem(Player player, String customId, int amount) {
        if (!hasItem(player, customId, amount)) return false;

        Inventory inv = player.getInventory();
        int remaining = amount;

        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (item == null || item.getType().isAir()) continue;

            if (CorePlugin.getItemManager().isCustomItem(item, customId)) {
                int currentAmount = item.getAmount();

                if (currentAmount > remaining) {
                    // 현재 슬롯의 양이 더 많으면 차감하고 종료
                    item.setAmount(currentAmount - remaining);
                    remaining = 0;
                    break;
                } else {
                    // 현재 슬롯을 다 써야 하면 제거하고 계속 진행
                    inv.setItem(i, null);
                    remaining -= currentAmount;
                }
            }
        }
        return true;
    }
}