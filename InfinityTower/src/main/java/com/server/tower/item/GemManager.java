package com.server.tower.item;

import com.server.core.api.CoreProvider;
import com.server.core.api.builder.ItemBuilder;
import org.bukkit.Sound;
import com.server.core.util.LoreUtil;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class GemManager {

    /**
     * 보석 장착 시도
     * @return 성공 시 장착된 아이템, 실패 시 null
     */
    public ItemStack attachGem(Player player, ItemStack weapon, ItemStack gem) {
        // 1. 유효성 검사
        if (weapon == null || gem == null) return null;
        if (CoreProvider.getItemDataInt(gem, "is_gem") != 1) {
            player.sendMessage("§c올바른 보석 아이템이 아닙니다.");
            return null;
        }

        int maxSockets = CoreProvider.getItemDataInt(weapon, "sockets");
        int filledSockets = CoreProvider.getItemDataInt(weapon, "filled_sockets");

        if (maxSockets <= 0) {
            player.sendMessage("§c이 아이템에는 소켓이 없습니다.");
            return null;
        }
        if (filledSockets >= maxSockets) {
            player.sendMessage("§c더 이상 보석을 장착할 수 없습니다.");
            return null;
        }

        // 2. 데이터 추출
        String gemType = CoreProvider.getItemDataString(gem, "gem_type"); // str, int...
        int gemValue = CoreProvider.getItemDataInt(gem, "gem_value");

        // 3. 무기 스탯 업데이트
        // 기존 스탯 + 보석 스탯
        int currentStat = CoreProvider.getItemDataInt(weapon, "stat_" + gemType);
        int newStat = currentStat + gemValue;

        // 4. 아이템 빌더로 수정 (Lore 업데이트 포함)
        ItemStack upgradedWeapon = new ItemBuilder(weapon)
                .setData("stat_" + gemType, newStat) // 스탯 NBT 저장
                .setData("filled_sockets", filledSockets + 1) // 소켓 카운트 증가
                .build();

        // 5. Lore 시각적 업데이트 (빈 소켓 -> 채워진 소켓)

        String gemName = (gemType.equals("str") ? "§c힘" : "§b지능") + "의 보석";
        String replacement = "§6[●] " + gemName + " (+" + gemValue + ")";

        CoreProvider.updateFirstLore(upgradedWeapon, "[○] 빈 소켓", replacement);

        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1f, 1f);
        player.sendMessage("§a보석 장착 성공!");

        return upgradedWeapon;
    }
}