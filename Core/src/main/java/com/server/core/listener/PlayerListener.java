package com.server.core.listener;

import com.server.core.CorePlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;



public class PlayerListener implements Listener {


    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // 리소스팩이 준비되었는지 확인  *해시값 체크
        byte[] hash = CorePlugin.getWebServerManager().getPackHash();
        if (hash == null) return;
        // url 파싱
        String url = CorePlugin.getWebServerManager().getDownloadUrl();
        // config에서 메시지와 강제 여부 파싱
        String prompt = CorePlugin.getInstance().getConfig().getString("resourcepack.prompt", "Default Message");
        boolean required = CorePlugin.getInstance().getConfig().getBoolean("resourcepack.required", true);
        // 리소스팩 전송
        event.getPlayer().setResourcePack(
                url,
                hash,
                Component.text(prompt),
                required
        );
    }


    // 메모리 누수 방지 용
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // 보스바 정리
        CorePlugin.getBossBarManager().clearAll(event.getPlayer());
        // 타이틀 데이터 정리
        CorePlugin.getTitleManager().clearTitle(event.getPlayer());
        // HUD 정리
        CorePlugin.getHudManager().clearLayers(event.getPlayer());
        // 사이드바 정리
        CorePlugin.getSidebarManager().clear(event.getPlayer());
        // 쿨다운 계산 정리
        CorePlugin.getCooldownManager().clear(event.getPlayer());
    }




}