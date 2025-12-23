package com.server.tower.game;

import com.server.core.CorePlugin;
import com.server.core.api.CoreProvider;
import com.server.core.api.builder.MobBuilder;
import com.server.tower.TowerPlugin;
import com.server.tower.game.wave.FloorType;
import com.server.tower.game.wave.WaveData;
import com.server.tower.user.TowerUserData;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import com.server.tower.item.ArmorGenerator;

import java.util.*;

public class GameManager {

    private final TowerPlugin plugin;

    // 플레이어별 게임 상태 관리
    private final Map<UUID, GameState> activeGames = new HashMap<>();
    private final Map<UUID, BukkitTask> floorTimers = new HashMap<>(); // 타임어택용 타이머
    public List<UUID> spawnedGimmicks = new ArrayList<>(); // [추가] 기믹 관리용

    // 챕터별 아레나 중심 좌표 (Chapter 1, 2, 3...)
    // 실제로는 MapManager에 등록된 워프 포인트를 사용하는 것이 좋음
    private Location defaultArena;

    public static class GameState {
        public int chapter = 1;
        public int floor = 0; // 0부터 시작, 1층 진입 시 1이 됨
        public int remainingMobs = 0;
        public List<UUID> spawnedMobs = new ArrayList<>();
        public WaveData currentWaveData; // 현재 층의 정보
        public List<UUID> spawnedGimmicks = new ArrayList<>();
    }

    public GameManager(TowerPlugin plugin) {
        this.plugin = plugin;
        loadArenaConfig();
    }

    public void setArenaCenter(Location loc) {
        this.defaultArena = loc;
        plugin.getConfig().set("arena.world", loc.getWorld().getName());
        plugin.getConfig().set("arena.x", loc.getX());
        plugin.getConfig().set("arena.y", loc.getY());
        plugin.getConfig().set("arena.z", loc.getZ());
        plugin.saveConfig();

        // MapManager에도 등록 (챕터 1)
        CoreProvider.registerWarp("chapter_1_start", loc);
    }

    private void loadArenaConfig() {
        if (plugin.getConfig().contains("arena.world")) {
            String worldName = plugin.getConfig().getString("arena.world");
            double x = plugin.getConfig().getDouble("arena.x");
            double y = plugin.getConfig().getDouble("arena.y");
            double z = plugin.getConfig().getDouble("arena.z");
            this.defaultArena = new Location(Bukkit.getWorld(worldName), x, y, z);

            CoreProvider.registerWarp("chapter_1_start", this.defaultArena);
        }
    }

    // 1. 게임 시작
    public void startGame(Player player) {
        if (activeGames.containsKey(player.getUniqueId())) return;

        // 초기화
        activeGames.remove(player.getUniqueId());
        if (floorTimers.containsKey(player.getUniqueId())) {
            floorTimers.get(player.getUniqueId()).cancel();
            floorTimers.remove(player.getUniqueId());
        }

        if (defaultArena == null) defaultArena = player.getLocation();

        // 세션 시작
        CoreProvider.startSession(player);
        activeGames.put(player.getUniqueId(), new GameState());

        // 챕터 1 맵으로 이동
        // (나중에 챕터별 맵이 생기면 MapManager.teleport 사용)
        player.teleport(defaultArena.clone().add(0, 1, 0));

        player.sendMessage("§a[Tower] 탑에 입장했습니다. 1층부터 도전을 시작합니다!");
        player.playSound(player.getLocation(), Sound.BLOCK_PORTAL_TRAVEL, 1f, 1f);

        // 3초 후 1층 시작
        new BukkitRunnable() {
            @Override
            public void run() {
                startNextFloor(player);
            }
        }.runTaskLater(plugin, 60L);
    }

    // 2. 다음 층 진행 (핵심 로직)
    public void startNextFloor(Player player) {
        GameState state = activeGames.get(player.getUniqueId());
        if (state == null) return;

        // 기존 타이머/몹 정리
        cleanupFloor(player);

        state.floor++;

        // 챕터 변경 체크 (10층 클리어 후 11층 진입 시)
        if (state.floor > 10) {
            state.chapter++;
            state.floor = 1;
            // TODO: 맵 이동 및 챕터 변경 연출 (나중에 구현)
            player.sendMessage("§b[System] 챕터 " + state.chapter + " 진입!");
        }

        // WaveManager에서 해당 층 데이터 가져오기
        WaveData data = plugin.getWaveManager().getWaveData(state.floor);
        state.currentWaveData = data;

        // 층 타입에 따른 분기 처리
        if (data.getType() == FloorType.REST) {
            startRestFloor(player, state);
        } else {
            startCombatFloor(player, state, data);
        }
    }

    // 휴게실 (전투 없음)
    private void startRestFloor(Player player, GameState state) {
        player.sendTitle("§a[ 휴게실 ]", "§7정비 후 다음 층으로 이동하세요.", 10, 60, 20);
        player.playSound(player.getLocation(), Sound.MUSIC_DISC_CAT, 1f, 1f);

        // 보스바 갱신 (휴식 중)
        CoreProvider.showBossBar(player, "wave_info", Component.text("§a휴식 중... 정비하세요"), 1.0f, BossBar.Color.GREEN, BossBar.Overlay.PROGRESS);

        // [수정] RestAreaManager 호출 (NPC 소환 및 세팅)
        // state를 넘겨줘서 NPC UUID를 관리하게 함
        plugin.getRestAreaManager().setupRestArea(player, state);

        // 안내 메시지
        player.sendMessage("§e[Tip] 문지기에게 말을 걸어 다음 층으로 이동하거나,");
        player.sendMessage("§e      상인에게서 필요한 물품을 구매하세요.");
    }

    // 전투 스테이지 (일반, 타임어택, 보스)
    private void startCombatFloor(Player player, GameState state, WaveData data) {
        // 1. 몬스터 소환
        int totalMobs = 0;
        Location center = player.getLocation(); // 아레나 중앙 기준

        for (Map.Entry<String, Integer> entry : data.getMonsters().entrySet()) {
            String mobId = entry.getKey();
            int count = entry.getValue();

            // 챕터가 오를수록 몬스터 스펙 보정 (레벨 스케일링)
            int mobLevel = (state.chapter - 1) * 10 + state.floor;

            for (int i = 0; i < count; i++) {
                Location spawnLoc = getRandomLocation(center, 10);
                LivingEntity mob = MobBuilder.from(mobId)
                        .level(mobLevel)
                        .spawn(spawnLoc);
                state.spawnedMobs.add(mob.getUniqueId());
            }
            totalMobs += count;
        }
        state.remainingMobs = totalMobs;

        // 2. 기믹 소환 (있다면)
        if (data.getGimmickId() != null) {
            spawnGimmick(data.getGimmickId(), getRandomLocation(center, 5));
        }

        // 3. UI 및 알림
        String title = "§e" + state.floor + "층 시작!";
        String subTitle = "§7몬스터를 모두 처치하세요.";
        BossBar.Color barColor = BossBar.Color.RED;

        if (data.getType() == FloorType.TIME_ATTACK) {
            title = "§c§l타임 어택!";
            subTitle = "§7" + data.getTimeLimit() + "초 안에 돌파하세요!";
            barColor = BossBar.Color.PURPLE;

            // 제한시간 타이머 가동
            startTimeLimit(player, state, data.getTimeLimit());
        } else if (data.getType() == FloorType.BOSS) {
            title = "§4§lBOSS BATTLE";
            subTitle = "§c강력한 적이 나타났습니다!";
            barColor = BossBar.Color.RED;
            player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1f, 0.5f);
        }

        CoreProvider.sendTitle(player,
                Component.text(title),
                Component.text(subTitle),
                10, 40, 10, 10); // [수정] 마지막 인자(priority) 추가
        updateBossBar(player, state, totalMobs, barColor);
    }

    // 타임어택 타이머
    private void startTimeLimit(Player player, GameState state, int seconds) { // state 인자 추가
        final int totalTime = seconds;

        BukkitTask task = new BukkitRunnable() {
            int timeLeft = totalTime;

            @Override
            public void run() {
                if (timeLeft <= 0) {
                    player.sendMessage("§c[실패] 제한 시간이 초과되었습니다!");
                    endGame(player);
                    this.cancel();
                    return;
                }

                // 보스바 갱신
                float progress = (float) timeLeft / totalTime;
                CoreProvider.showBossBar(
                        player,
                        "wave_info", // 기존 웨이브 정보 덮어쓰기
                        Component.text("§c§l타임 어택! §f남은 시간: " + timeLeft + "초"),
                        progress,
                        BossBar.Color.PURPLE,
                        BossBar.Overlay.NOTCHED_10
                );

                timeLeft--;
            }
        }.runTaskTimer(plugin, 0L, 20L); // 1초마다 실행

        floorTimers.put(player.getUniqueId(), task);
    }

    // 3. 몬스터 처치 처리
    public void onMobDeath(Player player, LivingEntity mob) {
        GameState state = activeGames.get(player.getUniqueId());
        if (state == null) return;

        if (state.spawnedMobs.contains(mob.getUniqueId())) {
            state.spawnedMobs.remove(mob.getUniqueId());
            state.remainingMobs--;

            // 보상 지급 (골드/아이템) - 기존 로직 유지
            giveRewards(player, state, mob);

            // 보스바 갱신
            updateBossBar(player, state, state.remainingMobs + 1, BossBar.Color.RED);

            // 클리어 체크
            if (state.remainingMobs <= 0) {
                completeFloor(player, state);
            }
        }
    }

    private void completeFloor(Player player, GameState state) {
        // 타이머 취소
        if (floorTimers.containsKey(player.getUniqueId())) {
            floorTimers.get(player.getUniqueId()).cancel();
            floorTimers.remove(player.getUniqueId());
        }

        player.sendMessage("§a" + state.floor + "층 클리어!");
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 2f);

        // 퍽(Perk) 선택 -> 다음 층
        // (휴게실이나 보스방 직전에는 퍽을 안 줄 수도 있음 - 기획에 따라 조정)
        new BukkitRunnable() {
            @Override
            public void run() {
                plugin.getPerkListener().startPerkPhase(player, () -> {
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            startNextFloor(player);
                        }
                    }.runTaskLater(plugin, 40L);
                });
            }
        }.runTaskLater(plugin, 60L);
    }

    // 4. 게임 종료
    public void endGame(Player player) {
        if (activeGames.containsKey(player.getUniqueId())) {
            cleanupFloor(player);
            activeGames.remove(player.getUniqueId());

            CoreProvider.endSession(player);
            CoreProvider.removeBossBar(player, "wave_info");
            CoreProvider.clearHud(player);

            if (player.isOnline()) {
                plugin.getUserManager().updateSidebar(player);
                // 로비 귀환
                org.bukkit.World mainWorld = Bukkit.getWorlds().get(0);
                player.teleport(mainWorld.getSpawnLocation());
                player.sendMessage("§e[Tower] 로비로 귀환했습니다.");

                // HUD 복구
                if (plugin.getTowerHud() != null) plugin.getTowerHud().registerTo(player);
            }
        }
    }

    // 유틸리티: 층 정리 (몹 제거, 타이머 취소)
    private void cleanupFloor(Player player) {
        GameState state = activeGames.get(player.getUniqueId());
        if (state != null) {
            // 몬스터 삭제
            for (UUID mobId : state.spawnedMobs) {
                var entity = Bukkit.getEntity(mobId);
                if (entity != null) entity.remove();
            }
            state.spawnedMobs.clear();

            // [추가] 기믹 삭제
            for (UUID gimmickId : state.spawnedGimmicks) {
                // Core의 GimmickManager를 통해 삭제
                com.server.core.CorePlugin.getGimmickManager().removeGimmick(gimmickId);
            }
            state.spawnedGimmicks.clear();
        }
        if (floorTimers.containsKey(player.getUniqueId())) {
            floorTimers.get(player.getUniqueId()).cancel();
            floorTimers.remove(player.getUniqueId());
        }
    }

    private Location getRandomLocation(Location center, double radius) {
        double angle = Math.random() * Math.PI * 2;
        double dist = Math.random() * radius;
        double x = center.getX() + (Math.cos(angle) * dist);
        double z = center.getZ() + (Math.sin(angle) * dist);
        double y = center.getWorld().getHighestBlockYAt((int)x, (int)z) + 1;
        return new Location(center.getWorld(), x, y, z);
    }

    private void updateBossBar(Player player, GameState state, int maxMobs, BossBar.Color color) {
        if (maxMobs <= 0) maxMobs = 1;
        float progress = (float) state.remainingMobs / maxMobs;
        if (progress > 1.0f) progress = 1.0f;

        CoreProvider.showBossBar(
                player,
                "wave_info",
                Component.text("§c" + state.floor + "F §7- 남은 적: §f" + state.remainingMobs),
                progress,
                color,
                BossBar.Overlay.NOTCHED_10
        );
    }

    // 기존 보상 로직 분리
    private void giveRewards(Player player, GameState state, LivingEntity mob) {
        int goldReward = 10 + (int)(Math.random() * 10) + (state.floor * 2);
        TowerUserData data = plugin.getUserManager().getUser(player);
        if (data != null) {
            data.gold += goldReward;
            plugin.getUserManager().updateSidebar(player);
            showGoldNotification(player, goldReward);
        }

        // 아이템 드랍 (확률)
        if (Math.random() < 0.02) {
            ItemStack dropItem = com.server.tower.item.ItemGenerator.generateWeapon(state.floor * state.chapter);
            mob.getWorld().dropItemNaturally(mob.getLocation(), dropItem);
            player.sendMessage("§6✨ 장비 획득!");
        }
    }

    private void showGoldNotification(Player player, int amount) {
        player.sendMessage(Component.text("§e+" + amount + " G"));
    }

    // 기믹 소환 (임시)
    private void spawnGimmick(String gimmickId, Location center) {
        if (gimmickId == null) return;

        /// 기믹은 맵 중앙 근처 랜덤 위치에 소환
        Location loc = getRandomLocation(center, 8);

        // 바닥에 붙이기 (가장 높은 블록 위)
        loc.setY(loc.getWorld().getHighestBlockYAt(loc) + 1);

        // [수정] TowerGimmickManager에게 위임
        plugin.getGimmickManager().spawnGimmick(gimmickId, loc);

        switch (gimmickId) {
            case "BLOOD_ALTAR": // [1~3F] 피의 제단
                CoreProvider.spawnInteractableGimmick(loc, org.bukkit.Material.RED_NETHER_BRICK_SLAB, player -> {
                    // 우클릭 시: 체력 20% 소모 -> 공격력 50% 증가 (30초)
                    double cost = player.getMaxHealth() * 0.2;
                    if (player.getHealth() > cost) {
                        player.damage(cost);
                        player.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.STRENGTH, 600, 1)); // Strength II
                        player.sendMessage("§c[피의 제단] §7체력을 바쳐 §c강력한 힘§7을 얻었습니다!");
                        player.playSound(player.getLocation(), Sound.ENTITY_ILLUSIONER_PREPARE_MIRROR, 1f, 0.5f);

                        // 제단 사용 후 효과 (파티클 등) - 일회용이라면 여기서 제거 로직 필요
                    } else {
                        player.sendMessage("§c[!] 제물을 바치기엔 체력이 부족합니다.");
                    }
                });
                // 안내 메시지
                Bukkit.broadcast(Component.text("§c[!] 피의 제단이 나타났습니다. 우클릭하여 힘을 얻으세요."));
                break;

            case "CURSE_TOTEM": // [7~9F] 저주받은 토템
                // 토템이 살아있는 동안 몬스터 무적 (주기적 체크 필요하거나, 이벤트 리스너에서 처리)
                // 여기서는 간단하게: 토템 주변 몬스터에게 지속적인 회복/저항 버프 부여

                // 토템 생성 (체력 50)
                CoreProvider.spawnDestructibleGimmick(loc, org.bukkit.Material.CRYING_OBSIDIAN, 50, () -> {
                    // 파괴 시
                    Bukkit.broadcast(Component.text("§a[!] 저주받은 토템이 파괴되었습니다! 몬스터의 보호막이 사라집니다."));
                    // (심화 구현 시: GameManager에 토템 파괴 상태 저장 -> MobAbilityListener에서 무적 해제)
                });

                Bukkit.broadcast(Component.text("§5[!] 저주받은 토템이 몬스터들을 보호하고 있습니다! 먼저 파괴하세요!"));
                break;
        }
    }

    public boolean isIngame(Player player) {
        return activeGames.containsKey(player.getUniqueId());
    }

    // 수동 다음 층 이동 (휴게실용)
    public void forceNextFloor(Player player) {
        if (isIngame(player)) {
            startNextFloor(player);
        }
    }

    // [추가] 외부에서(디버그 명령어 등) 게임 상태를 가져오기 위한 Getter
    public GameState getGameState(Player player) {
        return activeGames.get(player.getUniqueId());
    }
}