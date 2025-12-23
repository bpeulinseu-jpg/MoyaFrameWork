package com.server.tower.game;

import com.server.core.api.CoreProvider;
import com.server.tower.TowerPlugin;
import com.server.tower.game.GameManager.GameState;
import com.server.tower.ui.RestAreaGUI;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;

public class RestAreaManager implements Listener {

    private final TowerPlugin plugin;
    private final RestAreaGUI restAreaGUI;

    public RestAreaManager(TowerPlugin plugin) {
        this.plugin = plugin;
        this.restAreaGUI = new RestAreaGUI(plugin);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /**
     * 휴게실 세팅 (NPC 소환, 가구 배치)
     */
    public void setupRestArea(Player player, GameState state) {
        Location center = player.getLocation(); // 아레나 중앙이라 가정

        // 1. 상인 NPC 소환 (왼쪽)
        spawnNPC(center.clone().add(3, 0, 3), EntityType.VILLAGER, "§6[상인] 떠돌이", state);

        // 2. 문지기 NPC 소환 (정면 - 다음 층 담당)
        spawnNPC(center.clone().add(0, 0, 5), EntityType.IRON_GOLEM, "§b[문지기] 수호자", state);

        player.sendMessage("§a[System] 휴게실이 구축되었습니다.");
    }

    // NPC 소환 헬퍼
    private void spawnNPC(Location loc, EntityType type, String name, GameState state) {
        LivingEntity npc = (LivingEntity) loc.getWorld().spawnEntity(loc, type);
        npc.setCustomName(name);
        npc.setCustomNameVisible(true);
        npc.setAI(false); // 움직이지 않음
        npc.setInvulnerable(true); // 무적
        npc.setRemoveWhenFarAway(false);

        // Villager라면 직업 설정
        if (npc instanceof Villager v) {
            v.setProfession(Villager.Profession.WEAPONSMITH);
        }

        // [중요] GameState에 등록하여 층 이동 시 자동 삭제되게 함
        state.spawnedMobs.add(npc.getUniqueId());
    }

    // --- 상호작용 이벤트 ---
    @EventHandler
    public void onInteractNPC(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof LivingEntity entity)) return;

        // 이름으로 NPC 구분 (간단한 방식)
        Component nameComponent = entity.customName();
        if (nameComponent == null) return;

        // Component -> String 변환이 필요하지만, 여기선 간단히 텍스트 포함 여부로 체크
        // (실제로는 CoreProvider.getPlainText() 같은 유틸을 쓰는 게 좋음)
        String name = entity.getCustomName();
        if (name == null) return;

        Player player = event.getPlayer();

        if (name.contains("[상인]")) {
            event.setCancelled(true);
            plugin.getShopManager().openStatShop(player); // 상점 열기
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_TRADE, 1f, 1f);
        }
        else if (name.contains("[문지기]")) {
            event.setCancelled(true);
            restAreaGUI.open(player); // 선택지 GUI 열기
            player.playSound(player.getLocation(), Sound.ENTITY_IRON_GOLEM_ATTACK, 0.5f, 0.5f);
        }
    }
}