package com.server.tower.game;

import com.server.tower.TowerPlugin;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import com.server.core.api.CoreProvider;

public class GameListener implements Listener {

    private final GameManager gameManager;

    public GameListener(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    @EventHandler
    public void onMobDeath(EntityDeathEvent event) {
        LivingEntity mob = event.getEntity();
        Player killer = mob.getKiller();

        // 플레이어가 잡은 경우만 처리
        if (killer != null && gameManager.isIngame(killer)) {
            gameManager.onMobDeath(killer, mob);
        }
    }

    @EventHandler
    public void onPlayerDeath(org.bukkit.event.entity.PlayerDeathEvent event) {
        org.bukkit.entity.Player player = event.getEntity();

        if (gameManager.isIngame(player)) {
            // [신규] 사망 페널티: 모든 장비 내구도 25% 차감
            applyDeathPenalty(player);

            gameManager.endGame(player);
        }
    }

    private void applyDeathPenalty(org.bukkit.entity.Player player) {
        player.sendMessage("§c☠ 사망하여 장비 내구도가 손상되었습니다. (-25%)");

        // 갑옷 + 손에 든 아이템
        for (org.bukkit.inventory.ItemStack item : player.getInventory().getContents()) {
            if (item == null || item.getType().isAir()) continue;

            // 내구도가 있는 아이템인지 확인 (Damageable)
            if (item.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable meta) {
                // 커스텀 아이템만 적용 (선택사항)
                if (CoreProvider.getCustomId(item) == null) continue;

                int maxDurability = item.getType().getMaxDurability();
                if (maxDurability <= 0) continue; // 내구도 없는 템 제외

                int currentDamage = meta.getDamage();
                int penalty = (int) (maxDurability * 0.25); // 25%

                int newDamage = currentDamage + penalty;

                // 파괴 처리
                if (newDamage >= maxDurability) {
                    item.setAmount(0); // 아이템 파괴
                    player.sendMessage("§c⚠ 장비가 파괴되었습니다!");
                } else {
                    meta.setDamage(newDamage);
                    item.setItemMeta(meta);
                }
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (gameManager.isIngame(event.getPlayer())) {
            gameManager.endGame(event.getPlayer());
        }
    }
}