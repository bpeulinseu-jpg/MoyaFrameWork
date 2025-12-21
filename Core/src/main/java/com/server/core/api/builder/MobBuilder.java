package com.server.core.api.builder;

import com.server.core.CorePlugin;
import com.server.core.system.block.DropItem;
import com.server.core.system.mob.MobManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.entity.PiglinAbstract;
import org.bukkit.entity.Hoglin;

import java.util.ArrayList;
import java.util.List;

public class MobBuilder {

    private final MobManager.CustomMobData data;

    // 오버라이드할 속성들
    private double health;
    private double damage;
    private double speed;
    private String customName;
    private boolean nameVisible = true;

    // 동적 드랍 테이블 관리를 위한 변수
    private final List<DropItem> currentDrops;
    private boolean dropsModified = false;

    // 정적 팩토리 메소드
    public static MobBuilder from(String fullId) {
        MobManager.CustomMobData data = CorePlugin.getMobManager().getMobData(fullId);
        if (data == null) throw new IllegalArgumentException("존재하지 않는 몹 ID: " + fullId);
        return new MobBuilder(data);
    }

    // 생성자
    private MobBuilder(MobManager.CustomMobData data) {
        this.data = data;
        // 기본값은 원본 데이터에서 가져옴
        this.health = data.maxHealth;
        this.damage = data.attackDamage;
        this.speed = data.movementSpeed;
        this.customName = data.displayName;
        // 드랍 테이블 복사 (원본 보호)
        this.currentDrops = new ArrayList<>(data.drops);
    }

    // --- 체이닝 메소드 (설정) ---

    public MobBuilder health(double health) {
        this.health = health;
        return this;
    }

    public MobBuilder damage(double damage) {
        this.damage = damage;
        return this;
    }

    public MobBuilder speed(double speed) {
        this.speed = speed;
        return this;
    }

    public MobBuilder name(String name) {
        this.customName = name;
        return this;
    }

    // 레벨 스케일링 (레벨에 비례해 능력치 증가)
    public MobBuilder level(int level) {
        double multiplier = 1.0 + (level * 1); // 레벨당
        this.health = data.maxHealth * multiplier;
        this.damage = data.attackDamage * (1.0 + (level * 0.5)); //
        this.customName = "§e[Lv." + level + "] " + data.displayName;
        return this;
    }

    // --- 드랍 테이블 조작 ---

    public MobBuilder clearDrops() {
        this.currentDrops.clear();
        this.dropsModified = true;
        return this;
    }

    public MobBuilder addDrop(ItemStack item, double chance, int min, int max) {
        // DropItem 생성 (행운 적용 true로 설정)
        this.currentDrops.add(new DropItem(item, chance, min, max, true));
        this.dropsModified = true;
        return this;
    }

    // --- 소환 로직 ---

    public LivingEntity spawn(Location loc) {
        // 1. 엔티티 소환
        LivingEntity entity = (LivingEntity) loc.getWorld().spawnEntity(loc, data.type);

        // 2. 정체성 부여 (MobManager 위임)
        CorePlugin.getMobManager().applyIdentity(entity, data.uniqueName);

        // 3. 동적 드랍 테이블 등록
        if (dropsModified) {
            CorePlugin.getMobManager().saveDynamicDrops(entity, new ArrayList<>(currentDrops));
        }

        // 4. 스탯 적용 (헬퍼 메소드 사용)
        setAttribute(entity, Attribute.MAX_HEALTH, health);
        entity.setHealth(health); // 현재 체력도 최대치로 설정
        setAttribute(entity, Attribute.ATTACK_DAMAGE, damage);
        setAttribute(entity, Attribute.MOVEMENT_SPEED, speed);

        // 5. 이름 적용
        entity.customName(Component.text(customName));
        entity.setCustomNameVisible(nameVisible);

        // 6. 장비 적용 (기본 데이터 사용)
        EntityEquipment equip = entity.getEquipment();
        if (equip != null) {
            if (data.helmet != null) equip.setHelmet(data.helmet);
            if (data.chestplate != null) equip.setChestplate(data.chestplate);
            if (data.leggings != null) equip.setLeggings(data.leggings);
            if (data.boots != null) equip.setBoots(data.boots);
            if (data.mainHand != null) equip.setItemInMainHand(data.mainHand);
            if (data.offHand != null) equip.setItemInOffHand(data.offHand);

            // 장비 드랍 확률 0으로 설정 (커스텀 드랍만 사용하기 위해)
            equip.setHelmetDropChance(0f);
            equip.setChestplateDropChance(0f);
            equip.setLeggingsDropChance(0f);
            equip.setBootsDropChance(0f);
            equip.setItemInMainHandDropChance(0f);
            equip.setItemInOffHandDropChance(0f);
        }

        // 피글린.호글린 류 좀비화 방지
        if (entity instanceof PiglinAbstract piglin) {
            piglin.setImmuneToZombification(true); // 좀비화 면역 설정
        } else if (entity instanceof Hoglin hoglin) {
            hoglin.setImmuneToZombification(true);
        }

        return entity;
    }

    // [헬퍼 메소드] 속성 값 설정
    private void setAttribute(LivingEntity entity, Attribute attr, double value) {
        AttributeInstance instance = entity.getAttribute(attr);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }
}