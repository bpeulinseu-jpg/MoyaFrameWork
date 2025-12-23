package com.server.tower.mob;

import com.server.core.CorePlugin;
import com.server.core.api.CoreProvider;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

public class MobAbilityListener implements Listener {

    // 1. 리빙 아머: 정면 방어력 (Living Armor)
    @EventHandler(priority = org.bukkit.event.EventPriority.HIGH)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity victim) || !(event.getDamager() instanceof Player attacker)) return;

        String mobId = CorePlugin.getMobManager().getCustomMobId(victim);
        if (mobId == null) return;

        // 그림자 망령: 피격 시 은신 해제 및 텔레포트
        if (mobId.endsWith("shadow_wraith")) {
            if (Math.random() < 0.3) { // 30% 확률로 회피 텔레포트
                Location teleportLoc = victim.getLocation().add((Math.random()-0.5)*6, 0, (Math.random()-0.5)*6);
                victim.teleport(teleportLoc);
                victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
            }
        }
    }

    // 2. 맹독 슬라임: 자폭 (Toxic Slime)
    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        String mobId = CorePlugin.getMobManager().getCustomMobId(entity);
        if (mobId == null) return;

        if (mobId.endsWith("toxic_slime")) {
            // [수정] 1.21 호환: ITEM_SLIME 사용
            entity.getWorld().spawnParticle(
                    Particle.ITEM, // ITEM_SLIME 대신 ITEM 사용 (범용성 좋음)
                    entity.getLocation().add(0, 0.5, 0),
                    30, // 개수
                    0.5, 0.5, 0.5, // 오프셋
                    0.1, // 속도
                    new ItemStack(Material.SLIME_BALL) // 데이터
            );

            entity.getNearbyEntities(3, 3, 3).forEach(target -> {
                if (target instanceof Player p) {
                    p.damage(5.0, entity);
                    p.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 100, 1));
                    p.sendMessage("§2[독] 맹독 슬라임이 자폭했습니다!");
                }
            });
        }
    }
}