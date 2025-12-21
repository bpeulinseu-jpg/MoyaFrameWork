package com.server.core.system.block;

import org.bukkit.inventory.ItemStack;

public class DropItem {
    private final ItemStack item;
    private final double chance; // 0.0 ~ 1.0 (1.0 = 100%)
    private final int minAmount;
    private final int maxAmount;
    private final boolean applyFortune; // 행운 인챈트 적용 여부

    public DropItem(ItemStack item, double chance, int minAmount, int maxAmount, boolean applyFortune) {
        this.item = item;
        this.chance = chance;
        this.minAmount = minAmount;
        this.maxAmount = maxAmount;
        this.applyFortune = applyFortune;
    }

    public ItemStack getItem() {
        // [수정] 아이템이 null이면 null 반환 (BlockListener에서 체크함)
        return item == null ? null : item.clone();
    }

    public double getChance() { return chance; }
    public int getMinAmount() { return minAmount; }
    public int getMaxAmount() { return maxAmount; }
    public boolean isApplyFortune() { return applyFortune; }
}