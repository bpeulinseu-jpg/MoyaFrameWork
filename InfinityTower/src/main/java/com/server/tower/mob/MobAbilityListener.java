package com.server.tower.mob;

import com.server.core.CorePlugin;
import com.server.core.api.CoreProvider;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.entity.SlimeSplitEvent;
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
        String mobId = CoreProvider.getCustomMobId(entity);
        if (mobId == null) return;

        if (mobId.endsWith("toxic_slime")) {
            try {
                // [수정] 폭발 이펙트 (EXPLOSION_EMITTER = 거대한 폭발)
                entity.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, entity.getLocation(), 1);

                // 슬라임 파편도 같이 튀게 (시각적 효과 강화)
                entity.getWorld().spawnParticle(
                        Particle.ITEM,
                        entity.getLocation().add(0, 0.5, 0),
                        30, 0.5, 0.5, 0.5, 0.2,
                        new ItemStack(Material.SLIME_BALL)
                );

                // 소리: 폭발음 + 슬라임 소리
                entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1f, 1f);
                entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_SLIME_SQUISH, 1f, 0.5f);

                // 독 데미지
                entity.getNearbyEntities(3, 3, 3).forEach(target -> {
                    if (target instanceof Player p) {
                        p.damage(5.0);
                        p.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 100, 1));
                        p.sendMessage("§2[독] 맹독 슬라임이 자폭했습니다!");
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // 2. [신규] 슬라임 분열 방지
    @EventHandler
    public void onSlimeSplit(SlimeSplitEvent event) {
        Slime slime = event.getEntity();
        String mobId = CoreProvider.getCustomMobId(slime);

        // 우리 맹독 슬라임이라면 분열 금지
        if (mobId != null && mobId.endsWith("toxic_slime")) {
            event.setCancelled(true);
        }
    }
}
