package com.server.tower.game.skill;

import org.bukkit.Color;

public enum Element {
    NONE("무속성", Color.WHITE),
    FIRE("화염", Color.fromRGB(255, 85, 85)),   // 빨강
    ICE("혹한", Color.fromRGB(85, 255, 255)),    // 하늘색
    STORM("폭풍", Color.fromRGB(255, 255, 85)),  // 노랑
    LIGHT("빛", Color.fromRGB(255, 255, 255)),   // 흰색
    DARK("어둠", Color.fromRGB(170, 0, 170));    // 보라

    private final String name;
    private final Color color;

    Element(String name, Color color) {
        this.name = name;
        this.color = color;
    }

    public String getName() { return name; }
    public Color getColor() { return color; }
}