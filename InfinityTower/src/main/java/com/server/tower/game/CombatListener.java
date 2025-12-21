package com.server.tower.game;

import com.server.core.api.CoreProvider;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public class CombatListener implements Listener {

    @EventHandler
    public void onSkillUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null) return;

        if (CoreProvider.isCustomItem(item, "infinity_tower:beginner_wand")) {
            useWandSkill(player);
        }
        else if (CoreProvider.isCustomItem(item, "infinity_tower:beginner_sword")) {
            useSwordSkill(player);
        }
    }

    // --- 지팡이 스킬 (마법) ---
    private void useWandSkill(Player player) {
        String skillId = "magic_bolt";
        if (CoreProvider.hasCooldown(player, skillId)) return;

        double intelligence = CoreProvider.getStat(player, "int");

        // [수정] 마법 공식: 스킬기본댐 * (1 + INT%)
        double skillBaseDmg = 10.0;
        double damage = skillBaseDmg * (1.0 + (intelligence * 0.01));

        ItemStack projectileVisual = CoreProvider.getItem("infinity_tower:beginner_wand");

        CoreProvider.shootProjectile(player, projectileVisual, 1.5, 20.0, (target) -> {
            if (target instanceof LivingEntity victim) {
                boolean isCrit = Math.random() < 0.2;
                double finalDmg = damage; // 람다용 변수
                if (isCrit) finalDmg *= 1.5;

                CoreProvider.dealDamage(player, victim, finalDmg, isCrit);
                victim.getWorld().spawnParticle(Particle.WITCH, victim.getLocation().add(0, 1, 0), 10, 0.5, 0.5, 0.5, 0.1);
            }
        });

        player.playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1f, 1.5f);
        CoreProvider.setCooldown(player, skillId, 10L);
    }

    // --- 검 스킬 (물리) ---
    private void useSwordSkill(Player player) {
        String skillId = "power_slash";
        if (CoreProvider.hasCooldown(player, skillId)) return;

        double strength = CoreProvider.getStat(player, "str");

        // [수정] 물리 공식: 스킬기본댐 * (1 + STR%)
        double skillBaseDmg = 15.0;
        double damage = skillBaseDmg * (1.0 + (strength * 0.01));

        player.getNearbyEntities(3, 3, 3).forEach(entity -> {
            if (entity instanceof LivingEntity victim && entity != player) {
                if (player.getLocation().getDirection().dot(victim.getLocation().subtract(player.getLocation()).toVector().normalize()) > 0.5) {
                    CoreProvider.dealDamage(player, victim, damage, true);
                }
            }
        });

        player.getWorld().spawnParticle(Particle.SWEEP_ATTACK, player.getLocation().add(player.getLocation().getDirection().multiply(1.5)).add(0, 1, 0), 1);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 1f);
        CoreProvider.setCooldown(player, skillId, 20L);
    }

    // --- 일반 공격 (평타) ---
    @EventHandler
    public void onAttack(EntityDamageByEntityEvent event) {
        if (CoreProvider.isDamageProcessing(event.getEntity().getUniqueId())) return;
        if (!(event.getDamager() instanceof Player player)) return;
        if (!(event.getEntity() instanceof LivingEntity victim)) return;

        ItemStack weapon = player.getInventory().getItemInMainHand();

        // 1. 무기 대미지 확인
        double phys = CoreProvider.getItemDataInt(weapon, "stat_phys_atk");
        double mag = CoreProvider.getItemDataInt(weapon, "stat_mag_atk");
        double base = CoreProvider.getItemDataInt(weapon, "damage");

        // [수정] 무기 데이터가 없으면(0이면) 바닐라 대미지를 가져와서라도 진행 (인디케이터 표시 위해)
        double weaponDamage = Math.max(phys, Math.max(mag, base));
        if (weaponDamage <= 0) {
            weaponDamage = event.getDamage(); // 바닐라 대미지
            if (weaponDamage <= 0) weaponDamage = 1.0; // 최소값 보장
        }

        // 2. 스탯 보정 (STR or INT)
        String scalingStat = CoreProvider.getItemDataString(weapon, "scaling_stat");
        double statValue = 0;

        if ("phys_atk".equals(scalingStat)) {
            statValue = CoreProvider.getStat(player, "phys_atk");
        } else if ("mag_atk".equals(scalingStat)) {
            statValue = CoreProvider.getStat(player, "mag_atk");
        } else {
            // 스케일링 정보가 없으면(맨손 등) 물리(STR) 기본 적용
            statValue = CoreProvider.getStat(player, "phys_atk");
        }

        // [공식] 무기댐 * (1 + 스탯%)
        double finalDamage = weaponDamage * (1.0 + (statValue * 0.01));

        // 3. 쿨타임 패널티
        float cooldownFactor = player.getAttackCooldown();
        if (cooldownFactor < 0.9f) {
            finalDamage *= (0.2 + (cooldownFactor * 0.8));
        }

        // 4. 치명타 (LUK -> crit_chance)
        boolean isCrit = false;
        if (cooldownFactor > 0.9f) {
            double critChance = CoreProvider.getStat(player, "crit_chance");
            if (Math.random() * 100 < critChance) {
                isCrit = true;
                double critDmg = CoreProvider.getStat(player, "crit_damage");
                if (critDmg <= 0) critDmg = 150.0;
                finalDamage *= (critDmg / 100.0);
            }
        }

        // 5. 적 방어/회피
        if (victim instanceof Player victimPlayer) {
            double dodge = CoreProvider.getStat(victimPlayer, "dodge");
            if (Math.random() * 100 < dodge) {
                player.sendMessage("§7공격이 빗나갔습니다!");
                event.setCancelled(true);
                return;
            }
            double def = CoreProvider.getStat(victimPlayer, "def");
            finalDamage = Math.max(1.0, finalDamage - def);
        }

        // 6. 적용
        event.setDamage(0);
        CoreProvider.dealDamage(player, victim, finalDamage, isCrit);
    }
}