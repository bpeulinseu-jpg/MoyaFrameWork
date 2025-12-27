package com.server.tower.game.skill.handler;

import com.server.core.api.CoreProvider;
import com.server.core.system.particle.ParticleBuilder;
import com.server.tower.TowerPlugin;
import com.server.tower.game.DamageCalculator;
import com.server.tower.game.skill.Element;
import com.server.tower.game.skill.WeaponHandler;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SwordHandler implements WeaponHandler {

    private static class ComboState {
        int step = 0;
        long lastActionTime = 0;
    }

    private final Map<UUID, ComboState> comboMap = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastClickTime = new ConcurrentHashMap<>();
    private final Map<UUID, Long> heavyGlobalCooldown = new ConcurrentHashMap<>();

    // [수정] 콤보 유지 시간 단축 (1.5초 -> 0.8초)
    private static final long COMBO_TIMEOUT = 1000;

    @Override
    public void onLeftClick(Player player, Element element) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();

        if (now - lastClickTime.getOrDefault(uuid, 0L) < 250) return;
        lastClickTime.put(uuid, now);

        ComboState state = comboMap.computeIfAbsent(uuid, k -> new ComboState());

        // 타임아웃 체크
        if (now - state.lastActionTime > COMBO_TIMEOUT) state.step = 0;
        state.lastActionTime = now;

        // --- 약공격 (Light Attack) ---
        switch (state.step) {
            case 0: // 1타: /
                performSlash(player, element, 45.0, 0.3);
                state.step = 1;
                break;
            case 1: // 2타: \
                performSlash(player, element, -45.0, 0.3);
                state.step = 2;
                break;
            case 2: // 3타: |
                performSlash(player, element, 90.0, 0.5);
                state.step = 3;
                break;
            case 3: // 4타: ㅡ
                performSlash(player, element, 0.0, 0.5);
                state.step = 4; // 강공격 연계 대기
                break;
            case 4: // 4타 후 다시 1타
                performSlash(player, element, 45.0, 0.3);
                state.step = 1;
                break;
            default:
                performSlash(player, element, 45.0, 0.3);
                state.step = 1;
                break;
        }
    }

    @Override
    public void onRightClick(Player player, Element element) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();

        // 우클릭 쿨타임 (스팸 방지용 최소 딜레이)
        if (now - lastClickTime.getOrDefault(uuid, 0L) < 300) return;
        lastClickTime.put(uuid, now);

        // 2. [핵심] 강공격 후딜레이 체크 (무한 비행 방지)
        if (now < heavyGlobalCooldown.getOrDefault(uuid, 0L)) {
            player.sendMessage("§c[!] 숨 고르기...");
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_BREATH, 1f, 1f);
            return;
        }

        ComboState state = comboMap.computeIfAbsent(uuid, k -> new ComboState());

        if (now - state.lastActionTime > COMBO_TIMEOUT) state.step = 0;
        state.lastActionTime = now;

        // --- 강공격 (Heavy Attack) ---
        switch (state.step) {
            case 0:
            case 1:
                doHeavyUppercut(player, element);
                state.step = 10;
                break;
            case 10:
                doHeavyJumpSlam(player, element);
                state.step = 0;
                break;
            case 2:
                doComboThrust(player, element);
                state.step = 0;
                break;
            case 3:
                doComboChargeSlash(player, element);
                state.step = 0;
                break;
            case 4:
                doComboGroundSmash(player, element);
                state.step = 0;
                break;
            default:
                doHeavyUppercut(player, element);
                state.step = 10;
                break;
        }
    }

    // [Action] 약공격 (이펙트만 출력, 대미지는 CombatListener가 처리)
    private void performSlash(Player player, Element element, double tilt, double forward) {
        Vector dir = player.getLocation().getDirection();

        if (forward > 0) {
            player.setVelocity(dir.clone().multiply(0.4).setY(0));
        }

        // 이펙트 재생
        playSlashAnimation(player, element, dir, 3.5, 0.5, tilt, 1.0f, 1);
    }

    // =================================================================
    // [Skill] 강공격 1타: 올려베기 (적에게 회오리 발생)
    // =================================================================
    private void doHeavyUppercut(Player player, Element element) {
        Vector dir = player.getLocation().getDirection().normalize();

        // 1. 대시 (지상 돌진)
        player.setVelocity(dir.clone().multiply(0).setY(0));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 0.5f);
        player.playSound(player.getLocation(), Sound.ENTITY_BAT_TAKEOFF, 1f, 0.5f);

        // 2. 검기 이펙트 (플레이어 기준)
        playSlashAnimation(player, element, dir, 5.0, 0.5, -90.0, 2.0f, 5);

        // 3. 타격 및 개별 회오리 생성
        Location center = player.getEyeLocation().add(dir.multiply(2.5));

        for (LivingEntity victim : getTargets(player, center, 4, 3)) {
            applyDamageAndEffect(player, victim, element, 1.5);

            // 에어본
            victim.setVelocity(new Vector(0, 1.2, 0));
            victim.sendMessage("§c[!] 띄워졌습니다!");

            // [핵심] 피격된 대상에게 이중 나선 애니메이션 재생
            playRisingHelix(victim, element);
        }
    }

    // =================================================================
    // [Skill] 강공격 2타: 점프 내려찍기 (Slam)
    // =================================================================
    private void doHeavyJumpSlam(Player player, Element element) {
        player.setVelocity(new Vector(0, 1.2, 0));
        player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 1f, 1f);

        heavyGlobalCooldown.put(player.getUniqueId(), System.currentTimeMillis() + 2000);

        new BukkitRunnable() {
            @Override
            public void run() {
                // 급강하
                Vector downDir = player.getLocation().getDirection().multiply(1).setY(-1.5);
                player.setVelocity(downDir);

                Location landLoc = player.getLocation();

                // 사운드
                player.getWorld().playSound(landLoc, Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.8f);
                player.getWorld().playSound(landLoc, Sound.BLOCK_ANVIL_LAND, 1f, 0.5f);

                // [핵심] 그라데이션 휠윈드 (7중 레이어)
                // 검기 로직과 동일한 색상 보간(Interpolation) 사용
                int layers = 7;
                double baseRadius = 4.5; // 기본 반지름

                for (int l = 0; l < layers; l++) {
                    double ratio = (double) l / (layers - 1);

                    // 반지름 변화: 안쪽(작음) -> 바깥쪽(큼)
                    // 4.0 ~ 5.5 범위로 퍼짐
                    double currentRadius = baseRadius + (ratio * 1.5);

                    // 색상 계산 (검정 -> 속성 -> 흰색)
                    Color color = interpolateColor(element.getColor(), ratio);

                    // 두께: 중간이 두껍게
                    float pSize = 1.5f;
                    if (ratio > 0.2 && ratio < 0.8) pSize = 2.5f;

                    ParticleBuilder circlePart = CoreProvider.createParticle()
                            .setParticle(Particle.DUST)
                            .setColor(color.getRed(), color.getGreen(), color.getBlue())
                            .setSize(pSize).setCount(1);

                    // 360도 원 그리기 (Tilt 90 = 수직으로 세움)
                    CoreProvider.getParticleManager().drawCrescent(
                            landLoc.clone().add(0, 1, 0),
                            player.getLocation().getDirection(),
                            currentRadius, 360.0, 90.0, 50, circlePart
                    );
                }

                // 바닥 충격파 (Impact)
                for (int i = 0; i < 20; i++) {
                    Location randomLoc = landLoc.clone().add((Math.random()-0.5)*5, 4, (Math.random()-0.5)*5);
                    player.getWorld().spawnParticle(Particle.CRIT, randomLoc, 0, 0, -1.5, 0, 0.5); // 꽂히는 속도 증가
                    player.getWorld().spawnParticle(Particle.FLASH, landLoc, 1);
                }

                // 타격 및 끌어오기
                for (LivingEntity victim : getTargets(player, landLoc, 7, 5)) {
                    applyDamageAndEffect(player, victim, element, 2.0);
                    Vector pullDir = landLoc.toVector().subtract(victim.getLocation().toVector()).normalize();
                    victim.setVelocity(pullDir.multiply(1.8).setY(-2.0)); // 더 강하게 처박음
                    victim.getWorld().spawnParticle(Particle.CRIT, victim.getLocation(), 5);
                }
            }
        }.runTaskLater(TowerPlugin.getInstance(), 10L);
    }

    // =================================================================
    // [Combo] L2 -> H: 찌르기 (나선 관통 + 하얀색 소닉붐 + 원통형 히트박스)
    // =================================================================
    private void doComboThrust(Player player, Element element) {
        Vector dir = player.getLocation().getDirection().normalize();

        // 1. 강력한 대시
        player.setVelocity(dir.clone().multiply(1.8).setY(0.1));

        player.getWorld().playSound(player.getLocation(), Sound.ITEM_TRIDENT_THROW, 1f, 0.8f);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 1.5f);

        // 2. 드릴 이펙트
        new BukkitRunnable() {
            @Override
            public void run() {
                Location start = player.getEyeLocation().add(0, -0.4, 0);
                double length = 7.0;
                double points = 40;

                Vector up = (Math.abs(dir.getY()) > 0.95) ? new Vector(1, 0, 0) : new Vector(0, 1, 0);
                Vector right = dir.getCrossProduct(up).normalize();

                // 메인 코어
                ParticleBuilder coreDust = CoreProvider.createParticle()
                        .setParticle(Particle.DUST)
                        .setColor(element.getColor().getRed(), element.getColor().getGreen(), element.getColor().getBlue())
                        .setSize(1.2f);

                // 나선 (검은색)
                ParticleBuilder spiralDust = CoreProvider.createParticle()
                        .setParticle(Particle.DUST)
                        .setColor(0, 0, 0)
                        .setSize(1.0f);

                // [수정] 소닉붐 고리 (하얀색 고정)
                ParticleBuilder ringDust = CoreProvider.createParticle()
                        .setParticle(Particle.DUST)
                        .setColor(255, 255, 255) // White
                        .setSize(1.5f);

                // 길이만큼 진행하며 그리기
                for (double i = 0; i < length; i += (length / points)) {
                    Location current = start.clone().add(dir.clone().multiply(i));

                    // 1. 중앙 코어
                    CoreProvider.getParticleManager().spawn(current, coreDust);

                    // 2. 나선 (Drill)
                    double angle = i * 2.5;
                    double radius = 0.4 + (i * 0.05);

                    Vector offset1 = right.clone().rotateAroundAxis(dir, angle).multiply(radius);
                    Vector offset2 = right.clone().rotateAroundAxis(dir, angle + Math.PI).multiply(radius);

                    CoreProvider.getParticleManager().spawn(current.clone().add(offset1), spiralDust);
                    CoreProvider.getParticleManager().spawn(current.clone().add(offset2), spiralDust);
                }

                // 3. 소닉붐 고리 그리기 (하얀색)
                for (double d = 2.0; d < length; d += 2.0) {
                    Location ringCenter = start.clone().add(dir.clone().multiply(d));

                    for (int k = 0; k < 24; k++) {
                        double ringAngle = Math.toRadians(k * 15);
                        double ringRadius = 0.6 + (d * 0.15); // 뒤로 갈수록 커짐

                        Vector ringOffset = right.clone().rotateAroundAxis(dir, ringAngle).multiply(ringRadius);
                        CoreProvider.getParticleManager().spawn(ringCenter.clone().add(ringOffset), ringDust);
                    }
                }
            }
        }.runTaskLater(TowerPlugin.getInstance(), 1L);

        // 3. [핵심 수정] 원통형(Cylinder) 타격 판정
        // 기존의 부채꼴(Angle) 방식은 찌르기에 부적합하여, 직선 거리 계산으로 변경

        Location startPos = player.getEyeLocation();
        double maxRange = 7.5; // 사거리
        double hitRadius = 1.5; // 피격 범위 (두께) - 이펙트 크기에 맞춰 넉넉하게

        // 넓게 검색한 뒤 정밀 판정
        for (LivingEntity victim : getTargets(player, player.getLocation(), 8, 4)) {
            // 대상의 몸통 중심
            Location vLoc = victim.getLocation().add(0, 0.5, 0);
            Vector toVictim = vLoc.toVector().subtract(startPos.toVector());

            // 1. 전방 거리 (Projection) 계산
            double dot = toVictim.dot(dir);

            // 내 앞(0)에 있고, 사거리(7.5) 안쪽인가?
            if (dot > 0 && dot < maxRange) {
                // 2. 직선과의 수직 거리 계산 (Distance from Line)
                // 피타고라스: 수직거리^2 = 대각선거리^2 - 전방거리^2
                double distSq = toVictim.lengthSquared() - (dot * dot);

                // 수직 거리가 히트박스 반지름보다 작은가? (원통 내부)
                if (distSq < (hitRadius * hitRadius)) {
                    applyDamageAndEffect(player, victim, element, 1.8);

                    // 찌르기 넉백 (뒤로 밀려남)
                    victim.setVelocity(dir.clone().multiply(1.0).setY(0.2));
                    victim.getWorld().spawnParticle(Particle.CRIT, victim.getEyeLocation(), 10);
                }
            }
        }
    }

    // =================================================================
    // [Combo] L3 -> H: 차징 베기
    // =================================================================
    // =================================================================
    // [Combo] L3 -> H: 3단 차징 -> 대시 -> 270도 광역 베기 (수평)
    // =================================================================
    private void doComboChargeSlash(Player player, Element element) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 25, 10));
        player.sendActionBar(net.kyori.adventure.text.Component.text("§e⚡ 기 모으는 중..."));

        new BukkitRunnable() {
            int step = 0;

            @Override
            public void run() {
                Location loc = player.getLocation().add(0, 0.2, 0);

                // --- [1단계] 차징 (기존 유지) ---
                if (step < 3) {
                    float pitch = 0.5f + (step * 0.5f);
                    double radius = 1.5 + (step * 1.0);

                    player.getWorld().playSound(loc, Sound.BLOCK_PORTAL_TRIGGER, 0.5f, pitch);

                    ParticleBuilder chargeDust = CoreProvider.createParticle()
                            .setParticle(Particle.DUST)
                            .setColor(element.getColor().getRed(), element.getColor().getGreen(), element.getColor().getBlue())
                            .setSize(2.0f);
                    CoreProvider.getParticleManager().drawCircle(loc, radius, 40, chargeDust);

                    ParticleBuilder spark = CoreProvider.createParticle().setParticle(Particle.CRIT).setCount(1);
                    CoreProvider.getParticleManager().drawCircle(loc, radius, 20, spark);

                    player.getWorld().spawnParticle(Particle.ENCHANT, player.getEyeLocation(), 15, 0.5, 0.5, 0.5, 1.0);

                    step++;
                }
                // --- [2단계] 발동 ---
                else {
                    this.cancel();

                    Vector dir = player.getLocation().getDirection().normalize();
                    player.setVelocity(dir.clone().multiply(1.8).setY(0.2));
                    player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 0.5f);

                    // 2. [지연 실행] 베기 이펙트 (수평)
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            // 높이: 허리춤 (0.8)
                            Location center = player.getLocation().add(0, 0.8, 0);
                            Vector forward = player.getLocation().getDirection().setY(0).normalize();

                            // 소리
                            player.getWorld().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1.5f);
                            player.getWorld().playSound(center, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 0.5f);

                            // [핵심] 그라데이션 수평 부채꼴 (7중 레이어)
                            int points = 60;
                            double startAngle = -135.0;
                            double totalAngle = 270.0;
                            double step = totalAngle / points;

                            int layers = 7;
                            double baseRadius = 5.0; // 기본 반지름

                            for (int i = 0; i <= points; i++) {
                                double angle = Math.toRadians(startAngle + (i * step));
                                Vector directionVector = forward.clone().rotateAroundY(angle);

                                for (int l = 0; l < layers; l++) {
                                    double ratio = (double) l / (layers - 1); // 0.0 ~ 1.0

                                    // 색상 계산 (흰색 -> 속성 -> 검정)
                                    // 기존 로직: 0(흰색), 1(검정)
                                    Color color = interpolateColor(element.getColor(), ratio);

                                    // 반지름 계산 (레이어링)
                                    // 흰색(0.0)이 가장 바깥쪽(날카로운 날)에 있어야 함 -> 반지름 크게
                                    // 검정(1.0)이 가장 안쪽(그림자)에 있어야 함 -> 반지름 작게
                                    double radiusOffset = 0.5 - (ratio * 1.5); // +0.5(바깥) ~ -1.0(안쪽)
                                    double finalRadius = baseRadius + radiusOffset;

                                    // 두께(Size) 계산
                                    float pSize = 1.5f;
                                    if (ratio > 0.2 && ratio < 0.8) pSize = 2.5f; // 중간(속성)은 두껍게

                                    ParticleBuilder p = CoreProvider.createParticle()
                                            .setParticle(Particle.DUST)
                                            .setColor(color.getRed(), color.getGreen(), color.getBlue())
                                            .setSize(pSize)
                                            .setCount(1);

                                    // 소환
                                    Vector offset = directionVector.clone().multiply(finalRadius);
                                    CoreProvider.getParticleManager().spawn(center.clone().add(offset), p);
                                }
                            }
                            // 타격 판정
                            for (LivingEntity victim : getTargets(player, player.getLocation(), 6, 3)) {
                                Vector toTarget = victim.getLocation().subtract(player.getLocation()).toVector().normalize();
                                if (dir.dot(toTarget) > -0.5) {
                                    applyDamageAndEffect(player, victim, element, 3.0);
                                    victim.setVelocity(toTarget.multiply(1.5).setY(0.5));
                                }
                            }
                        }
                    }.runTaskLater(TowerPlugin.getInstance(), 5L);
                }
            }
        }.runTaskTimer(TowerPlugin.getInstance(), 0L, 7L);
    }

    // =================================================================
    // [Combo] L4 -> H: 땅 찍기 (승룡권 점프 -> 더스트 검 결계)
    // =================================================================
    private void doComboGroundSmash(Player player, Element element) {
        // 1. 도약
        player.setVelocity(new Vector(0, 1.2, 0));
        player.playSound(player.getLocation(), Sound.ENTITY_BAT_TAKEOFF, 1f, 0.5f);
        playRisingHelix(player, element);

        // 2. 급강하 및 착지 감지 루프
        new BukkitRunnable() {
            int tick = 0;
            boolean isPlunging = false;

            @Override
            public void run() {
                // 10틱(0.5초) 뒤에 급강하 시작 (공중 체류)
                if (!isPlunging && tick >= 10) {
                    player.setVelocity(new Vector(0, -2.5, 0));
                    isPlunging = true;
                }

                // 급강하 중일 때 착지 여부 확인
                if (isPlunging) {
                    // 땅에 닿았거나, 너무 오래 걸리면(안전장치) 발동
                    if (player.isOnGround() || tick > 40) {
                        this.cancel();
                        triggerSmash(player, element);
                    }
                }
                tick++;
            }
        }.runTaskTimer(TowerPlugin.getInstance(), 0L, 1L); // 1틱마다 체크
    }

    // 착지 시 실행되는 로직 분리
    private void triggerSmash(Player player, Element element) {
        Location loc = player.getLocation();

        // 사운드 (기존)
        player.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1.0f);
        player.getWorld().playSound(loc, Sound.BLOCK_ANVIL_LAND, 1f, 0.5f);

        // 바닥 균열 (기존)
        ParticleBuilder crack = CoreProvider.createParticle().setParticle(Particle.CRIT).setCount(10);
        CoreProvider.getParticleManager().drawLine(loc.clone().add(-4,0,0), loc.clone().add(4,0,0), 5, crack);
        CoreProvider.getParticleManager().drawLine(loc.clone().add(0,0,-4), loc.clone().add(0,0,4), 5, crack);

        // 검의 결계 소환 (기존)
        spawnPersistentSwordCircle(loc, element);

        // [수정] 타격 및 임팩트 이펙트 추가
        for (LivingEntity victim : getTargets(player, loc, 7, 3)) {
            applyDamageAndEffect(player, victim, element, 2.5);
            victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 10));

            // 띄우기 (칼 솟아오르는 느낌)
            victim.setVelocity(new Vector(0, 0.8, 0)); // 조금 더 높게 0.5 -> 0.8

            // [추가] 피격 임팩트 재생
            playSmashImpact(victim, element);
        }
    }

    // [New Helper] 타격 이펙트 (솟구치는 빛기둥 + 파열)
    private void playSmashImpact(LivingEntity victim, Element element) {
        Location vLoc = victim.getLocation();

        // 1. 속성 빛기둥 (Pillar) - 바닥에서 위로 솟구침
        ParticleBuilder pillarDust = CoreProvider.createParticle()
                .setParticle(Particle.DUST)
                .setColor(element.getColor().getRed(), element.getColor().getGreen(), element.getColor().getBlue())
                .setSize(1.5f)
                .setCount(1);

        // 발밑에서 머리 위 3칸까지 빠르게 올라가는 선 그리기
        CoreProvider.getParticleManager().drawLine(
                vLoc,
                vLoc.clone().add(0, 3.0, 0),
                5.0, // 밀도
                pillarDust
        );

        // 2. 파열 이펙트 (Burst) - 사방으로 튐
        // FLASH: 번쩍임
        victim.getWorld().spawnParticle(Particle.FLASH, vLoc.clone().add(0, 1, 0), 1);

        // CRIT: 강한 타격감
        victim.getWorld().spawnParticle(Particle.CRIT, vLoc.clone().add(0, 1, 0), 15, 0.5, 0.5, 0.5, 0.5);

        // 3. 추가 사운드 (피격자 위치에서)
        // 슉! 하고 꿰뚫는 소리 + 둔탁한 타격음
        victim.getWorld().playSound(vLoc, Sound.ITEM_TRIDENT_THUNDER, 0.5f, 2.0f);
        victim.getWorld().playSound(vLoc, Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 0.5f, 0.5f);
    }

    // [Helper] 먼지로 검 모양 그리기 (상하 반전: 손잡이 위, 칼날 아래)
    private void spawnPersistentSwordCircle(Location center, Element element) {
        int swordCount = 12;
        double radius = 4.0;

        // 1. 칼날 (Blade) - 속성 색상
        ParticleBuilder bladeDust = CoreProvider.createParticle()
                .setParticle(Particle.DUST)
                .setColor(element.getColor().getRed(), element.getColor().getGreen(), element.getColor().getBlue())
                .setSize(1.2f);

        // 2. 손잡이 & 코등이 (Hilt & Guard) - 검은색
        ParticleBuilder handleDust = CoreProvider.createParticle()
                .setParticle(Particle.DUST)
                .setColor(0, 0, 0)
                .setSize(1.5f);

        new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                if (tick >= 20) {
                    this.cancel();
                    return;
                }

                for (int i = 0; i < swordCount; i++) {
                    double angle = 2 * Math.PI * i / swordCount;
                    double x = Math.cos(angle) * radius;
                    double z = Math.sin(angle) * radius;

                    Location swordRoot = center.clone().add(x, 0, z);
                    Vector outward = new Vector(x, 0, z).normalize();
                    Vector cross = new Vector(0, 1, 0).getCrossProduct(outward).normalize();

                    // --- [검 그리기 (상하 반전)] ---
                    // 높이 기준:
                    // 손잡이 끝: 3.0
                    // 코등이: 2.2
                    // 칼날 끝: 0.2 (땅에 박힘)

                    // 1. 손잡이 (Hilt): 위(3.0) -> 코등이(2.2)
                    CoreProvider.getParticleManager().drawLine(
                            swordRoot.clone().add(0, 3.0, 0),
                            swordRoot.clone().add(0, 2.2, 0),
                            3.0, handleDust
                    );

                    // 2. 코등이 (Guard): 높이 2.2에서 가로지름
                    CoreProvider.getParticleManager().drawLine(
                            swordRoot.clone().add(0, 2.2, 0).add(cross.clone().multiply(-0.5)),
                            swordRoot.clone().add(0, 2.2, 0).add(cross.clone().multiply(0.5)),
                            5.0, handleDust
                    );

                    // 3. 칼날 (Blade): 코등이(2.2) -> 바닥(0.2)
                    CoreProvider.getParticleManager().drawLine(
                            swordRoot.clone().add(0, 2.2, 0),
                            swordRoot.clone().add(0, 0.2, 0),
                            5.0, bladeDust
                    );
                }
                tick += 3;
            }
        }.runTaskTimer(TowerPlugin.getInstance(), 0L, 3L);
    }


    // =================================================================
    // [Visual] 그라데이션 검기 애니메이션 (복구됨)
    // =================================================================
    private void playSlashAnimation(Player player, Element element, Vector dir, double size, double curve, double tilt, float thickness, int speed) {
        Location startLoc = player.getEyeLocation().add(dir.clone().multiply(3.0));

        new BukkitRunnable() {
            final int totalPoints = 50;
            int currentPoint = 0;

            // 축 계산 및 회전 (Tilt 적용)
            final Vector up = (Math.abs(dir.getY()) > 0.95) ? new Vector(1, 0, 0) : new Vector(0, 1, 0);
            final Vector right = dir.getCrossProduct(up).normalize();
            final Vector tiltedRight = right.clone().rotateAroundAxis(dir, Math.toRadians(tilt));

            @Override
            public void run() {
                // 속도만큼 한 번에 그림 (즉발이면 speed=50 주면 됨)
                int loopCount = (speed <= 1) ? totalPoints : 10;

                for (int i = 0; i < loopCount; i++) {
                    if (currentPoint > totalPoints) { this.cancel(); return; }

                    double t = (double) currentPoint / totalPoints;
                    double x = (t - 0.5) * size;
                    double curveOffset = curve * (1.0 - (4 * (t - 0.5) * (t - 0.5)));

                    Vector baseOffset = tiltedRight.clone().multiply(x).add(dir.clone().multiply(curveOffset));

                    // [7중 그라데이션 레이어]
                    int layers = 7;
                    for (int l = 0; l < layers; l++) {
                        double ratio = (double) l / (layers - 1);
                        double depthOffset = -0.8 + (ratio * 1.3); // -0.8(뒤) ~ +0.5(앞)

                        Color color = interpolateColor(element.getColor(), ratio);

                        // 두께 조절 (입력받은 thickness 반영)
                        float pSize = thickness;
                        if (ratio > 0.2 && ratio < 0.8) pSize *= 1.3f; // 중간은 더 두껍게

                        ParticleBuilder p = CoreProvider.createParticle()
                                .setParticle(Particle.DUST)
                                .setColor(color.getRed(), color.getGreen(), color.getBlue())
                                .setSize(pSize)
                                .setCount(1);

                        CoreProvider.getParticleManager().spawn(
                                startLoc.clone().add(baseOffset).add(dir.clone().multiply(depthOffset)),
                                p
                        );
                    }
                    currentPoint++;
                }
            }
        }.runTaskTimer(TowerPlugin.getInstance(), 0L, 1L);
    }

    // 색상 보간 (흰색 -> 속성 -> 검정)
    private Color interpolateColor(Color baseColor, double ratio) {
        int r, g, b;
        if (ratio < 0.2) { // 흰색 -> 속성
            double t = ratio * 5.0;
            r = (int) (255 + (baseColor.getRed() - 255) * t);
            g = (int) (255 + (baseColor.getGreen() - 255) * t);
            b = (int) (255 + (baseColor.getBlue() - 255) * t);
        } else if (ratio < 0.8) { // 속성 유지
            r = baseColor.getRed();
            g = baseColor.getGreen();
            b = baseColor.getBlue();
        } else { // 속성 -> 검정
            double t = (ratio - 0.8) * 5.0;
            r = (int) (baseColor.getRed() * (1 - t));
            g = (int) (baseColor.getGreen() * (1 - t));
            b = (int) (baseColor.getBlue() * (1 - t));
        }
        return Color.fromRGB(r, g, b);
    }

    // --- Helper Methods ---
    private List<LivingEntity> getTargets(Player player, Location center, double radius, double height) {
        List<LivingEntity> targets = new java.util.ArrayList<>();
        for (org.bukkit.entity.Entity e : center.getWorld().getNearbyEntities(center, radius, height, radius)) {
            if (e instanceof LivingEntity victim && e != player) targets.add(victim);
        }
        return targets;
    }

    private void checkHit(Player player, Element element, Location center, Vector dir, double multiplier, boolean isWide) {
        for (LivingEntity victim : getTargets(player, center, 4, 3)) {
            Vector toTarget = victim.getLocation().subtract(player.getLocation()).toVector().normalize();
            double angle = isWide ? 0.0 : 0.5;
            if (dir.dot(toTarget) > angle) {
                applyDamageAndEffect(player, victim, element, multiplier);
            }
        }
    }

    private void applyDamageAndEffect(Player attacker, LivingEntity victim, Element element, double multiplier) {
        DamageCalculator.DamageResult result = DamageCalculator.calculate(attacker, victim, multiplier, true);
        if (result.isCancelled()) return;

        double damage = result.damage();
        switch (element) {
            case FIRE: victim.setFireTicks(60); damage *= 1.1; break;
            case ICE: victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1)); break;
            case STORM: damage += 10; victim.getWorld().strikeLightningEffect(victim.getLocation()); break;
            case DARK:
                double maxHp = victim.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
                damage += (maxHp - victim.getHealth()) * 0.1;
                break;
            case LIGHT:
                double heal = damage * 0.1;
                double newHp = Math.min(attacker.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue(), attacker.getHealth() + heal);
                attacker.setHealth(newHp);
                break;
        }
        CoreProvider.dealDamage(attacker, victim, damage, result.isCrit());
    }

    // [Helper] 타겟을 감싸는 이중 나선 애니메이션 (가시성 대폭 강화)
    private void playRisingHelix(LivingEntity target, Element element) {
        // 시작 시 바닥에 충격파 (시각적 알림)
        target.getWorld().spawnParticle(Particle.EXPLOSION, target.getLocation(), 1);

        new BukkitRunnable() {
            double currentY = 0;
            double angle = 0;
            final double radius = 1.5; // 반경을 살짝 키움 (1.2 -> 1.5)
            final double maxHeight = 3.5;

            // 1. 메인 색상 (아주 두껍게)
            final ParticleBuilder mainDust = CoreProvider.createParticle()
                    .setParticle(Particle.DUST)
                    .setColor(element.getColor().getRed(), element.getColor().getGreen(), element.getColor().getBlue())
                    .setSize(3.0f); // 1.5 -> 3.0 (2배 확대)

            // 2. 검은색 윤곽선 (아주 두껍게)
            final ParticleBuilder blackDust = CoreProvider.createParticle()
                    .setParticle(Particle.DUST)
                    .setColor(0, 0, 0)
                    .setSize(3.0f); // 1.5 -> 3.0 (2배 확대)

            @Override
            public void run() {
                if (target == null || !target.isValid() || target.isDead()) {
                    this.cancel();
                    return;
                }

                Location center = target.getLocation();

                // 한 틱에 그리는 점의 개수를 늘려서(4->6) 더 촘촘하게 그림
                for (int i = 0; i < 6; i++) {
                    if (currentY > maxHeight) {
                        this.cancel();
                        return;
                    }

                    // --- [이중 나선] ---

                    // Line 1: 속성 색상
                    double x1 = Math.cos(angle) * radius;
                    double z1 = Math.sin(angle) * radius;
                    CoreProvider.getParticleManager().spawn(center.clone().add(x1, currentY, z1), mainDust);

                    // Line 2: 검은색 (반대편)
                    double x2 = Math.cos(angle + Math.PI) * radius;
                    double z2 = Math.sin(angle + Math.PI) * radius;
                    CoreProvider.getParticleManager().spawn(center.clone().add(x2, currentY, z2), blackDust);

                    // --- [볼륨감 추가] ---
                    // 회오리 내부에 구름을 채워서 덩어리감을 줌
                    if (i % 2 == 0) { // 너무 많으면 렉 걸리니까 가끔씩
                        target.getWorld().spawnParticle(Particle.CLOUD, center.clone().add(0, currentY, 0), 0, 0, 0, 0, 0.1);
                    }

                    // 상승 속도는 줄이고(촘촘함), 회전 속도는 유지
                    currentY += 0.1; // 0.15 -> 0.1 (더 빽빽하게 그려짐)
                    angle += 0.4;
                }
            }
        }.runTaskTimer(TowerPlugin.getInstance(), 0L, 1L);
    }
}