package com.server.tower.ui;

import com.server.core.api.CoreProvider;
import com.server.core.api.builder.ItemBuilder;
import com.server.tower.TowerPlugin;
import com.server.tower.item.GemManager;
import com.server.tower.user.TowerUserData;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class SocketingUI implements Listener {

    private final TowerPlugin plugin;
    private final GemManager gemManager;
    private final String GUI_TITLE = "보석 장착소";

    public SocketingUI(TowerPlugin plugin) {
        this.plugin = plugin;
        this.gemManager = new GemManager();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player) {
        // locked=false로 열어서 아이템 이동 허용 (단, 리스너에서 슬롯 제한)
        Inventory gui = CoreProvider.openGui(player, "infinity_tower:menu_bg", GUI_TITLE, -16, 3, false);

        // 배경 꾸미기 (유리판) - 입력 슬롯 제외하고 막기
        ItemStack filler = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < gui.getSize(); i++) {
            if (i != 10 && i != 12 && i != 16) {
                gui.setItem(i, filler);
            }
        }

        // 버튼 배치
        updateButton(gui, player);
    }

    private void updateButton(Inventory gui, Player player) {
        ItemStack btn = new ItemBuilder(Material.ANVIL)
                .name("§a[장착하기]")
                .lore("§7비용: §e1000 G", "", "§e[클릭 시 장착]")
                .build();
        gui.setItem(16, btn);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().contains(GUI_TITLE)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        int slot = event.getRawSlot();
        Inventory inv = event.getInventory();

        // 1. 상단 인벤토리 클릭 시
        if (slot < inv.getSize()) {
            // 입력 슬롯(10, 12)은 허용
            if (slot == 10 || slot == 12) {
                return; // 기본 동작 허용 (아이템 넣기/빼기)
            }

            // 제작 버튼(16)
            if (slot == 16) {
                event.setCancelled(true);
                processSocketing(player, inv);
                return;
            }

            // 나머지는 차단 (유리판 등)
            event.setCancelled(true);
        }
        // 2. 하단(내 인벤) 클릭은 허용 (아이템을 올려야 하니까)
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!event.getView().getTitle().contains(GUI_TITLE)) return;

        // 창 닫을 때 아이템 돌려주기
        Inventory inv = event.getInventory();
        returnItem(event.getPlayer(), inv, 10);
        returnItem(event.getPlayer(), inv, 12);
    }

    private void returnItem(org.bukkit.entity.HumanEntity player, Inventory inv, int slot) {
        ItemStack item = inv.getItem(slot);
        if (item != null && item.getType() != Material.AIR) {
            player.getInventory().addItem(item);
        }
    }

    private void processSocketing(Player player, Inventory inv) {
        ItemStack weapon = inv.getItem(10);
        ItemStack gem = inv.getItem(12);

        // 1. 아이템 확인
        if (weapon == null || gem == null) {
            player.sendMessage("§c무기와 보석을 모두 넣어주세요.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        // 2. 비용 확인
        TowerUserData user = plugin.getUserManager().getUser(player);
        if (user.gold < 1000) {
            player.sendMessage("§c골드가 부족합니다. (필요: 1000 G)");
            return;
        }

        // 3. 장착 시도
        ItemStack result = gemManager.attachGem(player, weapon, gem);
        if (result != null) {
            // 성공
            user.gold -= 1000;
            plugin.getUserManager().updateSidebar(player); // 돈 갱신

            inv.setItem(10, result); // 결과물로 교체
            inv.setItem(12, null);   // 보석 제거

            player.sendMessage("§a장착 완료!");
        }
    }
}