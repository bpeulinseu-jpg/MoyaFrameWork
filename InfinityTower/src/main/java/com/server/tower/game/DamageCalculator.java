package com.server.tower.game;

import com.server.core.api.CoreProvider;
import com.server.tower.system.transcendence.UniqueAbility;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Random;

public class DamageCalculator {

    private static final Random random = new Random();

    // 계산 결과를 담을 객체
    public record DamageResult(double damage, boolean isCrit, boolean isCancelled) {}

    /**
     * 최종 대미지를 계산합니다.
     * @param attacker 공격자
     * @param victim 피해자
     * @param skillMultiplier 스킬 계수 (평타는 1.0, 스킬은 1.5, 2.0 등)
     * @param isSkill 스킬 공격 여부 (true면 쿨타임 패널티 무시)
     */
    public static DamageResult calculate(Player attacker, LivingEntity victim, double skillMultiplier, boolean isSkill) {
        ItemStack weapon = attacker.getInventory().getItemInMainHand();

        // 1. 기본 무기 대미지 확인
        double phys = CoreProvider.getItemDataInt(weapon, "stat_phys_atk");
        double mag = CoreProvider.getItemDataInt(weapon, "stat_mag_atk");
        double base = CoreProvider.getItemDataInt(weapon, "damage");

        double weaponDamage = Math.max(phys, Math.max(mag, base));
        if (weaponDamage <= 0) weaponDamage = 1.0; // 맨손 등

        // 2. 스탯 보정 (STR/INT)
        String scalingStat = CoreProvider.getItemDataString(weapon, "scaling_stat");
        double statValue;
        if ("mag_atk".equals(scalingStat)) {
            statValue = CoreProvider.getStat(attacker, "int");
        } else {
            statValue = CoreProvider.getStat(attacker, "str");
        }

        // 기본 공식: (무기댐 * 스킬계수) * (1 + 스탯%)
        double finalDamage = (weaponDamage * skillMultiplier) * (1.0 + (statValue * 0.01));

        // 3. 공격자 버프(힘) 적용
        finalDamage = applyAttackBuffs(attacker, finalDamage);

        // 4. 쿨타임 패널티 (스킬은 무시)
        if (!isSkill) {
            float cooldownFactor = attacker.getAttackCooldown();
            if (cooldownFactor < 0.9f) {
                finalDamage *= (0.2 + (cooldownFactor * 0.8));
            }
        }

        // 5. 치명타 계산 (LUK)
        // 스킬도 치명타가 터지게 할 것인가? -> 보통 YES
        boolean isCrit = false;
        // 평타일 땐 쿨타임 찼을 때만, 스킬은 항상 체크
        if (isSkill || attacker.getAttackCooldown() > 0.9f) {
            double critChance = CoreProvider.getStat(attacker, "crit_chance");
            if (random.nextDouble() * 100 < critChance) {
                isCrit = true;
                double critDmg = CoreProvider.getStat(attacker, "crit_damage");
                if (critDmg <= 0) critDmg = 150.0;
                finalDamage *= (critDmg / 100.0);
            }
        }

        // 6. 고유 능력 (Unique Ability) - 뇌제 등
        String abilityName = CoreProvider.getItemDataString(weapon, "unique_ability");
        if (abilityName != null) {
            try {
                UniqueAbility ability = UniqueAbility.valueOf(abilityName);
                switch (ability) {
                    case THUNDER_STRIKE:
                        if (random.nextDouble() < 0.3) {
                            finalDamage *= 1.5;
                            victim.getWorld().strikeLightningEffect(victim.getLocation());
                            attacker.playSound(attacker.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.5f, 2f);
                            attacker.sendMessage("§e⚡ 뇌제 발동!");
                        }
                        break;
                    case INFERNO:
                        victim.setFireTicks(100);
                        victim.getWorld().spawnParticle(Particle.FLAME, victim.getLocation(), 15, 0.5, 0.5, 0.5, 0.1);
                        break;
                }
            } catch (IllegalArgumentException ignored) {}
        }

        // 7. 피해자 방어 로직 (회피, 방어력, 저항)
        finalDamage = applyDefenseLogic(victim, finalDamage);

        // 회피 등으로 대미지가 0이 되면 캔슬 처리
        if (finalDamage <= 0) {
            return new DamageResult(0, false, true);
        }

        return new DamageResult(finalDamage, isCrit, false);
    }

    // --- Helper Methods (기존 로직 이동) ---

    private static double applyAttackBuffs(LivingEntity attacker, double damage) {
        PotionEffect strength = attacker.getPotionEffect(PotionEffectType.STRENGTH);
        if (strength != null) {
            int lvl = strength.getAmplifier() + 1;
            damage *= (1.0 + (lvl * 0.3));
        }
        return damage;
    }

    private static double applyDefenseLogic(LivingEntity victim, double damage) {
        // 1. 회피 (플레이어)
        if (victim instanceof Player p) {
            double dodge = CoreProvider.getStat(p, "dodge");
            if (random.nextDouble() * 100 < dodge) {
                p.sendMessage("§7공격이 빗나갔습니다!");
                return 0.0;
            }
        }

        // 2. 저항 버프
        PotionEffect resistance = victim.getPotionEffect(PotionEffectType.RESISTANCE);
        if (resistance != null) {
            int lvl = resistance.getAmplifier() + 1;
            double reduction = lvl * 0.2;
            damage *= Math.max(0.0, 1.0 - reduction);
        }

        // 3. 리빙 아머 정면 방어
        String mobId = CoreProvider.getCustomMobId(victim);
        if (mobId != null && mobId.endsWith("living_armor")) {
            // 공격자가 있는 경우 (계산기 호출 시점에는 공격자를 알 수 있음)
            // 주의: victim.getLastDamageCause()는 이전 틱의 정보일 수 있으므로
            // 여기서는 벡터 계산을 생략하거나, 인자로 방향을 받아야 완벽함.
            // 하지만 간소화를 위해 일단 패스하거나, 리빙아머 로직은 리스너에 두는 게 나을 수도 있음.
            // 여기서는 '계산기' 역할에 집중하기 위해 리빙아머 로직은 제외하거나,
            // CombatListener/SkillHandler에서 별도로 처리하는 것을 권장.
        }

        // 4. 고정 방어력 (DEF)
        if (victim instanceof Player p) {
            double def = CoreProvider.getStat(p, "def");
            damage = Math.max(1.0, damage - def);
        }

        return damage;
    }
}