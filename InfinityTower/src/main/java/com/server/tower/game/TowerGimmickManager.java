package com.server.tower.game;

import com.server.core.api.CoreProvider;
import com.server.tower.TowerPlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Monster;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;

public class TowerGimmickManager {

    private final TowerPlugin plugin;

    public TowerGimmickManager(TowerPlugin plugin) {
        this.plugin = plugin;
    }

    // [추가된 메서드] 안내 메시지 출력
    public void announceGimmick(String id) {
        if (id == null) return;
        switch (id) {
            case "BLOOD_ALTAR":
                Bukkit.broadcast(Component.text("§c[!] 피의 제단이 나타났습니다. 우클릭하여 힘을 얻으세요."));
                break;
            case "CURSE_TOTEM":
                Bukkit.broadcast(Component.text("§5[!] 저주받은 토템이 몬스터들을 보호하고 있습니다! 먼저 파괴하세요!"));
                break;
        }
    }

    public UUID spawnGimmick(String id, Location loc) {
        if (id == null) return null;
        switch (id) {
            case "BLOOD_ALTAR": return spawnBloodAltar(loc);
            case "CURSE_TOTEM": return spawnCurseTotem(loc);
            default:
                plugin.getLogger().warning("알 수 없는 기믹 ID: " + id);
                return null;
        }
    }

    private UUID spawnBloodAltar(Location loc) {
        // 메시지 삭제됨 (announceGimmick에서 처리)
        return CoreProvider.spawnInteractableGimmick(loc, Material.REDSTONE_BLOCK, player -> {
            double cost = player.getMaxHealth() * 0.3;
            if (player.getHealth() > cost) {
                player.damage(cost);
                player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 1200, 1));
                player.sendMessage("§c[피의 제단] §7피를 바쳐 §c강력한 힘§7을 얻었습니다!");
                player.playSound(player.getLocation(), Sound.ENTITY_ILLUSIONER_PREPARE_MIRROR, 1f, 0.5f);
                loc.getWorld().spawnParticle(Particle.FLAME, loc.clone().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.1);
            } else {
                player.sendMessage("§c[!] 제물을 바치기엔 체력이 부족합니다.");
            }
        });
    }

    private UUID spawnCurseTotem(Location loc) {
        // 메시지 삭제됨 (announceGimmick에서 처리)

        BukkitTask buffTask = new BukkitRunnable() {
            @Override
            public void run() {
                loc.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, loc.clone().add(0, 1.5, 0), 5, 0.2, 0.2, 0.2, 0.05);
                loc.getWorld().getNearbyEntities(loc, 20, 10, 20).forEach(entity -> {
                    if (entity instanceof Monster monster && !entity.isDead()) {
                        monster.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 40, 4));
                        monster.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 40, 1));

                        if (Math.random() < 0.1) {
                            spawnParticleLine(loc.clone().add(0, 1, 0), monster.getEyeLocation());
                        }
                    }
                });
            }
        }.runTaskTimer(plugin, 0L, 20L);

        return CoreProvider.spawnDestructibleGimmick(loc, Material.CRYING_OBSIDIAN, 50, () -> {
            buffTask.cancel();
            Bukkit.broadcast(Component.text("§a[!] 저주받은 토템이 파괴되었습니다!"));
            loc.getWorld().playSound(loc, Sound.BLOCK_BEACON_DEACTIVATE, 1f, 0.5f);
            loc.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, loc, 1);
        });
    }

    private void spawnParticleLine(Location start, Location end) {
        double distance = start.distance(end);
        org.bukkit.util.Vector dir = end.toVector().subtract(start.toVector()).normalize();
        for (double i = 0; i < distance; i += 1.0) {
            start.getWorld().spawnParticle(Particle.WITCH, start.clone().add(dir.clone().multiply(i)), 1, 0, 0, 0, 0);
        }
    }
}