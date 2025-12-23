package com.server.tower.mob;

import com.server.core.api.CoreProvider;
import com.server.core.api.builder.ItemBuilder;
import com.server.core.system.mob.MobManager;
import com.server.tower.TowerPlugin;
import com.server.tower.mob.skill.BuffSkill;
import com.server.tower.mob.skill.ShieldBashSkill;
import com.server.tower.mob.skill.SnipeSkill;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;

public class MobRegistry {

    private final TowerPlugin plugin;

    public MobRegistry(TowerPlugin plugin) {
        this.plugin = plugin;
    }

    public void registerAll() {
        // 1. 고블린 (기본형) - 좀비
        MobManager.CustomMobData goblin = CoreProvider.registerMob(plugin, "goblin", EntityType.ZOMBIE, "§2고블린");
        goblin.setStats(20, 3, 0.25);
        goblin.setEquipment(
                new ItemStack(Material.WOODEN_SWORD), // 무기
                new ItemBuilder(Material.LEATHER_HELMET).build(), // 머리 (햇빛 화상 방지)
                new ItemStack(Material.LEATHER_CHESTPLATE),
                null, null
        );
        goblin.setExp(5);

        // 2. 스켈레톤 명사수 (원거리) - 스켈레톤
        MobManager.CustomMobData sniper = CoreProvider.registerMob(plugin, "skeleton_sniper", EntityType.SKELETON, "§7스켈레톤 명사수");
        sniper.setStats(15, 5, 0.23);
        sniper.setEquipment(
                new ItemStack(Material.CROSSBOW),
                null, null, null, null);
        sniper.addSkill(new SnipeSkill(plugin)); // [스킬] 저격

        // 3. 리빙 아머 (탱커) - 아이언 골렘 (혹은 갑옷 입은 좀비)
        // 아이언 골렘은 장비를 못 입으므로, '갑옷 입은 좀비'나 '위더 스켈레톤' 추천. 여기선 일단 아이언 골렘 사용.
        MobManager.CustomMobData armor = CoreProvider.registerMob(plugin, "living_armor", EntityType.ZOMBIE, "§8리빙 아머");
        armor.setStats(100, 10, 0.18);
        armor.setEquipment(
                new ItemStack(Material.IRON_SWORD),
                new ItemStack(Material.IRON_HELMET),
                new ItemStack(Material.IRON_CHESTPLATE),
                new ItemStack(Material.IRON_LEGGINGS),
                new ItemStack(Material.IRON_BOOTS)
        );
        armor.addSkill(new ShieldBashSkill());

        // 4. 맹독 슬라임 (자폭) - 슬라임
        MobManager.CustomMobData slime = CoreProvider.registerMob(plugin, "toxic_slime", EntityType.SLIME, "§a맹독 슬라임");
        slime.setStats(10, 2, 0.3);
        // 슬라임 사이즈 조절은 소환 후 별도 처리가 필요할 수 있음 (기본은 랜덤)
        // 자폭 기능은 MobAbilityListener에서 처리

        // 5. 고블린 주술사 (서포터) - 마녀
        MobManager.CustomMobData shaman = CoreProvider.registerMob(plugin, "goblin_shaman", EntityType.WITCH, "§5고블린 주술사");
        shaman.setStats(30, 2, 0.25);
        shaman.addSkill(new BuffSkill()); // [스킬] 광란의 춤

        // 6. 그림자 망령 (암살자) - 엔더맨
        MobManager.CustomMobData wraith = CoreProvider.registerMob(plugin, "shadow_wraith", EntityType.ENDERMAN, "§8그림자 망령");
        wraith.setStats(40, 8, 0.35); // 빠르고 아픔
        // 기습 패턴은 엔더맨 기본 AI + MobAbilityListener로 보완

        // 7. 오크 대장 (챕터 1 보스) - 피글린 브루트 or 위더 스켈레톤
        MobManager.CustomMobData boss = CoreProvider.registerMob(plugin, "orc_chief", EntityType.PIGLIN_BRUTE, "§4§l오크 대장");
        boss.setStats(300, 15, 0.2); // 높은 체력, 강력한 공격
        boss.setEquipment(
                new ItemStack(Material.GOLDEN_AXE), // 도끼
                new ItemStack(Material.GOLDEN_HELMET),
                new ItemStack(Material.GOLDEN_CHESTPLATE),
                new ItemStack(Material.GOLDEN_LEGGINGS),
                new ItemStack(Material.GOLDEN_BOOTS)
        );
        boss.setExp(100);
        // boss.addSkill(...) // 추후 보스 전용 스킬(점프 공격, 부하 소환) 추가 필요
    }
}