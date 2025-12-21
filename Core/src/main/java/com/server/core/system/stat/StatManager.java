package com.server.core.system.stat;

import com.server.core.CorePlugin;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class StatManager {

    private final CorePlugin plugin;

    private final Map<UUID, Map<String, Double>> baseStats = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Double>> sessionStats = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Double>> totalStats = new ConcurrentHashMap<>();

    public StatManager(CorePlugin plugin) {
        this.plugin = plugin;
    }

    public void setBaseStat(Player player, String key, double value) {
        baseStats.computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>()).put(key, value);
        recalculate(player);
    }

    public void addSessionStat(Player player, String key, double value) {
        Map<String, Double> stats = sessionStats.computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>());
        stats.put(key, stats.getOrDefault(key, 0.0) + value);
        recalculate(player);
    }

    public void clearSessionStats(Player player) {
        sessionStats.remove(player.getUniqueId());
        recalculate(player);
    }

    public void recalculate(Player player) {
        Map<String, Double> totals = new HashMap<>();
        UUID uuid = player.getUniqueId();

        // A. 기본 스탯 합산
        if (baseStats.containsKey(uuid)) {
            baseStats.get(uuid).forEach((k, v) -> totals.merge(k, v, Double::sum));
        }

        // B. 세션 스탯 합산
        if (sessionStats.containsKey(uuid)) {
            sessionStats.get(uuid).forEach((k, v) -> totals.merge(k, v, Double::sum));
        }

        // C. 장비 스탯 합산
        for (ItemStack item : player.getInventory().getArmorContents()) {
            addItemStats(item, totals);
        }
        addItemStats(player.getInventory().getItemInMainHand(), totals);
        addItemStats(player.getInventory().getItemInOffHand(), totals);

        // D. 저장
        totalStats.put(uuid, totals);

        // E. 바닐라 적용
        applyToVanilla(player, totals);

        // [디버그] 스탯 변경 로그 (테스트 후 삭제)
        // plugin.getLogger().info("Stat Update for " + player.getName() + ": HP=" + totals.get("max_health") + ", DEF=" + totals.get("def"));
    }

    private void addItemStats(ItemStack item, Map<String, Double> totals) {
        if (item == null || !item.hasItemMeta()) return;

        // [핵심 수정] 검사할 스탯 키 목록을 모두 추가해야 함!
        // ArmorGenerator나 ItemGenerator에서 저장하는 키들 ("stat_" 접두사 제외한 이름)
        String[] keys = {
                // 1차 스탯
                "str", "dex", "vit", "int", "luk",
                // 공격 관련
                "phys_atk", "mag_atk", "crit_chance", "crit_damage", "cdr",
                // 방어/생존 관련
                "def", "max_health", "hp_regen", "dodge",
                // 유틸
                "move_speed"
        };

        for (String key : keys) {
            // NBT 키: stat_str, stat_def, stat_max_health ...
            int value = CorePlugin.getDataManager().getInt(item, "stat_" + key);
            if (value != 0) {
                totals.merge(key, (double) value, Double::sum);
            }
        }
    }

    private void applyToVanilla(Player player, Map<String, Double> stats) {
        // 1. 최대 체력 (기본 20 + 추가분)
        double maxHealth = stats.getOrDefault("max_health", 20.0);
        // 안전장치: 최소 1.0
        if (maxHealth < 1.0) maxHealth = 1.0;

        AttributeInstance healthAttr = player.getAttribute(Attribute.MAX_HEALTH);
        if (healthAttr != null && healthAttr.getBaseValue() != maxHealth) {
            healthAttr.setBaseValue(maxHealth);
            // 체력이 늘어났을 때 현재 체력 비율 조정은 선택 사항 (여기선 생략)
        }

        // 2. 방어력 (Armor)
        double def = stats.getOrDefault("def", 0.0);
        AttributeInstance armorAttr = player.getAttribute(Attribute.ARMOR);
        if (armorAttr != null) {
            armorAttr.setBaseValue(def);
        }

        // 3. 이동 속도 (Walk Speed)
        // 기본 0.2, 스탯은 %단위 (예: 10 = 10% 증가)
        double speedPercent = stats.getOrDefault("move_speed", 0.0);
        float defaultSpeed = 0.2f;
        float finalSpeed = (float) (defaultSpeed * (1.0 + (speedPercent / 100.0)));

        // 너무 빠르거나 느리지 않게 제한 (0.0 ~ 1.0)
        player.setWalkSpeed(Math.min(1.0f, Math.max(0.0f, finalSpeed)));

        // 4. 공격력 (Attack Damage)
        // 우리는 자체 대미지 공식을 쓰지만, 바닐라 공격력을 1.0으로 고정하여 맨손 대미지 뻥튀기 방지
        AttributeInstance attackAttr = player.getAttribute(Attribute.ATTACK_DAMAGE);
        if (attackAttr != null) {
            attackAttr.setBaseValue(1.0);
        }
    }

    public double getStat(Player player, String key) {
        if (!totalStats.containsKey(player.getUniqueId())) return 0.0;
        return totalStats.get(player.getUniqueId()).getOrDefault(key, 0.0);
    }
}