package com.server.tower.ui;

import com.server.core.api.CoreProvider;
import com.server.core.api.builder.ItemBuilder;
import com.server.tower.TowerPlugin;
import com.server.tower.item.EnhanceManager;
import com.server.tower.item.enums.ItemTier; // [신규] Import
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

public class EnhanceUI implements Listener {

    private final TowerPlugin plugin;
    private final EnhanceManager enhanceManager;
    private final String GUI_TITLE = "장비 강화소";

    public EnhanceUI(TowerPlugin plugin, EnhanceManager enhanceManager) {
        this.plugin = plugin;
        this.enhanceManager = enhanceManager;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player) {
        Inventory gui = CoreProvider.openGui(player, "infinity_tower:menu_bg", GUI_TITLE, -16, 4, false);

        ItemStack filler = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < gui.getSize(); i++) {
            if (i != 11 && i != 13 && i != 15 && i != 22) gui.setItem(i, filler);
        }

        CoreProvider.setGuiButton(gui, 2, new ItemBuilder(Material.NETHERITE_SWORD).name("§7[장비 슬롯]").build(), e -> {});
        CoreProvider.setGuiButton(gui, 4, new ItemBuilder(Material.PAPER).name("§7[주문서 슬롯]").build(), e -> {});
        CoreProvider.setGuiButton(gui, 6, new ItemBuilder(Material.TOTEM_OF_UNDYING).name("§7[보조 슬롯]").build(), e -> {});

        updateInfoButton(gui, null);
        player.openInventory(gui);
    }

    private void updateInfoButton(Inventory gui, ItemStack item) {
        ItemStack btn;
        if (item == null || item.getType() == Material.AIR) {
            btn = new ItemBuilder(Material.ANVIL).name("§7[강화 대기]").lore("§c장비를 넣어주세요.").build();
        } else {
            int level = CoreProvider.getItemDataInt(item, "enhance_level");

            // [핵심 수정] 아이템의 tier 데이터를 읽어서 최대 레벨 결정
            String tierName = CoreProvider.getItemDataString(item, "tier");
            int maxLevel = 15; // 기본값
            try {
                if (tierName != null) {
                    ItemTier tier = ItemTier.valueOf(tierName);
                    maxLevel = tier.getMaxEnhanceLevel();
                }
            } catch (IllegalArgumentException ignored) {}

            if (level >= maxLevel) {
                btn = new ItemBuilder(Material.GOLD_BLOCK).name("§6[최고 레벨]").lore("§a최대 강화 단계입니다.").build();
            } else {
                long cost = 500L + (level * 500L);
                int chance = Math.max(10, 100 - (level * 10));
                btn = new ItemBuilder(Material.ANVIL).name("§e[강화하기]").lore("§7현재: §f+" + level, "§7다음: §a+" + (level + 1), "", "§7비용: §e" + cost + " G", "§7확률: §b" + chance + "%").build();
            }
        }
        gui.setItem(22, btn);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().contains(GUI_TITLE)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        int slot = event.getRawSlot();
        Inventory inv = event.getInventory();

        if (slot < inv.getSize()) {
            if (slot == 2 || slot == 4 || slot == 6) {
                event.setCancelled(true);
                return;
            }
            if (slot == 11 || slot == 13 || slot == 15) {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    updateInfoButton(inv, inv.getItem(11));
                });
                return;
            }
            if (slot == 22) {
                event.setCancelled(true);
                processEnhance(player, inv);
                return;
            }
            event.setCancelled(true);
        } else {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                updateInfoButton(inv, inv.getItem(11));
            });
        }
    }

    private void processEnhance(Player player, Inventory inv) {
        ItemStack item = inv.getItem(11);
        ItemStack scroll = inv.getItem(13);
        ItemStack aux = inv.getItem(15);

        if (item == null || scroll == null || item.getType().isAir() || scroll.getType().isAir()) {
            player.sendMessage("§c장비와 주문서를 모두 넣어주세요.");
            return;
        }

        String type = CoreProvider.getItemDataString(item, "weapon_type");
        String armorType = CoreProvider.getItemDataString(item, "armor_type");
        if (type == null && armorType == null) {
            player.sendMessage("§c강화할 수 없는 아이템입니다.");
            return;
        }

        boolean isWeapon = (type != null);
        String requiredScroll = isWeapon ? "infinity_tower:scroll_weapon" : "infinity_tower:scroll_armor";
        if (!CoreProvider.isCustomItem(scroll, requiredScroll)) {
            player.sendMessage("§c알맞은 강화 주문서가 아닙니다.");
            return;
        }

        // [핵심 수정] 최고 레벨 검사 (등급별)
        int level = CoreProvider.getItemDataInt(item, "enhance_level");
        String tierName = CoreProvider.getItemDataString(item, "tier");
        int maxLevel = 15;
        try {
            if (tierName != null) maxLevel = ItemTier.valueOf(tierName).getMaxEnhanceLevel();
        } catch (Exception ignored) {}

        if (level >= maxLevel) {
            player.sendMessage("§c이미 최고 레벨입니다.");
            return;
        }

        long cost = 500L + (level * 500L);
        TowerUserData user = plugin.getUserManager().getUser(player);
        if (user.gold < cost) {
            player.sendMessage("§c골드가 부족합니다.");
            return;
        }

        user.gold -= cost;
        scroll.setAmount(scroll.getAmount() - 1);

        boolean hasProtection = false;
        boolean hasLucky = false;
        if (aux != null && aux.getType() != Material.AIR) {
            if (CoreProvider.isCustomItem(aux, "infinity_tower:protection_charm")) hasProtection = true;
            if (CoreProvider.isCustomItem(aux, "infinity_tower:lucky_stone")) hasLucky = true;
            aux.setAmount(aux.getAmount() - 1);
        }

        ItemStack result = enhanceManager.tryEnhance(player, item, hasProtection, hasLucky);

        inv.setItem(11, result);
        updateInfoButton(inv, result);
        plugin.getUserManager().updateSidebar(player);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!event.getView().getTitle().contains(GUI_TITLE)) return;

        returnItem(event.getPlayer(), event.getInventory(), 11);
        returnItem(event.getPlayer(), event.getInventory(), 13);
        returnItem(event.getPlayer(), event.getInventory(), 15);
    }

    private void returnItem(org.bukkit.entity.HumanEntity player, Inventory inv, int slot) {
        ItemStack item = inv.getItem(slot);
        if (item != null && item.getType() != Material.AIR) {
            if (player.getInventory().firstEmpty() == -1) {
                player.getWorld().dropItem(player.getLocation(), item);
            } else {
                player.getInventory().addItem(item);
            }
        }
    }
}