package com.server.tower.ui;

import com.server.core.api.CoreProvider;
import com.server.tower.TowerPlugin;

import java.io.File;

public class HudRegistry {

    private final TowerPlugin plugin;

    // [설정] 여기서 크기와 높이를 조절하세요
    // HEIGHT: 구슬 크기 (기존 16 -> 40으로 확대)
    // ASCENT: 수직 위치 (낮을수록 아래로 내려감. -15 정도면 핫바 라인에 걸침)
    private static final int ORB_HEIGHT = 40;
    private static final int ORB_ASCENT = -15;

    public HudRegistry(TowerPlugin plugin) {
        this.plugin = plugin;
    }

    public void registerAll() {
        // 1. 구슬 배경
        register("orb_bg", "hud/orb_bg.png", ORB_ASCENT, ORB_HEIGHT);

        // 2. 구슬 광택
        register("orb_overlay", "hud/orb_overlay.png", ORB_ASCENT, ORB_HEIGHT);

        // 3. 체력 액체 (0~10)
        for (int i = 0; i <= 100; i++) {
            register("orb_fill_" + i, "hud/fill/fill_" + i + ".png", ORB_ASCENT, ORB_HEIGHT);
        }

        plugin.getLogger().info("🩸 HUD 리소스 등록 완료 (크기: " + ORB_HEIGHT + ")");
    }

    private void register(String id, String path, int ascent, int height) {
        File file = new File(plugin.getDataFolder(), path);
        if (!file.exists()) {
            try {
                plugin.saveResource(path, false);
            } catch (IllegalArgumentException e) { return; }
        }
        if (file.exists()) {
            CoreProvider.registerGlyph(plugin, id, file, ascent, height);
        }
    }
}