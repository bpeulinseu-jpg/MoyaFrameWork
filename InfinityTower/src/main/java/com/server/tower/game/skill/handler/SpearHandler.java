package com.server.tower.game.skill.handler;

import com.server.core.api.CoreProvider;
import com.server.core.system.particle.ParticleBuilder;
import com.server.tower.TowerPlugin;
import com.server.tower.game.DamageCalculator;
import com.server.tower.game.skill.Element;
import com.server.tower.game.skill.WeaponHandler;
import org.bukkit.*;
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

public class SpearHandler implements WeaponHandler {

    private static class ComboState {
        int step = 0; // 0~4 (5타)
        long lastActionTime = 0;
    }

    private final Map<UUID, ComboState> comboMap = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastClickTime = new ConcurrentHashMap<>();

    // 창은 콤보가 끊기지 않게 여유 시간을 줌 (1.2초)
    private static final long COMBO_TIMEOUT = 1200;

    @Override
    public void onLeftClick(Player player, Element element) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();

        if (now - lastClickTime.getOrDefault(uuid, 0L) < 150) return;
        lastClickTime.put(uuid, now);

        ComboState state = comboMap.computeIfAbsent(uuid, k -> new ComboState());
        if (now - state.lastActionTime > COMBO_TIMEOUT) state.step = 0;
        state.lastActionTime = now;

        switch (state.step) {
            case 0: // 1타: 뾰족한 찌르기
                performFlashSting(player, element, 4.5, 0.8);
                state.step = 1;
                break;
            case 1: // 2타: 화려한 횡베기
                performCrescentCut(player, element);
                state.step = 2;
                break;
            case 2: // 3타: 순차 2연격 (따닥!)
                performTwinFang(player, element);
                state.step = 3;
                break;
            case 3: // 4타: 3번 찌르기 (그림 1)
                performTripleSting(player, element);
                state.step = 4;
                break;
            case 4: // 5타: 거대 드릴 빔 (그림 2)
                performStarfall(player, element);
                state.step = 0;
                break;
        }
    }

    // =================================================================
    // [Action 1] 플래시 스팅 (직선 빔 + 하얀색 소닉붐 고리)
    // =================================================================
    private void performFlashSting(Player player, Element element, double range, double dmgMult) {
        Vector dir = player.getLocation().getDirection();
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1f, 1.5f);

        Location start = player.getEyeLocation().add(0, -0.3, 0);
        Location end = start.clone().add(dir.clone().multiply(range));

        // 파티클 설정
        ParticleBuilder mainDust = CoreProvider.createParticle()
                .setParticle(Particle.DUST)
                .setColor(element.getColor().getRed(), element.getColor().getGreen(), element.getColor().getBlue())
                .setSize(0.8f);

        // [수정] 소닉붐용 하얀색 파티클
        ParticleBuilder sonicDust = CoreProvider.createParticle()
                .setParticle(Particle.DUST)
                .setColor(255, 255, 255) // 완전 흰색
                .setSize(1.0f); // 약간 두껍게

        // 1. 메인 빔 (직선)
        CoreProvider.getParticleManager().drawLine(start, end, 8.0, mainDust);

        // 2. [추가] 소닉붐 고리 (Sonic Boom Rings)
        // 경로 중간중간에 하얀색 고리를 그림
        for (double d = 1.0; d < range; d += 1.5) {
            Location ringLoc = start.clone().add(dir.clone().multiply(d));

            // 거리에 따라 고리가 조금씩 커짐 (0.3 -> 0.5)
            double ringRadius = 0.3 + (d * 0.05);

            CoreProvider.getParticleManager().drawRing(
                    ringLoc,
                    dir,
                    ringRadius,
                    12,
                    sonicDust
            );
        }

        checkHitLine(player, element, range, 1.0, dmgMult);
    }

    // =================================================================
    // [Action 2] 크레센트 컷 (그라데이션 횡베기)
    // =================================================================
    private void performCrescentCut(Player player, Element element) {
        Vector dir = player.getLocation().getDirection();
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 1.2f);

        Location center = player.getEyeLocation().add(dir.clone().multiply(2.5));

        // 그라데이션 베기 (검과 동일한 퀄리티)
        // size: 4.0, curve: 0.2, tilt: 0.0 (수평), layers: 5
        drawLayeredSlash(center, dir, 4.0, 0.2, 0.0, 20, element, 5);

        checkHitCone(player, element, 3.5, 120.0, 1.0);
    }

    // =================================================================
    // [Action 3] 트윈 팡 (좌우 교차 찌르기)
    // =================================================================
    private void performTwinFang(Player player, Element element) {
        // 1타 (왼쪽에서 찌름)
        // 2타 (오른쪽에서 찌름) - 0.15초 뒤

        new BukkitRunnable() {
            int count = 0;

            @Override
            public void run() {
                if (count >= 2 || !player.isOnline()) {
                    this.cancel();
                    return;
                }

                Vector dir = player.getLocation().getDirection();
                // 기준 축 계산 (Right Vector)
                Vector right = dir.getCrossProduct(new Vector(0, 1, 0)).normalize();

                // 시작 위치 계산
                Location start = player.getEyeLocation().add(0, -0.3, 0);

                // 0번째: 왼쪽(-0.4), 1번째: 오른쪽(+0.4)
                double offsetMult = (count == 0) ? -0.4 : 0.4;
                start.add(right.multiply(offsetMult));

                Location end = start.clone().add(dir.clone().multiply(4.0));

                // 사운드
                player.getWorld().playSound(player.getLocation(), Sound.ITEM_TRIDENT_THROW, 1f, 1.5f + (count * 0.2f));

                // 파티클
                ParticleBuilder main = CoreProvider.createParticle()
                        .setParticle(Particle.DUST)
                        .setColor(element.getColor().getRed(), element.getColor().getGreen(), element.getColor().getBlue())
                        .setSize(0.8f);

                ParticleBuilder sub = CoreProvider.createParticle()
                        .setParticle(Particle.DUST)
                        .setColor(255, 255, 255)
                        .setSize(0.6f);

                // 찌르기 (메인 + 잔상)
                CoreProvider.getParticleManager().drawLine(start, end, 6.0, main);
                // 잔상은 살짝 더 옆에서
                CoreProvider.getParticleManager().drawLine(start.clone().add(right.clone().multiply(0.1)), end, 4.0, sub);

                // 타격 (0.8배 x 2회)
                checkHitLine(player, element, 4.0, 1.0, 0.8);

                count++;
            }
        }.runTaskTimer(TowerPlugin.getInstance(), 0L, 3L); // 3틱 간격
    }

    // =================================================================
    // [Action 4] 트리플 스팅 (삼각 찌르기 - 좌하/우하/상)
    // =================================================================
    private void performTripleSting(Player player, Element element) {
        // 2틱(0.1초) 간격으로 3번 찌름 (삼각형 모양)
        new BukkitRunnable() {
            int count = 0;

            @Override
            public void run() {
                if (count >= 3 || !player.isOnline()) {
                    this.cancel();
                    return;
                }

                Vector dir = player.getLocation().getDirection();
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1f, 1.2f + (count * 0.2f));

                // 기준 축 계산
                Vector upVector = new Vector(0, 1, 0);
                if (Math.abs(dir.getY()) > 0.95) upVector = new Vector(1, 0, 0);
                Vector right = dir.getCrossProduct(upVector).normalize();
                Vector trueUp = right.getCrossProduct(dir).normalize();

                // 시작 위치 (눈높이)
                Location start = player.getEyeLocation().add(0, -0.2, 0);

                // [위치 오프셋 적용] 삼각형 구도
                // 0: 왼쪽 아래
                // 1: 오른쪽 아래
                // 2: 위쪽 (중앙)
                switch (count) {
                    case 0: // 좌하
                        start.add(right.clone().multiply(-0.3)).add(trueUp.clone().multiply(-0.2));
                        break;
                    case 1: // 우하
                        start.add(right.clone().multiply(0.3)).add(trueUp.clone().multiply(-0.2));
                        break;
                    case 2: // 상 (정중앙보다 살짝 위)
                        start.add(trueUp.clone().multiply(0.2));
                        break;
                }

                Location end = start.clone().add(dir.clone().multiply(4.5));

                ParticleBuilder mainDust = CoreProvider.createParticle()
                        .setParticle(Particle.DUST)
                        .setColor(element.getColor().getRed(), element.getColor().getGreen(), element.getColor().getBlue())
                        .setSize(0.8f);

                ParticleBuilder sonicDust = CoreProvider.createParticle()
                        .setParticle(Particle.DUST)
                        .setColor(255, 255, 255)
                        .setSize(0.8f);

                // 1. 빔 발사
                CoreProvider.getParticleManager().drawLine(start, end, 6.0, mainDust);

                // 2. 소닉붐 고리
                CoreProvider.getParticleManager().drawRing(start.clone().add(dir.clone().multiply(2.0)), dir, 0.3, 8, sonicDust);
                CoreProvider.getParticleManager().drawRing(start.clone().add(dir.clone().multiply(3.5)), dir, 0.4, 8, sonicDust);

                // 3. 타격
                checkHitLine(player, element, 4.5, 0.8, 0.5);

                count++;
            }
        }.runTaskTimer(TowerPlugin.getInstance(), 0L, 2L);
    }

    // =================================================================
    // [Action 5] 스타폴 (거대 드릴 빔 - 뾰족한 형태 >)
    // =================================================================
    private void performStarfall(Player player, Element element) {
        Vector dir = player.getLocation().getDirection();
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 2.0f);
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_TRIDENT_THUNDER, 1f, 1.5f);

        Location start = player.getEyeLocation().add(0, -0.3, 0);
        double range = 7.0;

        // 1. 중앙 빔 (검은색 코어)
        ParticleBuilder coreBeam = CoreProvider.createParticle()
                .setParticle(Particle.DUST)
                .setColor(0, 0, 0)
                .setSize(2.0f);
        CoreProvider.getParticleManager().drawLine(start, start.clone().add(dir.clone().multiply(range)), 10.0, coreBeam);

        // 2. [수정] 수렴형 드릴 이펙트 (Converging Drill)
        // 플레이어 쪽이 넓고(Wide), 끝으로 갈수록 좁아짐(Pointy) -> '>' 모양
        ParticleBuilder redBeam = CoreProvider.createParticle()
                .setParticle(Particle.DUST)
                .setColor(element.getColor().getRed(), element.getColor().getGreen(), element.getColor().getBlue())
                .setSize(1.5f);

        Vector up = (Math.abs(dir.getY()) > 0.95) ? new Vector(1, 0, 0) : new Vector(0, 1, 0);
        Vector right = dir.getCrossProduct(up).normalize();

        // 6개의 나선 줄기
        for (int i = 0; i < 6; i++) {
            double startAngle = Math.toRadians(i * 60);

            for (double d = 0; d < range; d += 0.2) {
                Location current = start.clone().add(dir.clone().multiply(d));

                // [핵심 수정] 반지름 계산식 변경
                // 기존: 0.2 + (d * 0.3) -> 갈수록 커짐 (< 모양)
                // 변경: 1.5 * (1.0 - d/range) -> 갈수록 작아짐 (> 모양)
                double progress = d / range; // 0.0 ~ 1.0
                double r = 1.2 * (1.0 - progress); // 시작 반지름 1.2 -> 끝 0.0

                // 0보다 작아지면 그리지 않음
                if (r < 0) r = 0;

                double angle = startAngle + (d * 2.0); // 회전

                Vector offset = right.clone().rotateAroundAxis(dir, angle).multiply(r);
                CoreProvider.getParticleManager().spawn(current.add(offset), redBeam);
            }
        }

        // 3. [수정] 끝부분 이펙트 (FLASH)
        Location end = start.clone().add(dir.clone().multiply(range));
        player.getWorld().spawnParticle(Particle.FLASH, end, 1); // 번쩍!
        player.getWorld().spawnParticle(Particle.CRIT, end, 15, 0.5, 0.5, 0.5, 0.5); // 파편

        checkHitLine(player, element, range, 2.0, 1.5);
    }

    // --- Helper Methods ---

    // 그라데이션 베기 (검에서 가져옴)
    private void drawLayeredSlash(Location center, Vector dir, double size, double curvature, double tilt, int points, Element element, int layers) {
        for (int l = 0; l < layers; l++) {
            double ratio = (double) l / (layers - 1);
            double depthOffset = -0.3 + (ratio * 0.6);

            // 색상 보간 (간단 버전)
            Color c = element.getColor();
            if (l == 0 || l == layers - 1) c = Color.BLACK; // 테두리 검정
            else if (l == layers / 2) c = Color.WHITE; // 중앙 흰색

            ParticleBuilder p = CoreProvider.createParticle()
                    .setParticle(Particle.DUST)
                    .setColor(c.getRed(), c.getGreen(), c.getBlue())
                    .setSize(1.0f).setCount(1);

            CoreProvider.getParticleManager().drawSlash(
                    center.clone().add(dir.clone().multiply(depthOffset)),
                    dir, size, curvature, tilt, points, p
            );
        }
    }


    @Override
    public void onRightClick(Player player, Element element) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();

        if (now - lastClickTime.getOrDefault(uuid, 0L) < 200) return;
        lastClickTime.put(uuid, now);

        ComboState state = comboMap.computeIfAbsent(uuid, k -> new ComboState());
        if (now - state.lastActionTime > COMBO_TIMEOUT) state.step = 0;
        state.lastActionTime = now;

        // --- 강공격 콤보 분기 ---
        switch (state.step) {
            case 0: // 대기 -> 소닉 대시 (이동기)
                doSonicDash(player, element);
                break;
            case 1: // L1 -> 백스텝 스러스트
                doBackstepThrust(player, element);
                state.step = 0;
                break;
            case 2: // L2 -> 라이트닝 드릴
                doPhantomJavelin(player, element);
                state.step = 0;
                break;
            case 3: // L3 -> 미라지 스톰 (난사)
                doMirageStorm(player, element);
                state.step = 0;
                break;
            case 4: // L4 -> 드래곤 다이브 (궁극기)
                doDragonDive(player, element);
                state.step = 0;
                break;
        }
    }

    // =================================================================
    // [Skill] R: 소닉 대시 (이동기)
    // =================================================================
    private void doSonicDash(Player player, Element element) {
        Vector dir = player.getLocation().getDirection().normalize();

        // 순간 가속
        player.setVelocity(dir.multiply(2.5).setY(0.2));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.5f);

        // 잔상 이펙트
        player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation(), 10, 0.5, 0.5, 0.5, 0.1);

        // 지나가는 경로 타격 (약함)
        new BukkitRunnable() {
            int tick = 0;
            @Override
            public void run() {
                if (tick > 5) { this.cancel(); return; }
                checkHitLine(player, element, 2.0, 1.0, 0.5); // 0.5배 대미지
                tick++;
            }
        }.runTaskTimer(TowerPlugin.getInstance(), 0L, 1L);
    }

    // =================================================================
    // [Combo] L -> R: 백스텝 스러스트 (판정 상향)
    // =================================================================
    private void doBackstepThrust(Player player, Element element) {
        Vector dir = player.getLocation().getDirection().normalize();

        // 1. 진입
        player.setVelocity(dir.clone().multiply(1.2).setY(0.1));
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_TRIDENT_THROW, 1f, 1.5f);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 1.2f);

        // 2. 드릴 이펙트 (길이 증가: 8.0 -> 10.0)
        drawSharpDrill(player, element, dir, 10.0);

        // 3. 타격 판정 (범위 상향)
        List<LivingEntity> impaledVictims = new java.util.ArrayList<>();
        Location start = player.getEyeLocation();

        // 검색 범위도 같이 늘려줌 (12.0)
        for (LivingEntity victim : getTargets(player, player.getLocation(), 12.0, 4)) {
            Vector toVictim = victim.getLocation().add(0, 0.5, 0).toVector().subtract(start.toVector());
            double dot = toVictim.dot(dir);

            // [수정] 사거리 10.0까지 판정
            if (dot > 0 && dot < 10.0) {
                double distSq = toVictim.lengthSquared() - (dot * dot);

                // [수정] 너비 반경 1.5칸 (제곱하면 2.25)
                // 기존 1.0보다 훨씬 넉넉해짐
                if (distSq < 2.25) {
                    applyDamageAndEffect(player, victim, element, 0.8);
                    playPenetrationEffect(victim, dir);

                    victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20, 10));
                    impaledVictims.add(victim);
                }
            }
        }

        // 헛방 시 바로 백스텝
        if (impaledVictims.isEmpty()) {
            new BukkitRunnable() {
                @Override public void run() { performBackstep(player); }
            }.runTaskLater(TowerPlugin.getInstance(), 5L);
            return;
        }

        // 4. 홀딩 (0.7초)
        new BukkitRunnable() {
            int tick = 0;
            @Override
            public void run() {
                if (tick >= 14 || !player.isOnline()) {
                    this.cancel();
                    performRip(player, element, impaledVictims);
                    return;
                }

                for (LivingEntity victim : impaledVictims) {
                    if (victim.isValid()) {
                        victim.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, victim.getEyeLocation(), 3, 0.2, 0.2, 0.2, 0.1);
                        victim.setVelocity(new Vector(0, 0, 0));
                    }
                }

                if (tick % 4 == 0) {
                    player.getWorld().playSound(player.getLocation(), Sound.BLOCK_GRINDSTONE_USE, 1f, 1.5f);
                }
                tick++;
            }
        }.runTaskTimer(TowerPlugin.getInstance(), 0L, 1L);
    }

    // [Action] 뒤로 빠지기 (공통)
    private void performBackstep(Player player) {
        Vector backDir = player.getLocation().getDirection().multiply(-1.5).setY(0.3);
        player.setVelocity(backDir);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BAT_TAKEOFF, 1f, 1.2f);
    }

    // [Action] 뽑아내기 (Rip)
    private void performRip(Player player, Element element, List<LivingEntity> victims) {
        performBackstep(player); // 뒤로 이동

        Vector dir = player.getLocation().getDirection();

        for (LivingEntity victim : victims) {
            if (victim.isValid() && !victim.isDead()) {
                // 2차 대미지 (강력함)
                applyDamageAndEffect(player, victim, element, 1.5);

                // [핵심 3] 뽑히는 이펙트 (피 분수 + 충격파)
                playRipEffect(victim, player);

                // 끌려옴
                Vector pullDir = player.getLocation().toVector().subtract(victim.getLocation().toVector()).normalize();
                victim.setVelocity(pullDir.multiply(1.0).setY(0.2));
            }
        }
    }

    // [Visual] 날카로운 드릴 (Starfall 변형: 작고 길게)
    private void drawSharpDrill(Player player, Element element, Vector dir, double range) {
        Location start = player.getEyeLocation().add(0, -0.3, 0);

        // 1. 중앙 빔
        ParticleBuilder coreBeam = CoreProvider.createParticle()
                .setParticle(Particle.DUST)
                .setColor(element.getColor().getRed(), element.getColor().getGreen(), element.getColor().getBlue())
                .setSize(1.0f); // 얇게
        CoreProvider.getParticleManager().drawLine(start, start.clone().add(dir.clone().multiply(range)), 8.0, coreBeam);

        // 2. 나선 (Drill)
        ParticleBuilder spiral = CoreProvider.createParticle()
                .setParticle(Particle.DUST)
                .setColor(255, 255, 255) // 흰색 나선
                .setSize(0.6f);

        Vector up = (Math.abs(dir.getY()) > 0.95) ? new Vector(1, 0, 0) : new Vector(0, 1, 0);
        Vector right = dir.getCrossProduct(up).normalize();

        // 3줄기 나선
        for (int i = 0; i < 3; i++) {
            double startAngle = Math.toRadians(i * 120);
            for (double d = 0; d < range; d += 0.2) {
                Location current = start.clone().add(dir.clone().multiply(d));

                // 반지름: 0.4로 일정하게 (얇고 날카로움)
                double r = 0.4;
                double angle = startAngle + (d * 2.5); // 회전

                Vector offset = right.clone().rotateAroundAxis(dir, angle).multiply(r);
                CoreProvider.getParticleManager().spawn(current.add(offset), spiral);
            }
        }
    }

    // [Visual] 관통 이펙트 (푸슉!)
    private void playPenetrationEffect(LivingEntity victim, Vector dir) {
        Location loc = victim.getEyeLocation();
        // 적의 등 뒤로 파티클이 뚫고 나감
        victim.getWorld().spawnParticle(Particle.SWEEP_ATTACK, loc, 1, dir.getX(), dir.getY(), dir.getZ());
        victim.getWorld().spawnParticle(Particle.CRIT, loc, 10, 0.2, 0.2, 0.2, 0.5); // 빠르게 튐
    }

    // [Visual] 뜯어내는 이펙트 (피 튀김 + 섬광)
    private void playRipEffect(LivingEntity victim, Player player) {
        Location loc = victim.getEyeLocation();

        // 소리: 우지끈!
        victim.getWorld().playSound(loc, Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 0.5f, 1.5f);
        victim.getWorld().playSound(loc, Sound.ENTITY_ITEM_BREAK, 1f, 0.8f);

        // 섬광
        victim.getWorld().spawnParticle(Particle.FLASH, loc, 1);

        // 피가 플레이어 쪽으로 튐 (Directional Blood Spray)
        Vector toPlayer = player.getEyeLocation().toVector().subtract(loc.toVector()).normalize();

        // 블록 파편(피)을 플레이어 방향으로 쏘아 보냄
        victim.getWorld().spawnParticle(Particle.BLOCK, loc, 20, 0.2, 0.2, 0.2, 0.0,
                Material.REDSTONE_BLOCK.createBlockData()); // count 0이면 방향 설정 가능하지만, BLOCK은 불가능할 수 있음

        // 대신 DUST 파티클을 방향성 있게 쏘기 (수동 구현)
        for(int i=0; i<5; i++) {
            Vector spray = toPlayer.clone().add(Vector.getRandom().multiply(0.5));
            victim.getWorld().spawnParticle(Particle.DUST, loc, 0, spray.getX(), spray.getY(), spray.getZ(), 0.5,
                    new Particle.DustOptions(Color.RED, 1.0f));
        }
    }

    // =================================================================
    // [Combo] L -> L -> R: 환영 투창 (긴 대기 -> 발사 + 적중 섬광)
    // =================================================================
    private void doPhantomJavelin(Player player, Element element) {
        Vector dir = player.getLocation().getDirection().normalize();

        // 1. 백스텝
        player.setVelocity(dir.clone().multiply(-1.2).setY(0.4));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PHANTOM_FLAP, 1f, 1.5f);

        // 2. 창 생성 및 발사 로직
        new BukkitRunnable() {
            int tick = 0;

            // [수정] 대기 시간 증가 (10 -> 25틱, 약 1.25초)
            final int hoverTime = 25;

            Location current = player.getEyeLocation().add(0, 1.5, 0); // 위치도 살짝 더 위로
            Vector velocity = dir.clone().multiply(2.5);
            double distance = 0;
            final double maxDist = 30.0; // 사거리 증가

            final ParticleBuilder sonicDust = CoreProvider.createParticle()
                    .setParticle(Particle.DUST)
                    .setColor(255, 255, 255)
                    .setSize(1.2f);

            @Override
            public void run() {
                if (!player.isOnline()) { this.cancel(); return; }

                // --- [1단계] 대기 (Hovering) ---
                if (tick < hoverTime) {
                    // 창 그리기
                    drawGiantParticleSpear(current, dir, element);

                    // 웅웅거리는 소리 (점점 고조됨)
                    if (tick % 5 == 0) {
                        float pitch = 0.5f + ((float)tick / hoverTime); // 피치 올라감
                        player.getWorld().playSound(current, Sound.BLOCK_BEACON_AMBIENT, 1f, pitch);
                        player.getWorld().spawnParticle(Particle.ENCHANT, current, 5, 0.5, 0.5, 0.5, 0);
                    }
                }
                // --- [2단계] 발사 (Launch) ---
                else {
                    if (tick == hoverTime) {
                        player.getWorld().playSound(current, Sound.ITEM_TRIDENT_THROW, 1f, 0.6f);
                        player.getWorld().playSound(current, Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 2.0f);
                    }

                    if (distance >= maxDist) {
                        this.cancel();
                        return;
                    }

                    current.add(velocity);
                    distance += 2.5;

                    drawGiantParticleSpear(current, dir, element);

                    // 소닉붐 고리
                    Location ringLoc = current.clone().subtract(dir.clone().multiply(1.0));
                    CoreProvider.getParticleManager().drawRing(ringLoc, dir, 1.0, 16, sonicDust);

                    // 충돌 감지
                    for (LivingEntity victim : getTargets(player, current, 2.0, 2.0)) {
                        if (victim != player) {
                            applyDamageAndEffect(player, victim, element, 1.5);

                            // [추가] 적중 시 섬광 (FLASH)
                            victim.getWorld().spawnParticle(Particle.FLASH, victim.getLocation().add(0, 1, 0), 1);

                            playSplitImpact(player, victim.getLocation().add(0, 1.0, 0), dir, element);
                            this.cancel();
                            return;
                        }
                    }

                    if (current.getBlock().getType().isSolid()) {
                        // 벽 충돌 시에도 섬광
                        current.getWorld().spawnParticle(Particle.FLASH, current, 1);
                        playSplitImpact(player, current, dir, element);
                        this.cancel();
                    }
                }
                tick++;
            }
        }.runTaskTimer(TowerPlugin.getInstance(), 0L, 1L);
    }
    // [New Helper] 적중 시 3갈래 분열 (Split Burst)
    private void playSplitImpact(Player player, Location center, Vector dir, Element element) {
        // 타격음
        center.getWorld().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1.5f);
        center.getWorld().playSound(center, Sound.BLOCK_AMETHYST_BLOCK_BREAK, 1f, 2.0f); // 챙그랑 소리

        // 중앙 폭발 이펙트
        center.getWorld().spawnParticle(Particle.FLASH, center, 1);
        center.getWorld().spawnParticle(Particle.CRIT, center, 20, 0.5, 0.5, 0.5, 0.5);

        // 분열 빔 설정
        ParticleBuilder splitBeam = CoreProvider.createParticle()
                .setParticle(Particle.DUST)
                .setColor(element.getColor().getRed(), element.getColor().getGreen(), element.getColor().getBlue())
                .setSize(1.2f);

        // 기준 축 (좌우)
        Vector up = new Vector(0, 1, 0);
        if (Math.abs(dir.getY()) > 0.95) up = new Vector(1, 0, 0);
        Vector right = dir.getCrossProduct(up).normalize();

        // 3갈래 발사 (-30도, 0도, +30도)
        for (int i = -1; i <= 1; i++) {
            double angle = Math.toRadians(i * 30);

            // 방향 회전 (Rodrigues Rotation)
            Vector splitDir = dir.clone().multiply(Math.cos(angle))
                    .add(right.clone().multiply(Math.sin(angle))).normalize();

            Location start = center.clone();
            Location end = center.clone().add(splitDir.multiply(6.0)); // 뒤로 6칸 뚫고 나감

            // 빔 그리기
            CoreProvider.getParticleManager().drawLine(start, end, 5.0, splitBeam);

            // 2차 타격 판정 (RayTrace와 유사하게 경로상의 적 타격)
            // 여기서는 간단하게 끝점 주변을 타격하거나, 선형 판정을 함
            // (편의상 checkHitLine 로직을 응용하여 구현)
            for (LivingEntity target : getTargets(player, center, 7.0, 3.0)) {
                Vector toTarget = target.getLocation().add(0, 0.5, 0).toVector().subtract(center.toVector());
                double dot = toTarget.normalize().dot(splitDir);

                // 각도가 맞고 거리가 6 이내인 적
                if (dot > 0.95 && toTarget.length() < 6.0) {
                    // 이미 1차 타격 맞은 적은 제외할 수도 있지만, 관통 컨셉상 다단히트 허용
                    applyDamageAndEffect(player, target, element, 1.0); // 분열 대미지
                    target.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, target.getLocation().add(0, 1, 0), 5);
                }
            }
        }
    }

    // [New Helper] 파티클로 거대 창 그리기
    private void drawGiantParticleSpear(Location tip, Vector dir, Element element) {
        // tip: 창의 맨 앞부분 (뾰족한 끝)

        // 기준 축 계산
        Vector up = (Math.abs(dir.getY()) > 0.95) ? new Vector(1, 0, 0) : new Vector(0, 1, 0);
        Vector right = dir.getCrossProduct(up).normalize();

        // 파티클 설정
        ParticleBuilder headDust = CoreProvider.createParticle()
                .setParticle(Particle.DUST)
                .setColor(element.getColor().getRed(), element.getColor().getGreen(), element.getColor().getBlue())
                .setSize(1.5f); // 창날 (두꺼움)

        ParticleBuilder shaftDust = CoreProvider.createParticle()
                .setParticle(Particle.DUST)
                .setColor(0, 0, 0) // 자루 (검은색)
                .setSize(1.2f);

        // --- 그리기 (총 길이 약 5m) ---

        // 1. 창날 (Head) - 길이 2m
        // 팁에서 뒤로 가면서 넓어짐 (삼각형 모양)
        Location headBase = tip.clone().subtract(dir.clone().multiply(2.0));

        // 중앙선
        CoreProvider.getParticleManager().drawLine(headBase, tip, 4.0, headDust);

        // 날개 (양옆으로 벌어짐)
        Vector wingOffset = right.clone().multiply(0.6); // 폭 0.6
        CoreProvider.getParticleManager().drawLine(headBase.clone().add(wingOffset), tip, 3.0, headDust);
        CoreProvider.getParticleManager().drawLine(headBase.clone().subtract(wingOffset), tip, 3.0, headDust);

        // 2. 자루 (Shaft) - 길이 3m
        // 창날 밑부분부터 뒤로 쭉 뻗음
        Location shaftEnd = headBase.clone().subtract(dir.clone().multiply(5.0));
        CoreProvider.getParticleManager().drawLine(shaftEnd, headBase, 5.0, shaftDust);

        // 3. 장식 (Guard) - 창날과 자루 연결부위
        // 십자가 모양
        CoreProvider.getParticleManager().drawLine(
                headBase.clone().add(right.clone().multiply(0.4)),
                headBase.clone().subtract(right.clone().multiply(0.4)),
                3.0, shaftDust
        );
    }

    // =================================================================
    // [Combo] L -> L -> L -> R: 미라지 스톰 (난사 -> 1초 응축 -> 전방 수렴형 드릴)
    // =================================================================
    private void doMirageStorm(Player player, Element element) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 70, 5)); // 지속시간 조금 늘림

        ParticleBuilder mainDust = CoreProvider.createParticle()
                .setParticle(Particle.DUST)
                .setColor(element.getColor().getRed(), element.getColor().getGreen(), element.getColor().getBlue())
                .setSize(0.8f);

        ParticleBuilder sonicDust = CoreProvider.createParticle()
                .setParticle(Particle.DUST)
                .setColor(255, 255, 255)
                .setSize(0.8f);

        new BukkitRunnable() {
            int tick = 0;
            final int barrageDuration = 40; // 2초 난사
            final int pauseDuration = 20;   // [수정] 1.0초 대기 (20틱)
            final int finishTick = barrageDuration + pauseDuration;

            @Override
            public void run() {
                if (!player.isOnline() || player.isDead()) { this.cancel(); return; }

                // --- [1단계] 초고속 난사 ---
                if (tick < barrageDuration) {
                    for (int i = 0; i < 2; i++) {
                        Vector dir = player.getLocation().getDirection();
                        Vector randomDir = dir.clone().add(new Vector(
                                (Math.random()-0.5)*0.5, (Math.random()-0.5)*0.5, (Math.random()-0.5)*0.5
                        )).normalize();

                        Location start = player.getEyeLocation().add(0, -0.3, 0);
                        Location end = start.clone().add(randomDir.multiply(6.0));

                        CoreProvider.getParticleManager().drawLine(start, end, 4.0, mainDust);
                        CoreProvider.getParticleManager().drawRing(start.clone().add(randomDir.clone().multiply(3.0)), randomDir, 0.3, 6, sonicDust);

                        player.getWorld().spawnParticle(Particle.FLASH, end, 1);
                        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.5f, 1.8f + (float)Math.random()*0.2f);

                        checkHitCone(player, element, 6.0, 30.0, 0.3);
                    }
                }
                // --- [2단계] 응축 (1초간) ---
                else if (tick < finishTick) {
                    // 기 모으는 위치도 전방으로 살짝 이동
                    Location chargeLoc = player.getEyeLocation().add(player.getLocation().getDirection().multiply(2.5));

                    if (tick % 5 == 0) {
                        player.getWorld().playSound(chargeLoc, Sound.BLOCK_BEACON_AMBIENT, 0.5f, 2.0f);
                    }

                    // 입자가 빨려들어옴
                    player.getWorld().spawnParticle(Particle.PORTAL, chargeLoc, 10, 1.0, 1.0, 1.0, 1.0);
                    player.getWorld().spawnParticle(Particle.ENCHANT, chargeLoc, 10, 1.0, 1.0, 1.0, 1.0);
                }
                // --- [3단계] 피니시 (수렴형 드릴) ---
                else {
                    this.cancel();

                    Vector dir = player.getLocation().getDirection().normalize();

                    // [수정] 시작 위치를 눈앞 2.5칸으로 이동 (시야 확보)
                    Location start = player.getEyeLocation().add(dir.clone().multiply(2.5));

                    player.getWorld().playSound(start, Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, 1f, 1.0f);
                    player.getWorld().playSound(start, Sound.ITEM_TRIDENT_THUNDER, 1f, 0.5f);

                    // 1. 거대 창 그리기
                    drawSuperGiantSpear(start, dir, element);

                    // 2. 수렴형 드릴 (Converging Drill: O > 모양)
                    Vector up = (Math.abs(dir.getY()) > 0.95) ? new Vector(1, 0, 0) : new Vector(0, 1, 0);
                    Vector right = dir.getCrossProduct(up).normalize();

                    ParticleBuilder drillDust = CoreProvider.createParticle()
                            .setParticle(Particle.DUST)
                            .setColor(element.getColor().getRed(), element.getColor().getGreen(), element.getColor().getBlue())
                            .setSize(2.5f);

                    // 5줄기 나선
                    for (int k = 0; k < 5; k++) {
                        double startAngle = Math.toRadians(k * 72);
                        for (double d = 0; d < 12.0; d += 0.2) {
                            Location current = start.clone().add(dir.clone().multiply(d));

                            // [핵심 수정] 반지름 계산: 시작은 넓고(2.5), 끝은 좁게(0.0)
                            double progress = d / 12.0; // 0.0 ~ 1.0
                            double r = 2.5 * (1.0 - progress);

                            if (r < 0) r = 0; // 음수 방지

                            double angle = startAngle + (d * 1.5); // 회전

                            Vector offset = right.clone().rotateAroundAxis(dir, angle).multiply(r);
                            CoreProvider.getParticleManager().spawn(current.add(offset), drillDust);
                        }
                    }

                    // 끝부분 대폭발
                    Location end = start.clone().add(dir.clone().multiply(12.0));
                    player.getWorld().spawnParticle(Particle.EXPLOSION, end, 3);
                    player.getWorld().spawnParticle(Particle.FLASH, end, 5);

                    // 피니시 타격 (시작 위치 보정)
                    checkHitLine(player, element, 14.0, 3.0, 4.0);
                }
                tick++;
            }
        }.runTaskTimer(TowerPlugin.getInstance(), 0L, 1L);
    }

    // [New Helper] 초거대 창 그리기 (피니시 전용)
    private void drawSuperGiantSpear(Location start, Vector dir, Element element) {
        // 기준 축
        Vector up = (Math.abs(dir.getY()) > 0.95) ? new Vector(1, 0, 0) : new Vector(0, 1, 0);
        Vector right = dir.getCrossProduct(up).normalize();

        ParticleBuilder head = CoreProvider.createParticle().setParticle(Particle.DUST)
                .setColor(element.getColor().getRed(), element.getColor().getGreen(), element.getColor().getBlue()).setSize(3.0f);
        ParticleBuilder shaft = CoreProvider.createParticle().setParticle(Particle.DUST).setColor(0, 0, 0).setSize(2.0f);

        // 팁 위치 (10칸 앞)
        Location tip = start.clone().add(dir.clone().multiply(10.0));

        // 1. 창날 (길이 4m, 폭 1.5m)
        Location headBase = tip.clone().subtract(dir.clone().multiply(4.0));

        // 중앙선
        CoreProvider.getParticleManager().drawLine(headBase, tip, 8.0, head);
        // 날개
        Vector wing = right.clone().multiply(1.5);
        CoreProvider.getParticleManager().drawLine(headBase.clone().add(wing), tip, 5.0, head);
        CoreProvider.getParticleManager().drawLine(headBase.clone().subtract(wing), tip, 5.0, head);

        // 2. 자루 (길이 6m)
        Location shaftEnd = headBase.clone().subtract(dir.clone().multiply(6.0)); // 플레이어 위치까지 옴
        CoreProvider.getParticleManager().drawLine(shaftEnd, headBase, 10.0, shaft);

        // 3. 장식 (크게)
        CoreProvider.getParticleManager().drawLine(
                headBase.clone().add(right.clone().multiply(1.0)),
                headBase.clone().subtract(right.clone().multiply(1.0)),
                5.0, shaft
        );
    }

    // =================================================================
    // [Combo] L -> L -> L -> L -> R: 드래곤 다이브 (날개 수정 + 낙하 드릴)
    // =================================================================
    private void doDragonDive(Player player, Element element) {
        // 1. 승천
        player.setVelocity(new Vector(0, 2.2, 0));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 1f, 0.5f);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1f, 0.8f);

        playAscensionHelix(player, element);

        new BukkitRunnable() {
            int tick = 0;
            boolean isPlunging = false;

            @Override
            public void run() {
                if (!player.isOnline() || player.isDead()) { this.cancel(); return; }

                // --- [1단계] 공중 체류 (Wings) ---
                if (tick >= 10 && tick < 45) {
                    player.setVelocity(new Vector(0, 0.04, 0));
                    player.setFallDistance(0);

                    // [수정] 박쥐/드래곤 모양 날개 그리기
                    drawGiantDragonWings(player, element);

                    Location targetBlock = getLookAtGround(player);
                    if (targetBlock != null) {
                        drawTargetCircle(targetBlock, element, tick);
                    }
                }

                // --- [2단계] 강림 시작 ---
                if (tick == 45) {
                    isPlunging = true;
                    Vector lookDir = player.getLocation().getDirection().normalize();
                    player.setVelocity(lookDir.multiply(3.5));

                    player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1f);
                    player.getWorld().playSound(player.getLocation(), Sound.ITEM_TRIDENT_THUNDER, 1f, 0.5f);
                }

                // --- [3단계] 비행 (Drill Effect) ---
                if (isPlunging) {
                    // 용의 형상
                    drawDragonHead(player, element);

                    // [추가] 앞쪽으로 쏠리는 드릴 이펙트 (>)
                    drawPlungeDrill(player, element);

                    // 착지 감지
                    if (player.isOnGround() || player.getVelocity().length() < 0.1 || tick > 80) {
                        this.cancel();

                        Location loc = player.getLocation();
                        spawnDragonImpact(loc, element);

                        for (LivingEntity victim : getTargets(player, loc, 10, 5)) {
                            applyDamageAndEffect(player, victim, element, 4.5);
                            victim.setVelocity(new Vector(0, 1.5, 0));
                        }
                    }
                }
                tick++;
            }
        }.runTaskTimer(TowerPlugin.getInstance(), 0L, 1L);
    }

    // [Visual] 드래곤 날개 (베지에 곡선 + 갈퀴)
    private void drawGiantDragonWings(Player player, Element element) {
        Location back = player.getEyeLocation().add(player.getLocation().getDirection().multiply(-0.5)); // 등 뒤
        Vector right = player.getLocation().getDirection().getCrossProduct(new Vector(0,1,0)).normalize();

        ParticleBuilder bone = CoreProvider.createParticle().setParticle(Particle.DUST)
                .setColor(element.getColor().getRed(), element.getColor().getGreen(), element.getColor().getBlue()).setSize(1.0f);

        ParticleBuilder membrane = CoreProvider.createParticle().setParticle(Particle.DUST)
                .setColor(0, 0, 0).setSize(0.8f);

        // 양쪽 날개 그리기
        drawWingShape(back, right, bone, membrane); // 오른쪽
        drawWingShape(back, right.clone().multiply(-1), bone, membrane); // 왼쪽
    }

    private void drawWingShape(Location root, Vector dir, ParticleBuilder bone, ParticleBuilder membrane) {
        // 날개 뼈대 (Top Curve) - 베지에 곡선 제어점
        // P0(Root) -> P1(위로 솟음) -> P2(바깥으로 뻗음) -> P3(아래로 처짐)
        Vector p0 = new Vector(0, 0, 0);
        Vector p1 = dir.clone().multiply(1.5).add(new Vector(0, 1.5, 0)); // 어깨 관절
        Vector p2 = dir.clone().multiply(3.5).add(new Vector(0, 1.0, 0)); // 날개 중간
        Vector p3 = dir.clone().multiply(5.0).add(new Vector(0, -0.5, 0)); // 날개 끝 (Tip)

        // 3개의 뾰족한 끝점 (Bottom Spikes) - 그림의 뾰족한 부분들
        Vector spike1 = dir.clone().multiply(1.5).add(new Vector(0, -1.0, 0)); // 안쪽
        Vector spike2 = dir.clone().multiply(3.0).add(new Vector(0, -1.5, 0)); // 중간
        Vector spike3 = p3.clone(); // 날개 끝과 동일

        // 곡선 그리기 & 막 채우기
        for (double t = 0; t <= 1.0; t += 0.05) {
            // 3차 베지에 공식 (Cubic Bezier)
            double u = 1 - t;
            double tt = t * t;
            double uu = u * u;
            double uuu = uu * u;
            double ttt = tt * t;

            Vector point = p0.clone().multiply(uuu)
                    .add(p1.clone().multiply(3 * uu * t))
                    .add(p2.clone().multiply(3 * u * tt))
                    .add(p3.clone().multiply(ttt));

            // 뼈대 (윗선)
            CoreProvider.getParticleManager().spawn(root.clone().add(point), bone);

            // 막 (Membrane) - 윗선에서 아래쪽 스파이크 방향으로 선을 그음
            // t값에 따라 어느 스파이크로 연결될지 결정
            Vector targetSpike;
            if (t < 0.4) targetSpike = spike1;
            else if (t < 0.7) targetSpike = spike2;
            else targetSpike = spike3;

            // 윗선에서 스파이크까지 이어지는 선 (막)
            // 너무 빽빽하지 않게 간격 조절
            if ((t * 100) % 10 < 5) {
                CoreProvider.getParticleManager().drawLine(
                        root.clone().add(point),
                        root.clone().add(targetSpike),
                        2.0, membrane
                );
            }
        }
    }

    // [Visual] 낙하 드릴 이펙트 (수렴형 > 모양)
    private void drawPlungeDrill(Player player, Element element) {
        Location start = player.getEyeLocation();
        Vector dir = player.getVelocity().normalize(); // 이동 방향

        // 기준 축
        Vector up = (Math.abs(dir.getY()) > 0.95) ? new Vector(1, 0, 0) : new Vector(0, 1, 0);
        Vector right = dir.getCrossProduct(up).normalize();

        ParticleBuilder drillDust = CoreProvider.createParticle().setParticle(Particle.DUST)
                .setColor(element.getColor().getRed(), element.getColor().getGreen(), element.getColor().getBlue())
                .setSize(1.2f);

        // 플레이어 앞쪽으로 5칸 길이의 드릴 생성
        double length = 5.0;

        // 3줄기 나선
        for (int k = 0; k < 3; k++) {
            double startAngle = Math.toRadians(k * 120 + (System.currentTimeMillis() / 20.0)); // 시간따라 회전

            for (double d = 0; d < length; d += 0.5) {
                Location current = start.clone().add(dir.clone().multiply(d));

                // 반지름: 시작(플레이어 몸통)은 넓고, 끝은 좁게 (2.0 -> 0.0)
                double r = 2.0 * (1.0 - (d / length));
                if (r < 0) r = 0;

                double angle = startAngle + (d * 1.5); // 꼬임

                Vector offset = right.clone().rotateAroundAxis(dir, angle).multiply(r);
                CoreProvider.getParticleManager().spawn(current.add(offset), drillDust);
            }
        }

        // 끝부분(Tip) 강조
        Location tip = start.clone().add(dir.multiply(length));
        player.getWorld().spawnParticle(Particle.CRIT, tip, 2, 0.1, 0.1, 0.1, 0.1);
    }

    // [Visual] 이중 나선 승천 (Double Helix)
    private void playAscensionHelix(Player player, Element element) {
        new BukkitRunnable() {
            double currentY = 0;
            double angle = 0;
            final double radius = 1.5;

            ParticleBuilder mainDust = CoreProvider.createParticle()
                    .setParticle(Particle.DUST)
                    .setColor(element.getColor().getRed(), element.getColor().getGreen(), element.getColor().getBlue())
                    .setSize(2.0f);

            ParticleBuilder whiteDust = CoreProvider.createParticle()
                    .setParticle(Particle.DUST)
                    .setColor(255, 255, 255)
                    .setSize(2.0f);

            @Override
            public void run() {
                if (currentY > 4.0) { this.cancel(); return; }

                Location center = player.getLocation();

                // 한 번에 많이 그려서 선처럼 보이게 함
                for (int i = 0; i < 5; i++) {
                    // 나선 1 (속성색)
                    double x1 = Math.cos(angle) * radius;
                    double z1 = Math.sin(angle) * radius;
                    CoreProvider.getParticleManager().spawn(center.clone().add(x1, currentY, z1), mainDust);

                    // 나선 2 (흰색) - 반대편
                    double x2 = Math.cos(angle + Math.PI) * radius;
                    double z2 = Math.sin(angle + Math.PI) * radius;
                    CoreProvider.getParticleManager().spawn(center.clone().add(x2, currentY, z2), whiteDust);

                    currentY += 0.2;
                    angle += 0.5;
                }
            }
        }.runTaskTimer(TowerPlugin.getInstance(), 0L, 1L);
    }



    private void drawWingSide(Location root, Vector dir, ParticleBuilder bone, ParticleBuilder membrane, int side) {
        // 뼈대 1: 위로 솟음
        Vector v1 = dir.clone().multiply(2.0).add(new Vector(0, 2.5, 0));
        // 뼈대 2: 옆으로 뻗음
        Vector v2 = dir.clone().multiply(3.5).add(new Vector(0, 1.0, 0));
        // 뼈대 3: 아래로 처짐
        Vector v3 = dir.clone().multiply(2.5).add(new Vector(0, -1.0, 0));

        // 뼈대 그리기
        CoreProvider.getParticleManager().drawLine(root, root.clone().add(v1), 5.0, bone);
        CoreProvider.getParticleManager().drawLine(root, root.clone().add(v2), 5.0, bone);
        CoreProvider.getParticleManager().drawLine(root, root.clone().add(v3), 5.0, bone);

        // 날개막 채우기 (뼈대 사이 연결)
        CoreProvider.getParticleManager().drawLine(root.clone().add(v1), root.clone().add(v2), 3.0, membrane);
        CoreProvider.getParticleManager().drawLine(root.clone().add(v2), root.clone().add(v3), 3.0, membrane);
    }

    // 시선이 닿는 바닥 찾기
    private Location getLookAtGround(Player player) {
        var result = player.getWorld().rayTraceBlocks(player.getEyeLocation(), player.getLocation().getDirection(), 30.0);
        if (result != null && result.getHitBlock() != null) {
            return result.getHitBlock().getLocation().add(0, 1, 0); // 바닥 위
        }
        return null; // 허공을 보고 있음
    }

    // [Visual] 용의 머리 형상 (Dust Art)
    private void drawDragonHead(Player player, Element element) {
        Location center = player.getEyeLocation();
        Vector dir = new Vector(0, -1, 0); // 아래를 향함 (낙하 중이므로)
        Vector look = player.getLocation().getDirection().setY(0).normalize(); // 플레이어가 보는 방향
        Vector right = look.getCrossProduct(new Vector(0, 1, 0)).normalize();

        ParticleBuilder outline = CoreProvider.createParticle().setParticle(Particle.DUST)
                .setColor(element.getColor().getRed(), element.getColor().getGreen(), element.getColor().getBlue()).setSize(1.5f);

        ParticleBuilder eye = CoreProvider.createParticle().setParticle(Particle.DUST)
                .setColor(255, 255, 255).setSize(2.0f); // 흰색 눈

        // 1. 눈 (Eyes) - 플레이어 양옆
        CoreProvider.getParticleManager().spawn(center.clone().add(right.clone().multiply(0.8)), eye);
        CoreProvider.getParticleManager().spawn(center.clone().add(right.clone().multiply(-0.8)), eye);

        // 2. 주둥이 (Snout) - V자 형태로 아래로 모임
        // 눈 위치에서 시작해서 발밑 2칸 아래로 모임
        Location noseTip = center.clone().add(0, -3.0, 0).add(look.multiply(1.0));

        CoreProvider.getParticleManager().drawLine(center.clone().add(right.multiply(0.8)), noseTip, 5.0, outline);
        CoreProvider.getParticleManager().drawLine(center.clone().add(right.multiply(-0.8)), noseTip, 5.0, outline);

        // 3. 뿔/갈기 (Horns) - 뒤쪽 위로 뻗어나감
        // 곡선을 그려서 휘날리는 느낌
        for (double i = 0; i < 3.0; i += 0.3) {
            // 뒤로 + 위로
            Vector offset = look.clone().multiply(-1.0 - (i*0.5)).add(new Vector(0, 1.0 + i, 0));

            // 왼쪽 갈기
            Location lHorn = center.clone().add(right.clone().multiply(0.8 + (i*0.3))).add(offset);
            CoreProvider.getParticleManager().spawn(lHorn, outline);

            // 오른쪽 갈기
            Location rHorn = center.clone().add(right.clone().multiply(-0.8 - (i*0.3))).add(offset);
            CoreProvider.getParticleManager().spawn(rHorn, outline);
        }

        // 4. 중앙 코어 (용의 숨결)
        player.getWorld().spawnParticle(Particle.FLAME, player.getLocation(), 5, 0.5, 1.0, 0.5, 0.1);
    }

    // [Visual] 바닥 조준 마법진
    private void drawTargetCircle(Location center, Element element, int tick) {
        ParticleBuilder ring = CoreProvider.createParticle().setParticle(Particle.DUST)
                .setColor(element.getColor().getRed(), element.getColor().getGreen(), element.getColor().getBlue()).setSize(1.2f);

        // 회전하는 마법진
        double radius = 4.0;
        int points = 30;
        double angleOffset = tick * 0.2; // 회전

        for (int i = 0; i < points; i++) {
            double angle = 2 * Math.PI * i / points + angleOffset;
            Location p = center.clone().add(Math.cos(angle) * radius, 0.2, Math.sin(angle) * radius);
            CoreProvider.getParticleManager().spawn(p, ring);
        }

        // 중앙으로 모이는 선
        if (tick % 5 == 0) {
            CoreProvider.getParticleManager().drawCircle(center.add(0, 0.2, 0), radius * 0.5, 15, ring);
        }
    }

    // [Effect] 착지 대폭발
    private void spawnDragonImpact(Location center, Element element) {
        // 사운드
        center.getWorld().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.5f);
        center.getWorld().playSound(center, Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, 1f, 1f);

        // 1. 중앙 폭발
        center.getWorld().spawnParticle(Particle.EXPLOSION, center, 1);
        center.getWorld().spawnParticle(Particle.FLASH, center, 5);

        // 2. 방사형 빛기둥 (8방향으로 퍼져나감)
        ParticleBuilder blast = CoreProvider.createParticle().setParticle(Particle.DUST)
                .setColor(element.getColor().getRed(), element.getColor().getGreen(), element.getColor().getBlue()).setSize(2.0f);

        for (int i = 0; i < 8; i++) {
            double angle = Math.toRadians(i * 45);
            Vector vec = new Vector(Math.cos(angle), 0, Math.sin(angle));

            // 바닥을 타고 퍼지는 충격파
            Location start = center.clone().add(0, 0.5, 0);
            Location end = center.clone().add(vec.multiply(8.0));
            CoreProvider.getParticleManager().drawLine(start, end, 5.0, blast);

            // 끝부분 솟구침
            center.getWorld().spawnParticle(Particle.LAVA, end, 5, 0.5, 1.0, 0.5);
        }
    }

    // --- Helper Methods ---

    private List<LivingEntity> getTargets(Player player, Location center, double radius, double height) {
        List<LivingEntity> targets = new java.util.ArrayList<>();
        for (org.bukkit.entity.Entity e : center.getWorld().getNearbyEntities(center, radius, height, radius)) {
            if (e instanceof LivingEntity victim && e != player) targets.add(victim);
        }
        return targets;
    }

    // 직선 타격 판정 (찌르기용)
    private void checkHitLine(Player player, Element element, double range, double width, double multiplier) {
        Location start = player.getEyeLocation();
        Vector dir = player.getLocation().getDirection().normalize();

        for (LivingEntity victim : getTargets(player, player.getLocation(), range + 1, 3)) {
            Location vLoc = victim.getLocation().add(0, 0.5, 0);
            Vector toVictim = vLoc.toVector().subtract(start.toVector());

            double dot = toVictim.dot(dir);
            if (dot > 0 && dot < range) {
                double distSq = toVictim.lengthSquared() - (dot * dot);
                if (distSq < (width * width)) {
                    applyDamageAndEffect(player, victim, element, multiplier);
                }
            }
        }
    }

    // 부채꼴 타격 판정 (베기용)
    private void checkHitCone(Player player, Element element, double range, double angleDeg, double multiplier) {
        Vector dir = player.getLocation().getDirection().setY(0).normalize();
        for (LivingEntity victim : getTargets(player, player.getLocation(), range, 3)) {
            Vector toTarget = victim.getLocation().subtract(player.getLocation()).toVector().normalize();
            double angleRad = Math.toRadians(angleDeg / 2.0);
            if (dir.dot(toTarget) > Math.cos(angleRad)) {
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
            case STORM: damage += 10; break;
            // ...
        }
        CoreProvider.dealDamage(attacker, victim, damage, result.isCrit());
    }
}