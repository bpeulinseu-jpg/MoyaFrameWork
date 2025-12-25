package com.server.tower.game.skill;

import com.server.core.api.CoreProvider;
import com.server.tower.TowerPlugin;
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
        handlers.put("sword", sword);
        handlers.put("greatsword", sword); // 대검도 일단 검 로직 공유 (나중에 GreatswordHandler 만들면 교체)
        handlers.put("katana", sword);
        handlers.put("spear", new SpearHandler());

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
            // 쿨타임 체크 (키: skill_sword, skill_axe 등)
            String cooldownKey = "skill_" + typeStr.toLowerCase();
            if (CoreProvider.hasCooldown(player, cooldownKey)) return;

            // [핵심] 핸들러에게 속성 정보를 같이 넘김!
            handler.onRightClick(player, element);

            // 쿨타임 적용 (예: 3초)
            CoreProvider.setCooldown(player, cooldownKey, 60L);
        } else {
            handler.onLeftClick(player, element);
        }
    }
}