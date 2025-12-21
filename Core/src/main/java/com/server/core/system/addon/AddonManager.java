package com.server.core.system.addon;

import com.server.core.CorePlugin;
import com.server.core.api.CoreAddon;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AddonManager {

    private final CorePlugin plugin;
    private final Map<String, AddonInfo> addons = new ConcurrentHashMap<>();

    // 애드온 상태 정의
    public enum AddonStatus {
        REGISTERED("§7대기중"), // 회색
        ACTIVE("§a정상"),     // 초록색
        ERROR("§c오류");      // 빨간색

        public final String display;
        AddonStatus(String display) { this.display = display; }
    }

    // 애드온 정보 래퍼 클래스
    public static class AddonInfo {
        public final CoreAddon addon;
        public AddonStatus status;

        public AddonInfo(CoreAddon addon) {
            this.addon = addon;
            this.status = AddonStatus.REGISTERED;
        }
    }

    public AddonManager(CorePlugin plugin) {
        this.plugin = plugin;
    }

    // 1. 등록
    public void register(CoreAddon addon) {
        if (addons.containsKey(addon.getNamespace())) {
            plugin.getLogger().warning("⚠️ 중복된 애드온 감지: " + addon.getNamespace());
            return;
        }
        addons.put(addon.getNamespace(), new AddonInfo(addon));
        plugin.getLogger().info("🔌 애드온 연결됨: " + addon.getNamespace());
    }

    // 2. 초기화 신호 (상태 업데이트 포함)
    public void notifyCoreReady() {
        plugin.getLogger().info("📢 애드온 초기화 시작...");

        for (AddonInfo info : addons.values()) {
            try {
                if (info.addon.getPlugin().isEnabled()) {
                    info.addon.onCoreReady();
                    info.status = AddonStatus.ACTIVE; // 성공 시 상태 변경
                }
            } catch (Exception e) {
                info.status = AddonStatus.ERROR; // 실패 시 상태 변경
                plugin.getLogger().severe("❌ 애드온 초기화 실패 [" + info.addon.getNamespace() + "]");
                e.printStackTrace();
            }
        }
    }

    // 3. 리로드 신호
    public void notifyReload() {
        for (AddonInfo info : addons.values()) {
            try {
                if (info.addon.getPlugin().isEnabled()) {
                    info.addon.onReload();
                    // 리로드 성공 시 에러 상태였다면 다시 정상으로 복구 시도
                    if (info.status == AddonStatus.ERROR) info.status = AddonStatus.ACTIVE;
                }
            } catch (Exception e) {
                info.status = AddonStatus.ERROR;
                plugin.getLogger().severe("❌ 애드온 리로드 실패 [" + info.addon.getNamespace() + "]");
                e.printStackTrace();
            }
        }
    }

    // 모니터링용 Getter
    public Collection<AddonInfo> getAddonList() {
        return addons.values();
    }
}