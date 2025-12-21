package com.server.core.system.projectile;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

import java.util.function.Consumer;

public class CustomProjectile {

    private final LivingEntity shooter;
    private final ItemDisplay visual; // 보여지는 모습 (1.19.4+)
    private final Vector direction;
    private final double speed;
    private final double gravity; // 0.0 = 직사, 0.05 = 화살 정도
    private final double maxDistance;
    private double distanceTraveled = 0;

    // 콜백 함수 (맞았을 때 실행)
    private final Consumer<Entity> onHitEntity;
    private final Runnable onHitBlock;
    private final Runnable onTimeout; // 사거리 끝남

    private boolean isDead = false;

    public CustomProjectile(LivingEntity shooter, ItemDisplay visual, Vector direction, double speed, double gravity, double maxDistance, Consumer<Entity> onHitEntity, Runnable onHitBlock, Runnable onTimeout) {
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

        Location currentLoc = visual.getLocation();

        // 1. 중력 적용
        if (gravity > 0) {
            direction.setY(direction.getY() - gravity);
        }

        // 2. 이동할 위치 계산
        Location nextLoc = currentLoc.clone().add(direction);

        // 3. 충돌 감지 (RayTrace)
        // 현재 위치에서 다음 위치까지 선을 그어 충돌 체크
        var result = currentLoc.getWorld().rayTrace(
                currentLoc,
                direction,
                direction.length(),
                org.bukkit.FluidCollisionMode.NEVER,
                true, // 통과 가능한 블록 무시 여부
                0.5, // 히트박스 크기 보정
                (entity) -> entity != shooter && entity != visual && entity instanceof LivingEntity // 나 자신과 내 투사체 제외
        );

        if (result != null) {
            if (result.getHitEntity() != null) {
                // 엔티티 명중
                if (onHitEntity != null) onHitEntity.accept(result.getHitEntity());
                remove();
                return;
            } else if (result.getHitBlock() != null) {
                // 블록 명중
                if (onHitBlock != null) onHitBlock.run();
                remove();
                return;
            }
        }

        // 4. 이동 적용
        visual.teleport(nextLoc);

        // 5. 사거리 체크
        distanceTraveled += speed;
        if (distanceTraveled >= maxDistance) {
            if (onTimeout != null) onTimeout.run();
            remove();
        }
    }

    public void remove() {
        isDead = true;
        if (visual != null) visual.remove();
    }

    public boolean isDead() {
        return isDead;
    }
}