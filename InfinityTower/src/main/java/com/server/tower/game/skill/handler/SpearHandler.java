package com.server.tower.game.skill.handler;

import com.server.core.api.CoreProvider;
import com.server.core.system.particle.ParticleBuilder;
import com.server.tower.TowerPlugin;
import com.server.tower.game.DamageCalculator;
import com.server.tower.game.skill.Element;
import com.server.tower.game.skill.WeaponHandler;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.io.File;

public class SpearHandler implements WeaponHandler {

    @Override
    public void onLeftClick(Player player, Element element) {
        // [평타] 찌르기 (직선 파티클)
        Location start = player.getEyeLocation().add(0, -0.2, 0);
        Location end = start.clone().add(player.getLocation().getDirection().multiply(4.0));

        ParticleBuilder dust = CoreProvider.createParticle()
                .setParticle(Particle.DUST)
                .setColor(element.getColor().getRed(), element.getColor().getGreen(), element.getColor().getBlue())
                .setSize(0.5f);

        CoreProvider.getParticleManager().drawLine(start, end, 5.0, dust);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1f, 1.5f);
    }

    @Override
    public void onRightClick(Player player, Element element) {
        // [스킬] 관통 돌진 (거대 창 투사체)

        // 1. 커스텀 텍스처 아이템 생성
        // "spear_icon"은 등록했던 파티클 텍스처 ID
        int cmd = CoreProvider.getParticleModelData("spear_icon");

        // [디버그 로그 추가]
        System.out.println("DEBUG: spear_icon CMD = " + cmd);

        // 종이 아이템에 CMD 적용
        ItemStack spearVisual = new com.server.core.api.builder.ItemBuilder(Material.SNOWBALL)
                .model(cmd)
                .build();

        // 2. 투사체 발사 (크기 3.0배!)
        // shootProjectile이 명중 시 콜백을 실행하므로, 별도의 for문 탐색이 필요 없음
        CoreProvider.shootProjectile(player, spearVisual, 2.0, 10.0, 3.0f, (hitEntity) -> {
            if (hitEntity instanceof LivingEntity victim) {
                applyDamage(player, victim, element);

                // 타격감: 꿰뚫는 소리
                victim.getWorld().playSound(victim.getLocation(), Sound.ITEM_TRIDENT_HIT, 1f, 1f);
            }
        });

        // 3. 플레이어 돌진 (투사체와 함께 날아감)
        player.setVelocity(player.getLocation().getDirection().normalize().multiply(1.5).setY(0.2));
        player.playSound(player.getLocation(), Sound.ITEM_TRIDENT_THROW, 1f, 1f);

        // [삭제됨] 여기에 있던 for 반복문은 제거했습니다. (투사체가 대신 처리함)
    }

    private void applyDamage(Player attacker, LivingEntity victim, Element element) {
        DamageCalculator.DamageResult result = DamageCalculator.calculate(attacker, victim, 2.0, true);
        if (result.isCancelled()) return;

        double damage = result.damage();

        // 창 특수 효과: 폭풍 속성일 때 추가 피해
        if (element == Element.STORM) {
            damage *= 1.3;
            victim.getWorld().strikeLightningEffect(victim.getLocation());
        }

        CoreProvider.dealDamage(attacker, victim, damage, result.isCrit());
    }
}