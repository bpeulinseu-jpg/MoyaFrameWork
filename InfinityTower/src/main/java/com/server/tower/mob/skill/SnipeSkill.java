package com.server.tower.mob.skill; // [수정] 패키지 경로 확인

import com.server.core.api.CoreProvider;
import com.server.core.system.mob.skill.MobSkill;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public class SnipeSkill extends MobSkill { // 파일명도 SnipeSkill.java 여야 함

    private final JavaPlugin plugin;

    public SnipeSkill(JavaPlugin plugin) {
        super(100L, 20.0);
        this.plugin = plugin;
    }

    @Override
    public void onCast(LivingEntity caster, LivingEntity target) {
        new BukkitRunnable() {
            int tick = 0;
            @Override
            public void run() {
                if (caster.isDead() || target.isDead() || tick >= 60) {
                    this.cancel();
                    if (tick >= 60) fire(caster, target);
                    return;
                }

                Location start = caster.getEyeLocation();
                Location end = target.getEyeLocation();
                Vector dir = end.clone().subtract(start).toVector().normalize();

                for (double i = 0; i < start.distance(end); i += 0.5) {
                    // [수정] 1.21 호환: REDSTONE -> DUST
                    start.getWorld().spawnParticle(Particle.DUST,
                            start.clone().add(dir.clone().multiply(i)), 1,
                            new Particle.DustOptions(org.bukkit.Color.RED, 0.5f));
                }

                if (tick % 10 == 0) {
                    caster.getWorld().playSound(caster.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 2f);
                }
                tick += 2;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    private void fire(LivingEntity caster, LivingEntity target) {
        caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_SKELETON_SHOOT, 1f, 0.5f);

        // 실제 화살 엔티티 발사
        Location start = caster.getEyeLocation();
        Location end = target.getEyeLocation().add(0, -0.5, 0); // 살짝 아래 조준
        Vector dir = end.subtract(start).toVector().normalize().multiply(3.0); // 속도 3.0

        org.bukkit.entity.Arrow arrow = caster.launchProjectile(org.bukkit.entity.Arrow.class, dir);
        arrow.setCritical(true); // 크리티컬 파티클 효과
        arrow.setDamage(10.0);   // 기본 대미지

        // 메타데이터로 스킬 화살임을 표시 (Listener에서 대미지 증폭 가능)
        arrow.addScoreboardTag("snipe_arrow");
    }
}