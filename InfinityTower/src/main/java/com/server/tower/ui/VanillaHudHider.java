package com.server.tower.ui;

import com.server.core.CorePlugin;
import com.server.tower.TowerPlugin;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class VanillaHudHider {

    private final TowerPlugin plugin;

    // 지워야 할 바닐라 하트 경로 목록 (1.20.2+ 스프라이트 구조 기준)
    private static final String[] VANILLA_HEARTS = {
            // 기본 하트
            "gui/sprites/hud/heart/full.png",
            "gui/sprites/hud/heart/half.png",
            "gui/sprites/hud/heart/container.png",
            "gui/sprites/hud/heart/container_blinking.png",
            "gui/sprites/hud/heart/full_blinking.png",
            "gui/sprites/hud/heart/half_blinking.png",

            // 흡수 하트 (황금사과)
            "gui/sprites/hud/heart/absorbing_full.png",
            "gui/sprites/hud/heart/absorbing_half.png",
            "gui/sprites/hud/heart/absorbing_full_blinking.png",
            "gui/sprites/hud/heart/absorbing_half_blinking.png",

            // 독 (Poisoned)
            "gui/sprites/hud/heart/poisoned_full.png",
            "gui/sprites/hud/heart/poisoned_half.png",
            "gui/sprites/hud/heart/poisoned_full_blinking.png",
            "gui/sprites/hud/heart/poisoned_half_blinking.png",

            // 위더 (Withered)
            "gui/sprites/hud/heart/withered_full.png",
            "gui/sprites/hud/heart/withered_half.png",
            "gui/sprites/hud/heart/withered_full_blinking.png",
            "gui/sprites/hud/heart/withered_half_blinking.png",

            // 동결 (Frozen)
            "gui/sprites/hud/heart/frozen_full.png",
            "gui/sprites/hud/heart/frozen_half.png",
            "gui/sprites/hud/heart/frozen_full_blinking.png",
            "gui/sprites/hud/heart/frozen_half_blinking.png",

            // 하드코어 (필요시)
            "gui/sprites/hud/heart/hardcore_full.png",
            "gui/sprites/hud/heart/hardcore_half.png",
            "gui/sprites/hud/heart/hardcore_full_blinking.png",
            "gui/sprites/hud/heart/hardcore_half_blinking.png"
    };

    public VanillaHudHider(TowerPlugin plugin) {
        this.plugin = plugin;
    }

    public void hideHearts() {
        // 1. 투명 이미지 파일 생성 (1x1 px)
        File transparentFile = new File(plugin.getDataFolder(), "hud/transparent.png");
        createTransparentImage(transparentFile);

        if (!transparentFile.exists()) {
            plugin.getLogger().warning("투명 이미지 생성 실패로 인해 바닐라 하트를 숨길 수 없습니다.");
            return;
        }

        // 2. 모든 하트 경로에 투명 이미지 등록 (덮어쓰기)
        for (String path : VANILLA_HEARTS) {
            // "minecraft" 네임스페이스를 지정하여 바닐라 텍스처를 오버라이드
            CorePlugin.getResourcePackManager().registerTexture("minecraft", path, transparentFile);
        }

        plugin.getLogger().info("👻 바닐라 체력바 숨김 처리 완료");
    }

    private void createTransparentImage(File file) {
        if (file.exists()) return; // 이미 있으면 패스

        try {
            file.getParentFile().mkdirs();
            // 1x1 픽셀, ARGB (투명 지원)
            BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
            // 아무것도 안 그리면 기본이 투명(0,0,0,0)

            ImageIO.write(image, "png", file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}