package com.server.core.system.display;

import com.server.core.CorePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TitleManager {

    private final CorePlugin plugin;

    // 플레이어별 현재 표시 중인 타이틀의 우선순위와 만료 시간 저장
    private final Map<UUID, TitleSession> activeTitles = new ConcurrentHashMap<>();

    private record TitleSession(int priority, long endTime) {}

    public TitleManager(CorePlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 타이틀 전송
     * @param priority 높을수록 기존 타이틀을 덮어씀
     */
    public void sendTitle(Player player, Component title, Component subtitle, int fadeIn, int stay, int fadeOut, int priority) {
        long now = System.currentTimeMillis();
        long durationMs = (fadeIn + stay + fadeOut) * 50L; // Tick -> MS 변환
        long endTime = now + durationMs;

        // 우선순위 체크
        if (activeTitles.containsKey(player.getUniqueId())) {
            TitleSession current = activeTitles.get(player.getUniqueId());
            // 현재 재생 중인 타이틀이 있고, 아직 안 끝났으며, 새 요청의 우선순위가 더 낮으면 무시
            if (current.endTime > now && current.priority > priority) {
                return;
            }
        }

        // 타이틀 생성 및 전송
        Title.Times times = Title.Times.times(
                Duration.ofMillis(fadeIn * 50L),
                Duration.ofMillis(stay * 50L),
                Duration.ofMillis(fadeOut * 50L)
        );

        Title titleObj = Title.title(title, subtitle, times
        );

        player.showTitle(titleObj);

        // 세션 기록
        activeTitles.put(player.getUniqueId(), new TitleSession(priority, endTime));
    }

    public void clearTitle(Player player) {
        player.clearTitle();
        activeTitles.remove(player.getUniqueId());
    }
}