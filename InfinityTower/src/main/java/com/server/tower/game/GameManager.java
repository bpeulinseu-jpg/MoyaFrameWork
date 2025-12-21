package com.server.tower.game;

import com.server.core.CorePlugin;
import com.server.core.api.CoreProvider;
import com.server.core.api.builder.MobBuilder;
import com.server.tower.TowerPlugin;
import com.server.tower.user.TowerUserData;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import com.server.tower.item.ItemGenerator;
import com.server.tower.item.ArmorGenerator;

import java.util.*;

public class GameManager {

    private final TowerPlugin plugin;

    // 플레이어별 게임 상태 관리 (UUID -> GameState)
    private final Map<UUID, GameState> activeGames = new HashMap<>();
    // 알림 타이머 관리용
    private final Map<UUID, BukkitTask> notificationTasks = new HashMap<>();
    // 웨이브 타이머
    private final Map<UUID, BukkitTask> waveTimers = new HashMap<>();
    private static final int WAVE_TIME_LIMIT = 180; // 3분 (초)

    // 아레나 중앙 위치 (임시로 0, 100, 0 사용. 나중에 명령어로 설정 가능하게 변경 추천)
    private Location arenaCenter;

    public static class GameState {
        int wave = 0;
        int remainingMobs = 0;
        List<UUID> spawnedMobs = new ArrayList<>(); // 소환된 몹 UUID 추적
    }

    public GameManager(TowerPlugin plugin) {
        this.plugin = plugin;
        loadArenaConfig();
    }
    // 아레나 위치 콘피그
    public void setArenaCenter(Location loc) {
        this.arenaCenter = loc;

        plugin.getConfig().set("arena.world", loc.getWorld().getName());
        plugin.getConfig().set("arena.x", loc.getX());
        plugin.getConfig().set("arena.y", loc.getY());
        plugin.getConfig().set("arena.z", loc.getZ());
        plugin.saveConfig();
    }

    // 아레나 위치 로드
    private void loadArenaConfig() {
        if (plugin.getConfig().contains("arena.world")) {
            String worldName = plugin.getConfig().getString("arena.world");
            double x = plugin.getConfig().getDouble("arena.x");
            double y = plugin.getConfig().getDouble("arena.y");
            double z = plugin.getConfig().getDouble("arena.z");

            this.arenaCenter = new Location(org.bukkit.Bukkit.getWorld(worldName), x, y, z);
        }
    }

    // 1. 게임 시작
    public void startGame(Player player) {
        if (activeGames.containsKey(player.getUniqueId())) return;

        // 혹시 모르니 데이터 정리
        activeGames.remove(player.getUniqueId());
        if (waveTimers.containsKey(player.getUniqueId())) {
            waveTimers.get(player.getUniqueId()).cancel();
            waveTimers.remove(player.getUniqueId());
        }

        // 아레나 위치 설정 안됐으면 플레이어 위치를 기준함
        if (arenaCenter == null) arenaCenter = player.getLocation();

        // Core 세션 시작 (레벨/스탯 초기화)
        CoreProvider.startSession(player);

        // 게임 상태 생성
        activeGames.put(player.getUniqueId(), new GameState());

        //아레나로 tp
        player.teleport(arenaCenter.clone().add(0, 1, 0));

        player.sendMessage("§a[Tower] 던전에 입장했습니다! 잠시 후 웨이브가 시작됩니다.");

        // 3초 후 첫 웨이브 시작
        new BukkitRunnable() {
            @Override
            public void run() {
                startNextWave(player);
            }
        }.runTaskLater(plugin, 60L);
    }

    // 2. 웨이브 시작
    public void startNextWave(Player player) {
        GameState state = activeGames.get(player.getUniqueId());
        if (state == null) return;

        // 기존 타이머 취소
        if (waveTimers.containsKey(player.getUniqueId())) {
            waveTimers.get(player.getUniqueId()).cancel();
        }

        state.wave++;

        // 기존에 남은 몬스터가 있다면 합산 (쌓임 방지 로직 필요)
        if (state.remainingMobs > 200) { // 50마리 이상 쌓이면 패배 처리
            player.sendMessage("§c몬스터가 너무 많이 쌓여 패배했습니다!");
            endGame(player);
            return;
        }

        // 타이틀 알림
        CoreProvider.sendTitle(player,
                Component.text("§eWave " + state.wave),
                Component.text("§7제한시간 3분!"),
                10, 40, 10, 10);
        player.playSound(player.getLocation(), Sound.EVENT_RAID_HORN, 1f, 1f);

        // 전체 몬스터 숫자 갱신
        updateBossBar(player, state, state.remainingMobs);

        // 3분 타이머 시작
        BukkitTask timer = new BukkitRunnable() {
            @Override
            public void run() {
                player.sendMessage("§c[Time Over] 시간이 초과되어 다음 웨이브가 강제 시작됩니다!");
                startNextWave(player);
            }
        }.runTaskLater(plugin, WAVE_TIME_LIMIT * 20L);

        waveTimers.put(player.getUniqueId(), timer);

        // 5웨이브 마다 보스출현
        boolean isBossWave = (state.wave % 5 == 0);

        if (isBossWave) {
            // --- 보스 웨이브 ---
            int mobCount = 1;
            state.remainingMobs = mobCount;

            CoreProvider.sendTitle(player,
                    Component.text("§4§lWARNING"),
                    Component.text("§c보스가 등장했습니다!"),
                    10, 60, 20, 100);

            player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1f, 0.5f);

            // 보스 소환
            spawnBoss(player, state, mobCount);

        } else {
            // 일반 웨이브
            // 난이도 설정 (웨이브마다 몬스터 수 증가)
            int mobCount = 3 + (state.wave * 2);
            state.remainingMobs = mobCount;

            // 타이틀 알림
            CoreProvider.sendTitle(player,
                    Component.text("§eWave " + state.wave),
                    Component.text("§7몬스터 " + mobCount + "마리가 등장합니다!"),
                    10, 40, 10, 10);

            player.playSound(player.getLocation(), Sound.EVENT_RAID_HORN, 1f, 1f);

            // 몬스터 소환 (약간의 텀을 두고 소환)
            spawnMobs(player, state, mobCount);
        }
        // 보스바 갱신
        updateBossBar(player, state, state.remainingMobs);
    }

    // 보스 소환 메소드
    private void spawnBoss(Player player, GameState state, int count) {
        Location center = (arenaCenter != null) ? arenaCenter : player.getLocation();

        for (int i = 0; i < count; i++) {

            // 반경 5 ~ 15칸 사이 랜덤
            double angle = Math.random() * Math.PI * 2;
            double distance = 5 + (Math.random() * 10);
            double x = center.getX() + (Math.cos(angle) * distance);
            double z = center.getZ() + (Math.sin(angle) * distance);
            double y = center.getWorld().getHighestBlockYAt((int) x, (int) z) + 1;

            Location spawnLoc = new Location(center.getWorld(), x, y, z);
            // MobBuilder를 사용해 강화된 보스 소환
            LivingEntity boss = MobBuilder.from("infinity_tower:orc_boss")
                    .level(state.wave) // 웨이브만큼 레벨 스케일링 (체력/공격력 증가)
                    .spawn(spawnLoc);

            state.spawnedMobs.add(boss.getUniqueId());

        }
    }

    private void spawnMobs(Player player, GameState state, int count) {
        Location center = (arenaCenter != null) ? arenaCenter : player.getLocation();

        for (int i = 0; i < count; i++) {
            // 반경 5 ~ 15칸 사이 랜덤
            double angle = Math.random() * Math.PI * 2;
            double distance = 5 + (Math.random() * 10);
            double x = center.getX() + (Math.cos(angle) * distance);
            double z = center.getZ() + (Math.sin(angle) * distance);
            double y = center.getWorld().getHighestBlockYAt((int)x, (int)z) + 1;

            Location spawnLoc = new Location(center.getWorld(), x, y, z);

            // [신규] 웨이브에 따른 능력치 증가 (MobBuilder.level 활용)
            LivingEntity mob = MobBuilder.from("infinity_tower:goblin")
                    .level(state.wave) // 웨이브만큼 레벨 스케일링 (체력/공격력 증가)
                    .spawn(spawnLoc);

            state.spawnedMobs.add(mob.getUniqueId());
        }
    }

    // 3. 몬스터 처치 처리
    public void onMobDeath(Player player, LivingEntity mob) {
        GameState state = activeGames.get(player.getUniqueId());
        if (state == null) return;

        // 내 게임의 몬스터인지 확인
        if (state.spawnedMobs.contains(mob.getUniqueId())) {
            state.spawnedMobs.remove(mob.getUniqueId());
            state.remainingMobs--;

            // 골드 지급 로직
            int goldReward = 10 + (int)(Math.random() * 10) + (state.wave * 5);

            TowerUserData data = plugin.getUserManager().getUser(player);
            if (data != null) {
                data.gold += goldReward;
                // 실시간으로 돈이 오르는 걸 보여주려면 여기서 스코어보드 갱신
                plugin.getUserManager().updateSidebar(player);
            }

            // 무기 드랍 로직
            if (Math.random() < 0.025) {
                // 아이템 레벨 = 현재 웨이브
                // 웨이브가 높을수록 기본 깡뎀이 높은 무기가 나옴
                ItemStack dropItem = com.server.tower.item.ItemGenerator.generateWeapon(state.wave);

                mob.getWorld().dropItemNaturally(mob.getLocation(), dropItem);

                player.sendMessage("§6✨ 희귀한 장비가 떨어졌습니다!");
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 1.5f);
            }
            // 방어구 드랍
            if (Math.random() < 0.025) {
                ItemStack armor = ArmorGenerator.generateArmor(state.wave);
                mob.getWorld().dropItemNaturally(mob.getLocation(), armor);
                player.sendMessage("§6✨ 희귀한 장비가 떨어졌습니다!");
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 1.2f);
            }

            // 액션바 알람을 레이어로 전환
            showGoldNotification(player, goldReward);

            // 보스바 갱신
            updateBossBar(player, state, state.remainingMobs + 1);

            // 웨이브 클리어 체크
            if (state.remainingMobs <= 0) {
                // 타이머 취소 (클리어했으므로)
                if (waveTimers.containsKey(player.getUniqueId())) {
                    waveTimers.get(player.getUniqueId()).cancel();
                }

                player.sendMessage("§a웨이브 " + state.wave + " 클리어!");
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 2f);

                // 퍽 선택 페이즈
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        // PerkListener에게 위임: "선택 다 끝나면 startNextWave 실행해줘"
                        plugin.getPerkListener().startPerkPhase(player, () -> {
                            // 퍽 선택이 다 끝나면 실행될 코드 (다음 웨이브)
                            new BukkitRunnable() {
                                @Override
                                public void run() {
                                    startNextWave(player);
                                }
                            }.runTaskLater(plugin, 40L); // 선택 후 2초 뒤 시작
                        });
                    }
                }.runTaskLater(plugin, 60L);
            }
        }
    }

    // 4. 게임 종료 (사망 또는 나가기)
    public void endGame(Player player) {
        if (activeGames.containsKey(player.getUniqueId())) {
            GameState state = activeGames.get(player.getUniqueId());

            // 1. 몬스터 제거
            for (UUID mobId : state.spawnedMobs) {
                var entity = org.bukkit.Bukkit.getEntity(mobId);
                if (entity != null) entity.remove();
            }

            // 2. 데이터 정리
            activeGames.remove(player.getUniqueId());
            if (waveTimers.containsKey(player.getUniqueId())) {
                waveTimers.get(player.getUniqueId()).cancel();
                waveTimers.remove(player.getUniqueId());
            }

            // 3. Core 세션 종료 & UI 제거
            CoreProvider.endSession(player);
            CoreProvider.removeBossBar(player, "wave_info");
            CoreProvider.clearHud(player);

            // 4. 스코어보드 갱신
            if (player.isOnline()) plugin.getUserManager().updateSidebar(player);

            // [신규] 5. 로비(월드 스폰)로 텔레포트
            // 메인 월드("world")의 스폰 포인트로 이동
            org.bukkit.World mainWorld = org.bukkit.Bukkit.getWorlds().get(0);
            player.teleport(mainWorld.getSpawnLocation());

            player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
            player.sendMessage("§e[Tower] 로비로 귀환했습니다.");
        }
    }
    // 골드 알림 메소드
    private void showGoldNotification(Player player, int amount) {
        // 1. 기존 타이머가 있다면 취소 (연속 킬 시 시간 연장 효과)
        if (notificationTasks.containsKey(player.getUniqueId())) {
            notificationTasks.get(player.getUniqueId()).cancel();
        }

        // 2. HUD 레이어 등록 (우선순위 100: 가장 위에 덮어씀)
        CorePlugin.getHudManager().registerLayer(player, "gold_noti", 100, (p) -> {
            return Component.text("§e+" + amount + " G  ");
        });

        // 3. 2초 뒤에 레이어 삭제하는 타이머 예약
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                CorePlugin.getHudManager().removeLayer(player, "gold_noti");
                notificationTasks.remove(player.getUniqueId());
            }
        }.runTaskLater(plugin, 40L); // 2초 (40틱)

        notificationTasks.put(player.getUniqueId(), task);
    }


    private void updateBossBar(Player player, GameState state, int maxMobs) {
        // maxMobs가 0이면 1로 방어
        if (maxMobs <= 0) maxMobs = 1;
        float progress = (float) state.remainingMobs / maxMobs;
        if (progress > 1.0f) progress = 1.0f; // 몹이 쌓이면 1.0 넘을 수 있음

        CoreProvider.showBossBar(
                player,
                "wave_info",
                Component.text("§cWave " + state.wave + " §7- 남은 적: §f" + state.remainingMobs),
                progress,
                BossBar.Color.RED,
                BossBar.Overlay.NOTCHED_10
        );
    }

    public boolean isIngame(Player player) {
        return activeGames.containsKey(player.getUniqueId());
    }
}