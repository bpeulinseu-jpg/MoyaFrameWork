package com.server.core.system.mob.skill;

import org.bukkit.entity.LivingEntity;

public abstract class MobSkill {

    private final long cooldownTicks;
    private final double range;
    private long lastCastTime = 0;

    /**
     * @param cooldownTicks 스킬 쿨타임 (20 ticks = 1초)
     * @param range 스킬 사거리 (블록 단위)
     */
    public MobSkill(long cooldownTicks, double range) {
        this.cooldownTicks = cooldownTicks;
        this.range = range;
    }

    /**
     * AI 엔진이 호출하는 메서드 (조건 체크)
     */
    public boolean canCast(LivingEntity caster, LivingEntity target) {
        if (target == null) return false;

        // 1. 쿨타임 체크
        if (System.currentTimeMillis() - lastCastTime < (cooldownTicks * 50L)) {
            return false;
        }

        // 2. 사거리 체크
        if (caster.getLocation().distanceSquared(target.getLocation()) > (range * range)) {
            return false;
        }

        // 3. 추가 조건 (애드온에서 구현)
        return checkExtraCondition(caster, target);
    }

    /**
     * 스킬 실행 (쿨타임 갱신 포함)
     */
    public void execute(LivingEntity caster, LivingEntity target) {
        lastCastTime = System.currentTimeMillis();
        onCast(caster, target);
    }

    // --- 애드온 개발자가 구현해야 할 메서드 ---

    /**
     * 실제 스킬 로직 (화살 발사, 돌진, 버프 등)
     */
    public abstract void onCast(LivingEntity caster, LivingEntity target);

    /**
     * (선택) 거리/쿨타임 외의 추가 조건 (예: 체력 50% 이하일 때만)
     */
    public boolean checkExtraCondition(LivingEntity caster, LivingEntity target) {
        return true;
    }
}