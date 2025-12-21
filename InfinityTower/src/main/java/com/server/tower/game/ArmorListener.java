package com.server.tower.game;

import com.server.core.api.CoreProvider;
import com.server.tower.TowerPlugin;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemBreakEvent;
import org.bukkit.inventory.ItemStack;

public class ArmorListener implements Listener {

    private final TowerPlugin plugin;

    public ArmorListener(TowerPlugin plugin) {
        this.plugin = plugin;
    }

    // 1. 인벤토리 클릭 (장착/해제/스왑)
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // 갑옷 슬롯을 건드렸거나, Shift 클릭으로 아이템을 옮길 때
        boolean isArmorSlot = event.getSlotType() == InventoryType.SlotType.ARMOR;
        boolean isShiftClick = event.getClick() == ClickType.SHIFT_LEFT || event.getClick() == ClickType.SHIFT_RIGHT;
        boolean isNumberKey = event.getClick() == ClickType.NUMBER_KEY;

        if (isArmorSlot || isShiftClick || isNumberKey) {
            updateStatsLater(player);
        }
    }

    // 2. 우클릭으로 장착 (손에 든 아이템)
    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            ItemStack item = event.getItem();
            if (item != null) {
                String type = item.getType().name();
                // 갑옷류 아이템이면 장착 시도 가능성이 있으므로 업데이트
                if (type.endsWith("_HELMET") || type.endsWith("_CHESTPLATE") ||
                        type.endsWith("_LEGGINGS") || type.endsWith("_BOOTS")) {
                    updateStatsLater(event.getPlayer());
                }
            }
        }
    }

    // 3. 아이템 드랍 (갑옷을 입은 상태에서 Q로 버림 - 드물지만 가능)
    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        updateStatsLater(event.getPlayer());
    }

    // 4. 아이템 파괴 (내구도 0)
    @EventHandler
    public void onBreak(PlayerItemBreakEvent event) {
        updateStatsLater(event.getPlayer());
    }

    // 1틱 뒤에 스탯 재계산 (이벤트가 끝난 후의 인벤토리 상태를 읽어야 함)
    private void updateStatsLater(Player player) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            CoreProvider.recalculateStats(player);
            // 스코어보드도 같이 갱신 (선택 사항)
            plugin.getUserManager().updateSidebar(player);
        });
    }
}