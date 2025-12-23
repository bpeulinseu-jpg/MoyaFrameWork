package com.server.tower.game;

import com.server.core.api.CoreProvider;
import com.server.tower.system.transcendence.UniqueAbility;
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

        // [수정] 지능 계수 상향 및 기본댐 조정
        double skillBaseDmg = 15.0;
        double damage = skillBaseDmg * (1.0 + (intelligence * 0.02)); // 지능 1당 2%

        // [추가] 공격자 버프 적용 (마법도 힘 버프 영향 받게 할지, 지능 버프 따로 만들지 결정 필요. 여기선 힘 적용 X)

        ItemStack projectileVisual = CoreProvider.getItem("infinity_tower:beginner_wand");

        CoreProvider.shootProjectile(player, projectileVisual, 1.5, 20.0, (target) -> {
            if (target instanceof LivingEntity victim) {
                boolean isCrit = random.nextDouble() < 0.2; // 마법 크리티컬 20% 고정 (혹은 LUK 비례)
                double finalDmg = damage * (isCrit ? 1.5 : 1.0);

                // [추가] 피해자 저항 적용
                finalDmg = applyDefenseLogic(victim, finalDmg);

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
        double skillBaseDmg = 20.0;
        double damage = skillBaseDmg * (1.0 + (strength * 0.02)); // 힘 1당 2%

        // [추가] 버프 적용
        damage = applyAttackBuffs(player, damage);

        // 람다식 내부에서 변수 사용을 위해 final 처리
        double finalDamage = damage;

        player.getNearbyEntities(3, 3, 3).forEach(entity -> {
            if (entity instanceof LivingEntity victim && entity != player) {
                if (player.getLocation().getDirection().dot(victim.getLocation().subtract(player.getLocation()).toVector().normalize()) > 0.5) {
                    // [추가] 방어 로직 적용
                    double actualDmg = applyDefenseLogic(victim, finalDamage);
                    CoreProvider.dealDamage(player, victim, actualDmg, true);
                }
            }
        });

        player.getWorld().spawnParticle(Particle.SWEEP_ATTACK, player.getLocation().add(player.getLocation().getDirection().multiply(1.5)).add(0, 1, 0), 1);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 1f);
        CoreProvider.setCooldown(player, skillId, 20L);
    }

    // --- 2. 일반 공격 및 패시브 발동 ---
    @EventHandler
    public void onAttack(EntityDamageByEntityEvent event) {
        if (CoreProvider.isDamageProcessing(event.getEntity().getUniqueId())) return;
        if (!(event.getDamager() instanceof Player player)) return;
        if (!(event.getEntity() instanceof LivingEntity victim)) return;

        ItemStack weapon = player.getInventory().getItemInMainHand();

        // 1. 기본 무기 대미지
        double phys = CoreProvider.getItemDataInt(weapon, "stat_phys_atk");
        double mag = CoreProvider.getItemDataInt(weapon, "stat_mag_atk");
        double base = CoreProvider.getItemDataInt(weapon, "damage");

        double weaponDamage = Math.max(phys, Math.max(mag, base));
        if (weaponDamage <= 0) {
            weaponDamage = event.getDamage();
            if (weaponDamage <= 0) weaponDamage = 1.0;
        }

        // 2. 스탯 보정
        String scalingStat = CoreProvider.getItemDataString(weapon, "scaling_stat");
        double statValue;
        if ("mag_atk".equals(scalingStat)) {
            statValue = CoreProvider.getStat(player, "int");
        } else {
            statValue = CoreProvider.getStat(player, "str");
        }

        double finalDamage = weaponDamage * (1.0 + (statValue * 0.01));

        // [신규] 3. 공격자 버프(힘 포션 등) 적용
        finalDamage = applyAttackBuffs(player, finalDamage);

        // 4. 쿨타임 패널티
        float cooldownFactor = player.getAttackCooldown();
        if (cooldownFactor < 0.9f) {
            finalDamage *= (0.2 + (cooldownFactor * 0.8));
        }

        // 5. 치명타
        boolean isCrit = false;
        if (cooldownFactor > 0.9f) {
            double critChance = CoreProvider.getStat(player, "crit_chance");
            if (random.nextDouble() * 100 < critChance) {
                isCrit = true;
                double critDmg = CoreProvider.getStat(player, "crit_damage");
                if (critDmg <= 0) critDmg = 150.0;
                finalDamage *= (critDmg / 100.0);
            }
        }

        // 6. 고유 능력 (Unique Ability)
        String abilityName = CoreProvider.getItemDataString(weapon, "unique_ability");
        if (abilityName != null && cooldownFactor > 0.9f) {
            try {
                UniqueAbility ability = UniqueAbility.valueOf(abilityName);
                switch (ability) {
                    case THUNDER_STRIKE:
                        if (random.nextDouble() < 0.3) {
                            finalDamage *= 1.5; // 50% 증폭
                            victim.getWorld().strikeLightningEffect(victim.getLocation());
                            player.playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.5f, 2f);
                            player.sendMessage("§e⚡ 뇌제 발동!");
                        }
                        break;
                    case INFERNO:
                        victim.setFireTicks(100);
                        victim.getWorld().spawnParticle(Particle.FLAME, victim.getLocation(), 15, 0.5, 0.5, 0.5, 0.1);
                        break;
                }
            } catch (IllegalArgumentException ignored) {}
        }

        // [신규] 7. 피해자 방어 로직 (회피, 방어력, 저항 버프)
        finalDamage = applyDefenseLogic(victim, finalDamage);

        // 회피가 떠서 대미지가 0이 되었을 경우 이벤트 취소
        if (finalDamage <= 0) {
            event.setDamage(0);
            event.setCancelled(true);
            return;
        }

        // 8. 최종 적용
        event.setDamage(0);
        CoreProvider.dealDamage(player, victim, finalDamage, isCrit);
    }

    // --- [Helper] 공격 버프 계산 ---
    private double applyAttackBuffs(LivingEntity attacker, double damage) {
        // 힘(Strength) 효과 체크
        PotionEffect strength = attacker.getPotionEffect(PotionEffectType.STRENGTH);
        if (strength != null) {
            int lvl = strength.getAmplifier() + 1;
            // 레벨당 30% 증가 (바닐라는 고정수치지만 RPG는 %가 좋음)
            damage *= (1.0 + (lvl * 0.3));
        }
        return damage;
    }

    // --- [Helper] 방어 로직 계산 ---
    private double applyDefenseLogic(LivingEntity victim, double damage) {
        // 1. 회피 (플레이어인 경우)
        if (victim instanceof Player p) {
            double dodge = CoreProvider.getStat(p, "dodge");
            if (random.nextDouble() * 100 < dodge) {
                p.sendMessage("§7공격이 빗나갔습니다!");
                return 0.0; // 회피 성공
            }
        }

        // 2. 저항(Resistance) 버프 체크 (기믹 토템 등)
        PotionEffect resistance = victim.getPotionEffect(PotionEffectType.RESISTANCE);
        if (resistance != null) {
            int lvl = resistance.getAmplifier() + 1;
            // 레벨당 20% 감소 (5레벨이면 100% 면역)
            double reduction = lvl * 0.2;
            damage *= Math.max(0.0, 1.0 - reduction);
        }

        // [이동됨] 3. 리빙 아머 정면 방어 (몹 ID 확인)
        String mobId = CoreProvider.getCustomMobId(victim);
        if (mobId != null && mobId.endsWith("living_armor")) {
            // 공격자가 있는 경우에만 방향 계산
            if (victim.getLastDamageCause() instanceof EntityDamageByEntityEvent dmgEvent
                    && dmgEvent.getDamager() instanceof LivingEntity attacker) {

                org.bukkit.util.Vector victimDir = victim.getLocation().getDirection();
                org.bukkit.util.Vector attackDir = attacker.getLocation().getDirection();

                // 서로 마주보는 경우 (내적 < -0.5)
                if (victimDir.dot(attackDir) < -0.5) {
                    damage *= 0.2; // 80% 감소
                    victim.getWorld().playSound(victim.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1f, 0.5f);
                    victim.getWorld().spawnParticle(Particle.CRIT, victim.getEyeLocation(), 5);
                    // (선택) 공격자에게 메시지
                    if (attacker instanceof Player p) p.sendActionBar(net.kyori.adventure.text.Component.text("§7[방어] 대미지가 감소되었습니다."));
                }
            }
        }

        // 4. 고정 방어력 (DEF)
        // 몬스터는 MobManager에서 설정한 방어력이 없으므로, 필요하면 MobData에 def 필드를 추가해야 함.
        // 여기서는 플레이어의 방어력만 적용
        if (victim instanceof Player p) {
            double def = CoreProvider.getStat(p, "def");
            damage = Math.max(1.0, damage - def);
        }

        return damage;
    }

    // --- 3. 처치 시 발동 ---
    @EventHandler
    public void onKill(EntityDeathEvent event) {
        if (event.getEntity().getKiller() == null) return;
        Player killer = event.getEntity().getKiller();

        ItemStack weapon = killer.getInventory().getItemInMainHand();
        String abilityName = CoreProvider.getItemDataString(weapon, "unique_ability");

        if (abilityName != null) {
            try {
                UniqueAbility ability = UniqueAbility.valueOf(abilityName);
                if (ability == UniqueAbility.WIND_WALKER) {
                    killer.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, 1));
                    killer.getWorld().spawnParticle(Particle.CLOUD, killer.getLocation(), 10, 0.5, 0.1, 0.5, 0.1);
                    killer.playSound(killer.getLocation(), Sound.ENTITY_PHANTOM_FLAP, 1f, 2f);
                    killer.sendActionBar(net.kyori.adventure.text.Component.text("§b💨 신속 발동!"));
                }
            } catch (IllegalArgumentException ignored) {}
        }
    }
}