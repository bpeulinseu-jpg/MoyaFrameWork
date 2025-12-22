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
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class TranscendenceManager {

    private final TowerPlugin plugin;
    private static final int ETHER_COST = 50; // [설정] 초월 비용

    public TranscendenceManager(TowerPlugin plugin) {
        this.plugin = plugin;
    }

    public void tryTranscend(Player player, ItemStack item) {
        // 1. 아이템 유효성 검사
        if (!isTranscendable(item)) {
            player.sendMessage("§c초월 불가능한 아이템입니다. (전설 등급, 15강 필요)");
            return;
        }

        // 2. 에테르 확인
        TowerUserData data = plugin.getUserManager().getUser(player);
        if (data == null) return;

        if (data.ether < ETHER_COST) {
            player.sendMessage("§c에테르가 부족합니다. (필요: " + ETHER_COST + "개)");
            return;
        }

        // 3. 재화 차감 및 저장
        data.ether -= ETHER_COST;
        plugin.getUserManager().saveUser(player);
        plugin.getUserManager().updateSidebar(player);

        // 4. 아이템 변환 로직
        ItemStack newItem = processTranscendence(item);

        // 5. 결과 지급
        // 기존 아이템을 제거하고 새 아이템 지급 (혹은 덮어쓰기)
        // [주의] GUI에서 호출된 경우, 보통 손에 든 아이템을 처리하므로 setItemInMainHand가 안전함
        player.getInventory().setItemInMainHand(newItem);

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
     * 아이템 변환 프로세스 (Lore 재구성 방식)
     */
    private ItemStack processTranscendence(ItemStack item) {
        // 1. 랜덤 능력 선정 및 스탯 배율 계산
        UniqueAbility ability = UniqueAbility.pickRandom();
        double multiplier = ItemTier.UNIQUE.getStatMultiplier() / ItemTier.LEGENDARY.getStatMultiplier(); // 1.5배

        // 2. 기존 스탯 데이터 가져오기
        int oldAtk = CoreProvider.getItemDataInt(item, "stat_phys_atk");
        int oldMag = CoreProvider.getItemDataInt(item, "stat_mag_atk");
        int oldDef = CoreProvider.getItemDataInt(item, "stat_def");
        int oldHp = CoreProvider.getItemDataInt(item, "stat_max_health");

        // 3. 새 스탯 계산
        int newAtk = (int) (oldAtk * multiplier);
        int newMag = (int) (oldMag * multiplier);
        int newDef = (int) (oldDef * multiplier);
        int newHp = (int) (oldHp * multiplier);

        // 4. Lore 재구성 (핵심 로직)
        ItemMeta meta = item.getItemMeta();
        List<String> oldLore = meta.getLore(); // 기존 Lore 가져오기 (Legacy String)
        if (oldLore == null) oldLore = new ArrayList<>();

        List<String> newLore = new ArrayList<>();

        for (String line : oldLore) {
            // 색상 코드를 무시하고 키워드만 확인하여 값 교체
            // ItemGenerator의 포맷을 정확히 따라야 함 (색상 코드 포함)

            if (line.contains("물리 공격력")) {
                newLore.add("§f물리 공격력: §c" + newAtk);
            }
            else if (line.contains("마법 공격력")) {
                newLore.add("§b마법 공격력: §c" + newMag);
            }
            else if (line.contains("방어력")) {
                newLore.add("§f방어력: §a" + newDef); // 방어력은 소수점 없이 정수로 표기한다고 가정
            }
            else if (line.contains("최대 체력")) {
                newLore.add("§f최대 체력: §c+" + newHp);
            }
            else if (line.contains("등급:")) {
                newLore.add("§f등급: " + ItemTier.UNIQUE.getPrefix());
            }
            else {
                // 그 외(옵션, 소켓, 구분선 등)는 그대로 유지
                newLore.add(line);
            }
        }

        // 5. 고유 능력 설명 추가 (맨 마지막 구분선 위나, 소켓 위에 넣으면 좋음)
        // 편의상 맨 뒤에 추가
        newLore.add("§7----------------");
        newLore.add("§d§l[" + ability.getName() + "]");
        newLore.add(ability.getDescription());

        // 6. ItemBuilder로 데이터 주입 및 Lore 적용
        ItemBuilder builder = new ItemBuilder(item)
                .name(meta.getDisplayName().replace("§e(+15) §6§l[전설]", ItemTier.UNIQUE.getPrefix())) // 이름 변경
                .lore(newLore.toArray(new String[0])) // [중요] 재구성한 Lore 적용

                // 데이터 업데이트 (NBT)
                .setData("tier", ItemTier.UNIQUE.name())
                .setData("unique_ability", ability.name())
                .setData("stat_phys_atk", newAtk)
                .setData("stat_mag_atk", newMag)
                .setData("stat_def", newDef)
                .setData("base_damage", newAtk)
                .setData("enhance_level", 0)
                .setData("stat_max_health", newHp);


        return builder.build();
    }

    public boolean isTranscendable(ItemStack item) {
        if (item == null) return false;
        String tier = CoreProvider.getItemDataString(item, "tier");
        int enhance = CoreProvider.getItemDataInt(item, "enhance_level");

        return "LEGENDARY".equals(tier) && enhance >= 15;
    }

    public int getCost() { return ETHER_COST; }
}