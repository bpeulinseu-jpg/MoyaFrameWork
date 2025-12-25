package com.server.core.system.projectile;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

import java.util.function.Consumer;

public class CustomProjectile {

    protected final LivingEntity shooter;
    protected final Entity visual;
    protected final Vector direction;
    protected final double speed;
    protected final double gravity;
    protected final double maxDistance;
    protected double distanceTraveled = 0;

    protected final Consumer<Entity> onHitEntity;
    protected final Runnable onHitBlock;
    protected final Runnable onTimeout;

    protected boolean isDead = false;

    public CustomProjectile(LivingEntity shooter, Entity visual, Vector direction, double speed, double gravity, double maxDistance, Consumer<Entity> onHitEntity, Runnable onHitBlock, Runnable onTimeout) {
        this.shooter = shooter;
        this.visual = visual;
        this.direction = direction.normalize().multiply(speed);
        this.speed = speed;
        this.gravity = gravity;
        this.maxDistance = maxDistance;
        this.onHitEntity = onHitEntity;
        this.onHitBlock = onHitBlock;
        this.onTimeout = onTimeout;
    }

    public void tick() {
        if (isDead) return;

        // [핵심 수정] 속도가 0이면(제자리 이펙트) 물리 연산(이동, 충돌)을 건너뜀
        // 아주 작은 값(0.0001)보다 클 때만 실행
        if (speed > 0.0001) {
            Location currentLoc = visual.getLocation();

            // 1. 중력
            if (gravity > 0) {
                direction.setY(direction.getY() - gravity);
            }

            // 2. 이동 및 회전
            Location nextLoc = currentLoc.clone().add(direction);
            nextLoc.setDirection(direction);

            // 3. 충돌 감지 (속도가 0이면 direction이 (0,0,0)이라 여기서 에러났었음)
            try {
                var result = currentLoc.getWorld().rayTrace(
                        currentLoc.clone().add(0, 0.5, 0),
                        direction,
                        direction.length(),
                        org.bukkit.FluidCollisionMode.NEVER,
                        true,
                        0.5,
                        (entity) -> entity != shooter && entity != visual && entity instanceof LivingEntity
                );

                if (result != null) {
                    if (result.getHitEntity() != null) {
                        if (onHitEntity != null) onHitEntity.accept(result.getHitEntity());
                        remove();
                        return;
                    } else if (result.getHitBlock() != null) {
                        if (onHitBlock != null) onHitBlock.run();
                        remove();
                        return;
                    }
                }
            } catch (Exception ignored) {}

            // 4. 이동 적용
            visual.teleport(nextLoc);

            // 5. 사거리 체크
            distanceTraveled += speed;
            if (distanceTraveled >= maxDistance) {
                if (onTimeout != null) onTimeout.run();
                remove();
            }
        }
    }

    public void remove() {
        isDead = true;
        if (visual != null) visual.remove();
    }

    public boolean isDead() {
        return isDead;
    }

    public Entity getVisual() {
        return visual;
    }
}