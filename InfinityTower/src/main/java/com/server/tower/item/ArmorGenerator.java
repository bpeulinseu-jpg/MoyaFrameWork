package com.server.tower.item;

import com.server.core.api.builder.ItemBuilder;
import com.server.tower.item.enums.ArmorElement;
import com.server.tower.item.enums.ArmorPrefix;
import com.server.tower.item.enums.ArmorType;
import com.server.tower.item.enums.ItemTier;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ArmorGenerator {

    private static final Random random = new Random();

    public static ItemStack generateArmor(int level) {
        // 1. 랜덤 요소
        ArmorType type = ArmorType.values()[random.nextInt(ArmorType.values().length)];
        ArmorPrefix prefix = ArmorPrefix.values()[random.nextInt(ArmorPrefix.values().length)];
        ArmorElement element = ArmorElement.values()[random.nextInt(ArmorElement.values().length)];

        // 2. 등급 결정
        double score = (random.nextDouble() * 100) + (level * 0.5);
        ItemTier tier;
        if (score > 95) tier = ItemTier.LEGENDARY;
        else if (score > 80) tier = ItemTier.EPIC;
        else if (score > 50) tier = ItemTier.RARE;
        else tier = ItemTier.COMMON;

        // 3. 스탯 계산
        double baseDef = (2 + (level * 0.5)) * type.getStatMultiplier();
        double finalDef = baseDef * prefix.getDefMult() * tier.getStatMultiplier();

        double baseHp = (10 + (level * 2)) * type.getStatMultiplier();
        double finalHp = baseHp * prefix.getHpMult() * tier.getStatMultiplier();

        int speed = prefix.getSpeedAdd();

        // 4. 속성 보너스
        int bonusValue = (int) (level * 0.5);
        if (bonusValue < 1) bonusValue = 1;

        // 5. ID 및 이름
        String resourceId;
        if (element == ArmorElement.NONE) {
            resourceId = "infinity_tower:" + type.name().toLowerCase();
        } else {
            resourceId = "infinity_tower:" + element.name().toLowerCase() + "_" + type.name().toLowerCase();
        }
        String setId;
        if (element == ArmorElement.NONE) {
            setId = "infinity_tower_base";
        } else {
            setId = "infinity_tower_" + element.name().toLowerCase();
        }

        String displayName = tier.getPrefix() + " " + element.getPrefix() + " " + prefix.getName() + " " + type.getName();
        int sockets = random.nextInt(tier.getMaxSockets() - tier.getMinSockets() + 1) + tier.getMinSockets();

        // 6. Lore
        List<String> lore = new ArrayList<>();
        lore.add("§7----------------");
        lore.add("§f등급: " + tier.getPrefix());
        lore.add("§f방어력: §a" + String.format("%.1f", finalDef));
        lore.add("§f최대 체력: §c+" + String.format("%.0f", finalHp));

        if (speed != 0) {
            String color = speed > 0 ? "§b+" : "§c";
            lore.add("§f이동 속도: " + color + speed + "%");
        }

        if (element != ArmorElement.NONE) {
            lore.add("§d" + element.getStatName() + ": +" + bonusValue);
        }

        lore.add("§7----------------");
        for (int i = 0; i < sockets; i++) {
            lore.add("§8[○] 빈 소켓");
        }

        // 세트 id 생성

        // 7. 아이템 생성 (체이닝 연결 수정됨)
        ItemBuilder builder = ItemBuilder.from(resourceId)
                .name(displayName)
                .lore(lore.toArray(new String[0]))
                .amount(1)

                // 기본 스탯
                .setData("stat_def", (int) finalDef)
                .setData("stat_max_health", (int) finalHp)
                .setData("stat_move_speed", speed)

                // 메타 데이터
                .setData("armor_type", type.name())
                .setData("tier", tier.name())
                .setData("enhance_level", 0)
                .setData("sockets", sockets)

                // [수정] 여기서 세미콜론 없이 연결
                // resourceId(infinity_tower:helmet) -> infinity_tower_helmet
                .setArmorModel(setId);

        // 8. 속성 스탯 추가
        if (element == ArmorElement.STORM) {
            int totalSpeed = speed + bonusValue;
            builder.setData("stat_move_speed", totalSpeed);
        } else if (element != ArmorElement.NONE) {
            builder.setData(element.getStatKey(), bonusValue);
        }

        if (tier.isGlow()) {
            builder.glow();
        }

        return builder.build();
    }
}