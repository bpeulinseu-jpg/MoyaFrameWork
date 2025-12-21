package com.server.tower.game.perk;

import com.server.core.api.CoreProvider;
import com.server.core.api.builder.ItemBuilder;
import com.server.core.api.event.SessionLevelUpEvent;
import com.server.tower.TowerPlugin;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PerkListener implements Listener {

    private final TowerPlugin plugin;
    // 플레이어별 남은 선택 횟수 저장
    private final Map<UUID, Integer> pendingSelections = new HashMap<>();
    // 선택 완료 후 실행 할 행동 저장소
    private final Map<UUID, Runnable> pendingCallbacks = new HashMap<>();
    // 현재 보여주고 있는 퍽 목록 캐시 (선택하기 전까지 유지)
    private final Map<UUID, List<Perk>> cachedPerks = new HashMap<>();

    public PerkListener(TowerPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onLevelUp(SessionLevelUpEvent event) {
        Player player = event.getPlayer();
        int newLevel = event.getNewLevel();
        int gained = event.getLevelsGained();

        // 대기열에 추가 (기존에 남은 게 있으면 더함)
        pendingSelections.merge(player.getUniqueId(), gained, Integer::sum);

        // 레벨업 축하 메시지
        player.sendMessage("§b§l[Level Up!] §f레벨이 올랐습니다! (남은 선택: " + pendingSelections.get(player.getUniqueId()) + "회)");
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 2f);
    }

    // 웨이브 종료 시 호출될 퍽 메소드
    public void startPerkPhase(Player player, Runnable onComplete) {
        int remaining = pendingSelections.getOrDefault(player.getUniqueId(), 0);

        // 선택할 게 없으면 바로 완료 처리
        if (remaining <= 0) {
            onComplete.run();
            return;
        }

        // 콜백 저장 (창이 닫혀도 기억하기 위해)
        pendingCallbacks.put(player.getUniqueId(), onComplete);
        player.sendMessage("§e[Perk] 레벨업 보너스를 선택하세요!");

        // 시작 시 캐시 초기화
        cachedPerks.remove(player.getUniqueId());

        openPerkSelection(player);
    }

    private void openPerkSelection(Player player) {
        int remaining = pendingSelections.getOrDefault(player.getUniqueId(), 0);

        // 플레이어가 던전에 없으면 종료
        if (!com.server.core.api.CoreProvider.isInSession(player)) {
            pendingSelections.remove(player.getUniqueId());
            pendingCallbacks.remove(player.getUniqueId());
            return;
        }

        // 남은 게 없으면 콜백 실행 후 종료
        if (remaining <= 0) {
            finishSelection(player);
            return;
        }

        Inventory gui = CoreProvider.openGui(player, "infinity_tower:menu_bg", "능력 선택 (남은 횟수: " + remaining + ")", -16, 3, true);

        // 캐시 된 퍽이 있는 지 확인
        List<Perk> choices;
        if (cachedPerks.containsKey(player.getUniqueId())) {
            // 이미 뽑아둔 게 있으면 그거 사용 (슬롯머신 방지)
            choices = cachedPerks.get(player.getUniqueId());
        } else {
            // 없으면 새로 뽑고 저장
            choices = plugin.getPerkRegistry().getRandomPerks(3);
            cachedPerks.put(player.getUniqueId(), choices);
        }

        int[] slots = {11, 13, 15};

        for (int i = 0; i < choices.size(); i++) {
            Perk perk = choices.get(i);
            int slot = slots[i];

            ItemStack icon = new ItemBuilder(perk.icon())
                    .name(perk.name())
                    .lore(perk.description(), "", "§e[클릭하여 선택]")
                    .build();

            CoreProvider.setGuiButton(gui, slot, icon, (e) -> {
                //효과 적용
                perk.effect().accept(player);
                plugin.getUserManager().updateSidebar(player);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 2f);
                // 선택 후 캐시 삭제
                cachedPerks.remove(player.getUniqueId());
                // 횟수 차감
                int current = pendingSelections.getOrDefault(player.getUniqueId(), 0);
                if (current > 1) {
                    pendingSelections.put(player.getUniqueId(), current - 1);
                    // 다음 선택창 열기 (1틱)
                    plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                        openPerkSelection(player);
                    }, 1L);
                } else {
                    // 끝났으면 대기열 지우고 창 닫고, 다음 웨이브 진행
                    pendingSelections.remove(player.getUniqueId());
                    player.closeInventory();
                    player.sendMessage("§a모든 보너스 선택 완료! 다음 웨이브를 준비하세요.");

                    // [핵심] 콜백 실행 (다음 웨이브 시작)
                    finishSelection(player);
                }
            });
        }
    }

    // 선택 완료 처리
    private void finishSelection(Player player) {
        // 끝났으면 다시 캐시 삭제
        cachedPerks.remove(player.getUniqueId());

        Runnable callback = pendingCallbacks.remove(player.getUniqueId());
        if (callback != null) {
            player.sendMessage("§a모든 보너스 선택 완료! 다음 웨이브를 준비하세요.");
            callback.run(); // 다음 웨이브 시작
        }
    }

    //  GUI 닫기 감지 (ESC/E 키 방지)
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!event.getView().getTitle().contains("능력 선택")) return;
        if (!(event.getPlayer() instanceof Player player)) return;

        // 아직 선택할 횟수가 남았는데 창이 닫혔다면?
        if (pendingSelections.containsKey(player.getUniqueId())) {
            int remaining = pendingSelections.get(player.getUniqueId());

            if (remaining > 0) {
                // 강제로 다시 열기 (5틱 뒤)
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    // 채팅창이 도배되는 현상으로 인해 주석처리
                    //player.sendMessage("§c보너스를 모두 선택해야 다음으로 넘어갈 수 있습니다!");
                    openPerkSelection(player);
                }, 5L);
            }
        }
    }

    // 게임 종료 시 초기화
    public void clearPending(Player player) {
        pendingSelections.remove(player.getUniqueId());
        pendingCallbacks.remove(player.getUniqueId());
        cachedPerks.remove(player.getUniqueId());
    }
}