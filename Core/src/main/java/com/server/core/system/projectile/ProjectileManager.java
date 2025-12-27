package com.server.core.system.projectile;

import com.server.core.CorePlugin;
import com.server.core.api.builder.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Display;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
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

    // 기존 shoot 메서드 (단일 이미지)
    public void shoot(LivingEntity shooter, ItemStack item, double speed, double gravity, double range, float scale, Consumer<org.bukkit.entity.Entity> onHitEntity) {
        Location loc = shooter.getEyeLocation();
        // 눈높이 보정
        loc.add(0, -0.5, 0);
        Vector dir = loc.getDirection();

        ItemDisplay display = (ItemDisplay) loc.getWorld().spawnEntity(loc, EntityType.ITEM_DISPLAY);
        display.setItemStack(item);
        display.setTransformation(new Transformation(
                new Vector3f(0, 0, 0),
                new AxisAngle4f(0, 0, 0, 1),
                new Vector3f(scale, scale, scale),
                new AxisAngle4f(0, 0, 0, 1)
        ));

        CustomProjectile proj = new CustomProjectile(
                shooter, display, dir, speed, gravity, range, onHitEntity, null, null
        );
        projectiles.add(proj);
    }

    // [추가] 애니메이션 투사체 발사
    public void shootAnimated(LivingEntity shooter, double speed, double range,
                              double forwardOffset, // [추가] 전방 거리 조절 변수
                              Vector scale,
                              Vector rotation,
                              int startCmd, int frameCount, int tickPerFrame, boolean loop,
                              Consumer<org.bukkit.entity.Entity> onHitEntity) {

        Location loc = shooter.getEyeLocation();
        Vector dir = loc.getDirection();

        // [수정] 위치를 전방으로 3.5칸 이동 (이 숫자를 늘리면 더 멀리서 나갑니다)
        loc.add(dir.clone().multiply(forwardOffset));

        // 높이 보정 (눈높이보다 살짝 아래)
        loc.add(0, -0.5, 0);

        ItemStack firstFrame = new ItemBuilder(Material.SNOWBALL).model(startCmd).build();

        ItemDisplay display = (ItemDisplay) loc.getWorld().spawnEntity(loc, EntityType.ITEM_DISPLAY);
        display.setItemStack(firstFrame);

        display.setBillboard(Display.Billboard.FIXED);

        Quaternionf quaternion = new Quaternionf()
                .rotateX((float) Math.toRadians(rotation.getX()))
                .rotateY((float) Math.toRadians(rotation.getY()))
                .rotateZ((float) Math.toRadians(rotation.getZ()));

        display.setTransformation(new Transformation(
                new Vector3f(0, 0, 0),
                quaternion,
                new Vector3f((float) scale.getX(), (float) scale.getY(), (float) scale.getZ()),
                new Quaternionf()
        ));

        AnimatedProjectile proj = new AnimatedProjectile(
                shooter, display, dir, speed, range,
                startCmd, frameCount, tickPerFrame, loop, onHitEntity
        );

        projectiles.add(proj);
    }

}