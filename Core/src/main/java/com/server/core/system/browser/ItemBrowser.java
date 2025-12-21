package com.server.core.system.browser;

import com.server.core.CorePlugin;
import com.server.core.api.builder.ItemBuilder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ItemBrowser implements Listener {

    private final CorePlugin plugin;
    private final Map<UUID, Integer> playerPages = new HashMap<>();
    private static final int ITEMS_PER_PAGE = 45;

    public ItemBrowser(CorePlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player, int page) {
        // [수정] 프리셋 맵 가져오기
        Map<String, ItemStack> presets = plugin.getItemManager().getAllPresets();
        List<String> keys = new ArrayList<>(presets.keySet());

        if (page < 0) page = 0;
        int totalPages = (int) Math.ceil((double) keys.size() / ITEMS_PER_PAGE);
        if (page >= totalPages && totalPages > 0) page = totalPages - 1;

        playerPages.put(player.getUniqueId(), page);

        Inventory inv = Bukkit.createInventory(null, 54, Component.text("Item Browser (" + (page + 1) + "/" + (totalPages == 0 ? 1 : totalPages) + ")"));

        int startIndex = page * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, keys.size());

        for (int i = startIndex; i < endIndex; i++) {
            String id = keys.get(i);
            ItemStack item = presets.get(id); // 프리셋 아이템 가져오기

            // [수정] 아이템에 설명 추가 (기존 속성 유지)
            ItemStack displayItem = new ItemBuilder(item)
                    .addLore("")
                    .addLore("§e[Click] 가져오기")
                    .addLore("§8ID: " + id)
                    .build();

            inv.setItem(i - startIndex, displayItem);
        }

        if (page > 0) inv.setItem(45, new ItemBuilder(Material.ARROW).name("§a< 이전").build());
        if (page < totalPages - 1) inv.setItem(53, new ItemBuilder(Material.ARROW).name("§a다음 >").build());
        inv.setItem(49, new ItemBuilder(Material.BARRIER).name("§c닫기").build());

        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!event.getView().title().toString().contains("Item Browser")) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getCurrentItem() == null) return;

        int slot = event.getRawSlot();
        int page = playerPages.getOrDefault(player.getUniqueId(), 0);

        if (slot < ITEMS_PER_PAGE) {
            Map<String, ItemStack> presets = plugin.getItemManager().getAllPresets();
            List<String> keys = new ArrayList<>(presets.keySet());
            int index = (page * ITEMS_PER_PAGE) + slot;

            if (index < keys.size()) {
                String id = keys.get(index);
                ItemStack item = presets.get(id); // 원본 아이템
                player.getInventory().addItem(item.clone()); // 복제해서 지급
                player.sendMessage(Component.text("아이템 지급: " + id, NamedTextColor.GREEN));
            }
        } else if (slot == 45) {
            open(player, page - 1);
        } else if (slot == 53) {
            open(player, page + 1);
        } else if (slot == 49) {
            player.closeInventory();
        }
    }
}