package com.server.core.system.browser;

import com.server.core.CorePlugin;
import com.server.core.api.CoreProvider;
import com.server.core.api.builder.ItemBuilder;
import com.server.core.system.mob.MobManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class MobBrowser implements Listener {

    private final CorePlugin plugin;
    private final Map<UUID, Integer> playerPages = new HashMap<>();
    private static final int ITEMS_PER_PAGE = 45;

    public MobBrowser(CorePlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player, int page) {
        List<MobManager.CustomMobData> mobs = plugin.getMobManager().getAllMobs();
        // ID 순으로 정렬
        mobs.sort(Comparator.comparing(m -> m.uniqueName));

        if (page < 0) page = 0;
        int totalPages = (int) Math.ceil((double) mobs.size() / ITEMS_PER_PAGE);
        if (page >= totalPages && totalPages > 0) page = totalPages - 1;
        playerPages.put(player.getUniqueId(), page);

        Inventory inv = Bukkit.createInventory(null, 54, Component.text("Mob Browser (" + (page + 1) + "/" + (totalPages == 0 ? 1 : totalPages) + ")"));

        int startIndex = page * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, mobs.size());

        for (int i = startIndex; i < endIndex; i++) {
            MobManager.CustomMobData data = mobs.get(i);

            // 1. 아이콘 결정 (스폰 알 찾기)
            Material iconMat = Material.getMaterial(data.type.name() + "_SPAWN_EGG");
            if (iconMat == null) iconMat = Material.NAME_TAG; // 스폰 알 없으면 이름표

            // 2. 아이템 생성
            ItemStack icon = new ItemBuilder(iconMat)
                    .name("§6" + data.displayName)
                    .lore(
                            "§7ID: §f" + data.uniqueName,
                            "§7Type: §f" + data.type.name(),
                            "§7HP: §c" + data.maxHealth,
                            "§7Damage: §c" + data.attackDamage,
                            "",
                            "§e[Click] 내 위치에 소환"
                    )
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
        if (!event.getView().title().toString().contains("Mob Browser")) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getCurrentItem() == null) return;

        int slot = event.getRawSlot();
        int page = playerPages.getOrDefault(player.getUniqueId(), 0);

        if (slot < ITEMS_PER_PAGE) {
            List<MobManager.CustomMobData> mobs = plugin.getMobManager().getAllMobs();
            mobs.sort(Comparator.comparing(m -> m.uniqueName));

            int index = (page * ITEMS_PER_PAGE) + slot;
            if (index < mobs.size()) {
                MobManager.CustomMobData data = mobs.get(index);

                // 몹 소환
                CoreProvider.spawnMob(player.getLocation(), data.uniqueName);
                player.sendMessage("§a[Mob] " + data.displayName + "§a을(를) 소환했습니다.");
                player.closeInventory();
            }
        } else if (slot == 45) open(player, page - 1);
        else if (slot == 53) open(player, page + 1);
        else if (slot == 49) player.closeInventory();
    }
}