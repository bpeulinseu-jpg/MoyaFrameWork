package com.server.tower.user;

import com.server.core.api.CoreProvider;
import com.server.tower.TowerPlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class UserManager {

    private final TowerPlugin plugin;
    private final Map<UUID, TowerUserData> userCache = new ConcurrentHashMap<>();

    public UserManager(TowerPlugin plugin) {
        this.plugin = plugin;
    }

    // 접속 시 데이터 로드
    public void loadUser(Player player) {
        String uuid = player.getUniqueId().toString();

        // Core DB에서 비동기 로드
        CoreProvider.loadDBAsync(uuid, "tower_data", TowerUserData.class).thenAccept(data -> {
            if (data == null) data = new TowerUserData(); // 없으면 새 데이터

            userCache.put(player.getUniqueId(), data);

            // [중요] 로드된 데이터를 Core StatManager에 적용 (메인 스레드)
            TowerUserData finalData = data;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                applyStatsToCore(player, finalData);
                updateSidebar(player);
                player.sendMessage("§a[Tower] 데이터 로드 완료. (STR: " + finalData.statStr + ")");
            }, 1L);
        });
    }

    // 명령어 용 저장 (캐시는 유지)
    public void saveUser(Player player) {
        TowerUserData data = userCache.get(player.getUniqueId());
        if (data != null) {
            CoreProvider.saveDB(player.getUniqueId().toString(), "tower_data", data);
        }
    }

    // 퇴장 시 저장하고 캐시 제거
    public void unloadUser(Player player) {
        saveUser(player);
        userCache.remove(player.getUniqueId());
    }

    public TowerUserData getUser(Player player) {
        return userCache.get(player.getUniqueId());
    }

    // Core StatManager에 영구 스탯 반영
    public void applyStatsToCore(Player player, TowerUserData data) {
        // 기초 스탯 등록 (스코어 보드 표시용)
        CoreProvider.setBaseStat(player, "str", data.statStr);
        CoreProvider.setBaseStat(player, "vit", data.statVit);
        CoreProvider.setBaseStat(player, "dex", data.statDex);
        CoreProvider.setBaseStat(player, "int", data.statInt);
        CoreProvider.setBaseStat(player, "luk", data.statLuk);
        // 2차 스탯으로 데이터 변환

        // 힘 str -> 물리 공격력 , 방어력
        CoreProvider.setBaseStat(player, "phys_atk", data.statStr * 1.0);
        CoreProvider.setBaseStat(player, "def", data.statStr * 0.1);
        // 활력 vit -> 최대 채력 , 재생량
        CoreProvider.setBaseStat(player, "max_health", 20 + (data.statVit * 1.0));
        CoreProvider.setBaseStat(player, "hp_regen", 3 + (data.statVit * 0.05));
        // 민첩 Dex -> 이동속도 , 회피확률
        CoreProvider.setBaseStat(player, "move_speed", data.statDex * 0.01); // % 단위로 저장 (나중에 /100)
        CoreProvider.setBaseStat(player, "dodge", data.statDex * 0.01);
        // 지능 int -> 마법 공격력, 쿨타임 감소
        CoreProvider.setBaseStat(player, "mag_atk", data.statInt * 1.0);
        CoreProvider.setBaseStat(player, "cdr", data.statInt * 0.25);
        // 행운 Luk -> 치명타 확률, 치명타 피해
        CoreProvider.setBaseStat(player, "crit_chance", 1.0 + (data.statLuk * 0.01));
        CoreProvider.setBaseStat(player, "crit_damage", 150.0 + (data.statLuk * 0.1));

        // 스탯 재계산 요청 (스탯 즉시 반영)
        CoreProvider.recalculateStats(player);
    }

    // 스코어보드 갱신
    public void updateSidebar(Player player) {
        TowerUserData data = getUser(player);
        if (data == null) return;

        // DB 데이터(재화)와 StatManager 데이터(전투력)를 섞어서 표시
        // CoreProvider.getStat()을 써야 장비/버프가 포함된 수치가 나옴
        double totalStr = CoreProvider.getStat(player, "str");
        double totalVit = CoreProvider.getStat(player, "vit");
        double totalDex = CoreProvider.getStat(player, "dex");
        double totalInt = CoreProvider.getStat(player, "int");
        double totalLuk = CoreProvider.getStat(player, "luk");

        List<Component> lines = Arrays.asList(
                Component.empty(),
                Component.text("§f보유 골드: §e" + data.gold + " G"),
                Component.text("§f에테르: §b" + data.ether),
                Component.empty(),
                Component.text("§7[ 전투력 ]"),
                Component.text("§c힘 (STR): " + String.format("%.0f", totalStr)),
                Component.text("§a활력 (VIT): " + String.format("%.0f", totalVit)),
                Component.text("§b민첩 (DEX): " + String.format("%.0f", totalDex)),
                Component.text("§d지능 (INT): " + String.format("%.0f", totalInt)),
                Component.text("§5운 (LUK): " + String.format("%.0f", totalLuk)),
                Component.empty(),
                Component.text("§7최고 기록: " + data.maxFloor + "층")
        );


        CoreProvider.setSidebar(player, "tower_lobby", Component.text("§6§l[ INFINITY TOWER ]"), lines, 10);
    }
}