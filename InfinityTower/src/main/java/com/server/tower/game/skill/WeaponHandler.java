package com.server.tower.game.skill;

import org.bukkit.entity.Player;

public interface WeaponHandler {
    // 좌클릭 (평타)
    void onLeftClick(Player player, Element element);

    // 우클릭 (스킬)
    void onRightClick(Player player, Element element);
}