package com.server.core.system.session;

import com.server.core.CorePlugin;
import com.server.core.api.CoreProvider;
import com.server.core.api.event.SessionLevelUpEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerLevelChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class SessionManager implements Listener {

    private final CorePlugin plugin;
    // 세션에 참여 중인 플레이어 목록
    private final Set<UUID> activeSessions = new HashSet<>();

    public SessionManager(CorePlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    // --- API ---

    public void startSession(Player player) {
        if (activeSessions.contains(player.getUniqueId())) return;

        // 1. 상태 등록
        activeSessions.add(player.getUniqueId());

        // 2. 초기화 (레벨 0, 경험치 0)
        player.setLevel(0);
        player.setExp(0);

        // 3. 기존 임시 스탯 제거 (StatManager 연동)
        CorePlugin.getStatManager().clearSessionStats(player);

        player.sendMessage("§a[Session] 던전에 입장했습니다. 레벨이 초기화됩니다.");
    }

    public void endSession(Player player) {
        if (!activeSessions.contains(player.getUniqueId())) return;

        // 1. 상태 제거
        activeSessions.remove(player.getUniqueId());

        // 2. 스탯 초기화
        CorePlugin.getStatManager().clearSessionStats(player);

        // 3. 레벨 초기화 (또는 로비 레벨로 복구 - 여기선 그냥 0)
        player.setLevel(0);
        player.setExp(0);

        player.sendMessage("§e[Session] 던전 세션이 종료되었습니다.");
    }

    public boolean isInSession(Player player) {
        return activeSessions.contains(player.getUniqueId());
    }

    // --- Listeners ---

    // 1. 레벨업 감지 -> 커스텀 이벤트 발동
    @EventHandler
    public void onLevelChange(PlayerLevelChangeEvent event) {
        Player player = event.getPlayer();
        if (!isInSession(player)) return;

        int diff = event.getNewLevel() - event.getOldLevel();

        if (event.getNewLevel() > event.getOldLevel()) {
            // 레벨업 효과 (사운드 등)
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);


            // 레벨이 올랐을 때만 (내려갔을 땐 무시)
            if (diff > 0) {
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);

                // [수정] 오른 레벨 수(diff)를 같이 전달
                Bukkit.getPluginManager().callEvent(new SessionLevelUpEvent(player, event.getNewLevel(), diff));
            }
        }
    }

    // 2. 사망 시 세션 종료
    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        if (isInSession(event.getEntity())) {
            endSession(event.getEntity());
            event.getEntity().sendMessage("§c[Session] 사망하여 세션이 종료되었습니다.");
        }
    }

    // 3. 퇴장 시 세션 종료
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (isInSession(event.getPlayer())) {
            endSession(event.getPlayer());
        }
    }
}