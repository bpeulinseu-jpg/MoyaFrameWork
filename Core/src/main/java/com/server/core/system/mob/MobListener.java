package com.server.core.system.mob;

import com.server.core.CorePlugin;
import com.server.core.system.block.DropItem;
import com.server.core.system.mob.MobManager;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class MobListener implements Listener {

    @EventHandler
    public void onMobDeath(EntityDeathEvent event) {
        // 1. 커스텀 몹인지 확인
        String mobId = CorePlugin.getMobManager().getCustomMobId(event.getEntity());
        if (mobId == null) return;

        MobManager.CustomMobData data = CorePlugin.getMobManager().getMobData(mobId);
        if (data == null) return;

        // 2. 바닐라 드랍 지우기
        event.getDrops().clear();
        event.setDroppedExp(data.exp);

        // 3. 약탈 레벨 확인
        int lootingLevel = 0;
        if (event.getEntity().getKiller() != null) {
            Player killer = event.getEntity().getKiller();
            ItemStack hand = killer.getInventory().getItemInMainHand();
            lootingLevel = hand.getEnchantmentLevel(Enchantment.LOOTING);
        }

        // 4. [핵심] 드랍 테이블 결정 로직
        // 엔티티에서 드랍 테이블 꺼내오기
        List<DropItem> dropTable = CorePlugin.getMobManager().loadDynamicDrops(event.getEntity());

        if (dropTable == null) {
            dropTable = data.drops; // 없으면 기본값 사용
        }

        // 5. 드랍 계산 및 추가
        for (DropItem drop : dropTable) {
            if (Math.random() > drop.getChance()) continue;

            int amount = java.util.concurrent.ThreadLocalRandom.current().nextInt(drop.getMinAmount(), drop.getMaxAmount() + 1);

            if (drop.isApplyFortune() && lootingLevel > 0) {
                amount += java.util.concurrent.ThreadLocalRandom.current().nextInt(0, lootingLevel + 1);
            }

            if (amount > 0) {
                ItemStack item = drop.getItem();
                item.setAmount(amount);
                event.getDrops().add(item);
            }
        }
    }
}