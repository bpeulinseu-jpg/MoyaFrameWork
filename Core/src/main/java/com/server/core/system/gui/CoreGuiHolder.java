package com.server.core.system.gui;

import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class CoreGuiHolder implements InventoryHolder {

    private final boolean isLocked;
    private Inventory inventory;
    private Consumer<InventoryCloseEvent> closeAction;

    // [신규] 슬롯 번호 -> 실행할 동작(Action) 매핑
    private final Map<Integer, Consumer<InventoryClickEvent>> actions = new HashMap<>();

    public CoreGuiHolder(boolean isLocked) {
        this.isLocked = isLocked;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public boolean isLocked() {
        return isLocked;
    }

    //  액션 등록
    public void setAction(int slot, Consumer<InventoryClickEvent> action) {
        actions.put(slot, action);
    }

    // 액션 가져오기
    public Consumer<InventoryClickEvent> getAction(int slot) {
        return actions.get(slot);
    }

    // 싀프트 클릭 차단 및 클로즈 액션 등록
    public void setCloseAction(Consumer<InventoryCloseEvent> action) {
        this.closeAction = action;
    }
    public Consumer<InventoryCloseEvent> getCloseAction() {
        return closeAction;
    }



    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}