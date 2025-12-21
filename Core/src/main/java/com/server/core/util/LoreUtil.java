package com.server.core.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class LoreUtil {

    /**
     * 아이템 Lore에서 특정 문자열(target)이 포함된 줄을 찾아 새로운 내용(replacement)으로 교체
     * 예: updateLore(item, "공격력:", "§7공격력: +50");
     */
    public static void updateLore(ItemStack item, String target, String replacement) {
        if (item == null || !item.hasItemMeta()) return;

        ItemMeta meta = item.getItemMeta();
        if (!meta.hasLore()) return;

        List<Component> lore = meta.lore();
        List<Component> newLore = new ArrayList<>();
        boolean updated = false;

        for (Component line : lore) {
            // Component를 문자열로 변환하여 비교
            String text = LegacyComponentSerializer.legacySection().serialize(line);

            if (text.contains(target)) {
                // 타겟을 찾으면 교체 (이탤릭 제거 적용)
                newLore.add(Component.text(replacement).decoration(TextDecoration.ITALIC, false));
                updated = true;
            } else {
                newLore.add(line);
            }
        }

        if (updated) {
            meta.lore(newLore);
            item.setItemMeta(meta);
        }
    }

    // 소켓 시스템용 첫번째 줄만 교체 로직
    public static void updateFirstLore(ItemStack item, String target, String replacement) {
        if (item == null || !item.hasItemMeta()) return;

        ItemMeta meta = item.getItemMeta();
        if (!meta.hasLore()) return;

        List<Component> lore = meta.lore();
        List<Component> newLore = new ArrayList<>();
        boolean replaced = false; // 교체 여부 플래그

        for (Component line : lore) {
            String text = LegacyComponentSerializer.legacySection().serialize(line);

            // 타겟을 찾았고, 아직 교체하지 않았다면
            if (text.contains(target) && !replaced) {
                newLore.add(Component.text(replacement).decoration(TextDecoration.ITALIC, false));
                replaced = true; // 교체 완료 표시
            } else {
                newLore.add(line);
            }
        }

        if (replaced) {
            meta.lore(newLore);
            item.setItemMeta(meta);
        }
    }

    /**
     * Lore의 특정 줄(Index)을 변경
     */
    public static void setLoreLine(ItemStack item, int index, String line) {
        if (item == null || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        List<Component> lore = meta.hasLore() ? meta.lore() : new ArrayList<>();

        if (index >= 0 && index < lore.size()) {
            lore.set(index, Component.text(line).decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
            item.setItemMeta(meta);
        }
    }
}