package com.server.tower.mob;

import com.server.core.api.CoreProvider;
import com.server.tower.TowerPlugin;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import com.server.core.api.builder.ItemBuilder;


public class MobRegistry {

    private final TowerPlugin plugin;

    public MobRegistry(TowerPlugin plugin) {
        this.plugin = plugin;
    }

    public void registerAll() {
        // 드랍 테이블용 아이템 불러오기
        ItemStack scrollWeapon = CoreProvider.getItem("infinity_tower:scroll_weapon");
        ItemStack scrollArmor = CoreProvider.getItem("infinity_tower:scroll_armor");
        ItemStack protectionCharm = CoreProvider.getItem("infinity_tower:protection_charm");
        ItemStack luckyStone = CoreProvider.getItem("infinity_tower:lucky_stone");

        // 고블린 등록 (기본 좀비)
        var goblin = CoreProvider.registerMob(plugin, "goblin", EntityType.ZOMBIE, "§2고블린");

        // 기본 스탯
        goblin.setStats(20.0, 3.0, 0.25);

        // 장비
        goblin.setEquipment(
                new ItemStack(Material.WOODEN_SWORD),
                new ItemStack(Material.LEATHER_HELMET),
                null, null, null
        );

        // 드랍 테이블
        // 경험치 드랍
        goblin.setExp(3);
        // 힘의 보석 (데이터 포함 생성)
        ItemStack gemStr = ItemBuilder.from("infinity_tower:gem_str")
                .name("§c힘의 보석")
                .setData("is_gem", 1).setData("gem_type", "str").setData("gem_value", 5)
                .build();

        // 지능의 보석
        ItemStack gemInt = ItemBuilder.from("infinity_tower:gem_int")
                .name("§b지능의 보석")
                .setData("is_gem", 1).setData("gem_type", "int").setData("gem_value", 5)
                .build();

        if (protectionCharm != null) goblin.addDrop(protectionCharm, 0.01, 1, 1); // 10%
        if (luckyStone != null) goblin.addDrop(luckyStone, 0.01, 1, 1); // 10%
        if (scrollWeapon != null) goblin.addDrop(scrollWeapon, 0.02, 1, 1);
        if (scrollArmor != null) goblin.addDrop(scrollArmor, 0.02, 1, 1);
        goblin.addDrop(gemStr, 0.01, 1, 1); // 1%
        goblin.addDrop(gemInt, 0.01, 1, 1); // 1%

        // [신규] 2. 오크 대장 (보스)
        var orcBoss = CoreProvider.registerMob(plugin, "orc_boss", EntityType.PIGLIN_BRUTE, "§4§l오크 대장");
        orcBoss.setStats(150.0, 10.0, 0.2); // 체력 150, 공격력 10
        orcBoss.setEquipment(new ItemStack(Material.GOLDEN_AXE), null, null, null, null);

        // 보스 보상: 대량의 경험치 + 희귀 아이템
        orcBoss.setExp(100);
        // 드랍 테이블
        if (protectionCharm != null) orcBoss.addDrop(protectionCharm, 0.1, 1, 1); // 10%
        if (luckyStone != null) orcBoss.addDrop(luckyStone, 0.1, 1, 1); // 10%
        if (scrollWeapon != null) orcBoss.addDrop(scrollWeapon, 0.2, 1, 1);
        if (scrollArmor != null) orcBoss.addDrop(scrollArmor, 0.2, 1, 1);
        orcBoss.addDrop(gemStr, 0.2, 1, 2);
        orcBoss.addDrop(gemInt, 0.2, 1, 2);

        plugin.getLogger().info("🧟 몬스터 등록 완료.");
    }
}