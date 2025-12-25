package com.server.core.system.world;

import com.server.core.CorePlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.entity.Player;
import org.bukkit.structure.Structure;
import org.bukkit.structure.StructureManager;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

public class MapManager {

    private final CorePlugin plugin;
    private final Map<String, Location> warpPoints = new HashMap<>();
    private final StructureManager structureManager;
    private final Random random = new Random();

    public MapManager(CorePlugin plugin) {
        this.plugin = plugin;
        this.structureManager = Bukkit.getStructureManager();
    }

    // --- 1. 좌표 관리 시스템 ---

    /**
     * 특정 ID에 좌표를 등록합니다. (예: "chapter_1_center")
     */
    public void registerWarp(String id, Location location) {
        warpPoints.put(id, location);
    }

    /**
     * 등록된 좌표를 가져옵니다.
     */
    public Location getWarp(String id) {
        return warpPoints.get(id);
    }

    /**
     * 플레이어를 해당 지점으로 텔레포트시킵니다.
     * (월드가 로드되지 않았으면 로드 시도)
     */
    public void teleport(Player player, String id) {
        Location loc = warpPoints.get(id);
        if (loc == null) {
            player.sendMessage("§c[Map] 이동 지점을 찾을 수 없습니다: " + id);
            return;
        }

        // 월드가 언로드 상태라면 로드
        if (loc.getWorld() == null) {
            // 월드 이름이 Location 객체 내부에 남아있을 경우 재로드 시도 가능
            // 하지만 보통 Location 객체는 World 참조를 들고 있으므로,
            // 별도로 월드 이름을 관리하거나 WorldCreator를 쓰는 것이 안전함.
            player.sendMessage("§c[Map] 해당 월드가 로드되지 않았습니다.");
            return;
        }

        player.teleport(loc);
    }

    // --- 2. 구조물(.nbt) 붙여넣기 시스템 ---

    /**
     * .nbt 구조물 파일을 특정 위치에 붙여넣습니다.
     * @param location 기준 좌표 (구조물의 원점)
     * @param fileName 파일 이름 (예: "arena_goblin" -> arena_goblin.nbt)
     * @param includeEntities 엔티티 포함 여부
     */
    public void pasteStructure(Location location, String fileName, boolean includeEntities) {
        // 비동기로 파일 로드 (렉 방지)
        CompletableFuture.runAsync(() -> {
            try {
                // 1. 파일 찾기 (plugins/Core/structures/arena_goblin.nbt)
                File file = new File(plugin.getDataFolder(), "structures/" + fileName + ".nbt");
                if (!file.exists()) {
                    plugin.getLogger().warning("구조물 파일이 없습니다: " + file.getAbsolutePath());
                    return;
                }
                Structure structure = structureManager.loadStructure(file);

                Bukkit.getScheduler().runTask(plugin, () -> {
                    // [수정] 요청하신 오프셋 (-23, 1, -23) 적용
                    // clone()을 사용하여 원본 좌표 객체에 영향을 주지 않도록 함
                    Location pasteLoc = location.clone().add(-23, 0, -23);

                    structure.place(
                            pasteLoc, // 수정된 좌표 사용
                            includeEntities,
                            StructureRotation.NONE,
                            Mirror.NONE,
                            0,
                            1.0f,
                            random
                    );
                    // plugin.getLogger().info("구조물 생성 완료: " + fileName);
                });

            } catch (IOException e) {
                plugin.getLogger().severe("구조물 로드 중 오류 발생: " + fileName);
                e.printStackTrace();
            }
        });
    }

    /**
     * 리소스(JAR)에 있는 구조물 파일을 밖으로 꺼냅니다.
     */
    public void saveDefaultStructure(String fileName) {
        String path = "structures/" + fileName + ".nbt";
        File file = new File(plugin.getDataFolder(), path);
        if (!file.exists()) {
            try {
                plugin.saveResource(path, false);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("내장 구조물이 없습니다: " + path);
            }
        }
    }
}