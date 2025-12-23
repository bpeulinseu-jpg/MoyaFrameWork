package com.server.tower.mob.skill; // [수정] 패키지

import com.server.core.system.mob.skill.MobSkill;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class BuffSkill extends MobSkill {

    public BuffSkill() {
        super(200L, 10.0);
    }

    @Override
    public void onCast(LivingEntity caster, LivingEntity target) {
        caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_EVOKER_PREPARE_WOLOLO, 1f, 1f);

        caster.getNearbyEntities(10, 5, 10).forEach(entity -> {
            if (entity instanceof Monster && entity != caster) {
                LivingEntity ally = (LivingEntity) entity;
                // [수정] 1.21 호환
                ally.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 100, 0));
                ally.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 1));

                // [수정] 1.21 호환
                ally.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, ally.getEyeLocation(), 5, 0.5, 0.5, 0.5);
            }
        });
        caster.sendMessage(net.kyori.adventure.text.Component.text("§e[주술사] 광란의 춤!"));
    }
}