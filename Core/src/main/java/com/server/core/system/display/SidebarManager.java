package com.server.core.system.display;

import com.server.core.CorePlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SidebarManager {

    private final CorePlugin plugin;
    private final Map<UUID, SidebarSession> activeSidebars = new ConcurrentHashMap<>();

    // 스코어보드 세션 데이터
    private static class SidebarSession {
        final Scoreboard scoreboard;
        final Objective objective;
        int priority;
        String id;

        public SidebarSession(Scoreboard scoreboard, Objective objective, String id, int priority) {
            this.scoreboard = scoreboard;
            this.objective = objective;
            this.id = id;
            this.priority = priority;
        }
    }

    public SidebarManager(CorePlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 스코어보드 설정 (깜빡임 방지 적용)
     * @param player 대상 플레이어
     * @param id 사이드바 고유 ID (예: "rpg_stats")
     * @param title 제목 (이미지 포함 가능)
     * @param lines 내용 (최대 15줄)
     * @param priority 우선순위
     */
    public void setSidebar(Player player, String id, Component title, List<Component> lines, int priority) {
        SidebarSession session = activeSidebars.get(player.getUniqueId());

        // 1. 우선순위 체크 (기존 것이 있고, 새 요청이 우선순위가 낮으면 무시)
        if (session != null && !session.id.equals(id) && session.priority > priority) {
            return;
        }

        // 2. 세션이 없거나 ID가 다르면 새로 생성 (초기화)
        if (session == null || !session.id.equals(id)) {
            createScoreboard(player, id, title, priority);
            session = activeSidebars.get(player.getUniqueId());
        }

        // 3. 제목 업데이트
        session.objective.displayName(title);

        // 4. 줄 업데이트 (Team Prefix 방식)
        updateLines(session.scoreboard, lines);
    }

    /**
     * 스코어보드 제거
     */
    public void removeSidebar(Player player, String id) {
        SidebarSession session = activeSidebars.get(player.getUniqueId());
        if (session != null && session.id.equals(id)) {
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard()); // 초기화
            activeSidebars.remove(player.getUniqueId());
        }
    }

    public void clear(Player player) {
        activeSidebars.remove(player.getUniqueId());
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }

    // --- 내부 로직 ---

    private void createScoreboard(Player player, String id, Component title, int priority) {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        Scoreboard board = manager.getNewScoreboard();

        // Objective 생성
        Objective obj = board.registerNewObjective("sidebar", Criteria.DUMMY, title);
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        // 15개의 라인(Team) 미리 생성
        for (int i = 0; i < 15; i++) {
            Team team = board.registerNewTeam("line_" + i);
            // 각 라인마다 고유한 투명 엔트리 할당 (§0 ~ §e)
            String entry = ChatColor.values()[i].toString();
            team.addEntry(entry);

            // 점수 박제 (15점부터 1점까지 내림차순)
            obj.getScore(entry).setScore(15 - i);
        }

        player.setScoreboard(board);
        activeSidebars.put(player.getUniqueId(), new SidebarSession(board, obj, id, priority));
    }

    private void updateLines(Scoreboard board, List<Component> lines) {
        // 최대 15줄까지만 처리
        int count = Math.min(lines.size(), 15);

        for (int i = 0; i < 15; i++) {
            Team team = board.getTeam("line_" + i);
            if (team == null) continue;

            if (i < count) {
                // 내용이 있으면 Prefix 업데이트
                team.prefix(lines.get(i));
                // 점수 표시 (숨겨진 엔트리 활성화 효과)
                String entry = ChatColor.values()[i].toString();
                board.getObjective(DisplaySlot.SIDEBAR).getScore(entry).setScore(15 - i);
            } else {
                // 내용이 없으면 해당 라인 숨기기 (점수 제거가 아니라 빈값 처리)
                // 점수를 제거하면 깜빡일 수 있으므로, 그냥 빈 텍스트로 둠
                team.prefix(Component.empty());
                // 혹은 아예 안보이게 하려면 점수를 리셋해야 함 (선택사항)
                // 여기서는 빈 줄로 유지
            }
        }
    }
}