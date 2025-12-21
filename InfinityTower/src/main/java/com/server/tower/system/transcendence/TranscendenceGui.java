package com.server.tower.system.transcendence;

import com.server.core.api.CoreProvider;
import com.server.core.api.builder.ItemBuilder;
import com.server.tower.TowerPlugin;
import com.server.tower.user.TowerUserData;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class TranscendenceGui {

    private final TowerPlugin plugin;

    public TranscendenceGui(TowerPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        // 3줄짜리 GUI 생성 (Locked=false: 유저가 아이템을 올려야 함)
        // 하지만 CoreProvider.openGui는 기본적으로 Locked이므로,
        // 여기서는 직접 인벤토리를 생성하거나 Core의 기능을 응용해야 합니다.
        // 편의상 CoreProvider의 GUI 기능을 사용하되, 특정 슬롯만 잠금 해제하는 로직이 필요합니다.
        // CoreFramework 특성상, 보통 '등록' 버튼을 누르면 손에 든 아이템을 체크하는 방식을 많이 씁니다.
        // 여기서는 "손에 든 아이템"을 바로 초월하는 간편한 방식으로 구현하겠습니다.
        // (드래그 앤 드롭 방식은 CoreGuiHolder 커스텀이 필요하여 복잡해질 수 있음)

        ItemStack handItem = player.getInventory().getItemInMainHand();

        Inventory gui = CoreProvider.openGui(player, "infinity_tower:menu_bg", "장비 초월", -16, 3, true);

        // 중앙에 아이템 표시 (손에 든 것)
        if (handItem != null && handItem.getType() != Material.AIR) {
            CoreProvider.setGuiButton(gui, 13, handItem, e -> {
                // 클릭 시 아무 동작 안함 (보기용)
            });
        } else {
            CoreProvider.setGuiButton(gui, 13, new ItemBuilder(Material.BARRIER).name("§c장비를 손에 들고 열어주세요").build(), null);
        }

        // 진행 버튼
        updateTranscendButton(player, gui, handItem);
    }

    private void updateTranscendButton(Player player, Inventory gui, ItemStack item) {
        TranscendenceManager manager = plugin.getTranscendenceManager();
        boolean isValid = manager.isTranscendable(item);
        TowerUserData data = plugin.getUserManager().getUser(player);
        int cost = manager.getCost();
        boolean hasEther = data.ether >= cost;

        ItemBuilder btnBuilder;

        if (isValid) {
            if (hasEther) {
                btnBuilder = new ItemBuilder(Material.NETHER_STAR).name("§d§l[초월 시작]");
                btnBuilder.lore("§7에테르 §f" + cost + "§7개를 소모하여", "§7장비의 한계를 돌파합니다.", "", "§e클릭 시 즉시 진행됩니다!");
                btnBuilder.glow();
            } else {
                btnBuilder = new ItemBuilder(Material.RED_STAINED_GLASS_PANE).name("§c에테르 부족");
                btnBuilder.lore("§7보유: §c" + data.ether + " §7/ §f" + cost);
            }
        } else {
            btnBuilder = new ItemBuilder(Material.RED_STAINED_GLASS_PANE).name("§c조건 불충족");
            btnBuilder.lore("§7- 전설 등급 아이템", "§7- 강화 레벨 +15 이상");
        }

        CoreProvider.setGuiButton(gui, 15, btnBuilder.build(), event -> {
            if (isValid && hasEther) {
                player.closeInventory();
                // 손에 있는 아이템 삭제 (안전하게)
                player.getInventory().setItemInMainHand(null);
                // 초월 진행
                manager.tryTranscend(player, item);
            } else {
                player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 1f, 1f);
            }
        });
    }
}