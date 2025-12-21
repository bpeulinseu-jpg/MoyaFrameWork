package com.server.tower.game;

import com.server.core.api.CoreProvider;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;

public class EquipmentListener implements Listener {

    // 1. F키 스왑 방지
    @EventHandler
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        // [수정] 변수명 통일
        ItemStack mainHand = event.getMainHandItem();
        ItemStack offHand = event.getOffHandItem();

        // 양손 무기를 왼손으로 보내려 하거나(offHand가 양손무기),
        // 양손 무기 들고 있는데(mainHand가 양손무기) 왼손에 뭐 가져오려 할 때(offHand가 공기가 아님)
        if (isTwoHanded(offHand) || (isTwoHanded(mainHand) && offHand.getType() != Material.AIR)) {
            event.getPlayer().sendActionBar(Component.text("§c양손 무기 사용 중입니다."));
            event.setCancelled(true);
        }
    }

    // 2. 인벤토리 클릭 방지 (왼손 슬롯에 직접 넣기)
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getSlotType() != InventoryType.SlotType.QUICKBAR) return;
        if (event.getSlot() != 40) return; // 40번 = 왼손 슬롯

        ItemStack cursor = event.getCursor();
        if (isTwoHanded(cursor)) {
            event.getWhoClicked().sendMessage("§c양손 무기는 왼손에 낄 수 없습니다.");
            event.setCancelled(true);
        }
    }

    // 3. 핫바 슬롯 변경 시 체크
    @EventHandler
    public void onItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        ItemStack newItem = player.getInventory().getItem(event.getNewSlot());
        ItemStack offHand = player.getInventory().getItemInOffHand();

        if (isTwoHanded(newItem) && offHand.getType() != Material.AIR) {
            player.getInventory().setItemInOffHand(null);
            player.getInventory().addItem(offHand);
            player.sendActionBar(Component.text("§e양손 무기 장착을 위해 보조장비가 해제되었습니다."));
        }
    }

    private boolean isTwoHanded(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        return CoreProvider.getItemDataInt(item, "is_two_handed") == 1;
    }
}