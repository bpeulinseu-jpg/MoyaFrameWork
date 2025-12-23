package com.server.core.system.mob;

import com.server.core.CorePlugin;
import com.server.core.api.CoreAddon;
import com.server.core.system.block.DropItem;
import com.server.core.system.mob.skill.MobSkill;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MobManager {

    private final CorePlugin plugin;
    private final Map<String, CustomMobData> mobMap = new HashMap<>();
    private final NamespacedKey KEY_ID;
    private final NamespacedKey KEY_DROPS;
    private MobSkillManager skillManager;

    public static class CustomMobData {
        public final String namespace;
        public final String id;
        public final String uniqueName;
        public final EntityType type;
        public final String displayName;

        public double maxHealth = 20.0;
        public double attackDamage = 1.0;
        public double movementSpeed = 0.23;

        public ItemStack helmet;
        public ItemStack chestplate;
        public ItemStack leggings;
        public ItemStack boots;
        public ItemStack mainHand;
        public ItemStack offHand;

        public List<DropItem> drops = new ArrayList<>();
        public int exp = 0;

        public List<MobSkill> skills = new ArrayList<>();

        public CustomMobData(String namespace, String id, EntityType type, String displayName) {
            this.namespace = namespace;
            this.id = id;
            this.uniqueName = namespace + ":" + id;
            this.type = type;
            this.displayName = displayName;
        }

        // [추가] 스킬 등록 메서드 (체이닝)
        public CustomMobData addSkill(MobSkill skill) {
            this.skills.add(skill);
            return this;
        }

        public CustomMobData setStats(double health, double damage, double speed) {
            this.maxHealth = health;
            this.attackDamage = damage;
            this.movementSpeed = speed;
            return this;
        }

        public CustomMobData setEquipment(ItemStack hand, ItemStack helmet, ItemStack chest, ItemStack legs, ItemStack boots) {
            this.mainHand = hand;
            this.helmet = helmet;
            this.chestplate = chest;
            this.leggings = legs;
            this.boots = boots;
            return this;
        }

        public CustomMobData addDrop(ItemStack item, double chance, int min, int max) {
            this.drops.add(new DropItem(item, chance, min, max, true));
            return this;
        }

        public CustomMobData setExp(int exp) {
            this.exp = exp;
            return this;
        }
    }

    public MobManager(CorePlugin plugin) {
        this.plugin = plugin;
        this.KEY_ID = new NamespacedKey(plugin, "mob_id");
        this.KEY_DROPS = new NamespacedKey(plugin, "mob_drops");
        this.skillManager = new MobSkillManager(plugin);
    }

    public CustomMobData registerMob(CoreAddon addon, String id, EntityType type, String name) {
        String fullId = addon.getNamespace() + ":" + id;
        CustomMobData data = new CustomMobData(addon.getNamespace(), id, type, name);
        mobMap.put(fullId, data);
        return data;
    }

    public CustomMobData getMobData(String fullId) {
        return mobMap.get(fullId);
    }

    // [복구됨] 몹 소환 메소드
    public LivingEntity spawnMob(Location loc, String fullId) {
        CustomMobData data = mobMap.get(fullId);
        if (data == null) return null;

        LivingEntity entity = (LivingEntity) loc.getWorld().spawnEntity(loc, data.type);

        // 정체성 부여
        applyIdentity(entity, fullId);

        // 스탯 적용
        AttributeInstance hpAttr = entity.getAttribute(Attribute.MAX_HEALTH);
        if (hpAttr != null) hpAttr.setBaseValue(data.maxHealth);
        entity.setHealth(data.maxHealth);

        AttributeInstance dmgAttr = entity.getAttribute(Attribute.ATTACK_DAMAGE);
        if (dmgAttr != null) dmgAttr.setBaseValue(data.attackDamage);

        AttributeInstance spdAttr = entity.getAttribute(Attribute.MOVEMENT_SPEED);
        if (spdAttr != null) spdAttr.setBaseValue(data.movementSpeed);

        // 장비 적용
        EntityEquipment equip = entity.getEquipment();
        if (equip != null) {
            if (data.helmet != null) equip.setHelmet(data.helmet);
            if (data.chestplate != null) equip.setChestplate(data.chestplate);
            if (data.leggings != null) equip.setLeggings(data.leggings);
            if (data.boots != null) equip.setBoots(data.boots);
            if (data.mainHand != null) equip.setItemInMainHand(data.mainHand);
            if (data.offHand != null) equip.setItemInOffHand(data.offHand);

            // 장비 드랍 방지
            equip.setHelmetDropChance(0f);
            equip.setChestplateDropChance(0f);
            equip.setLeggingsDropChance(0f);
            equip.setBootsDropChance(0f);
            equip.setItemInMainHandDropChance(0f);
            equip.setItemInOffHandDropChance(0f);
        }

        // [추가] AI 엔진에 몬스터 등록 (스킬 사용을 위해)
        if (!data.skills.isEmpty()) {
            skillManager.registerActiveMob(entity, fullId);
        }

        return entity;
    }

    public void saveDynamicDrops(LivingEntity entity, List<DropItem> drops) {
        if (drops == null || drops.isEmpty()) return;

        YamlConfiguration config = new YamlConfiguration();
        for (int i = 0; i < drops.size(); i++) {
            DropItem drop = drops.get(i);
            String path = "d." + i;
            config.set(path + ".item", drop.getItem());
            config.set(path + ".chance", drop.getChance());
            config.set(path + ".min", drop.getMinAmount());
            config.set(path + ".max", drop.getMaxAmount());
            config.set(path + ".fortune", drop.isApplyFortune());
        }

        String serialized = config.saveToString();
        entity.getPersistentDataContainer().set(KEY_DROPS, PersistentDataType.STRING, serialized);
    }

    public List<DropItem> loadDynamicDrops(Entity entity) {
        if (!entity.getPersistentDataContainer().has(KEY_DROPS, PersistentDataType.STRING)) {
            return null;
        }

        String serialized = entity.getPersistentDataContainer().get(KEY_DROPS, PersistentDataType.STRING);
        List<DropItem> drops = new ArrayList<>();

        try {
            YamlConfiguration config = new YamlConfiguration();
            config.loadFromString(serialized);

            if (config.contains("d")) {
                for (String key : config.getConfigurationSection("d").getKeys(false)) {
                    String path = "d." + key;
                    ItemStack item = config.getItemStack(path + ".item");
                    double chance = config.getDouble(path + ".chance");
                    int min = config.getInt(path + ".min");
                    int max = config.getInt(path + ".max");
                    boolean fortune = config.getBoolean(path + ".fortune");

                    if (item != null) {
                        drops.add(new DropItem(item, chance, min, max, fortune));
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return drops;
    }

    public void applyIdentity(LivingEntity entity, String fullId) {
        entity.getPersistentDataContainer().set(KEY_ID, PersistentDataType.STRING, fullId);
    }

    public String getCustomMobId(Entity entity) {
        if (!(entity instanceof LivingEntity)) return null;
        return entity.getPersistentDataContainer().get(KEY_ID, PersistentDataType.STRING);
    }

    public List<CustomMobData> getAllMobs() {
        return new ArrayList<>(mobMap.values());
    }
}