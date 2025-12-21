package com.server.tower.item;

import com.server.core.api.CoreProvider;
import com.server.core.api.builder.ItemBuilder;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class EnhanceLogicHelper {

    public ItemStack applySuccess(Player player, ItemStack item, int currentLevel) {
        return modifyItem(player, item, currentLevel, 1); // +1
    }

    public ItemStack applyDowngrade(Player player, ItemStack item, int currentLevel) {
        return modifyItem(player, item, currentLevel, -1); // -1
    }

    private ItemStack modifyItem(Player player, ItemStack item, int currentLevel, int change) {
        int nextLevel = currentLevel + change;

        // 스탯 키 찾기
        String scalingStat = CoreProvider.getItemDataString(item, "scaling_stat");
        String damageKey;
        String loreTarget;

        // 무기인지 방어구인지 확인
        String weaponType = CoreProvider.getItemDataString(item, "weapon_type");
        boolean isWeapon = (weaponType != null);

        if (isWeapon) {
            damageKey = "str".equals(scalingStat) ? "stat_phys_atk" : "stat_mag_atk";
            loreTarget = "str".equals(scalingStat) ? "물리 공격력" : "마법 공격력";
        } else {
            damageKey = "stat_def"; // 방어구는 방어력 강화
            loreTarget = "방어력";
        }

        int currentVal = CoreProvider.getItemDataInt(item, damageKey);
        int baseVal = CoreProvider.getItemDataInt(item, "base_damage"); // 방어구는 base_def로 저장했어야 함 (통일 필요)
        // 방어구 생성기에서 base_damage로 통일해서 저장하도록 수정 권장
        if (baseVal == 0) baseVal = currentVal;

        // 10% 씩 증감
        int bonus = (int) (baseVal * 0.1);
        if (bonus < 1) bonus = 1;

        int newVal = currentVal + (bonus * change); // +bonus or -bonus
        int totalBonus = newVal - baseVal;

        // 이름 변경
        String oldName = item.getItemMeta().getDisplayName();
        String cleanName = oldName.replaceAll("§e\\(\\+\\d+\\) ", "");
        String newName = "§e(+" + nextLevel + ") " + cleanName;

        ItemStack result = new ItemBuilder(item)
                .name(newName)
                .setData("enhance_level", nextLevel)
                .setData(damageKey, newVal)
                .setData("base_damage", baseVal)
                .build();

        String loreString = String.format("§f%s: §c%d §e(+%d)", loreTarget, newVal, totalBonus);
        CoreProvider.updateFirstLore(result, loreTarget + ":", loreString);

        if (change > 0) {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 2f);
            player.sendMessage("§a§l강화 성공! §7(+" + nextLevel + ")");
        }

        return result;
    }
}