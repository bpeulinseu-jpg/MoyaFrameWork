package com.server.tower.ui;

import com.server.core.api.CoreProvider;
import com.server.core.api.builder.ItemBuilder;
import com.server.tower.TowerPlugin;
import com.server.tower.item.RepairManager;
import com.server.tower.user.TowerUserData;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class RepairUI implements Listener {

    private final TowerPlugin plugin;
    private final RepairManager repairManager;
    private final String GUI_TITLE = "대장간 (수리)";

    public RepairUI(TowerPlugin plugin) {
        this.plugin = plugin;
        this.repairManager = new RepairManager();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player) {
        Inventory gui = CoreProvider.openGui(player, "infinity_tower:menu_bg", GUI_TITLE, -16, 3, false);

        ItemStack filler = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < gui.getSize(); i++) {
            if (i != 13 && i != 22) gui.setItem(i, filler);
        }

        updateButton(gui, 0);
        player.openInventory(gui);
    }

    private void updateButton(Inventory gui, long cost) {
        ItemStack btn = new ItemBuilder(Material.GRINDSTONE)
                .name("§e[수리하기]")
                .lore("§7비용: §e" + cost + " G", "", "§e[클릭 시 수리]")
                .build();
        gui.setItem(22, btn);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().contains(GUI_TITLE)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        int slot = event.getRawSlot();
        Inventory inv = event.getInventory();

        if (slot < inv.getSize()) {
            if (slot == 13) {
                // 아이템을 넣거나 뺄 때 비용 갱신 (1틱 뒤 실행하여 아이템 반영)
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    ItemStack item = inv.getItem(13);
                    long cost = repairManager.calculateRepairCost(item);
                    updateButton(inv, cost);
                });
                return;
            }

            if (slot == 22) {
                event.setCancelled(true);
                processRepair(player, inv);
                return;
            }
            event.setCancelled(true);
        }
    }

    private void processRepair(Player player, Inventory inv) {
        ItemStack item = inv.getItem(13);
        if (item == null) return;

        TowerUserData user = plugin.getUserManager().getUser(player);
        ItemStack result = repairManager.repairItem(player, item, user);

        inv.setItem(13, result);
        updateButton(inv, 0); // 수리 후 비용 0
        plugin.getUserManager().updateSidebar(player); // 골드 갱신
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!event.getView().getTitle().contains(GUI_TITLE)) return;
        ItemStack item = event.getInventory().getItem(13);
        if (item != null) event.getPlayer().getInventory().addItem(item);
    }
}