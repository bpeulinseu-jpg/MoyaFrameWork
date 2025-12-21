package com.server.tower.item;

import com.server.core.api.CoreProvider;
import com.server.core.api.builder.ItemBuilder;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Random;

public class EnhanceManager {

    private final Random random = new Random();

    public enum EnhanceResult {
        SUCCESS, FAIL_KEEP, FAIL_DOWN, DESTROY
    }

    public ItemStack tryEnhance(Player player, ItemStack item, boolean hasProtection, boolean hasLucky) {
        int currentLevel = CoreProvider.getItemDataInt(item, "enhance_level");

        EnhanceResult result = calculateResult(currentLevel, hasLucky, hasProtection);

        switch (result) {
            case SUCCESS:
                return success(player, item, currentLevel);
            case FAIL_KEEP:
                player.sendMessage("§e강화에 실패했지만 등급은 유지되었습니다.");
                player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 1f, 0.5f);
                return item;
            case FAIL_DOWN:
                return downgrade(player, item, currentLevel);
            case DESTROY:
                player.sendMessage("§c§l강화 실패! 장비가 파괴되었습니다...");
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 0.5f);
                return null;
        }
        return item;
    }

    private EnhanceResult calculateResult(int level, boolean lucky, boolean protection) {
        double roll = random.nextDouble() * 100; // 0.0 ~ 100.0
        double successChance;

        // 확률 테이블
        if (level < 5) successChance = 100.0;
        else if (level < 9) successChance = 60.0;
        else if (level < 12) successChance = 40.0;
        else successChance = 20.0;

        if (lucky) successChance += 10.0;

        if (roll < successChance) return EnhanceResult.SUCCESS;

        // 실패 시 분기
        if (level < 5) return EnhanceResult.FAIL_KEEP; // 사실상 도달 안 함
        if (level < 9) return EnhanceResult.FAIL_KEEP; // 9강까진 파괴/하락 없음

        // 10강 이상: 하락 vs 파괴
        // 파괴 방지권이 있으면 무조건 하락(또는 유지)
        if (protection) return EnhanceResult.FAIL_DOWN;

        // 파괴 확률 50% (실패한 것 중에서)
        return random.nextBoolean() ? EnhanceResult.FAIL_DOWN : EnhanceResult.DESTROY;
    }


    private ItemStack success(Player player, ItemStack item, int currentLevel) {
        int nextLevel = currentLevel + 1;

        // 1. 스탯 키 확인
        String scalingStat = CoreProvider.getItemDataString(item, "scaling_stat");
        String damageKey = "str".equals(scalingStat) ? "stat_phys_atk" : "stat_mag_atk";
        String loreTarget = "str".equals(scalingStat) ? "물리 공격력" : "마법 공격력";

        // 2. 대미지 계산
        int currentDamage = CoreProvider.getItemDataInt(item, damageKey);
        int baseDamage = CoreProvider.getItemDataInt(item, "base_damage");

        // 안전장치: 데이터가 없으면 현재 값을 베이스로 잡음
        if (baseDamage == 0) baseDamage = currentDamage;
        if (currentDamage == 0) currentDamage = baseDamage;

        // 10% 증가 (최소 1)
        int bonus = (int) (currentDamage * 0.1);
        if (bonus < 1) bonus = 1;

        int newDamage = currentDamage + bonus;
        int totalBonus = newDamage - baseDamage; // 총 증가량 (현재 - 원본)

        // 3. 이름 변경
        String oldName = item.getItemMeta().getDisplayName();
        String cleanName = oldName.replaceAll("§e\\(\\+\\d+\\) ", ""); // 기존 (+N) 제거
        String newName = "§e(+" + nextLevel + ") " + cleanName;

        // 4. 아이템 업데이트
        ItemStack result = new ItemBuilder(item)
                .name(newName)
                .setData("enhance_level", nextLevel)
                .setData(damageKey, newDamage)
                .setData("base_damage", baseDamage)
                .build();

        // 5. [핵심] Lore 업데이트: "물리 공격력: 110 (+10)"
        String loreString = String.format("§f%s: §c%d §e(+%d)", loreTarget, newDamage, totalBonus);

        // "물리 공격력"이 포함된 줄을 찾아서 교체
        CoreProvider.updateFirstLore(result, loreTarget, loreString);

        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 2f);
        player.sendMessage("§a§l강화 성공! §7(+" + nextLevel + ")");

        return new EnhanceLogicHelper().applySuccess(player, item, currentLevel);
    }

    private ItemStack downgrade(Player player, ItemStack item, int currentLevel) {
        player.sendMessage("§c강화 실패! 등급이 하락했습니다. (-1)");
        player.playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK, 1f, 1f);

        // 로직은 success와 비슷하지만 수치를 깎음 (구현 필요)
        // 편의상 여기서는 -1강 처리를 위한 헬퍼 호출
        return new EnhanceLogicHelper().applyDowngrade(player, item, currentLevel);
    }
}