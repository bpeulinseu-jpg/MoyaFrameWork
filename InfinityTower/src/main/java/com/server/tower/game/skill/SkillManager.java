package com.server.tower.game.skill;

import com.server.core.api.CoreProvider;
import com.server.tower.TowerPlugin;
import com.server.tower.game.skill.handler.AxeHandler;
import com.server.tower.game.skill.handler.GreatswordHandler;
import com.server.tower.game.skill.handler.SpearHandler;
import com.server.tower.game.skill.handler.SwordHandler;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

public class SkillManager {

    private final TowerPlugin plugin;
    private final Map<String, WeaponHandler> handlers = new HashMap<>();

    public SkillManager(TowerPlugin plugin) {
        this.plugin = plugin;
        registerHandlers();
    }

    private void registerHandlers() {
        // 무기 타입별 핸들러 등록 (소문자로 통일)
        SwordHandler sword = new SwordHandler();
        handlers.put("greatsword", new GreatswordHandler());
        handlers.put("sword", sword);
        handlers.put("spear", new SpearHandler());
        handlers.put("axe", new AxeHandler());

        // 추후 추가:
        // handlers.put("axe", new AxeHandler());
        // handlers.put("wand", new WandHandler());
    }

    public void castSkill(Player player, boolean isRightClick) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR) return;

        // 1. NBT 데이터 읽기 (ItemGenerator가 저장한 값)
        String typeStr = CoreProvider.getItemDataString(item, "weapon_type");
        String elementStr = CoreProvider.getItemDataString(item, "element");

        // 데이터가 없으면 스킬 발동 안 함 (일반 바닐라 아이템 등)
        if (typeStr == null) return;

        // 2. 핸들러 찾기
        WeaponHandler handler = handlers.get(typeStr.toLowerCase());
        if (handler == null) return;

        // 3. 속성 변환 (String -> Enum)
        Element element = Element.NONE;
        try {
            if (elementStr != null) {
                // ItemElement와 Skill Element의 이름이 같다면 바로 변환
                element = Element.valueOf(elementStr.toUpperCase());
            }
        } catch (IllegalArgumentException e) {
            // 매칭되는 속성이 없으면 무속성으로 처리
        }

        // 4. 실행
        if (isRightClick) {
            // 강공격도 콤보의 일부이므로 쿨타임 없이 바로 나가야 함.

            /* 삭제된 코드
            String cooldownKey = "skill_" + typeStr.toLowerCase();
            if (CoreProvider.hasCooldown(player, cooldownKey)) return;
            CoreProvider.setCooldown(player, cooldownKey, 60L);
            */

            handler.onRightClick(player, element);
        } else {
            handler.onLeftClick(player, element);
        }
    }
}