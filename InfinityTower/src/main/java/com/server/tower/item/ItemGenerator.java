package com.server.tower.item;

import com.server.core.api.builder.ItemBuilder;
import com.server.tower.item.enums.ItemElement;
import com.server.tower.item.enums.ItemPrefix;
import com.server.tower.item.enums.ItemTier;
import com.server.tower.item.enums.WeaponType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ItemGenerator {

    private static final Random random = new Random();

    // 무기에 붙을 수 있는 추가 옵션 목록
        private enum BonusStat {
            // 1차 스탯
            STR("stat_str", "힘", false),
            DEX("stat_dex", "민첩", false),
            INT("stat_int", "지능", false),
            VIT("stat_vit", "활력", false),
            LUK("stat_luk", "행운", false),

            // 공격 관련
            PHYS_ATK("stat_phys_atk", "물리 공격력", true),
            MAG_ATK("stat_mag_atk", "마법 공격력", true),
            CRIT_CHANCE("stat_crit_chance", "치명타 확률", true), // % 표시
            CRIT_DAMAGE("stat_crit_damage", "치명타 피해", true), // % 표시

            // 방어/생존 관련
            MAX_HEALTH("stat_max_health", "최대 체력", false),
            HP_REGEN("stat_hp_regen", "체력 재생", false),
            DEF("stat_def", "방어력", false),
            DODGE("stat_dodge", "회피 확률", true), // % 표시

            // 유틸
            MOVE_SPEED("stat_move_speed", "이동 속도", true); // % 표시

        final String key;
            final String name;
            final boolean isPercent; // 퍼센트(%) 단위인지 여부

            BonusStat(String key, String name, boolean isPercent) {
                this.key = key;
                this.name = name;
                this.isPercent = isPercent;
            }
        }

    public static ItemStack generateWeapon(int level) {
        WeaponType type = WeaponType.values()[random.nextInt(WeaponType.values().length)];
        ItemElement element = ItemElement.values()[random.nextInt(ItemElement.values().length)];
        ItemPrefix prefix = ItemPrefix.values()[random.nextInt(ItemPrefix.values().length)];

        double score = (random.nextDouble() * 100) + (level * 1);

        ItemTier tier;
        if (score > 120) tier = ItemTier.LEGENDARY;
        else if (score > 95) tier = ItemTier.EPIC;
        else if (score > 80) tier = ItemTier.RARE;
        else tier = ItemTier.COMMON;

        double baseDmgCalc = 10 + (level * 2);
        double finalDamage = baseDmgCalc * prefix.getDamageMult() * tier.getStatMultiplier();
        double attackSpeed = 1.6 * prefix.getSpeedMult();

        if (type.isTwoHanded()) {
            finalDamage *= 1.5;
            attackSpeed *= 0.6;
        }

        // 무기 추가 옵션
        int optionCount = (tier == ItemTier.LEGENDARY) ? 3 : (tier == ItemTier.EPIC) ? 2 : (tier == ItemTier.RARE) ? 1 : 0;
        List<BonusStat> selectedOptions = new ArrayList<>();

        List<BonusStat> pool = new ArrayList<>(List.of(BonusStat.values()));
        for (int i = 0; i < optionCount; i++) {
            if (pool.isEmpty()) break;
            int idx = random.nextInt(pool.size());
            selectedOptions.add(pool.remove(idx));
        }

        String resourceId = "infinity_tower:" + type.name().toLowerCase();
        String displayName = tier.getPrefix() + " " + element.getPrefix() + " " + prefix.getName() + " " + type.getName();

        int sockets = random.nextInt(tier.getMaxSockets() - tier.getMinSockets() + 1) + tier.getMinSockets();

        // 스탯 키 결정
        String damageKey = type.getScalingStat().equals("str") ? "stat_phys_atk" : "stat_mag_atk";
        String damageLore = type.getScalingStat().equals("str") ? "§f물리 공격력" : "§b마법 공격력";

        List<String> lore = new ArrayList<>();
        lore.add("§7----------------");
        lore.add("§f등급: " + tier.getPrefix());
        // [수정] 초기 상태이므로 (+0) 표기는 생략하거나 0으로 표시
        // 포맷 통일을 위해 "공격력: 20" 형태로 작성
        lore.add(damageLore + ": §c" + (int)finalDamage);
        lore.add("§f공격속도: §b" + String.format("%.1f", attackSpeed));

        for (BonusStat stat : selectedOptions) {
            int val = calculateBonusValue(stat, level, tier);
            String suffix = stat.isPercent ? "%" : ""; // 퍼센트면 % 붙임
            lore.add("§a" + stat.name + ": +" + val + suffix);
        }

        lore.add("§7----------------");
        lore.add("§e주 스탯: " + type.getScalingStat().toUpperCase());
        lore.add("§7----------------");


        for (int i = 0; i < sockets; i++) {
            lore.add("§8[○] 빈 소켓");
        }

        ItemBuilder builder = ItemBuilder.from(resourceId)
                .name(displayName)
                .lore(lore.toArray(new String[0]))
                .amount(1)
                .addAttribute(org.bukkit.attribute.Attribute.ATTACK_DAMAGE, finalDamage, org.bukkit.inventory.EquipmentSlot.HAND)
                .addAttribute(org.bukkit.attribute.Attribute.ATTACK_SPEED, attackSpeed - 4.0, org.bukkit.inventory.EquipmentSlot.HAND)

                .setData("weapon_type", type.name())
                .setData("tier", tier.name())
                .setData("element", element.name())
                .setData("prefix", prefix.name())

                // [중요] 초기 데이터 확실하게 저장
                .setData(damageKey, (int) finalDamage)
                .setData("base_damage", (int) finalDamage) // 베이스 대미지 저장
                .setData("enhance_level", 0) // 강화 레벨 0 초기화

                .setData("attack_speed", (int) (attackSpeed * 100))
                .setData("scaling_stat", type.getScalingStat())
                .setData("is_two_handed", type.isTwoHanded() ? 1 : 0)
                .setData("sockets", sockets);

        // [수정] 추가 옵션 데이터 저장
        for (BonusStat stat : selectedOptions) {
            int val = calculateBonusValue(stat, level, tier);
            builder.setData(stat.key, val);
        }

        if (tier.isGlow()) {
            builder.glow();
        }

        return builder.build();
    }

    // [핵심 수정] 스탯별 밸런싱 로직
    private static int calculateBonusValue(BonusStat stat, int level, ItemTier tier) {
        double base = 0;

        switch (stat) {
            // 1. 최대 체력 (Level 비례: 0.5 ~ 2.0 배율)
            case MAX_HEALTH:
                // 예: Lv.10 -> 5 ~ 20
                double healthMult = 0.5 + (random.nextDouble() * 1.5); // 0.5 ~ 2.0
                base = level * healthMult;
                break;

            // 2. 체력 재생 (Level 비례: 0.01 ~ 0.1 배율)
            case HP_REGEN:
                // 예: Lv.100 -> 1 ~ 10 (수치가 작으므로 고레벨에서 티가 남)
                double regenMult = 0.01 + (random.nextDouble() * 0.09); // 0.01 ~ 0.1
                base = level * regenMult;
                break;

            // 3. 치명타 피해 (고정: 0.01 ~ 3.0 -> 1% ~ 300%)
            case CRIT_DAMAGE:
                // int 저장을 위해 x100 스케일링 (1 ~ 300)
                base = 1 + (random.nextDouble() * 299);
                break;

            // 4. 물리/마법 공격력 (고정: 0.01 ~ 0.5 -> 1% ~ 50%)
            case PHYS_ATK:
            case MAG_ATK:
                // int 저장을 위해 x100 스케일링 (1 ~ 50)
                base = 1 + (random.nextDouble() * 49);
                break;

            // 5. 회피/치명타 확률 (고정: 0.01 ~ 0.3 -> 1% ~ 30%)
            case DODGE:
            case CRIT_CHANCE:
                // int 저장을 위해 x100 스케일링 (1 ~ 30)
                base = 1 + (random.nextDouble() * 29);
                break;

            // 6. 이동 속도 (고정: 0.01 ~ 0.75 -> 1% ~ 75%)
            case MOVE_SPEED:
                // int 저장을 위해 x100 스케일링 (1 ~ 75)
                base = 1 + (random.nextDouble() * 74);
                break;
        }

        // [등급 배율 적용]
        // 고정 상수라 하더라도 전설 등급이 일반 등급보다 좋아야 하므로 배율은 유지했습니다.
        // 만약 등급 무시하고 완전 고정을 원하시면 multiplier를 1.0으로 바꾸세요.
        double multiplier = tier.getStatMultiplier();

        int val = (int) (base * 1.0); //1.0을 multiplier 로 바꾸면 등급별 차등임

        // 최소값 1 보장 (0이 뜨면 옵션이 없는 것과 같으므로)
        return Math.max(1, val);
    }
}