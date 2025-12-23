package com.server.tower.mob.skill; // [수정] 패키지

import com.server.core.system.mob.skill.MobSkill;
import com.server.core.util.TargetingUtil;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

public class ShieldBashSkill extends MobSkill {

    public ShieldBashSkill() {
        super(100L, 3.0);
    }

    @Override
    public void onCast(LivingEntity caster, LivingEntity target) {
        caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_IRON_GOLEM_ATTACK, 1f, 0.5f);
        // [수정] 1.21 호환
        caster.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, caster.getLocation(), 1);

        for (Player p : TargetingUtil.getNearbyPlayers(caster, 4.0)) {
            Vector dir = p.getLocation().subtract(caster.getLocation()).toVector().normalize();
            p.setVelocity(dir.multiply(1.5).setY(0.5));

            // [수정] 1.21 호환: SLOW -> SLOWNESS
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 10));
            p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 1));
            p.sendMessage("§c[!] 방패 밀치기에 당해 기절했습니다!");
        }
    }
}