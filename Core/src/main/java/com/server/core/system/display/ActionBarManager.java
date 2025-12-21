package com.server.core.system.display;

import com.server.core.CorePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class ActionBarManager {

    private final CorePlugin plugin;

    // 플레이어별 레이어 저장소 (UUID -> (LayerID -> LayerData))
    private final Map<UUID, Map<String, HudLayer>> activeLayers = new ConcurrentHashMap<>();

    // 레이어 데이터 레코드
    public record HudLayer(
            String id,
            Function<Player, Component> provider,
            int priority // 높을수록 먼저(왼쪽) 표시됨
    ) {}

    public ActionBarManager(CorePlugin plugin) {
        this.plugin = plugin;
        startRenderLoop();
    }

    private void startRenderLoop() {
        // 0.1초(2틱)마다 갱신
        Bukkit.getScheduler().runTaskTimer(plugin, this::render, 0L, 2L);
    }

    private void render() {
        for (UUID uuid : activeLayers.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                activeLayers.remove(uuid);
                continue;
            }

            try {
                Map<String, HudLayer> layers = activeLayers.get(uuid);
                if (layers.isEmpty()) continue;

                // 1. 우선순위대로 정렬 (내림차순: 높은게 먼저)
                List<HudLayer> sortedLayers = layers.values().stream()
                        .sorted(Comparator.comparingInt(HudLayer::priority).reversed())
                        .toList();

                // 2. 컴포넌트 병합 (Join)
                TextComponent.Builder finalHud = Component.text();

                for (HudLayer layer : sortedLayers) {
                    try {
                        Component content = layer.provider().apply(player);
                        if (content != null && !content.equals(Component.empty())) {
                            finalHud.append(content);
                            // 레이어 간 간격 추가 (선택 사항: 필요하면 공백 문자 추가)
                            // finalHud.append(Component.text("  "));
                        }
                    } catch (Exception e) {
                        // 특정 레이어 에러 시 해당 레이어만 제거 (안전장치)
                        plugin.getLogger().warning("HUD 레이어 오류 [" + layer.id() + "]: " + e.getMessage());
                        layers.remove(layer.id());
                    }
                }

                // 3. 전송
                player.sendActionBar(finalHud.build());

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 레이어 등록 (HUD 켜기)
     * @param player 대상 플레이어
     * @param layerId 레이어 고유 ID (예: "rpg_status", "fishing_bar")
     * @param priority 우선순위 (높을수록 왼쪽)
     * @param provider 데이터 공급 함수
     */
    public void registerLayer(Player player, String layerId, int priority, Function<Player, Component> provider) {
        activeLayers.computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>())
                .put(layerId, new HudLayer(layerId, provider, priority));
    }

    /**
     * 레이어 제거 (HUD 끄기)
     */
    public void removeLayer(Player player, String layerId) {
        if (activeLayers.containsKey(player.getUniqueId())) {
            activeLayers.get(player.getUniqueId()).remove(layerId);
        }
    }

    /**
     * 특정 플레이어의 모든 HUD 제거
     */
    public void clearLayers(Player player) {
        if (activeLayers.containsKey(player.getUniqueId())) {
            activeLayers.get(player.getUniqueId()).clear();
            // 화면도 즉시 비워줌
            player.sendActionBar(Component.empty());
        }
    }

    // 디버깅용 정보 제공

    public List<String> getDebugInfo(Player player) {
        List<String> info = new ArrayList<>();
        if (!activeLayers.containsKey(player.getUniqueId())) {
            info.add("§7활성화된 HUD 레이어가 없습니다.");
            return info;
        }

        Map<String, HudLayer> layers = activeLayers.get(player.getUniqueId());
        info.add("§6=== [ Active HUD Layers ] ===");
        layers.values().stream()
                .sorted(Comparator.comparingInt(HudLayer::priority).reversed())
                .forEach(layer -> {
                    info.add("§eID: §f" + layer.id() + " §7| §ePriority: §b" + layer.priority());
                });
        return info;
    }

}
