package com.server.tower.ui;

import com.server.core.api.CoreProvider;
import com.server.tower.TowerPlugin;
import com.server.tower.user.TowerUserData;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;

public class TowerHud implements Listener {

    private final TowerPlugin plugin;

    // [설정] 위치 및 크기 (HudRegistry와 비율을 맞춰야 함)
    private static final int ORB_WIDTH = 40;  // 구슬의 가로 폭 (높이와 같게 설정)
    private static final int OFFSET_LEFT = 230; // 중앙에서 왼쪽으로 밀어낼 픽셀 수 (클수록 왼쪽)
    private static final int TEXT_OFFSET_X = -30; // 텍스트 위치 조정

    public TowerHud(TowerPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            registerTo(onlinePlayer);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        registerTo(event.getPlayer());
    }

    // [추가] 월드가 바뀌면 HUD가 사라질 수 있으므로 재등록
    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        registerTo(event.getPlayer());
    }

    public void registerTo(Player player) {
        CoreProvider.showHud(player, "health_orb", 20, this::renderHealthOrb);
    }



    private Component renderHealthOrb(Player player) {
        double maxHp = player.getAttribute(Attribute.MAX_HEALTH).getValue();
        double currentHp = player.getHealth();
        if (maxHp <= 0) maxHp = 100;

        // 체력 퍼센트 (0~10)
        double percent = (currentHp / maxHp) * 100.0;
        int stage = (int) Math.round(percent);
        if (stage < 0) stage = 0;
        if (stage > 100) stage = 100;

        // 태그 가져오기
        String bgTag = CoreProvider.getGlyphTag("infinity_tower:orb_bg");
        String fillTag = CoreProvider.getGlyphTag("infinity_tower:orb_fill_" + stage);
        String overlayTag = CoreProvider.getGlyphTag("infinity_tower:orb_overlay");

        // 태그 누락 시 공백 처리 (에러 방지)
        if (bgTag == null) bgTag = "";
        if (fillTag == null) fillTag = "";
        if (overlayTag == null) overlayTag = "";

        // [핵심] 음수 여백 생성
        // 구슬 크기만큼 정확히 뒤로 가야 겹쳐집니다.
        Component backSpace = CoreProvider.getSpace(-ORB_WIDTH);
        Component moveLeft = CoreProvider.getSpace(-OFFSET_LEFT);

        // [렌더링 순서]
        // 1. 왼쪽으로 이동
        // 2. 배경 그리기
        // 3. 뒤로 가기 (커서 원위치)
        // 4. 액체 그리기
        // 5. 뒤로 가기 (커서 원위치)
        // 6. 광택 그리기
        // 7. 텍스트 표시 (구슬 옆에)

        return Component.text()
                .append(moveLeft)                // 왼쪽 구석으로 이동
                .append(Component.text(bgTag))   // 배경
                .append(backSpace)               // ⏪
                .append(Component.text(fillTag)) // 액체
                .append(backSpace)               // ⏪
                .append(Component.text(overlayTag)) // 광택 (맨 위)
                .append(CoreProvider.getSpace(TEXT_OFFSET_X))
                .append(Component.text(String.format("§c%.0f/%.0f", currentHp, maxHp)))
                .build();
    }
}