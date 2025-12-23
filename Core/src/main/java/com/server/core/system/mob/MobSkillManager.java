package com.server.core.system.mob;

import com.server.core.CorePlugin;
import com.server.core.system.mob.skill.MobSkill;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import com.server.core.util.TargetingUtil;
import org.bukkit.entity.Mob;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MobSkillManager implements Listener {

    private final CorePlugin plugin;

    // 현재 살아있는 커스텀 몬스터 목록 (Entity UUID -> Mob ID)
    private final Map<UUID, String> activeMobs = new ConcurrentHashMap<>();

    public MobSkillManager(CorePlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        startAiLoop();
    }

    // 몬스터 소환 시 등록 (MobManager에서 호출)
    public void registerActiveMob(LivingEntity entity, String mobId) {
        activeMobs.put(entity.getUniqueId(), mobId);
    }

    // 몬스터 사망 시 목록에서 제거
    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        activeMobs.remove(event.getEntity().getUniqueId());
    }

    private void startAiLoop() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (activeMobs.isEmpty()) return;

            for (UUID uuid : new HashSet<>(activeMobs.keySet())) {
                LivingEntity entity = (LivingEntity) Bukkit.getEntity(uuid);

                // 유효성 검사
                if (entity == null || !entity.isValid() || entity.isDead()) {
                    activeMobs.remove(uuid);
                    continue;
                }

                String mobId = activeMobs.get(uuid);
                MobManager.CustomMobData data = CorePlugin.getMobManager().getMobData(mobId);
                if (data == null || data.skills.isEmpty()) continue;

                // [수정] TargetingUtil 사용
                LivingEntity target = null;

                // 1. 현재 어그로 대상 확인
                if (entity instanceof Mob mob) {
                    target = mob.getTarget();
                }

                // 2. 타겟이 없거나 죽었으면 주변 플레이어 탐색
                if (target == null || !target.isValid() || target.isDead()) {
                    target = TargetingUtil.getNearestPlayer(entity, 16.0);

                    // 찾았으면 어그로 고정
                    if (target != null && entity instanceof Mob mob) {
                        mob.setTarget(target);
                    }
                }

                if (target == null) continue; // 타겟 없으면 스킬 발동 불가

                // 3. 스킬 시전 시도
                for (MobSkill skill : data.skills) {
                    if (skill.canCast(entity, target)) {
                        skill.execute(entity, target);
                    }
                }
            }
        }, 20L, 10L);
    }
}