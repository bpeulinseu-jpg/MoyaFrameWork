package com.server.core.util;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class TargetingUtil {

    /**
     * 범위 내 가장 가까운 플레이어를 찾습니다. (생존 모드만)
     */
    public static Player getNearestPlayer(LivingEntity mob, double range) {
        Player nearest = null;
        double minDistSq = range * range;

        for (Player p : mob.getWorld().getPlayers()) {
            if (isValidTarget(p) && p.getLocation().distanceSquared(mob.getLocation()) < minDistSq) {
                double distSq = p.getLocation().distanceSquared(mob.getLocation());
                if (distSq < minDistSq) {
                    minDistSq = distSq;
                    nearest = p;
                }
            }
        }
        return nearest;
    }

    /**
     * 범위 내 모든 플레이어를 찾습니다. (광역기 용도)
     */
    public static List<Player> getNearbyPlayers(LivingEntity mob, double range) {
        List<Player> targets = new ArrayList<>();
        double rangeSq = range * range;

        for (Player p : mob.getWorld().getPlayers()) {
            if (isValidTarget(p) && p.getLocation().distanceSquared(mob.getLocation()) <= rangeSq) {
                targets.add(p);
            }
        }
        return targets;
    }

    /**
     * 시야가 확보되었는지 확인 (벽 뒤에 있는지 체크)
     */
    public static boolean hasLineOfSight(LivingEntity mob, LivingEntity target) {
        return mob.hasLineOfSight(target);
    }

    /**
     * 타겟이 유효한지 검사 (사망, 관전 모드 등 제외)
     */
    public static boolean isValidTarget(Player p) {
        return p != null
                && p.isOnline()
                && !p.isDead()
                && p.getGameMode() != GameMode.CREATIVE
                && p.getGameMode() != GameMode.SPECTATOR;
    }

    /**
     * 강제로 어그로를 변경합니다.
     */
    public static void setTarget(LivingEntity mob, LivingEntity target) {
        if (mob instanceof org.bukkit.entity.Mob m) {
            m.setTarget(target);
        }
    }
}