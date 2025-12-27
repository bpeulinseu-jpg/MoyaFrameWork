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
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GreatswordHandler implements WeaponHandler {

    private static class ComboState {
        int step = 0;
        long lastActionTime = 0;
        boolean isCharging = false;
    }

    private final Map<UUID, ComboState> comboMap = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastClickTime = new ConcurrentHashMap<>();

    // [추가] 강공격 후딜레이 관리
    private final Map<UUID, Long> heavyGlobalCooldown = new ConcurrentHashMap<>();

    private static final long COMBO_TIMEOUT = 2000;

    @Override
    public void onLeftClick(Player player, Element element) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();

        if (now - lastClickTime.getOrDefault(uuid, 0L) < 300) return;
        lastClickTime.put(uuid, now);

        ComboState state = comboMap.computeIfAbsent(uuid, k -> new ComboState());

        if (state.isCharging) return;

        if (now - state.lastActionTime > COMBO_TIMEOUT) state.step = 0;
        state.lastActionTime = now;

        switch (state.step) {
            case 0: performSlash(player, element, 0.0, 5.0, 1.2); state.step = 1; break;
            case 1: performSlash(player, element, -45.0, 4.5, 1.3); state.step = 2; break;
            case 2: performSlash(player, element, 90.0, 5.0, 1.5); state.step = 3; break;
            case 3: performSlash(player, element, 0.0, 5.0, 1.2); state.step = 1; break;
        }
    }

    @Override
    public void onRightClick(Player player, Element element) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();

        if (now - lastClickTime.getOrDefault(uuid, 0L) < 200) return;
        lastClickTime.put(uuid, now);

        // [핵심] 후딜레이 체크 (발사 후 멍때리는 시간)
        if (now < heavyGlobalCooldown.getOrDefault(uuid, 0L)) {
            // 쿨타임 중에는 소리나 메시지로 피드백
            // player.playSound(player.getLocation(), Sound.BLOCK_CHAIN_STEP, 0.5f, 0.5f);
            return;
        }

        ComboState state = comboMap.computeIfAbsent(uuid, k -> new ComboState());
        if (state.isCharging) return;

        if (now - state.lastActionTime > COMBO_TIMEOUT) state.step = 0;
        state.lastActionTime = now;

        switch (state.step) {
            case 0: doChargeCrash(player, element, state); break;
            case 1: doShoulderCharge(player, element); state.step = 0; break;
            case 2: doEarthquake(player, element); state.step = 0; break;
            case 3: doGuillotine(player, element); state.step = 0; break;
        }
    }

    // =================================================================
    // [Action] 평타
    // =================================================================
    private void performSlash(Player player, Element element, double tilt, double size, double dmgMult) {
        Vector dir = player.getLocation().getDirection();
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 0.5f);

        Location center = player.getEyeLocation().add(dir.clone().multiply(2.5));
        drawLayeredSlash(center, dir, size, 0.3, tilt, 20, element, 5);

        checkHit(player, element, center, dir, dmgMult, 5.0, 90.0);
    }

    // =================================================================
    // [Skill] R: 차지 크래시 (대각선 발사 + 조준 + 후딜레이)
    // =================================================================
    private void doChargeCrash(Player player, Element element, ComboState state) {
        state.isCharging = true;

        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 50, 2));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 50, 4));
        player.sendActionBar(net.kyori.adventure.text.Component.text("§c🛡 기 모으는 중... (2.0s)"));

        new BukkitRunnable() {
            int charge = 0;
            @Override
            public void run() {
                if (!player.isOnline() || player.isDead()) {
                    state.isCharging = false;
                    this.cancel();
                    return;
                }

                Location center = player.getLocation().add(0, 1.0, 0);

                // --- [1단계] 에너지 응축 ---
                if (charge < 4) {
                    float pitch = 0.5f + (charge * 0.15f);
                    player.getWorld().playSound(center, Sound.BLOCK_PORTAL_TRIGGER, 0.5f, pitch);
                    player.getWorld().playSound(center, Sound.BLOCK_ANVIL_LAND, 0.5f, 0.5f);

                    for (int i = 0; i < 30; i++) {
                        Vector offset = Vector.getRandom().subtract(new Vector(0.5, 0.5, 0.5)).normalize().multiply(4.0);
                        Location particleLoc = center.clone().add(offset);
                        Vector velocity = center.toVector().subtract(particleLoc.toVector()).normalize().multiply(0.5);

                        player.getWorld().spawnParticle(Particle.DUST, particleLoc, 0, velocity.getX(), velocity.getY(), velocity.getZ(), 1,
                                new Particle.DustOptions(element.getColor(), 1.5f));
                    }

                    ParticleBuilder ring = CoreProvider.createParticle().setParticle(Particle.CRIT).setCount(10);
                    CoreProvider.getParticleManager().drawCircle(player.getLocation().add(0, 0.2, 0), 2.0 + charge, 30, ring);

                    charge++;
                }
                // --- [2단계] 발사 ---
                else {
                    this.cancel();

                    // [수정] 조준 방향 그대로 사용 (Y축 유지)
                    Vector dir = player.getLocation().getDirection().normalize();

                    // 반동
                    player.setVelocity(dir.clone().multiply(-0.5).setY(0.1));

                    player.getWorld().playSound(center, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 0.5f);
                    player.getWorld().playSound(center, Sound.ITEM_TRIDENT_THROW, 1f, 0.5f);

                    // [수정] 대각선 검기 발사 (Tilt: -45.0 = \)
                    shootTsunamiSlash(player, element, dir, -45.0);

                    // [핵심] 후딜레이 적용 (1.5초)
                    heavyGlobalCooldown.put(player.getUniqueId(), System.currentTimeMillis() + 1500);

                    state.isCharging = false;
                }
            }
        }.runTaskTimer(TowerPlugin.getInstance(), 0L, 10L);
    }

    // [New Helper] 전진하는 거대 검기 (속도 UP, 곡률 UP)
    private void shootTsunamiSlash(Player player, Element element, Vector dir, double tilt) {
        new BukkitRunnable() {
            Location currentLoc = player.getEyeLocation().add(dir.clone().multiply(2.0));
            double distance = 0;
            double maxDistance = 16.0; // 사거리 소폭 증가
            final double fixedSize = 8.0;

            @Override
            public void run() {
                if (distance >= maxDistance) {
                    this.cancel();
                    return;
                }

                // 1. 이동 (속도 증가: 1.5 -> 2.0)
                // 더 빠르게 이동하여 잔상이 남지 않고 시원하게 뻗어나감
                currentLoc.add(dir.clone().multiply(2.0));
                distance += 2.0;

                // 2. 그리기 (곡률 대폭 증가)
                // Curve: 0.6 -> 1.5 (깊게 휨)
                // Tilt: 입력값(-45.0)
                drawLayeredSlash(currentLoc, dir, fixedSize, 1.5, tilt, 40, element, 5);

                // 바닥 스파크
                Location ground = currentLoc.clone();
                ground.setY(player.getLocation().getY());
                player.getWorld().spawnParticle(Particle.CRIT, ground, 2, 1.0, 0.1, 1.0, 0.1);

                // 3. 타격 판정 (이동 속도가 빨라졌으므로 판정 범위도 살짝 조정)
                for (LivingEntity victim : getTargets(player, currentLoc, 5.0, 3.0)) {
                    playHitExplosion(victim);
                    applyDamageAndEffect(player, victim, element, 1.5);

                    // 넉백
                    victim.setVelocity(dir.clone().multiply(1.8).setY(0.3));
                }
            }
        }.runTaskTimer(TowerPlugin.getInstance(), 0L, 1L);
    }

    private void playHitExplosion(LivingEntity victim) {
        Location loc = victim.getLocation().add(0, 1, 0);
        victim.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1.2f);
        victim.getWorld().spawnParticle(Particle.EXPLOSION, loc, 1);
        victim.getWorld().spawnParticle(Particle.FLASH, loc, 1);
    }

    // [Helper] 그라데이션 검기 그리기
    private void drawLayeredSlash(Location center, Vector dir, double size, double curvature, double tilt, int points, Element element, int layers) {
        for (int l = 0; l < layers; l++) {
            double ratio = (double) l / (layers - 1);
            double depthOffset = -0.5 + (ratio * 0.8);

            Color color = interpolateColor(element.getColor(), ratio);

            float pSize = 1.0f;
            if (ratio > 0.2 && ratio < 0.8) pSize = 1.5f;

            ParticleBuilder p = CoreProvider.createParticle()
                    .setParticle(Particle.DUST)
                    .setColor(color.getRed(), color.getGreen(), color.getBlue())
                    .setSize(pSize)
                    .setCount(1);

            CoreProvider.getParticleManager().drawSlash(
                    center.clone().add(dir.clone().multiply(depthOffset)),
                    dir, size, curvature, tilt, points, p
            );
        }
    }

    private Color interpolateColor(Color baseColor, double ratio) {
        int r, g, b;
        if (ratio < 0.2) {
            double t = ratio * 5.0;
            r = (int) (255 + (baseColor.getRed() - 255) * t);
            g = (int) (255 + (baseColor.getGreen() - 255) * t);
            b = (int) (255 + (baseColor.getBlue() - 255) * t);
        } else if (ratio < 0.8) {
            r = baseColor.getRed(); g = baseColor.getGreen(); b = baseColor.getBlue();
        } else {
            double t = (ratio - 0.8) * 5.0;
            r = (int) (baseColor.getRed() * (1 - t));
            g = (int) (baseColor.getGreen() * (1 - t));
            b = (int) (baseColor.getBlue() * (1 - t));
        }
        return Color.fromRGB(r, g, b);
    }

    // =================================================================
    // [Combo] L -> R: 숄더 차지 (혜성 돌파 + 소닉붐 임팩트)
    // =================================================================
    // =================================================================
    // [Combo] L -> R: 숄더 차지 (에너지 방패 돌진 + 소닉붐 임팩트)
    // =================================================================
    private void doShoulderCharge(Player player, Element element) {
        Vector dir = player.getLocation().getDirection().normalize();

        // 1. 강력한 돌진
        player.setVelocity(dir.clone().multiply(2.2).setY(0.1));
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_TRIDENT_RIPTIDE_3, 1f, 0.8f);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1f, 0.5f);

        // 2. 돌진 이펙트 (DUST로 교체)
        new BukkitRunnable() {
            int tick = 0;
            final java.util.Set<UUID> hitMobs = new java.util.HashSet<>();

            // 파티클 빌더 미리 생성
            final ParticleBuilder whiteDust = CoreProvider.createParticle()
                    .setParticle(Particle.DUST)
                    .setColor(255, 255, 255) // 하얀색 (공기 저항)
                    .setSize(0.8f);

            final ParticleBuilder shieldDust = CoreProvider.createParticle()
                    .setParticle(Particle.DUST)
                    .setColor(element.getColor().getRed(), element.getColor().getGreen(), element.getColor().getBlue())
                    .setSize(1.5f); // 속성색 (에너지 방패)

            @Override
            public void run() {
                if (tick >= 8) {
                    this.cancel();
                    return;
                }

                Location center = player.getLocation().add(0, 1.0, 0);

                // --- [A] 에너지 방패 & 공기 파동 ---

                // 1. 맨 앞: 속성 에너지 방패 (작고 진한 원)
                // 위치: 눈앞 1.5칸
                drawVerticalCircle(center.clone().add(dir.clone().multiply(1.5)), dir, 0.6, 20, shieldDust);

                // 2. 중간: 공기 파동 1 (중간 크기 흰색 원)
                // 위치: 눈앞 1.0칸
                drawVerticalCircle(center.clone().add(dir.clone().multiply(1.0)), dir, 0.9, 16, whiteDust);

                // 3. 뒤쪽: 공기 파동 2 (큰 흰색 원)
                // 위치: 눈앞 0.5칸
                drawVerticalCircle(center.clone().add(dir.clone().multiply(0.5)), dir, 1.2, 16, whiteDust);

                // 4. 잔상 (Trail) - 플레이어 몸 뒤쪽
                player.getWorld().spawnParticle(Particle.DUST, center.clone().add(dir.clone().multiply(-0.5)), 5, 0.3, 0.5, 0.3, 0,
                        new Particle.DustOptions(element.getColor(), 1.0f));


                // --- [B] 충돌 판정 ---
                for (LivingEntity victim : getTargets(player, center, 2.5, 2.5)) {
                    if (hitMobs.contains(victim.getUniqueId())) continue;
                    hitMobs.add(victim.getUniqueId());

                    applyDamageAndEffect(player, victim, element, 1.2);
                    victim.setVelocity(dir.clone().multiply(2.5).setY(0.4));
                    victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 2));

                    // 소닉붐 피격 이펙트 (기존 유지)
                    playSonicBoomImpact(victim.getLocation().add(0, 1.0, 0), dir, element);
                }

                tick++;
            }
        }.runTaskTimer(TowerPlugin.getInstance(), 0L, 1L);
    }

    // [New Helper] 피격 시 소닉붐 이펙트 (수직으로 퍼지는 원)
    private void playSonicBoomImpact(Location center, Vector dir, Element element) {
        // 소리: 쾅!
        center.getWorld().playSound(center, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.0f, 1.5f);
        center.getWorld().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 2.0f);

        // 1. 중앙 폭발
        center.getWorld().spawnParticle(Particle.FLASH, center, 1);

        // 2. 퍼져나가는 고리 (Animation)
        new BukkitRunnable() {
            double radius = 0.5;

            // 파티클: 속성색 + 흰색 섞기
            final ParticleBuilder ringDust = CoreProvider.createParticle()
                    .setParticle(Particle.DUST)
                    .setColor(element.getColor().getRed(), element.getColor().getGreen(), element.getColor().getBlue())
                    .setSize(1.5f);

            final ParticleBuilder whiteDust = CoreProvider.createParticle()
                    .setParticle(Particle.DUST)
                    .setColor(255, 255, 255)
                    .setSize(1.2f);

            @Override
            public void run() {
                if (radius > 3.5) {
                    this.cancel();
                    return;
                }

                // 수직 원 그리기 (진행 방향 기준)
                drawVerticalCircle(center, dir, radius, 30, ringDust);
                drawVerticalCircle(center, dir, radius - 0.2, 30, whiteDust); // 안쪽에 흰색 테두리

                radius += 0.8; // 빠르게 퍼짐
            }
        }.runTaskTimer(TowerPlugin.getInstance(), 0L, 1L);
    }

    // [Helper] 진행 방향에 수직인 원 그리기 (Vertical Circle)
    private void drawVerticalCircle(Location center, Vector dir, double radius, int points, ParticleBuilder builder) {
        // 1. 기준 축 계산
        // dir(진행방향)을 법선 벡터(Normal Vector)로 하는 평면을 구함
        Vector up = new Vector(0, 1, 0);
        if (Math.abs(dir.getY()) > 0.95) up = new Vector(1, 0, 0);

        Vector right = dir.getCrossProduct(up).normalize(); // 오른쪽
        Vector trueUp = right.getCrossProduct(dir).normalize(); // 위쪽 (진행방향 기준)

        for (int i = 0; i < points; i++) {
            double angle = 2 * Math.PI * i / points;

            // 평면상의 원 좌표 계산
            double x = Math.cos(angle) * radius; // Right축 성분
            double y = Math.sin(angle) * radius; // Up축 성분

            // 벡터 합성
            Vector offset = right.clone().multiply(x).add(trueUp.clone().multiply(y));

            CoreProvider.getParticleManager().spawn(center.clone().add(offset), builder);
        }
    }

    // =================================================================
    // [Combo] L -> L -> R: 어스 퀘이크 (BlockDisplay 돌덩이 + 착지 감지)
    // =================================================================
    // =================================================================
    // [Combo] L -> L -> R: 어스 퀘이크 (돌멩이 위치 동기화 수정)
    // =================================================================
    private void doEarthquake(Player player, Element element) {
        // 1. 도약
        player.setVelocity(new Vector(0, 1.2, 0));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1f, 0.5f);
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_GRASS_BREAK, 1f, 0.5f);

        // [핵심 1] 거대 돌멩이 소환
        List<org.bukkit.entity.BlockDisplay> floatingRocks = new java.util.ArrayList<>();
        Location center = player.getLocation();

        for (int i = 0; i < 8; i++) {
            double x = (Math.random() - 0.5) * 5.0;
            double z = (Math.random() - 0.5) * 5.0;
            // 플레이어보다 살짝 아래에서 시작
            Location rockLoc = center.clone().add(x, -1.0, z);

            org.bukkit.entity.BlockDisplay rock = (org.bukkit.entity.BlockDisplay) center.getWorld().spawnEntity(rockLoc, org.bukkit.entity.EntityType.BLOCK_DISPLAY);
            rock.setBlock(org.bukkit.Material.COBBLESTONE.createBlockData());

            float scale = 0.4f + (float)(Math.random() * 0.4);
            rock.setTransformation(new org.bukkit.util.Transformation(
                    new org.joml.Vector3f(0,0,0),
                    new org.joml.AxisAngle4f(0,0,0,1),
                    new org.joml.Vector3f(scale, scale, scale),
                    new org.joml.AxisAngle4f(0,0,0,1)
            ));

            floatingRocks.add(rock);
        }

        // 2. 급강하 및 착지 감지 루프
        new BukkitRunnable() {
            int tick = 0;
            boolean isPlunging = false;

            @Override
            public void run() {
                // [핵심 2] 돌멩이 높이 동기화 (플레이어 Y좌표 따라가기)
                // 플레이어가 공중에 있는 동안, 돌멩이들도 플레이어 발밑(-1.5칸) 높이로 이동
                if (!player.isDead() && player.isOnline()) {
                    double targetY = player.getLocation().getY() - 1.5;

                    for (org.bukkit.entity.BlockDisplay rock : floatingRocks) {
                        Location newLoc = rock.getLocation();
                        newLoc.setY(targetY); // Y좌표만 플레이어 따라감 (X, Z는 유지)

                        // 회전 애니메이션 추가 (선택사항: 빙글빙글 돌면서 올라감)
                        newLoc.setYaw(newLoc.getYaw() + 10);

                        rock.teleport(newLoc);
                    }
                }

                // 10틱(0.5초) 뒤 급강하 시작
                if (!isPlunging && tick >= 10) {
                    player.setVelocity(new Vector(0, -2.5, 0));
                    isPlunging = true;
                }

                // 땅에 닿았는지 확인
                if (isPlunging) {
                    if (player.isOnGround() || tick > 40) {
                        this.cancel();

                        // 돌멩이 파괴 이펙트
                        for (org.bukkit.entity.BlockDisplay rock : floatingRocks) {
                            rock.getWorld().spawnParticle(Particle.BLOCK, rock.getLocation(), 10, 0.5, 0.5, 0.5, org.bukkit.Material.COBBLESTONE.createBlockData());
                            rock.remove();
                        }

                        // 착지 이펙트 발동
                        triggerEarthquakeSmash(player, element);
                    }
                }
                tick++;
            }
        }.runTaskTimer(TowerPlugin.getInstance(), 0L, 1L);
    }

    // 착지 시 실행 (위치 보정됨)
    private void triggerEarthquakeSmash(Player player, Element element) {
        Location loc = player.getLocation(); // [중요] 현재 착지한 위치

        // 사운드
        player.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1f);
        player.getWorld().playSound(loc, Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 1f, 0.5f);

        // 이펙트 (가시 + 파동)
        playEarthquakeImpact(loc, element);

        // [핵심 3] 피격 범위 확대 (7 -> 10)
        for (LivingEntity victim : getTargets(player, loc, 10, 5)) {
            applyDamageAndEffect(player, victim, element, 2.0);

            victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 50, 2));
            victim.setVelocity(new Vector(0, 0.6, 0)); // 띄움
            victim.sendMessage("§c[!] 지진으로 인해 중심을 잃었습니다!");
        }
    }

    // [New Helper] 어스 퀘이크 복합 이펙트 (가시성 강화)
    private void playEarthquakeImpact(Location center, Element element) {
        // 1. 중앙 대폭발
        center.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, center, 3);
        center.getWorld().spawnParticle(Particle.BLOCK, center, 100, 3, 0.5, 3, 0.1,
                org.bukkit.Material.DIRT.createBlockData());

        // 2. 무작위 가시 솟구침 (강화됨)
        ParticleBuilder spikeDust = CoreProvider.createParticle()
                .setParticle(Particle.DUST)
                .setColor(element.getColor().getRed(), element.getColor().getGreen(), element.getColor().getBlue())
                .setSize(2.5f); // 두께 대폭 증가 (1.5 -> 2.5)

        for (int i = 0; i < 12; i++) { // 개수 증가 (8 -> 12)
            double angle = Math.random() * Math.PI * 2;
            double dist = 2.0 + (Math.random() * 5.0);
            Location spikeLoc = center.clone().add(Math.cos(angle) * dist, 0, Math.sin(angle) * dist);

            // 가시 그리기 (높이 3.5칸)
            CoreProvider.getParticleManager().drawLine(
                    spikeLoc,
                    spikeLoc.clone().add(0, 3.5, 0),
                    5.0,
                    spikeDust
            );

            // [추가] 돌기둥 솟구침 이펙트 (입체감)
            // 가시 위치에서 돌 파편이 기둥 모양으로 튐
            spikeLoc.getWorld().spawnParticle(Particle.BLOCK, spikeLoc.clone().add(0, 1.5, 0), 15, 0.3, 1.5, 0.3,
                    org.bukkit.Material.STONE.createBlockData());

            // 끝부분 섬광
            spikeLoc.getWorld().spawnParticle(Particle.CRIT, spikeLoc.clone().add(0, 3.5, 0), 5);
        }

        // 3. 퍼져나가는 고리 파동 (구름 추가)
        new BukkitRunnable() {
            double radius = 1.0;
            final double maxRadius = 10.0;

            ParticleBuilder ringDust = CoreProvider.createParticle()
                    .setParticle(Particle.DUST)
                    .setColor(element.getColor().getRed(), element.getColor().getGreen(), element.getColor().getBlue())
                    .setSize(2.0f); // 고리 두께 증가

            @Override
            public void run() {
                if (radius > maxRadius) {
                    this.cancel();
                    return;
                }

                // 메인 속성 고리
                CoreProvider.getParticleManager().drawCircle(center.clone().add(0, 0.2, 0), radius, (int)(radius * 12), ringDust);

                // [추가] 흙먼지 구름 파동 (범위 가시성 확보)
                // 바닥에 깔려서 퍼짐
                for (int i = 0; i < radius * 6; i++) {
                    double angle = 2 * Math.PI * i / (radius * 6);
                    double x = Math.cos(angle) * radius;
                    double z = Math.sin(angle) * radius;
                    center.getWorld().spawnParticle(Particle.CLOUD, center.clone().add(x, 0.2, z), 0, x, 0, z, 0.1);
                }

                radius += 0.8; // 퍼지는 속도 조절
            }
        }.runTaskTimer(TowerPlugin.getInstance(), 0L, 1L);
    }

    // =================================================================
    // [Combo] L -> L -> L -> R: 길로틴 (구체 유지 + 처형)
    // =================================================================
    // =================================================================
    // [Combo] L -> L -> L -> R: 길로틴 (적 머리 위 사형 선고 -> 처형)
    // =================================================================
    private void doGuillotine(Player player, Element element) {
        // 1. 전조 설정
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 25, 5));
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 2f, 2.0f);
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_TRIDENT_THUNDER, 1f, 0.5f);

        Vector dir = player.getLocation().getDirection().setY(0).normalize();
        Location center = player.getLocation().add(dir.clone().multiply(5.0)); // 전방 5칸 기준

        // [핵심 1] 사형수 명단 확보 (시전 즉시 타겟팅)
        List<LivingEntity> targets = getTargets(player, center, 7, 5);

        // 타겟이 없으면 허공(중앙)에라도 연출하기 위해 리스트에 가짜 위치용 null 대신 center 사용 로직 분리
        boolean hasTargets = !targets.isEmpty();

        ParticleBuilder chargeOrb = CoreProvider.createParticle()
                .setParticle(Particle.DUST)
                .setColor(element.getColor().getRed(), element.getColor().getGreen(), element.getColor().getBlue())
                .setSize(1.5f);

        // [핵심 2] 구체 유지 태스크 (적 머리 위 11m)
        BukkitTask orbTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (hasTargets) {
                    for (LivingEntity target : targets) {
                        if (target.isValid() && !target.isDead()) {
                            // 검 높이(10m)보다 높은 11m 위에 생성
                            Location orbLoc = target.getLocation().add(0, 11.0, 0);

                            // 지름 1.5m = 반지름 0.75
                            CoreProvider.getParticleManager().drawSphere(orbLoc, 0.75, 20, chargeOrb);

                            // 징표 느낌의 파티클
                            target.getWorld().spawnParticle(Particle.SOUL, orbLoc, 1, 0.2, 0.2, 0.2, 0.0);
                        }
                    }
                } else {
                    // 적이 없으면 스킬 중심점(Center) 위에 하나 띄움
                    Location orbLoc = center.clone().add(0, 11.0, 0);
                    CoreProvider.getParticleManager().drawSphere(orbLoc, 0.75, 20, chargeOrb);
                }
            }
        }.runTaskTimer(TowerPlugin.getInstance(), 0L, 1L);

        // 2. 처형 및 붕괴 (0.6초 후)
        new BukkitRunnable() {
            @Override
            public void run() {
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 1f, 0.8f);
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1f, 1f);

                // 지면 붕괴 이펙트
                playEnhancedFissure(player, dir);

                // [핵심 3] 거대 검 소환 (구체가 있던 위치 바로 아래로 꽂힘)
                if (hasTargets) {
                    for (LivingEntity target : targets) {
                        drawGiantParticleSword(target.getLocation(), element);
                    }
                } else {
                    drawGiantParticleSword(center, element);
                }

                // 실제 대미지 판정
                checkHitGuillotine(player, element, center, dir);
            }
        }.runTaskLater(TowerPlugin.getInstance(), 12L);

        // 3. 종료 (1.5초 후 구체 삭제)
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!orbTask.isCancelled()) {
                    orbTask.cancel();
                }
            }
        }.runTaskLater(TowerPlugin.getInstance(), 30L);
    }

    // [New Helper] 거대 파티클 검 그리기 (높이 10m)
    private void drawGiantParticleSword(Location loc, Element element) {
        // 위치 보정 (땅에 박힌 기준)
        Location root = loc.clone();
        root.setY(loc.getWorld().getHighestBlockYAt(loc));

        // 1. 칼날 (Blade) - 속성색
        ParticleBuilder blade = CoreProvider.createParticle()
                .setParticle(Particle.DUST)
                .setColor(element.getColor().getRed(), element.getColor().getGreen(), element.getColor().getBlue())
                .setSize(2.0f); // 두껍게

        // 2. 손잡이 (Hilt) - 검은색
        ParticleBuilder hilt = CoreProvider.createParticle()
                .setParticle(Particle.DUST)
                .setColor(0, 0, 0)
                .setSize(2.5f);

        // --- 그리기 (높이 10칸) ---
        // A. 칼날 (바닥 ~ 7칸)
        CoreProvider.getParticleManager().drawLine(
                root.clone().add(0, 0.5, 0),
                root.clone().add(0, 7.0, 0),
                8.0, blade
        );

        // B. 코등이 (7칸 높이에서 가로지름)
        // 십자가 모양으로 2개 그림
        CoreProvider.getParticleManager().drawLine(root.clone().add(-1.5, 7.0, 0), root.clone().add(1.5, 7.0, 0), 5.0, hilt);
        CoreProvider.getParticleManager().drawLine(root.clone().add(0, 7.0, -1.5), root.clone().add(0, 7.0, 1.5), 5.0, hilt);

        // C. 손잡이 (7칸 ~ 10칸)
        CoreProvider.getParticleManager().drawLine(
                root.clone().add(0, 7.0, 0),
                root.clone().add(0, 10.0, 0),
                4.0, hilt
        );

        // D. 충격파
        root.getWorld().spawnParticle(Particle.EXPLOSION, root, 1);
        root.getWorld().spawnParticle(Particle.LAVA, root, 10, 0.5, 0.5, 0.5);
    }

    // [New Helper] 강화된 지면 붕괴 (Fissure)
    private void playEnhancedFissure(Player player, Vector dir) {
        Vector right = dir.getCrossProduct(new Vector(0, 1, 0)).normalize(); // 오른쪽 벡터

        new BukkitRunnable() {
            double distance = 1.0;
            final double maxDist = 12.0;

            @Override
            public void run() {
                if (distance >= maxDist) {
                    this.cancel();
                    return;
                }

                Location ground = player.getLocation().add(dir.clone().multiply(distance));
                // 바닥 높이 보정
                ground.setY(ground.getWorld().getHighestBlockYAt(ground));

                // 1. 중앙 균열 (용암 + 폭발)
                player.getWorld().spawnParticle(Particle.EXPLOSION, ground.clone().add(0, 1, 0), 1);
                player.getWorld().spawnParticle(Particle.LAVA, ground.clone().add(0, 0.5, 0), 2);

                // 2. 양옆으로 갈라지는 땅 (블록 파편 대량)
                // 왼쪽
                player.getWorld().spawnParticle(Particle.BLOCK, ground.clone().add(right.clone().multiply(-1.5)).add(0, 1, 0),
                        15, 0.5, 0.5, 0.5, 0.1, org.bukkit.Material.DIRT.createBlockData());

                // 오른쪽
                player.getWorld().spawnParticle(Particle.BLOCK, ground.clone().add(right.clone().multiply(1.5)).add(0, 1, 0),
                        15, 0.5, 0.5, 0.5, 0.1, org.bukkit.Material.DIRT.createBlockData());

                distance += 2.0; // 빠르게 전진
            }
        }.runTaskTimer(TowerPlugin.getInstance(), 0L, 1L);
    }

    private List<LivingEntity> getTargets(Player player, Location center, double radius, double height) {
        List<LivingEntity> targets = new java.util.ArrayList<>();
        for (org.bukkit.entity.Entity e : center.getWorld().getNearbyEntities(center, radius, height, radius)) {
            if (e instanceof LivingEntity victim && e != player) targets.add(victim);
        }
        return targets;
    }

    private void checkHit(Player player, Element element, Location center, Vector dir, double multiplier, double range, double angleDeg) {
        for (LivingEntity victim : getTargets(player, center, range, 4)) {
            Vector toTarget = victim.getLocation().subtract(player.getLocation()).toVector().normalize();
            double angleRad = Math.toRadians(angleDeg / 2.0);
            if (dir.dot(toTarget) > Math.cos(angleRad)) {
                applyDamageAndEffect(player, victim, element, multiplier);
            }
        }
    }

    private void checkHitGuillotine(Player player, Element element, Location center, Vector dir) {
        for (LivingEntity victim : getTargets(player, center, 6, 5)) {
            Vector toTarget = victim.getLocation().subtract(player.getLocation()).toVector().normalize();
            if (dir.dot(toTarget) > 0.5) {
                double multiplier = 3.0;
                double maxHp = victim.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
                if (victim.getHealth() / maxHp < 0.3) {
                    multiplier = 6.0;
                    player.playSound(player.getLocation(), Sound.ENTITY_ARROW_HIT_PLAYER, 1f, 0.5f);
                    victim.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, victim.getLocation(), 20, 0.5, 1, 0.5, 0.1);
                }
                applyDamageAndEffect(player, victim, element, multiplier);
            }
        }
    }

    private void applyDamageAndEffect(Player attacker, LivingEntity victim, Element element, double multiplier) {
        DamageCalculator.DamageResult result = DamageCalculator.calculate(attacker, victim, multiplier, true);
        if (result.isCancelled()) return;
        double damage = result.damage();
        victim.setVelocity(attacker.getLocation().getDirection().multiply(0.5).setY(0.2));
        switch (element) {
            case FIRE: victim.setFireTicks(100); damage *= 1.2; break;
            case ICE: victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 2)); break;
            case STORM: damage += 20; victim.getWorld().strikeLightningEffect(victim.getLocation()); break;
            // ...
        }
        CoreProvider.dealDamage(attacker, victim, damage, result.isCrit());
    }
}