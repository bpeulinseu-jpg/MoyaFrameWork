package com.server.tower.game.perk;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.function.Consumer;

public record Perk(
        String id,
        String name,
        String description,
        Material icon, // 나중엔 커스텀 아이템 ID로 교체 가능
        Consumer<Player> effect // 선택 시 실행할 로직
) {}