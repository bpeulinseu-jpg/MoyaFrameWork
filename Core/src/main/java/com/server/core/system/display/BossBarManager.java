package com.server.core.system.display;

import com.server.core.CorePlugin;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BossBarManager {

    private final CorePlugin plugin;

    // UUID -> (BarID -> BossBar)
    private final Map<UUID, Map<String, BossBar>> activeBars = new ConcurrentHashMap<>();

    public BossBarManager(CorePlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 보스바 생성 또는 업데이트
     */
    public void setBossBar(Player player, String id, Component title, float progress, BossBar.Color color, BossBar.Overlay style) {
        Map<String, BossBar> playerBars = activeBars.computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>());

        BossBar bar = playerBars.get(id);

        if (bar == null) {
            // 없으면 새로 생성
            bar = BossBar.bossBar(title, progress, color, style);
            playerBars.put(id, bar);
            player.showBossBar(bar);
        } else {
            // 있으면 속성 업데이트
            bar.name(title);
            bar.progress(Math.max(0.0f, Math.min(1.0f, progress)));
            bar.color(color);
            bar.overlay(style);
        }
    }

    /**
     * 특정 보스바 제거
     */
    public void removeBossBar(Player player, String id) {
        if (activeBars.containsKey(player.getUniqueId())) {
            Map<String, BossBar> playerBars = activeBars.get(player.getUniqueId());
            BossBar bar = playerBars.remove(id);
            if (bar != null) {
                player.hideBossBar(bar);
            }
        }
    }

    /**
     * 플레이어의 모든 보스바 제거 (퇴장 시 호출)
     */
    public void clearAll(Player player) {
        if (activeBars.containsKey(player.getUniqueId())) {
            Map<String, BossBar> playerBars = activeBars.get(player.getUniqueId());
            playerBars.values().forEach(player::hideBossBar);
            playerBars.clear();
            activeBars.remove(player.getUniqueId());
        }
    }
}