package com.server.tower.game;

import com.server.core.api.CoreProvider;
import com.server.tower.TowerPlugin;
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

public class CombatListener implements Listener {

    // --- 1. 액티브 스킬 및 평타 이펙트 (좌/우클릭) ---
    @EventHandler
    public void onSkillUse(PlayerInteractEvent event) {
        // 우클릭: 액티브 스킬 발동
        if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            TowerPlugin.getInstance().getSkillManager().castSkill(event.getPlayer(), true);
        }
        // 좌클릭: 평타 이펙트 (파티클 등)
        else if (event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK) {
            TowerPlugin.getInstance().getSkillManager().castSkill(event.getPlayer(), false);
        }
    }

    // --- 2. 실제 대미지 적용 (평타) ---
    @EventHandler
    public void onAttack(EntityDamageByEntityEvent event) {
        // 무한 루프 방지
        if (CoreProvider.isDamageProcessing(event.getEntity().getUniqueId())) return;

        if (!(event.getDamager() instanceof Player player)) return;
        if (!(event.getEntity() instanceof LivingEntity victim)) return;

        // [핵심] DamageCalculator에게 계산 위임
        // 평타이므로 계수 1.0, isSkill = false (쿨타임 패널티 적용)
        DamageCalculator.DamageResult result = DamageCalculator.calculate(player, victim, 1.0, false);

        // 회피 등으로 인해 대미지가 0이거나 취소된 경우
        if (result.isCancelled()) {
            event.setDamage(0);
            event.setCancelled(true);
            return;
        }

        // 최종 적용 (CoreProvider가 인디케이터 표시 등 처리)
        event.setDamage(0); // 바닐라 대미지 무시
        CoreProvider.dealDamage(player, victim, result.damage(), result.isCrit());
    }

    // --- 3. 처치 시 발동 (신속 등) ---
    // 이 부분은 대미지 계산과 무관한 '이벤트'이므로 리스너에 남겨둡니다.
    @EventHandler
    public void onKill(EntityDeathEvent event) {
        if (event.getEntity().getKiller() == null) return;
        Player killer = event.getEntity().getKiller();

        ItemStack weapon = killer.getInventory().getItemInMainHand();
        String abilityName = CoreProvider.getItemDataString(weapon, "unique_ability");

        if (abilityName != null) {
            try {
                UniqueAbility ability = UniqueAbility.valueOf(abilityName);
                // 신속(Wind Walker) 능력 처리
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