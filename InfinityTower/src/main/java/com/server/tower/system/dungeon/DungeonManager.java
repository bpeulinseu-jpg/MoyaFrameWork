package com.server.tower.system.dungeon;

import com.server.core.api.CoreProvider;
import com.server.tower.TowerPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DungeonManager {

    private final TowerPlugin plugin;
    private World dungeonWorld;

    // 사용 중인 인스턴스 (Player UUID -> Instance)
    private final Map<UUID, DungeonInstance> activeInstances = new ConcurrentHashMap<>();

    // 사용 가능한 슬롯 대기열 (0 ~ 999)
    private final Queue<Integer> availableSlots = new LinkedList<>();

    // 그리드 설정
    private static final int GRID_OFFSET = 1000; // 슬롯 간 거리
    private static final int GRID_Y = 100;       // 고정 Y 높이

    public DungeonManager(TowerPlugin plugin) {
        this.plugin = plugin;
        initializeWorld();
        initializeSlots();
    }

    // 1. 던전 월드 로드 (없으면 생성)
    private void initializeWorld() {
        String worldName = "dungeon_world";
        dungeonWorld = Bukkit.getWorld(worldName);
        if (dungeonWorld == null) {
            WorldCreator creator = new WorldCreator(worldName);
            creator.generator(new VoidChunkGenerator()); // 빈 월드 생성기 적용
            dungeonWorld = creator.createWorld();
            plugin.getLogger().info("🌑 던전 인스턴스 월드 생성 완료: " + worldName);
        }

        // 월드 설정 (밤 고정, 몬스터 자연 스폰 금지 등)
        dungeonWorld.setTime(18000); // 밤
        dungeonWorld.setGameRule(org.bukkit.GameRule.DO_DAYLIGHT_CYCLE, false);
        dungeonWorld.setGameRule(org.bukkit.GameRule.DO_MOB_SPAWNING, false);
    }

    // 2. 슬롯 초기화 (0 ~ 1000번 슬롯 생성)
    private void initializeSlots() {
        for (int i = 0; i < 1000; i++) {
            availableSlots.add(i);
        }
    }

    // 3. 인스턴스 할당 (입장)
    public DungeonInstance assignInstance(Player player) {
        if (availableSlots.isEmpty()) {
            player.sendMessage("§c[System] 현재 던전이 가득 찼습니다. 잠시 후 다시 시도해주세요.");
            return null;
        }

        int slotId = availableSlots.poll();

        // 좌표 계산 공식: (Slot % 100) * 1000, (Slot / 100) * 1000
        double x = (slotId % 100) * GRID_OFFSET;
        double z = (slotId / 100) * GRID_OFFSET;
        Location center = new Location(dungeonWorld, x + 0.5, GRID_Y, z + 0.5);

        DungeonInstance instance = new DungeonInstance(slotId, center, player);
        activeInstances.put(player.getUniqueId(), instance);

        return instance;
    }

    // 4. 맵 로드 (제자리 교체 방식)
    public void loadMap(DungeonInstance instance, int chapter, int floor) {
        // NBT 파일명 규칙: chapter_1_1f.nbt
        String fileName = "chapter_" + chapter + "_" + floor + "f";
        Location loc = instance.getCenter();

        // Core의 구조물 붙여넣기 기능 사용
        // (기존 맵을 지울 필요 없이 덮어쓰기 - Void 월드라 겹칠 일 없음)
        CoreProvider.pasteStructure(loc, fileName);
    }

    // 5. 인스턴스 해제 (퇴장)
    public void releaseInstance(Player player) {
        DungeonInstance instance = activeInstances.remove(player.getUniqueId());
        if (instance == null) return;

        // 청소 (몬스터, 아이템 제거)
        cleanUpArea(instance.getCenter());

        // 슬롯 반환
        availableSlots.add(instance.getSlotId());
    }

    // 해당 구역 엔티티 청소
    private void cleanUpArea(Location center) {
        // 반경 200블록 내의 엔티티 제거 (플레이어 제외)
        center.getWorld().getNearbyEntities(center, 200, 100, 200).forEach(entity -> {
            if (!(entity instanceof Player)) {
                entity.remove();
            }
        });
    }

    public DungeonInstance getInstance(Player player) {
        return activeInstances.get(player.getUniqueId());
    }
}