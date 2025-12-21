package com.server.tower.item.enums;

import org.bukkit.Material;

public enum WeaponType {
    // 물리 (STR 기반)
    SWORD("검", "str", false, Material.IRON_SWORD),
    AXE("도끼", "str", false, Material.IRON_AXE),
    GREATSWORD("대검", "str", true, Material.IRON_SWORD),
    SPEAR("창", "str", true, Material.IRON_SWORD), // 리소스팩으로 창 모양 적용

    // 마법 (INT 기반)
    WAND("지팡이", "int", false, Material.STICK),
    GREATSTAFF("큰 지팡이", "int", true, Material.BLAZE_ROD);

    private final String name;
    private final String scalingStat; // "str" 또는 "int"
    private final boolean twoHanded;
    private final Material baseMaterial;

    WeaponType(String name, String scalingStat, boolean twoHanded, Material baseMaterial) {
        this.name = name;
        this.scalingStat = scalingStat;
        this.twoHanded = twoHanded;
        this.baseMaterial = baseMaterial;
    }

    public String getName() { return name; }
    public String getScalingStat() { return scalingStat; }
    public boolean isTwoHanded() { return twoHanded; }
    public Material getBaseMaterial() { return baseMaterial; }
}