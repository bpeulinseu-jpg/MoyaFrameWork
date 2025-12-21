package com.server.core.system.browser;

import com.server.core.CorePlugin;
import com.server.core.api.builder.ItemBuilder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class GlyphBrowser implements Listener {

    private final CorePlugin plugin;
    private final Map<UUID, Integer> playerPages = new HashMap<>();
    private static final int ITEMS_PER_PAGE = 45;

    public GlyphBrowser(CorePlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player, int page) {
        Map<String, Character> glyphs = plugin.getGlyphManager().getAllGlyphs();
        List<String> keys = new ArrayList<>(glyphs.keySet());
        Collections.sort(keys); // 이름순 정렬

        if (page < 0) page = 0;
        int totalPages = (int) Math.ceil((double) keys.size() / ITEMS_PER_PAGE);
        if (page >= totalPages && totalPages > 0) page = totalPages - 1;
        playerPages.put(player.getUniqueId(), page);

        Inventory inv = Bukkit.createInventory(null, 54, Component.text("Glyph Browser (" + (page + 1) + "/" + (totalPages == 0 ? 1 : totalPages) + ")"));

        int startIndex = page * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, keys.size());

        for (int i = startIndex; i < endIndex; i++) {
            String key = keys.get(i);
            Character unicode = glyphs.get(key);

            // 아이템 이름에 유니코드를 넣어서 이미지를 보여줌
            // Lore에 태그를 표시
            ItemStack icon = new ItemBuilder(Material.NAME_TAG)
                    .name("§f" + unicode) // 이미지 표시 (흰색)
                    .lore("§eTag: §f:" + key + ":", "§7[Click] 태그 채팅창에 입력")
                    //.model(10000) // 혹시 모를 바닐라 종이와 구분 (선택사항)
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
        if (!event.getView().title().toString().contains("Glyph Browser")) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getCurrentItem() == null) return;

        int slot = event.getRawSlot();
        int page = playerPages.getOrDefault(player.getUniqueId(), 0);

        if (slot < ITEMS_PER_PAGE) {
            Map<String, Character> glyphs = plugin.getGlyphManager().getAllGlyphs();
            List<String> keys = new ArrayList<>(glyphs.keySet());
            Collections.sort(keys);

            int index = (page * ITEMS_PER_PAGE) + slot;
            if (index < keys.size()) {
                String key = keys.get(index);
                String tag = ":" + key + ":";

                // 클릭 시 채팅창에 태그 제안 (클릭하면 입력됨)
                Component message = Component.text("§a[Glyph] 클릭하여 태그 복사: ")
                        .append(Component.text("§e" + tag)
                                .clickEvent(ClickEvent.suggestCommand(tag))
                                .hoverEvent(Component.text("클릭하면 채팅창에 입력됩니다.")));

                player.sendMessage(message);
                player.closeInventory();
            }
        } else if (slot == 45) open(player, page - 1);
        else if (slot == 53) open(player, page + 1);
        else if (slot == 49) player.closeInventory();
    }
}