package com.server.core.system.combat;

import com.server.core.CorePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Color;
import org.bukkit.EntityEffect;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Display;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.jetbrains.annotations.NotNull;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.Random;

public class DamageManager {

    private final CorePlugin plugin;
    private final Random random = new Random();

    // 재귀 방지용
    private final java.util.Set<java.util.UUID> processingEntities = new java.util.HashSet<>();
    private @NotNull EntityEffect EntityEffect;

    public DamageManager(CorePlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isProcessing(java.util.UUID uuid) {
        return processingEntities.contains(uuid);
    }

    public void dealDamage(LivingEntity attacker, LivingEntity victim, double damage, boolean isCrit, boolean ignoreArmor) {
        if (victim.isDead()) return;

        processingEntities.add(victim.getUniqueId());

        try {
            if (ignoreArmor) {
                double newHealth = Math.max(0, victim.getHealth() - damage);
                victim.setHealth(newHealth);
                if (newHealth <= 0) victim.damage(0, attacker);
                else victim.playEffect(org.bukkit.EntityEffect.ENTITY_ATTACK);
            } else {
                victim.damage(damage, attacker);
            }
        } finally {
            processingEntities.remove(victim.getUniqueId());
        }

        // 2. 대미지 인디케이터 표시
        spawnDamageIndicator(victim, damage, isCrit);

        if (isCrit) {
            victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 1f);
        }
    }

    private void spawnDamageIndicator(LivingEntity victim, double damage, boolean isCrit) {
        // [수정] 위치를 눈높이(EyeLocation) 위로 설정
        Location loc = victim.getEyeLocation().add(0, 0.5, 0);

        // 랜덤 오프셋 (겹침 방지)
        double xOffset = (random.nextDouble() - 0.5) * 0.5;
        double zOffset = (random.nextDouble() - 0.5) * 0.5;
        Location spawnLoc = loc.add(xOffset, 0, zOffset);

        TextDisplay display = (TextDisplay) loc.getWorld().spawnEntity(spawnLoc, EntityType.TEXT_DISPLAY);

        String damageText = String.format("%.0f", damage);
        TextColor color;
        float scale;

        if (isCrit) {
            damageText = "✨ " + damageText + " ✨";
            color = TextColor.color(0xFF5555); // 빨강
            scale = 1.2f; // 크리티컬은 크게
        } else {
            color = TextColor.color(0xFFFFFF); // 흰색
            scale = 0.8f; // 일반은 적당히
        }

        display.text(Component.text(damageText).color(color));
        display.setBillboard(Display.Billboard.CENTER); // 플레이어 바라보기
        display.setBackgroundColor(Color.fromARGB(0, 0, 0, 0)); // 배경 투명

        // [수정] 벽이나 몹에 가려져도 보이게 설정
        display.setSeeThrough(true);
        display.setShadowed(true);

        display.setTransformation(new Transformation(
                new Vector3f(0, 0, 0),
                new AxisAngle4f(0, 0, 0, 1),
                new Vector3f(scale, scale, scale),
                new AxisAngle4f(0, 0, 0, 1)
        ));

        // [디버그 로그] (안 뜨면 이 메소드 자체가 호출 안 된 것임)
        //plugin.getLogger().info("Damage Indicator Spawned: " + damageText + " at " + spawnLoc);

        // 애니메이션
        plugin.getServer().getScheduler().runTaskTimer(plugin, task -> {
            if (!display.isValid() || display.getTicksLived() > 20) {
                display.remove();
                task.cancel();
                return;
            }
            // 위로 천천히 이동
            display.teleport(display.getLocation().add(0, 0.05, 0));
        }, 0L, 1L);
    }
}