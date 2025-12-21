package com.server.tower.game.perk;

import com.server.core.api.CoreProvider;
import com.server.tower.TowerPlugin;
import com.server.tower.user.TowerUserData;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

public class PerkRegistry {

    private final TowerPlugin plugin;
    private final List<Perk> perks = new ArrayList<>();
    private final Random random = new Random();

    public PerkRegistry(TowerPlugin plugin) {
        this.plugin = plugin;
    }

    public void registerAll() {
        // 1. 물리 공격력 증가
        register("phys_up", "§c[무력 강화]", "§7물리 공격력이 5.0 증가합니다.", Material.IRON_SWORD, p -> {
            CoreProvider.addSessionStat(p, "phys_atk", 5.0);
            p.sendMessage("§c물리 공격력이 증가했습니다! (+5.0)");
        });

        // 2. 방어력 증가
        register("def_up", "§8[단단한 피부]", "§7방어력이 2.0 증가합니다.", Material.IRON_CHESTPLATE, p -> {
            CoreProvider.addSessionStat(p, "def", 2.0);
            p.sendMessage("§8방어력이 증가했습니다! (+2.0)");
        });

        // 3. 최대 생명력 증가
        register("hp_up", "§a[생명력 증폭]", "§7최대 체력이 20.0 (10칸) 증가합니다.", Material.GOLDEN_APPLE, p -> {
            CoreProvider.addSessionStat(p, "max_health", 20.0);
            // 늘어난 체력만큼 회복도 시켜줌
            p.setHealth(p.getHealth() + 20.0);
            p.sendMessage("§a최대 체력이 증가했습니다! (+20.0)");
        });

        // 4. 체력 재생량 증가
        register("regen_up", "§d[재생의 바람]", "§7체력 재생량이 1.0 증가합니다.", Material.GLISTERING_MELON_SLICE, p -> {
            CoreProvider.addSessionStat(p, "hp_regen", 1.0);
            p.sendMessage("§d체력 재생 속도가 빨라졌습니다! (+1.0)");
        });

        // 5. 마법 공격력 증가
        register("mag_up", "§b[마력 충전]", "§7마법 공격력이 5.0 증가합니다.", Material.BLAZE_ROD, p -> {
            CoreProvider.addSessionStat(p, "mag_atk", 5.0);
            p.sendMessage("§b마법 공격력이 증가했습니다! (+5.0)");
        });

        // 6. 치명타 확률 증가
        register("crit_chance_up", "§e[약점 포착]", "§7치명타 확률이 5% 증가합니다.", Material.FLINT, p -> {
            CoreProvider.addSessionStat(p, "crit_chance", 5.0);
            p.sendMessage("§e치명타 확률이 증가했습니다! (+5%)");
        });

        // 7. 치명타 피해 증가
        register("crit_dmg_up", "§4[치명적인 일격]", "§7치명타 피해량이 20% 증가합니다.", Material.REDSTONE, p -> {
            CoreProvider.addSessionStat(p, "crit_damage", 20.0);
            p.sendMessage("§4치명타 피해량이 증가했습니다! (+20%)");
        });

        // 8. 이동속도 증가
        register("speed_up", "§f[신속]", "§7이동 속도가 10% 증가합니다.", Material.FEATHER, p -> {
            CoreProvider.addSessionStat(p, "move_speed", 10.0);
            p.sendMessage("§f발걸음이 가벼워졌습니다! (+10%)");
        });

        // 9. 체력 완전 회복
        register("full_heal", "§d[신의 은총]", "§7체력을 100% 회복합니다.", Material.POTION, p -> {
            double maxHp = p.getAttribute(Attribute.MAX_HEALTH).getValue();
            p.setHealth(maxHp);
            p.sendMessage("§d체력이 모두 회복되었습니다!");
        });

        // 10. 1회성 골드 지급
        register("bonus_gold", "§6[보물 발견]", "§7즉시 500 골드를 획득합니다.", Material.GOLD_INGOT, p -> {
            TowerUserData data = plugin.getUserManager().getUser(p);
            if (data != null) {
                data.gold += 500;
                plugin.getUserManager().updateSidebar(p); // 스코어보드 갱신
                p.sendMessage("§6500 골드를 획득했습니다!");
            }
        });
    }

    private void register(String id, String name, String desc, Material icon, Consumer<Player> effect) {
        perks.add(new Perk(id, name, desc, icon, effect));
    }

    public List<Perk> getRandomPerks(int count) {
        List<Perk> shuffled = new ArrayList<>(perks);
        Collections.shuffle(shuffled);
        return shuffled.subList(0, Math.min(count, shuffled.size()));
    }
}