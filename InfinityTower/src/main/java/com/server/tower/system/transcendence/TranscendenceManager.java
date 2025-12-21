package com.server.tower.system.transcendence;

import com.server.core.api.CoreProvider;
import com.server.core.api.builder.ItemBuilder;
import com.server.tower.TowerPlugin;
import com.server.tower.item.enums.ItemTier;
import com.server.tower.user.TowerUserData;
import net.kyori.adventure.text.Component;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class TranscendenceManager {

    private final TowerPlugin plugin;
    private static final int ETHER_COST = 50; // [설정] 초월 비용

    public TranscendenceManager(TowerPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 초월 시도 (100% 성공)
     */
    public void tryTranscend(Player player, ItemStack item) {
        // 1. 아이템 유효성 검사 (GUI에서도 하지만 이중 체크)
        if (!isTranscendable(item)) {
            player.sendMessage("§c초월 불가능한 아이템입니다. (전설 등급, 15강 필요)");
            return;
        }

        // 2. 에테르 확인 및 소모
        TowerUserData data = plugin.getUserManager().getUser(player);
        if (data.ether < ETHER_COST) {
            player.sendMessage("§c에테르가 부족합니다. (필요: " + ETHER_COST + "개)");
            return;
        }

        data.ether -= ETHER_COST; // 재화 차감
        plugin.getUserManager().saveUser(player); // 비동기 저장 포함됨
        plugin.getUserManager().updateSidebar(player); // 사이드바 갱신 (에테르 줄어든 것 표시)

        // 3. 아이템 변환 로직
        ItemStack newItem = processTranscendence(item);

        // 4. 결과 지급 및 알림
        player.getInventory().addItem(newItem);

        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        player.playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1f, 1f);

        CoreProvider.sendTitle(player,
                Component.text("§d§l초월 성공!"),
                Component.text("§7강력한 고유 능력이 부여되었습니다."),
                10, 60, 20, 10
        );

        player.sendMessage("§d[System] §f장비가 §d[고유] §f등급으로 승급했습니다!");
    }

    /**
     * 아이템 변환 프로세스 (스탯 상승 + 능력 부여)
     */
    private ItemStack processTranscendence(ItemStack item) {
        // 랜덤 능력 선정
        UniqueAbility ability = UniqueAbility.pickRandom();

        // 스탯 배율 계산 (전설 2.0 -> 고유 3.0 이므로 1.5배 상승)
        double multiplier = ItemTier.UNIQUE.getStatMultiplier() / ItemTier.LEGENDARY.getStatMultiplier();

        // 기존 수치 가져오기
        int oldAtk = CoreProvider.getItemDataInt(item, "stat_phys_atk");
        int oldMag = CoreProvider.getItemDataInt(item, "stat_mag_atk");
        int oldDef = CoreProvider.getItemDataInt(item, "stat_def");
        int oldHp = CoreProvider.getItemDataInt(item, "stat_max_health");

        // 새 수치 계산
        int newAtk = (int) (oldAtk * multiplier);
        int newMag = (int) (oldMag * multiplier);
        int newDef = (int) (oldDef * multiplier);
        int newHp = (int) (oldHp * multiplier);

        // 빌더로 아이템 수정
        ItemBuilder builder = ItemBuilder.from(CoreProvider.getCustomId(item)) // 기존 ID 유지
                // 1. 등급 데이터 변경
                .setData("tier", ItemTier.UNIQUE.name())
                .setData("unique_ability", ability.name()) // 능력 저장

                // 2. 스탯 업데이트
                .setData("stat_phys_atk", newAtk)
                .setData("stat_mag_atk", newMag)
                .setData("stat_def", newDef)
                .setData("stat_max_health", newHp)

                // 3. 이름 변경 (전설 -> 고유)
                .name(item.getItemMeta().getDisplayName().replace("전설", "고유"))

                // 4. 로어 업데이트 (능력 설명 추가)
                .addLore(" ")
                .addLore("§d§l[" + ability.getName() + "]")
                .addLore(ability.getDescription());

        // 5. 로어 내 스탯 수치 업데이트 (기존 로어 파싱해서 교체)
        ItemStack temp = builder.build();
        if (oldAtk > 0) CoreProvider.updateLore(temp, "공격력: " + oldAtk, "공격력: " + newAtk);
        if (oldMag > 0) CoreProvider.updateLore(temp, "마법 공격력: " + oldMag, "마법 공격력: " + newMag);
        if (oldDef > 0) CoreProvider.updateLore(temp, "방어력: " + oldDef, "방어력: " + newDef);

        // 아이템 완성
        return temp;
    }

    public boolean isTranscendable(ItemStack item) {
        if (item == null) return false;
        String tier = CoreProvider.getItemDataString(item, "tier");
        int enhance = CoreProvider.getItemDataInt(item, "enhance_level");

        return "LEGENDARY".equals(tier) && enhance >= 15;
    }

    public int getCost() { return ETHER_COST; }
}