package com.server.core.system.gui;

import com.server.core.CorePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.function.Consumer;

public class GuiManager implements Listener {

    private final CorePlugin plugin;

    public GuiManager(CorePlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /**
     * 커스텀 GUI 열기
     *
     * @param locked true면 아이템을 가져가거나 넣을 수 없음 (도난 방지)
     * @return
     */
    public Inventory openGui(Player player, String bgTextureKey, String title, int offset, int rows, boolean locked) {
        // 1. 배경 이미지 및 오프셋 처리
        String unicode = CorePlugin.getGlyphManager().getTag(bgTextureKey);
        Component space = CorePlugin.getGlyphManager().getSpaceComponent(offset);

        Component finalTitle = Component.text("\u00A7f" + unicode)
                .append(space)
                .append(Component.text(title).color(TextColor.color(0x000000)));

        // [수정] Holder 생성 및 주입
        CoreGuiHolder holder = new CoreGuiHolder(locked);
        Inventory inv = Bukkit.createInventory(holder, rows * 9, finalTitle);
        holder.setInventory(inv); // Holder에 인벤토리 역주입 (필요시 사용)

        // 메인 스레드 실행
        Bukkit.getScheduler().runTask(plugin, () -> player.openInventory(inv));

        return inv; //생성된 인벤토리 반환기능
    }

    /**
     * 버튼 추가 및 콜백 등록
     * @param inv 대상 인벤토리
     * @param slot 슬롯 번호 (0 ~ size-1)
     * @param item 표시할 아이템
     * @param action 클릭 시 실행할 함수 (람다)
     */
    public void setButton(Inventory inv, int slot, ItemStack item, Consumer<InventoryClickEvent> action) {
        if (inv.getHolder() instanceof CoreGuiHolder holder) {
            inv.setItem(slot, item); // 아이템 배치
            holder.setAction(slot, action); // 동작 등록
        }
    }


    // [핵심] 클릭 방지 로직
    @EventHandler
    public void onClick(InventoryClickEvent event) {
        // 1. 인벤토리의 주인이 우리 프레임워크인지 확인
        if (event.getInventory().getHolder() instanceof CoreGuiHolder holder) {

            // 2. 잠금 설정이 되어 있다면 상단 인벤토리 클릭 취소
            if (holder.isLocked()) {
                if (event.getClickedInventory() == event.getInventory()) {
                    event.setCancelled(true);
                }
                // 내 인벤토리(Bottom)에서 Shift+Click으로 올리는 행위 차단
                else if (event.isShiftClick()) {
                    event.setCancelled(true);
                }
            }

            //콜백 실행
            if (event.getClickedInventory() == event.getInventory()) { // 상단 인벤토리 클릭 시만
                Consumer<InventoryClickEvent> action = holder.getAction(event.getSlot());
                if (action != null) {
                    // 클릭 소리 재생 (선택 사항)
                    if (event.getWhoClicked() instanceof Player p) {
                        p.playSound(p.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 1, 1);
                    }
                    // 등록된 함수 실행
                    action.accept(event);
                }
            }
        }
    }

    // [핵심] 드래그 방지 로직 (드래그로 아이템 뿌리기 방지)
    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof CoreGuiHolder holder) {
            if (holder.isLocked()) {
                event.setCancelled(true);
            }
        }
    }

    // 창 닫기 이벤트 처리
    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof CoreGuiHolder holder) {
            if (holder.getCloseAction() != null) {
                holder.getCloseAction().accept(event);
            }
        }
    }
}