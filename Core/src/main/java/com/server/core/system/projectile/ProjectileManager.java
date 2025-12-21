package com.server.core.system.projectile;

import com.server.core.CorePlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

public class ProjectileManager {

    private final CorePlugin plugin;
    private final List<CustomProjectile> projectiles = new ArrayList<>();

    public ProjectileManager(CorePlugin plugin) {
        this.plugin = plugin;
        startLoop();
    }

    private void startLoop() {
        // 1틱마다 물리 연산 수행
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            Iterator<CustomProjectile> it = projectiles.iterator();
            while (it.hasNext()) {
                CustomProjectile proj = it.next();
                if (proj.isDead()) {
                    it.remove();
                } else {
                    proj.tick();
                }
            }
        }, 0L, 1L);
    }

    /**
     * 투사체 발사
     */
    public void shoot(LivingEntity shooter, ItemStack item, double speed, double gravity, double range, Consumer<org.bukkit.entity.Entity> onHitEntity) {
        Location loc = shooter.getEyeLocation();
        Vector dir = loc.getDirection();

        // 1. 시각 효과(ItemDisplay) 생성
        ItemDisplay display = (ItemDisplay) loc.getWorld().spawnEntity(loc, EntityType.ITEM_DISPLAY);
        display.setItemStack(item);

        // 크기 및 회전 조정 (선택 사항)
        display.setTransformation(new Transformation(
                new Vector3f(0, 0, 0),
                new AxisAngle4f(0, 0, 0, 1),
                new Vector3f(0.5f, 0.5f, 0.5f), // 크기 0.5배
                new AxisAngle4f(0, 0, 0, 1)
        ));

        // 2. 투사체 등록
        CustomProjectile proj = new CustomProjectile(
                shooter,
                display,
                dir,
                speed,
                gravity,
                range,
                onHitEntity,
                () -> { /* 벽에 맞았을 때 (파티클 등 추가 가능) */ },
                () -> { /* 사거리 끝 */ }
        );

        projectiles.add(proj);
    }
}