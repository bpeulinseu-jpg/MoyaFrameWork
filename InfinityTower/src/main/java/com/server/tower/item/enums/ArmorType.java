package com.server.tower.item.enums;

import org.bukkit.Material;

public enum ArmorType {
    HELMET("투구", Material.IRON_HELMET, 0.6),
    CHESTPLATE("갑옷", Material.IRON_CHESTPLATE, 1.0), // 갑옷이 방어력 제일 높음
    LEGGINGS("바지", Material.IRON_LEGGINGS, 0.8),
    BOOTS("신발", Material.IRON_BOOTS, 0.5);

    private final String name;
    private final Material material;
    private final double statMultiplier; // 부위별 스탯 가중치

    ArmorType(String name, Material material, double statMultiplier) {
        this.name = name;
        this.material = material;
        this.statMultiplier = statMultiplier;
    }

    public String getName() { return name; }
    public Material getMaterial() { return material; }
    public double getStatMultiplier() { return statMultiplier; }
}