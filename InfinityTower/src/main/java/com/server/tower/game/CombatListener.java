package com.server.tower.game;

import com.server.core.api.CoreProvider;
import com.server.tower.system.transcendence.UniqueAbility; // Enum 경로 확인 필요
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Random;

public class CombatListener implements Listener {

    private final Random random = new Random();

    // --- 1. 액티브 스킬 (우클릭) ---
    @EventHandler
    public void onSkillUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null) return;

        // 아이템 ID 기반 스킬 분기
        if (CoreProvider.isCustomItem(item, "infinity_tower:beginner_wand")) {
            useWandSkill(player);
        }
        else if (CoreProvider.isCustomItem(item, "infinity_tower:beginner_sword")) {
            useSwordSkill(player);
        }
    }

    private void useWandSkill(Player player) {
        String skillId = "magic_bolt";
        if (CoreProvider.hasCooldown(player, skillId)) return;

        double intelligence = CoreProvider.getStat(player, "int");
        double skillBaseDmg = 10.0;
        double damage = skillBaseDmg * (1.0 + (intelligence * 0.01));

        ItemStack projectileVisual = CoreProvider.getItem("infinity_tower:beginner_wand");

        CoreProvider.shootProjectile(player, projectileVisual, 1.5, 20.0, (target) -> {
            if (target instanceof LivingEntity victim) {
                boolean isCrit = random.nextDouble() < 0.2;
                double finalDmg = damage * (isCrit ? 1.5 : 1.0);

                CoreProvider.dealDamage(player, victim, finalDmg, isCrit);
                victim.getWorld().spawnParticle(Particle.WITCH, victim.getLocation().add(0, 1, 0), 10, 0.5, 0.5, 0.5, 0.1);
            }
        });

        player.playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1f, 1.5f);
        CoreProvider.setCooldown(player, skillId, 10L);
    }

    private void useSwordSkill(Player player) {
        String skillId = "power_slash";
        if (CoreProvider.hasCooldown(player, skillId)) return;

        double strength = CoreProvider.getStat(player, "str");
        double skillBaseDmg = 15.0;
        double damage = skillBaseDmg * (1.0 + (strength * 0.01));

        player.getNearbyEntities(3, 3, 3).forEach(entity -> {
            if (entity instanceof LivingEntity victim && entity != player) {
                // 시선 방향 내적 (앞에 있는 적만)
                if (player.getLocation().getDirection().dot(victim.getLocation().subtract(player.getLocation()).toVector().normalize()) > 0.5) {
                    CoreProvider.dealDamage(player, victim, damage, true);
                }
            }
        });

        player.getWorld().spawnParticle(Particle.SWEEP_ATTACK, player.getLocation().add(player.getLocation().getDirection().multiply(1.5)).add(0, 1, 0), 1);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 1f);
        CoreProvider.setCooldown(player, skillId, 20L);
    }

    // --- 2. 일반 공격 및 패시브 발동 (통합됨) ---
    @EventHandler
    public void onAttack(EntityDamageByEntityEvent event) {
        // 무한 루프 방지 (dealDamage가 이벤트를 다시 호출하므로)
        if (CoreProvider.isDamageProcessing(event.getEntity().getUniqueId())) return;

        if (!(event.getDamager() instanceof Player player)) return;
        if (!(event.getEntity() instanceof LivingEntity victim)) return;

        ItemStack weapon = player.getInventory().getItemInMainHand();

        // 1. 기본 무기 대미지 계산
        double phys = CoreProvider.getItemDataInt(weapon, "stat_phys_atk");
        double mag = CoreProvider.getItemDataInt(weapon, "stat_mag_atk");
        double base = CoreProvider.getItemDataInt(weapon, "damage");

        double weaponDamage = Math.max(phys, Math.max(mag, base));
        if (weaponDamage <= 0) {
            weaponDamage = event.getDamage(); // 바닐라 대미지
            if (weaponDamage <= 0) weaponDamage = 1.0;
        }

        // 2. 스탯 보정 (STR/INT)
        String scalingStat = CoreProvider.getItemDataString(weapon, "scaling_stat");
        double statValue;
        if ("mag_atk".equals(scalingStat)) {
            statValue = CoreProvider.getStat(player, "int"); // INT 기반
        } else {
            statValue = CoreProvider.getStat(player, "str"); // STR 기반 (기본)
        }

        double finalDamage = weaponDamage * (1.0 + (statValue * 0.01));

        // 3. 쿨타임 패널티 (1.9+ 공격 속도)
        float cooldownFactor = player.getAttackCooldown();
        if (cooldownFactor < 0.9f) {
            finalDamage *= (0.2 + (cooldownFactor * 0.8));
        }

        // 4. 치명타 계산 (LUK)
        boolean isCrit = false;
        if (cooldownFactor > 0.9f) {
            double critChance = CoreProvider.getStat(player, "crit_chance"); // 예: 50.0
            if (random.nextDouble() * 100 < critChance) {
                isCrit = true;
                double critDmg = CoreProvider.getStat(player, "crit_damage");
                if (critDmg <= 0) critDmg = 150.0; // 기본 150%
                finalDamage *= (critDmg / 100.0);
            }
        }

        // =========================================================
        // [NEW] 고유 능력(Unique Ability) 발동 로직 (통합됨)
        // =========================================================
        String abilityName = CoreProvider.getItemDataString(weapon, "unique_ability");
        if (abilityName != null && cooldownFactor > 0.9f) { // 쿨타임 다 찼을 때만 발동
            try {
                UniqueAbility ability = UniqueAbility.valueOf(abilityName);
                switch (ability) {
                    case THUNDER_STRIKE: // 뇌제: 30% 확률로 추가 대미지 + 이펙트
                        if (random.nextDouble() < 0.3) {
                            // 대미지 50% 증폭
                            finalDamage *= 0.5;

                            // 시각 효과
                            victim.getWorld().strikeLightningEffect(victim.getLocation());
                            player.playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.5f, 2f);
                            player.sendMessage("§e⚡ 뇌제 발동!");
                        }
                        break;

                    case INFERNO: // 염화: 100% 확률로 화상
                        victim.setFireTicks(100); // 5초
                        victim.getWorld().spawnParticle(Particle.FLAME, victim.getLocation(), 15, 0.5, 0.5, 0.5, 0.1);
                        break;
                }
            } catch (IllegalArgumentException ignored) {}
        }
        // =========================================================

        // 5. 적 방어력 및 회피 (PvP/PvE)
        if (victim instanceof Player victimPlayer) {
            double dodge = CoreProvider.getStat(victimPlayer, "dodge");
            if (random.nextDouble() * 100 < dodge) {
                player.sendMessage("§7공격이 빗나갔습니다!");
                // 회피 시 대미지 0 (이벤트 취소하면 넉백도 안 들어감. 0으로 설정 권장)
                event.setDamage(0);
                event.setCancelled(true);
                return;
            }
            double def = CoreProvider.getStat(victimPlayer, "def");
            finalDamage = Math.max(1.0, finalDamage - def);
        }

        // 6. 최종 적용
        // CoreProvider.dealDamage는 내부적으로 event.setDamage를 호출하거나
        // 직접 체력을 깎고 대미지 인디케이터(홀로그램)를 띄워줍니다.
        event.setDamage(0); // 기본 이벤트 대미지는 무시
        CoreProvider.dealDamage(player, victim, finalDamage, isCrit);
    }

    // --- 3. 처치 시 발동 (신속 등) ---
    @EventHandler
    public void onKill(EntityDeathEvent event) {
        if (event.getEntity().getKiller() == null) return;
        Player killer = event.getEntity().getKiller();

        ItemStack weapon = killer.getInventory().getItemInMainHand();
        String abilityName = CoreProvider.getItemDataString(weapon, "unique_ability");

        if (abilityName != null) {
            try {
                UniqueAbility ability = UniqueAbility.valueOf(abilityName);
                if (ability == UniqueAbility.WIND_WALKER) { // 신속
                    // 신속 II (3초)
                    killer.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, 1));

                    // 이펙트
                    killer.getWorld().spawnParticle(Particle.CLOUD, killer.getLocation(), 10, 0.5, 0.1, 0.5, 0.1);
                    killer.playSound(killer.getLocation(), Sound.ENTITY_PHANTOM_FLAP, 1f, 2f);
                    killer.sendActionBar(net.kyori.adventure.text.Component.text("§b💨 신속 발동!"));
                }
            } catch (IllegalArgumentException ignored) {}
        }
    }
}