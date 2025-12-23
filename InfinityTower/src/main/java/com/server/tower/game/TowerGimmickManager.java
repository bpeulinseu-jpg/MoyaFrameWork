package com.server.tower.game;

import com.server.core.api.CoreProvider;
import com.server.tower.TowerPlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

public class TowerGimmickManager {

    private final TowerPlugin plugin;

    public TowerGimmickManager(TowerPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * ID에 맞는 기믹을 해당 위치에 소환합니다.
     */
    public void spawnGimmick(String id, Location loc) {
        if (id == null) return;

        switch (id) {
            case "BLOOD_ALTAR" -> spawnBloodAltar(loc);
            case "CURSE_TOTEM" -> spawnCurseTotem(loc);
            default -> plugin.getLogger().warning("알 수 없는 기믹 ID: " + id);
        }
    }

    // --- 1. 피의 제단 (Blood Altar) ---
    // 효과: 체력을 희생하여 공격력 버프 획득
    private void spawnBloodAltar(Location loc) {
        // 시각 효과: 레드스톤 블록
        CoreProvider.spawnInteractableGimmick(loc, Material.REDSTONE_BLOCK, player -> {

            double cost = player.getMaxHealth() * 0.3; // 체력 30% 소모

            if (player.getHealth() > cost) {
                // 1. 대가 지불
                player.damage(cost);
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_HURT, 1f, 1f);

                // 2. 보상 (힘 2단계 1분)
                player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 1200, 1));
                player.sendMessage("§c[피의 제단] §7피를 바쳐 §c강력한 힘§7을 얻었습니다!");

                // 3. 효과음 및 파티클
                player.playSound(player.getLocation(), Sound.ENTITY_ILLUSIONER_PREPARE_MIRROR, 1f, 0.5f);
                loc.getWorld().spawnParticle(Particle.FLAME, loc.clone().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.1);

            } else {
                player.sendMessage("§c[!] 제물을 바치기엔 체력이 부족합니다.");
                player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 1f, 2f);
            }
        });

        // 안내 메시지
        Bukkit.broadcast(Component.text("§c[!] 피의 제단이 나타났습니다. 우클릭하여 힘을 얻으세요."));
    }

    // --- 2. 저주받은 토템 (Cursed Totem) ---
    // 효과: 파괴되기 전까지 몬스터들에게 강력한 버프(저항/재생) 부여
    private void spawnCurseTotem(Location loc) {

        // 1. 버프를 주는 반복 태스크 시작
        BukkitTask buffTask = new BukkitRunnable() {
            @Override
            public void run() {
                // 토템 주변 20칸 내의 몬스터들에게 버프
                loc.getWorld().getNearbyEntities(loc, 20, 10, 20).forEach(entity -> {
                    if (entity instanceof Monster monster && !entity.isDead()) {
                        // 저항 5 (대미지 100% 감소) + 재생 2
                        monster.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 40, 5));
                        monster.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 40, 1));

                        // 연결선 파티클 (토템 -> 몬스터)
                        if (Math.random() < 0.1) {
                            spawnParticleLine(loc.clone().add(0, 1, 0), monster.getEyeLocation());
                        }
                    }
                });

                // 토템 자체 파티클
                loc.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, loc.clone().add(0, 1.5, 0), 5, 0.2, 0.2, 0.2, 0.05);
            }
        }.runTaskTimer(plugin, 0L, 20L); // 1초마다 실행

        // 2. 파괴 가능한 기믹 생성 (체력 50)
        CoreProvider.spawnDestructibleGimmick(loc, Material.CRYING_OBSIDIAN, 50, () -> {
            // 파괴 시 실행될 콜백
            buffTask.cancel(); // 버프 중단

            Bukkit.broadcast(Component.text("§a[!] 저주받은 토템이 파괴되었습니다! 몬스터의 보호막이 사라집니다."));
            loc.getWorld().playSound(loc, Sound.BLOCK_BEACON_DEACTIVATE, 1f, 0.5f);
            loc.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, loc, 1);
        });

        Bukkit.broadcast(Component.text("§5[!] 저주받은 토템이 몬스터들을 보호하고 있습니다! 먼저 파괴하세요!"));
    }

    // 유틸리티: 파티클 선 그리기
    private void spawnParticleLine(Location start, Location end) {
        double distance = start.distance(end);
        org.bukkit.util.Vector dir = end.toVector().subtract(start.toVector()).normalize();
        for (double i = 0; i < distance; i += 1.0) {
            start.getWorld().spawnParticle(Particle.WITCH, start.clone().add(dir.clone().multiply(i)), 1, 0, 0, 0, 0);
        }
    }
}