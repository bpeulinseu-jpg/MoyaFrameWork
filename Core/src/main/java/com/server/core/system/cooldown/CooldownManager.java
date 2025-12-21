package com.server.core.system.cooldown;

import com.server.core.CorePlugin;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CooldownManager {

    private final CorePlugin plugin;

    // UUID -> (CooldownKey -> EndTimestamp)
    private final Map<UUID, Map<String, Long>> cooldowns = new ConcurrentHashMap<>();

    public CooldownManager(CorePlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 쿨타임 설정
     * @param player 대상 플레이어
     * @param key 쿨타임 식별자 (예: "fireball", "heal")
     * @param ticks 틱 단위 시간 (20틱 = 1초)
     */
    public void setCooldown(Player player, String key, long ticks) {
        long endTime = System.currentTimeMillis() + (ticks * 50); // 1 Tick = 50ms
        cooldowns.computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>())
                .put(key, endTime);
    }

    /**
     * 쿨타임 중인지 확인
     */
    public boolean hasCooldown(Player player, String key) {
        if (!cooldowns.containsKey(player.getUniqueId())) return false;

        Long endTime = cooldowns.get(player.getUniqueId()).get(key);
        if (endTime == null) return false;

        if (System.currentTimeMillis() < endTime) {
            return true; // 아직 시간 안 됨
        } else {
            // 시간 지났으면 메모리 정리
            cooldowns.get(player.getUniqueId()).remove(key);
            return false;
        }
    }

    /**
     * 남은 시간 가져오기 (초 단위)
     * @return 남은 시간 (0.0이면 쿨타임 없음)
     */
    public double getRemainingSeconds(Player player, String key) {
        if (!cooldowns.containsKey(player.getUniqueId())) return 0.0;

        Long endTime = cooldowns.get(player.getUniqueId()).get(key);
        if (endTime == null) return 0.0;

        long left = endTime - System.currentTimeMillis();
        if (left <= 0) {
            cooldowns.get(player.getUniqueId()).remove(key);
            return 0.0;
        }

        return left / 1000.0;
    }

    // 플레이어 퇴장 시 정리
    public void clear(Player player) {
        cooldowns.remove(player.getUniqueId());
    }
}