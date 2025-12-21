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

import java.util.*;

public class SoundBrowser implements Listener {

    private final CorePlugin plugin;
    private final Map<UUID, Integer> playerPages = new HashMap<>();
    private static final int ITEMS_PER_PAGE = 45;

    public SoundBrowser(CorePlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player, int page) {
        List<String> sounds = plugin.getResourcePackManager().getAllSoundKeys();
        Collections.sort(sounds);

        if (page < 0) page = 0;
        int totalPages = (int) Math.ceil((double) sounds.size() / ITEMS_PER_PAGE);
        if (page >= totalPages && totalPages > 0) page = totalPages - 1;
        playerPages.put(player.getUniqueId(), page);

        Inventory inv = Bukkit.createInventory(null, 54, Component.text("Sound Browser (" + (page + 1) + "/" + (totalPages == 0 ? 1 : totalPages) + ")"));

        int startIndex = page * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, sounds.size());

        for (int i = startIndex; i < endIndex; i++) {
            String soundKey = sounds.get(i);

            // 소리 아이콘 (주크박스)
            ItemStack icon = new ItemBuilder(Material.JUKEBOX)
                    .name("§6🎵 " + soundKey)
                    .lore("§7[Click] 미리듣기", "§7[Shift+Click] 이름 복사")
                    .build();

            inv.setItem(i - startIndex, icon);
        }

        addNavigation(inv, page, totalPages);
        player.openInventory(inv);
    }

    private void addNavigation(Inventory inv, int page, int totalPages) {
        if (page > 0) inv.setItem(45, new ItemBuilder(Material.ARROW).name("§a< 이전").build());
        if (page < totalPages - 1) inv.setItem(53, new ItemBuilder(Material.ARROW).name("§a다음 >").build());
        inv.setItem(49, new ItemBuilder(Material.BARRIER).name("§c닫기").build());
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!event.getView().title().toString().contains("Sound Browser")) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getCurrentItem() == null) return;

        int slot = event.getRawSlot();
        int page = playerPages.getOrDefault(player.getUniqueId(), 0);

        if (slot < ITEMS_PER_PAGE) {
            List<String> sounds = plugin.getResourcePackManager().getAllSoundKeys();
            Collections.sort(sounds);

            int index = (page * ITEMS_PER_PAGE) + slot;
            if (index < sounds.size()) {
                String soundKey = sounds.get(index);

                if (event.isShiftClick()) {
                    player.sendMessage(Component.text("사운드 키 복사: " + soundKey, NamedTextColor.YELLOW)
                            .clickEvent(net.kyori.adventure.text.event.ClickEvent.copyToClipboard(soundKey)));
                } else {
                    // 소리 재생
                    player.stopSound(soundKey); // 기존 소리 끄고
                    player.playSound(player.getLocation(), soundKey, 1.0f, 1.0f); // 재생
                    player.sendMessage("§a🎵 재생 중: " + soundKey);
                }
            }
        } else if (slot == 45) open(player, page - 1);
        else if (slot == 53) open(player, page + 1);
        else if (slot == 49) player.closeInventory();
    }
}