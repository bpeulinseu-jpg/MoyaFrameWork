package com.server.tower.game.skill.handler;

import com.server.core.api.CoreProvider;
import com.server.core.system.particle.ParticleBuilder;
import com.server.tower.TowerPlugin;
import com.server.tower.game.DamageCalculator;
import com.server.tower.game.skill.Element;
import com.server.tower.game.skill.WeaponHandler;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SwordHandler implements WeaponHandler {

    private final Map<UUID, Integer> comboMap = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastAttackTime = new ConcurrentHashMap<>();

    @Override
    public void onLeftClick(Player player, Element element) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();

        if (now - lastAttackTime.getOrDefault(uuid, 0L) > 1500) {
            comboMap.put(uuid, 0);
        }
        lastAttackTime.put(uuid, now);

        int combo = comboMap.getOrDefault(uuid, 0);

        double tilt = 0.0;
        double size = 2.5;

        switch (combo) {
            case 0: tilt = 45.0; player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 1.2f); break;
            case 1: tilt = -45.0; player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 1.2f); break;
            case 2: tilt = 90.0; size = 3.0; player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 0.8f); break;
        }

        // [평타] 약식 그라데이션 (3겹)
        Vector dir = player.getLocation().getDirection();
        Location center = player.getEyeLocation().add(dir.clone().multiply(2.0));
        drawLayeredSlash(center, dir, size, 0.2, tilt, 15, element, 3);

        comboMap.put(uuid, (combo + 1) % 3);
    }

    @Override
    public void onRightClick(Player player, Element element) {
        // [스킬] 제자리 검기 애니메이션

        Vector dir = player.getLocation().getDirection().normalize();

        // 1. 대시 (플레이어 이동)
        player.setVelocity(dir.clone().multiply(1.2).setY(0.2));
        player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 1f, 1.5f);

        // 2. 이펙트 소환 (제자리 재생)
        // 속도: 0.0 (안 날아감)
        // 사거리: 0.0 (의미 없음)
        // Loop: false (한 번 재생하고 사라짐)
        // 콜백: null (안 움직이니 충돌 감지 안 함)
        Vector rotation = new Vector(90, 0, 270);

        Vector scale = new Vector(5.0, 5.0, 2);

        // [수정] shootAnimatedProjectile 호출 (rotation 추가)
        CoreProvider.shootAnimatedProjectile(player, 0.0, 0.0,
                scale, // 크기 벡터
                rotation,
                TowerPlugin.SLASH_ANIMATION_START_ID, 5, 1, false, null);

        // 3. 타격 판정 (직접 계산)
        // 이펙트는 장식일 뿐이고, 실제 타격은 여기서 즉시 발생시킴
        Location center = player.getEyeLocation().add(dir.clone().multiply(2.5)); // 타격 중심점

        // 타격음
        player.getWorld().playSound(center, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 0.5f);

        for (org.bukkit.entity.Entity entity : center.getWorld().getNearbyEntities(center, 4, 3, 4)) {
            if (entity instanceof LivingEntity victim && entity != player) {
                Vector toTarget = victim.getLocation().subtract(player.getLocation()).toVector().normalize();

                // 전방 120도 부채꼴 판정
                if (dir.dot(toTarget) > 0.5) {
                    applyDamageAndEffect(player, victim, element);
                }
            }
        }
    }
/*
    private void playSlashAnimation(Player player, Element element, Vector dir) {
        Location startLoc = player.getEyeLocation().add(dir.clone().multiply(4.0));

        // 사운드
        player.getWorld().playSound(startLoc, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 0.5f);
        if (element == Element.FIRE) player.getWorld().playSound(startLoc, Sound.ENTITY_BLAZE_SHOOT, 1f, 0.8f);
        else if (element == Element.STORM) player.getWorld().playSound(startLoc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.5f, 2f);

        // 애니메이션
        new BukkitRunnable() {
            final int totalPoints = 60;
            final int pointsPerTick = 20;
            int currentPoint = 0;

            final Vector up = (Math.abs(dir.getY()) > 0.95) ? new Vector(1, 0, 0) : new Vector(0, 1, 0);
            final Vector right = dir.getCrossProduct(up).normalize();

            final double size = 8.0;
            final double curvature = 1.8;

            @Override
            public void run() {
                for (int i = 0; i < pointsPerTick; i++) {
                    if (currentPoint > totalPoints) {
                        this.cancel();
                        checkHit(player, element, startLoc, dir);
                        return;
                    }

                    double t = (double) currentPoint / totalPoints;
                    double x = (t - 0.5) * size;
                    double curveOffset = curvature * (1.0 - (4 * (t - 0.5) * (t - 0.5)));

                    Vector baseOffset = right.clone().multiply(x).add(dir.clone().multiply(curveOffset));

                    // [수정] 7중 레이어 (흰색 -> 속성 -> 검정)
                    int layers = 7;
                    for (int l = 0; l < layers; l++) {
                        // 0.0 (뒤) ~ 1.0 (앞)
                        double ratio = (double) l / (layers - 1);

                        // 위치 오프셋: -0.8(뒤) ~ +0.5(앞)
                        double depthOffset = -0.8 + (ratio * 1.3);

                        // [핵심] 색상 계산 (넓은 속성 구간 적용)
                        Color color = interpolateColor(element.getColor(), ratio);

                        // 크기: 뒤쪽(흰색)은 얇고, 중간(속성)은 두껍고, 앞쪽(검정)은 다시 얇게
                        // 혹은 뒤에서 앞으로 갈수록 커지게? -> 사용자가 원하는 '두께감'을 위해 중간을 키움
                        float particleSize = 1.5f;
                        if (ratio > 0.2 && ratio < 0.8) particleSize = 2.0f; // 속성 구간은 두껍게
                        if (ratio == 1.0) particleSize = 2.5f; // 검은색 테두리는 가장 크게

                        ParticleBuilder p = CoreProvider.createParticle()
                                .setParticle(Particle.DUST)
                                .setColor(color.getRed(), color.getGreen(), color.getBlue())
                                .setSize(particleSize)
                                .setCount(1);

                        CoreProvider.getParticleManager().spawn(
                                startLoc.clone().add(baseOffset).add(dir.clone().multiply(depthOffset)),
                                p
                        );
                    }
                    currentPoint++;
                }
            }
        }.runTaskTimer(TowerPlugin.getInstance(), 0L, 1L);
    }

 */



    private void drawLayeredSlash(Location center, Vector dir, double size, double curvature, double tilt, int points, Element element, int layers) {
        for (int l = 0; l < layers; l++) {
            double ratio = (double) l / (layers - 1);
            double depthOffset = -0.3 + (ratio * 0.6);
            Color color = interpolateColor(element.getColor(), ratio);

            ParticleBuilder p = CoreProvider.createParticle()
                    .setParticle(Particle.DUST)
                    .setColor(color.getRed(), color.getGreen(), color.getBlue())
                    .setSize(1.0f)
                    .setCount(1);

            CoreProvider.getParticleManager().drawSlash(
                    center.clone().add(dir.clone().multiply(depthOffset)),
                    dir, size, curvature, tilt, points, p
            );
        }
    }

    // [핵심 수정] 색상 보간 로직 (White -> Color(Wide) -> Black)
    private Color interpolateColor(Color baseColor, double ratio) {
        int r, g, b;

        // 1. 뒤쪽 20% (0.0 ~ 0.2): 흰색 -> 속성색
        if (ratio < 0.2) {
            double t = ratio * 5.0; // 0~1로 정규화
            r = (int) (255 + (baseColor.getRed() - 255) * t);
            g = (int) (255 + (baseColor.getGreen() - 255) * t);
            b = (int) (255 + (baseColor.getBlue() - 255) * t);
        }
        // 2. 중간 60% (0.2 ~ 0.8): 속성색 유지 (가장 넓은 구간)
        else if (ratio < 0.8) {
            r = baseColor.getRed();
            g = baseColor.getGreen();
            b = baseColor.getBlue();
        }
        // 3. 앞쪽 20% (0.8 ~ 1.0): 속성색 -> 검은색
        else {
            double t = (ratio - 0.8) * 5.0; // 0~1로 정규화
            r = (int) (baseColor.getRed() * (1 - t));
            g = (int) (baseColor.getGreen() * (1 - t));
            b = (int) (baseColor.getBlue() * (1 - t));
        }

        return Color.fromRGB(r, g, b);
    }

    private void checkHit(Player player, Element element, Location center, Vector dir) {
        for (org.bukkit.entity.Entity entity : center.getWorld().getNearbyEntities(center, 5, 3, 5)) {
            if (entity instanceof LivingEntity victim && entity != player) {
                Vector toTarget = victim.getLocation().subtract(player.getLocation()).toVector().normalize();
                if (dir.dot(toTarget) > 0.3) {
                    applyDamageAndEffect(player, victim, element);
                }
            }
        }
    }

    private void applyDamageAndEffect(Player attacker, LivingEntity victim, Element element) {
        DamageCalculator.DamageResult result = DamageCalculator.calculate(attacker, victim, 2.5, true);
        if (result.isCancelled()) return;

        double damage = result.damage();

        switch (element) {
            case FIRE:
                victim.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, victim.getLocation(), 1);
                victim.setFireTicks(60);
                damage *= 1.2;
                break;
            case ICE:
                victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 2));
                victim.getWorld().spawnParticle(Particle.SNOWFLAKE, victim.getLocation(), 15, 0.5, 0.5, 0.5, 0.1);
                break;
            case STORM:
                victim.getWorld().strikeLightningEffect(victim.getLocation());
                damage += 20;
                break;
            case DARK:
                double maxHp = victim.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
                damage += (maxHp - victim.getHealth()) * 0.15;
                victim.getWorld().spawnParticle(Particle.SOUL, victim.getLocation(), 15, 0.5, 0.5, 0.5, 0.1);
                break;
            case LIGHT:
                double heal = damage * 0.3;
                double newHp = Math.min(attacker.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue(), attacker.getHealth() + heal);
                attacker.setHealth(newHp);
                victim.getWorld().spawnParticle(Particle.END_ROD, victim.getLocation(), 15, 0.5, 0.5, 0.5, 0.1);
                break;
        }

        CoreProvider.dealDamage(attacker, victim, damage, result.isCrit());
    }
}