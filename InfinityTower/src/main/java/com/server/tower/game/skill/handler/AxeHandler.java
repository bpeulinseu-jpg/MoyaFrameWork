package com.server.tower.game.skill.handler;

import com.server.core.api.CoreProvider;
import com.server.core.system.particle.ParticleBuilder;
import com.server.tower.TowerPlugin;
import com.server.tower.game.DamageCalculator;
import com.server.tower.game.skill.Element;
import com.server.tower.game.skill.WeaponHandler;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AxeHandler implements WeaponHandler {

    private static class ComboState {
        int step = 0;
        long lastActionTime = 0;
    }

    private final Map<UUID, ComboState> comboMap = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastClickTime = new ConcurrentHashMap<>();

    private static final long COMBO_TIMEOUT = 1200; // 도끼는 검보다 약간 여유 있게

    @Override
    public void onLeftClick(Player player, Element element) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();

        if (now - lastClickTime.getOrDefault(uuid, 0L) < 200) return;
        lastClickTime.put(uuid, now);

        ComboState state = comboMap.computeIfAbsent(uuid, k -> new ComboState());
        if (now - state.lastActionTime > COMBO_TIMEOUT) state.step = 0;
        state.lastActionTime = now;

        // --- 약공격 (Light Attack) ---
        // performSlash에 'comboStep' 인자를 추가하여 이펙트를 구분합니다.
        switch (state.step) {
            case 0: // 1타: 찍기 (\) - 피 튀김
                performSlash(player, element, -45.0, 3.0, 1.1, 0);
                state.step = 1;
                break;
            case 1: // 2타: 올려치기 (/) - 스파크
                performSlash(player, element, 45.0, 3.0, 1.1, 1);
                state.step = 2;
                break;
            case 2: // 3타: 가르기 (ㅡ) - 검은 연기
                performSlash(player, element, 0.0, 4.5, 1.3, 2);
                state.step = 3;
                break;
            case 3: // 3타 후 초기화
                performSlash(player, element, -45.0, 3.0, 1.1, 0);
                state.step = 1;
                break;
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

        switch (state.step) {
            case 0: // 대기 -> 리프 어택
                doLeapAttack(player, element);
                break;
            case 1: // L1 -> 토마호크 (투척)
                doTomahawk(player, element);
                state.step = 0;
                break;
            case 2: // L2 -> 블러드 스톰 (회전)
                doBloodStorm(player, element);
                state.step = 0;
                break;
            case 3: // L3 -> 익스큐션 (처형)
                doExecution(player, element);
                state.step = 0;
                break;
        }
    }

    // =================================================================
    // [Action] 평타 (피의 궤적 + 콤보별 특수 이펙트)
    // =================================================================
    private void performSlash(Player player, Element element, double tilt, double size, double dmgMult, int comboStep) {
        Vector dir = player.getLocation().getDirection();

        // 사운드: 묵직한 타격음
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_STRONG, 1f, 0.7f);
        if (comboStep == 2) { // 3타는 더 강한 소리
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 0.5f, 0.5f);
        }

        Location center = player.getEyeLocation().add(dir.clone().multiply(2.0));

        // 1. 메인 궤적 (검붉은색)
        // 속성 색상에 검은색을 섞어서 어둡게 만듦
        Color baseColor = element.getColor();
        Color bloodColor = Color.fromRGB(
                Math.max(0, baseColor.getRed() - 50),
                Math.max(0, baseColor.getGreen() - 50),
                Math.max(0, baseColor.getBlue() - 50)
        );

        ParticleBuilder mainSlash = CoreProvider.createParticle()
                .setParticle(Particle.DUST)
                .setColor(bloodColor.getRed(), bloodColor.getGreen(), bloodColor.getBlue())
                .setSize(1.2f).setCount(1);

        // 2. 테두리 (완전 검정)
        ParticleBuilder edgeSlash = CoreProvider.createParticle()
                .setParticle(Particle.DUST)
                .setColor(0, 0, 0)
                .setSize(1.5f).setCount(1);

        // 궤적 그리기 (3중 레이어)
        CoreProvider.getParticleManager().drawSlash(center, dir, size, 0.2, tilt, 15, mainSlash);
        CoreProvider.getParticleManager().drawSlash(center.clone().add(0, 0.2, 0), dir, size, 0.2, tilt, 15, edgeSlash);
        CoreProvider.getParticleManager().drawSlash(center.clone().add(0, -0.2, 0), dir, size, 0.2, tilt, 15, edgeSlash);

        // 3. [핵심] 콤보별 추가 이펙트
        switch (comboStep) {
            case 0: // 1타: 피 튀김 (Blood Splatter)
                // 궤적을 따라 레드스톤 블록 파편이 튐
                player.getWorld().spawnParticle(Particle.BLOCK, center, 10, 0.5, 0.5, 0.5, 0.1,
                        Material.REDSTONE_BLOCK.createBlockData());
                break;

            case 1: // 2타: 마찰 스파크 (Sparks)
                // 궤적을 따라 불꽃이 튐
                CoreProvider.getParticleManager().drawSlash(center, dir, size, 0.2, tilt, 5,
                        CoreProvider.createParticle().setParticle(Particle.CRIT).setCount(1));
                break;

            case 2: // 3타: 검은 연기 (Dark Aura)
                // 궤적 전체에 검은 연기가 피어오름
                CoreProvider.getParticleManager().drawSlash(center, dir, size, 0.2, tilt, 10,
                        CoreProvider.createParticle().setParticle(Particle.SMOKE).setCount(1).setSpeed(0.05));
                // 화면 흔들림 효과 (강한 타격감) - 폭발 파티클 하나 터뜨리기
                player.getWorld().spawnParticle(Particle.EXPLOSION, center, 1);
                break;
        }

        // 타격 판정
        checkHit(player, element, center, dir, dmgMult, 3.5, 90.0);
    }

    // =================================================================
    // [Skill] R: 리프 어택 (크림슨 메테오 - 승룡권 도약 + 강화된 착지)
    // =================================================================
    private void doLeapAttack(Player player, Element element) {
        // 1. 도약 (Jump)
        player.setVelocity(player.getLocation().getDirection().multiply(1.2).setY(1.2)); // 높이 좀 더 증가
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_GOAT_LONG_JUMP, 1f, 0.5f);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1f, 1.5f);

        // [추가] 도약 시 승룡권(Blood Helix) 이펙트
        playBloodHelix(player);

        // 2. 공중 궤적 & 착지 감지
        new BukkitRunnable() {
            int tick = 0;
            boolean isPlunging = false;

            @Override
            public void run() {
                // 공중 궤적
                if (!player.isOnGround()) {
                    player.getWorld().spawnParticle(Particle.DUST, player.getLocation().add(0, 1, 0), 3, 0.3, 0.5, 0.3, 0,
                            new Particle.DustOptions(Color.fromRGB(150, 0, 0), 1.0f));
                }

                // 5틱 후 급강하
                if (!isPlunging && tick >= 5) {
                    player.setVelocity(player.getLocation().getDirection().multiply(2.5).setY(-2.0)); // 더 빠르게 내리꽂음
                    isPlunging = true;
                }

                // 착지 처리
                if (isPlunging) {
                    if (player.isOnGround() || tick > 30) {
                        this.cancel();

                        Location loc = player.getLocation();

                        // [핵심] 강화된 착지 임팩트
                        playMeteorImpact(loc);

                        // 광역 타격
                        for (LivingEntity victim : getTargets(player, loc, 7, 3)) {
                            applyDamageAndEffect(player, victim, element, 2.0);
                            Vector push = victim.getLocation().toVector().subtract(loc.toVector()).normalize();
                            victim.setVelocity(push.multiply(1.2).setY(0.4));
                        }
                    }
                }
                tick++;
            }
        }.runTaskTimer(TowerPlugin.getInstance(), 0L, 1L);
    }

    // [New Helper] 도약 시 피의 회오리 (승룡권)
    private void playBloodHelix(Player player) {
        new BukkitRunnable() {
            double currentY = 0;
            double angle = 0;

            // 붉은색 & 검은색 파티클
            final ParticleBuilder redDust = CoreProvider.createParticle().setParticle(Particle.DUST).setColor(200, 0, 0).setSize(2.0f);
            final ParticleBuilder blackDust = CoreProvider.createParticle().setParticle(Particle.DUST).setColor(0, 0, 0).setSize(2.0f);

            @Override
            public void run() {
                // 플레이어 위치 따라가며 생성
                Location center = player.getLocation();

                // 한 번에 많이 그려서 빠르게 올라감
                for (int i = 0; i < 4; i++) {
                    if (currentY > 3.0) { this.cancel(); return; }

                    double x1 = Math.cos(angle) * 1.5;
                    double z1 = Math.sin(angle) * 1.5;
                    CoreProvider.getParticleManager().spawn(center.clone().add(x1, currentY, z1), redDust);

                    double x2 = Math.cos(angle + Math.PI) * 1.5;
                    double z2 = Math.sin(angle + Math.PI) * 1.5;
                    CoreProvider.getParticleManager().spawn(center.clone().add(x2, currentY, z2), blackDust);

                    currentY += 0.2;
                    angle += 0.5;
                }
            }
        }.runTaskTimer(TowerPlugin.getInstance(), 0L, 1L);
    }

    // [New Helper] 강화된 착지 이펙트 (불규칙 균열 + 원형 충격파 + 거대 가시)
    private void playMeteorImpact(Location center) {
        // 1. 사운드
        center.getWorld().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.8f);
        center.getWorld().playSound(center, Sound.BLOCK_ANVIL_LAND, 1f, 0.5f);

        // 2. 크레이터 폭발
        center.getWorld().spawnParticle(Particle.EXPLOSION, center, 1);
        center.getWorld().spawnParticle(Particle.BLOCK, center, 60, 3, 0.5, 3, 0.2, Material.NETHERRACK.createBlockData());

        // 3. [업그레이드] 가시 (잘 보이게)
        // 기존 블록 파편에 더해, 두꺼운 붉은 기둥을 추가
        ParticleBuilder spikeDust = CoreProvider.createParticle()
                .setParticle(Particle.DUST)
                .setColor(255, 0, 0)
                .setSize(3.0f); // 아주 두껍게

        // 중앙에서 솟구치는 거대 가시
        for(double y=0; y<5.0; y+=0.2) {
            CoreProvider.getParticleManager().spawn(center.clone().add(0, y, 0), spikeDust);
        }

        // 4. [업그레이드] 불규칙 균열 (Irregular Cracks)
        // 랜덤한 방향으로 5~6갈래의 균열이 퍼짐
        ParticleBuilder crackDust = CoreProvider.createParticle().setParticle(Particle.DUST).setColor(100, 0, 0).setSize(1.5f);

        for (int i = 0; i < 6; i++) {
            double angle = Math.random() * Math.PI * 2; // 랜덤 각도
            double length = 4.0 + (Math.random() * 3.0); // 랜덤 길이 (4~7칸)

            Location end = center.clone().add(Math.cos(angle) * length, 0.1, Math.sin(angle) * length);
            // 지면을 따라 그려짐
            CoreProvider.getParticleManager().drawLine(center.clone().add(0, 0.1, 0), end, 5.0, crackDust);

            // 균열 끝에서 작은 폭발
            center.getWorld().spawnParticle(Particle.SMOKE, end, 3, 0.2, 0.5, 0.2, 0.05);
        }

        // 5. [신규] 퍼져나가는 원형 충격파 (Shockwave Ring)
        new BukkitRunnable() {
            double radius = 1.0;
            final ParticleBuilder ringDust = CoreProvider.createParticle()
                    .setParticle(Particle.DUST)
                    .setColor(255, 50, 50) // 밝은 빨강
                    .setSize(1.5f);

            @Override
            public void run() {
                if (radius > 8.0) {
                    this.cancel();
                    return;
                }
                // 원 그리기
                CoreProvider.getParticleManager().drawCircle(center.clone().add(0, 0.2, 0), radius, (int)(radius * 10), ringDust);
                radius += 1.5; // 빠르게 확산
            }
        }.runTaskTimer(TowerPlugin.getInstance(), 0L, 1L);
    }

    // =================================================================
    // [Combo] L -> R: 토마호크 (블러드 체이서 - 이중 나선 + 소닉붐 링)
    // =================================================================
    private void doTomahawk(Player player, Element element) {
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_TRIDENT_THROW, 1f, 0.6f);

        Location startLoc = player.getEyeLocation().add(0, -0.5, 0);
        Vector dir = player.getLocation().getDirection().normalize();

        // 도끼 아이템 디스플레이
        ItemStack axeItem = new ItemStack(Material.IRON_AXE);
        ItemDisplay display = (ItemDisplay) player.getWorld().spawnEntity(startLoc, EntityType.ITEM_DISPLAY);
        display.setItemStack(axeItem);
        display.setBillboard(org.bukkit.entity.Display.Billboard.FIXED);

        new BukkitRunnable() {
            Location current = startLoc.clone();
            Vector velocity = dir.clone().multiply(1.8);
            double distance = 0;
            boolean returning = false;
            float rotation = 0;
            float helixAngle = 0;
            int tickCount = 0; // 소닉붐 간격 조절용

            // 소닉붐 파티클 설정 (하얀색 DUST)
            final ParticleBuilder sonicDust = CoreProvider.createParticle()
                    .setParticle(Particle.DUST)
                    .setColor(255, 255, 255)
                    .setSize(1.0f);

            @Override
            public void run() {
                if (distance > 15.0 && !returning) returning = true;

                if (returning) {
                    if (!player.isOnline()) { this.cancel(); display.remove(); return; }
                    Vector toPlayer = player.getEyeLocation().add(0, -0.5, 0).toVector().subtract(current.toVector());

                    if (toPlayer.length() < 1.5) {
                        player.getWorld().spawnParticle(Particle.FLASH, player.getEyeLocation(), 1);
                        player.playSound(player.getLocation(), Sound.ITEM_TRIDENT_RETURN, 1f, 1f);
                        player.playSound(player.getLocation(), Sound.ENTITY_IRON_GOLEM_REPAIR, 1f, 2f);
                        this.cancel();
                        display.remove();
                        return;
                    }
                    velocity = toPlayer.normalize().multiply(1.8);
                } else {
                    distance += 1.8;
                }

                current.add(velocity);

                // 1. 이중 나선 궤적 (기존 유지)
                Vector axis = velocity.clone().normalize();
                Vector up = new Vector(0, 1, 0);
                if (Math.abs(axis.getY()) > 0.95) up = new Vector(1, 0, 0);
                Vector right = axis.getCrossProduct(up).normalize();

                helixAngle += 1.0;
                Vector offset1 = right.clone().rotateAroundAxis(axis, helixAngle).multiply(0.5);
                Vector offset2 = right.clone().rotateAroundAxis(axis, helixAngle + Math.PI).multiply(0.5);

                player.getWorld().spawnParticle(Particle.DUST, current.clone().add(offset1), 1, 0, 0, 0, 0,
                        new Particle.DustOptions(Color.fromRGB(255, 0, 0), 0.8f));
                player.getWorld().spawnParticle(Particle.DUST, current.clone().add(offset2), 1, 0, 0, 0, 0,
                        new Particle.DustOptions(Color.fromRGB(0, 0, 0), 0.8f));

                // 2. [추가] 소닉붐 고리 (Sonic Boom Ring)
                // 날아갈 때만 생성 (돌아올 때는 생성 안 함)
                if (!returning && tickCount % 2 == 0) { // 2틱마다 생성
                    drawSonicRing(current, axis, right);
                }
                tickCount++;

                // 3. 도끼 회전 & 이동
                rotation += 60;
                display.setTransformation(new Transformation(
                        new Vector3f(0,0,0),
                        new AxisAngle4f((float)Math.toRadians(rotation), 1, 0, 0),
                        new Vector3f(1.5f, 1.5f, 1.5f),
                        new AxisAngle4f(0,0,0,1)
                ));
                display.teleport(current);

                // 4. 타격
                for (LivingEntity victim : getTargets(player, current, 1.5, 1.5)) {
                    applyDamageAndEffect(player, victim, element, 1.2);
                    victim.getWorld().spawnParticle(Particle.BLOCK, victim.getEyeLocation(), 10, 0.3, 0.3, 0.3, 0.1,
                            Material.REDSTONE_BLOCK.createBlockData());

                    if (!returning) {
                        returning = true;
                        player.playSound(current, Sound.BLOCK_ANVIL_PLACE, 1f, 2f);
                        // 적중 시 강한 충격파
                        drawSonicRing(current, axis, right);
                    }
                }

                if (current.getBlock().getType().isSolid()) {
                    returning = true;
                    player.getWorld().playSound(current, Sound.ENTITY_ITEM_BREAK, 1f, 1f);
                }
            }

            // [Helper] 진행 방향에 수직인 고리 그리기
            private void drawSonicRing(Location center, Vector axis, Vector right) {
                // axis: 진행 방향 (법선 벡터)
                // right: 기준 축
                // radius: 0.8 (도끼보다 조금 큼)
                double radius = 0.8;
                int points = 16; // 점 개수

                for (int i = 0; i < points; i++) {
                    double angle = 2 * Math.PI * i / points;
                    // 로드리게스 회전으로 원 그리기
                    Vector offset = right.clone().rotateAroundAxis(axis, angle).multiply(radius);
                    CoreProvider.getParticleManager().spawn(center.clone().add(offset), sonicDust);
                }
            }

        }.runTaskTimer(TowerPlugin.getInstance(), 0L, 1L);
    }

    // =================================================================
    // [Combo] L -> L -> R: 블러드 스톰 (선명한 자루 + 거대 도끼날)
    // =================================================================
    private void doBloodStorm(Player player, Element element) {
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_TRIDENT_RIPTIDE_2, 1f, 0.6f);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 0.5f);

        // 1. 칼날 (붉은색, 매우 큼)
        ParticleBuilder bladeDust = CoreProvider.createParticle()
                .setParticle(Particle.DUST)
                .setColor(element.getColor().getRed(), element.getColor().getGreen(), element.getColor().getBlue())
                .setSize(2.5f);

        // 2. 자루 (검은색)
        ParticleBuilder handleDust = CoreProvider.createParticle()
                .setParticle(Particle.DUST)
                .setColor(0, 0, 0)
                .setSize(1.5f);

        // 3. 스파크 (화려함 추가)
        ParticleBuilder spark = CoreProvider.createParticle()
                .setParticle(Particle.CRIT)
                .setCount(1);

        new BukkitRunnable() {
            int currentSpin = 0;
            double currentAngle = 0;
            final int maxSpins = 3;
            final double radius = 6.0;
            final double startYaw = Math.toRadians(player.getLocation().getYaw() + 90);

            @Override
            public void run() {
                if (!player.isOnline() || currentSpin >= maxSpins) {
                    this.cancel();
                    return;
                }

                Location center = player.getLocation().add(0, 1.0, 0);
                double speed = 40.0; // 회전 속도

                // [핵심] 잔상 루프 제거 -> 현재 위치만 그림 (깔끔함)
                double rad = startYaw + Math.toRadians(currentAngle + speed);
                Vector vec = new Vector(Math.cos(rad), 0, Math.sin(rad));

                Location handleEnd = center.clone().add(vec.clone().multiply(radius));

                // 1. 자루 그리기 (직선)
                CoreProvider.getParticleManager().drawLine(center, handleEnd, 2.0, handleDust);

                // 2. 도끼날 그리기 (Blade) - 자루 끝에 달린 거대한 곡선
                // 자루 끝을 중심으로 좌우 30도씩 부채꼴을 그림
                int bladeArc = 60; // 날의 넓이
                for (int i = -bladeArc/2; i <= bladeArc/2; i += 5) {
                    double bladeRad = rad + Math.toRadians(i);
                    Vector bladeVec = new Vector(Math.cos(bladeRad), 0, Math.sin(bladeRad));

                    // 날의 형태: 바깥쪽은 둥글고 안쪽은 뾰족하게
                    // 메인 날 (가장 바깥)
                    CoreProvider.getParticleManager().spawn(center.clone().add(bladeVec.clone().multiply(radius)), bladeDust);

                    // 날 두께 (안쪽으로 한 겹 더)
                    CoreProvider.getParticleManager().spawn(center.clone().add(bladeVec.clone().multiply(radius - 0.4)), bladeDust);

                    // 날카로운 이펙트 (스파크)
                    if (i % 15 == 0) {
                        CoreProvider.getParticleManager().spawn(center.clone().add(bladeVec.clone().multiply(radius + 0.2)), spark);
                    }
                }

                currentAngle += speed;

                // 타격 판정
                checkWhirlwindHit(player, center, radius, startYaw, currentAngle, speed, element);

                if (currentAngle >= 360) {
                    currentAngle = 0;
                    currentSpin++;
                    player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 0.8f + (currentSpin * 0.1f));
                }
            }
        }.runTaskTimer(TowerPlugin.getInstance(), 0L, 1L);
    }

    // [Helper] 휠윈드 타격 판정 (범위 내부 전체 타격)
    private void checkWhirlwindHit(Player player, Location center, double radius, double startYaw, double currentAngle, double arc, Element element) {
        // 현재 검기가 지나가는 각도 범위 (라디안)
        double endRad = startYaw + Math.toRadians(currentAngle + arc);

        // 검기의 현재 방향 벡터
        Vector dir = new Vector(Math.cos(endRad), 0, Math.sin(endRad)).normalize();

        // [수정] 범위 내 모든 적 검색 (radius 전체)
        for (LivingEntity victim : getTargets(player, center, radius, 3.0)) {
            Vector toTarget = victim.getLocation().subtract(center).toVector();

            // 거리 체크: 이미 getTargets에서 했지만 확실하게 한 번 더
            if (toTarget.lengthSquared() > radius * radius) continue;

            // 방향 체크: 현재 검기가 지나가는 각도와 비슷한지 (내적)
            // 가까이 있는 적은 각도 계산이 민감하므로, 아주 가까우면(1.5m 이내) 무조건 타격
            if (toTarget.length() < 1.5 || dir.dot(toTarget.normalize()) > 0.7) {
                applyDamageAndEffect(player, victim, element, 0.4);

                // 타격 이펙트 (피 튐)
                victim.getWorld().spawnParticle(Particle.BLOCK, victim.getEyeLocation(), 5, 0.2, 0.2, 0.2,
                        Material.REDSTONE_BLOCK.createBlockData());
            }
        }
    }

    // =================================================================
    // [Combo] L -> L -> L -> R: 익스큐션 (유도 난무 -> 다중 불기둥 폭발)
    // =================================================================
    private void doExecution(Player player, Element element) {
        // 1. 도약
        player.setVelocity(new Vector(0, 1.8, 0));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WOLF_GROWL, 1f, 0.5f);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1f);

        new BukkitRunnable() {
            int tick = 0;
            boolean isPlunging = false;
            // 검기가 꽂힌 위치들 저장 (불기둥 생성용)
            final List<Location> slashImpactLocations = new java.util.ArrayList<>();

            @Override
            public void run() {
                if (!player.isOnline() || player.isDead()) { this.cancel(); return; }

                if (tick < 100) drawWolfAvatar(player);

                // [1단계] 체류
                if (tick >= 10 && tick < 55) {
                    player.setVelocity(new Vector(0, 0.04, 0));
                    player.setFallDistance(0);
                }

                // [2단계] 공중 난무 (오토 타겟팅)
                if (tick >= 15 && tick <= 45 && tick % 8 == 0) { // 발사 간격 살짝 줄임 (더 많이 쏨)
                    // 반동
                    player.setVelocity(player.getVelocity().add(new Vector(0, 0.1, 0)));

                    // [핵심] 타겟팅 로직
                    Vector aimDir;
                    // 플레이어 아래쪽/전방의 적 탐색 (반경 15, 높이 20)
                    List<LivingEntity> targets = getTargets(player, player.getLocation().add(0, -5, 0), 15, 20);

                    if (!targets.isEmpty()) {
                        // 랜덤한 적 하나 골라서 조준
                        LivingEntity target = targets.get((int)(Math.random() * targets.size()));
                        Location targetCenter = target.getLocation().add(0, 0.5, 0);
                        aimDir = targetCenter.toVector().subtract(player.getEyeLocation().toVector()).normalize();
                    } else {
                        // 적이 없으면 전방 아래로 랜덤 발사
                        aimDir = player.getLocation().getDirection().add(new Vector(
                                (Math.random()-0.5)*0.3, -0.8, (Math.random()-0.5)*0.3
                        )).normalize();
                    }

                    Location impactLoc = shootAirSlash(player, element, aimDir);
                    if (impactLoc != null) {
                        slashImpactLocations.add(impactLoc);
                    }
                }

                // [3단계] 급강하
                if (tick == 55) {
                    player.setVelocity(player.getLocation().getDirection().multiply(1.5).setY(-3.5));
                    player.getWorld().playSound(player.getLocation(), Sound.ENTITY_GHAST_SCREAM, 1f, 0.5f);
                    isPlunging = true;
                }

                // [4단계] 착지 및 다중 폭발
                if (isPlunging) {
                    if (player.isOnGround() || tick > 90) {
                        this.cancel();

                        Location landLoc = player.getLocation();

                        // 착지 즉시 폭발
                        player.getWorld().playSound(landLoc, Sound.BLOCK_ANVIL_LAND, 1f, 0.5f);
                        player.getWorld().playSound(landLoc, Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.6f);
                        player.getWorld().playSound(landLoc, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 1f, 1f);

                        // [핵심] 다중 불기둥 생성
                        // 착지 지점 + 검기 맞은 지점들 모두 폭발
                        slashImpactLocations.add(landLoc); // 착지 지점도 포함
                        spawnHellPillarsAndAftermath(player, element, slashImpactLocations);

                        // 착지 지점 타격
                        for (LivingEntity victim : getTargets(player, landLoc, 8, 5)) {
                            applyDamageAndEffect(player, victim, element, 3.5);
                            victim.setVelocity(new Vector(0, 1.2, 0));
                            victim.setFireTicks(100);
                        }
                    }
                }
                tick++;
            }
        }.runTaskTimer(TowerPlugin.getInstance(), 0L, 1L);
    }

    // [Effect] 다중 고밀도 불기둥 + 후속 균열
    private void spawnHellPillarsAndAftermath(Player player, Element element, List<Location> locations) {

        // 1. 모든 타격 지점에 불기둥 생성
        for (Location loc : locations) {
            // 바닥 높이 보정 (공중에 뜨는 것 방지)
            loc.setY(loc.getWorld().getHighestBlockYAt(loc) + 1);

            // [수정] 고밀도 불기둥 (0.2 간격)
            for (double y = 0; y < 12; y += 0.2) {
                Location pLoc = loc.clone().add(0, y, 0);

                // 코어 (용암)
                if (y % 1.0 == 0) player.getWorld().spawnParticle(Particle.LAVA, pLoc, 1);

                // 외곽 불꽃 (두껍게)
                player.getWorld().spawnParticle(Particle.FLAME, pLoc, 2, 0.8, 0.1, 0.8, 0.02);

                // 검은 연기
                if (y % 2.0 == 0) player.getWorld().spawnParticle(Particle.SMOKE, pLoc, 1, 0.5, 0.5, 0.5, 0.05);
            }

            // 바닥 폭발
            player.getWorld().spawnParticle(Particle.EXPLOSION, loc, 1);
            player.getWorld().spawnParticle(Particle.FLASH, loc, 1);
        }

        // 2. 후속 균열 이펙트 (3초간 유지)
        new BukkitRunnable() {
            int duration = 0;
            ParticleBuilder crackDust = CoreProvider.createParticle().setParticle(Particle.DUST).setColor(0, 0, 0).setSize(1.5f);

            @Override
            public void run() {
                if (duration >= 60) {
                    this.cancel();
                    return;
                }

                for (Location crackLoc : locations) {
                    // 균열 (검은 원)
                    CoreProvider.getParticleManager().drawCircle(crackLoc.clone().add(0, 0.2, 0), 1.0, 10, crackDust);

                    // 잔불
                    player.getWorld().spawnParticle(Particle.SMOKE, crackLoc, 2, 0.5, 1.0, 0.5, 0.05);
                    player.getWorld().spawnParticle(Particle.FLAME, crackLoc, 1, 0.5, 0.5, 0.5, 0.02);
                }
                duration += 5;
            }
        }.runTaskTimer(TowerPlugin.getInstance(), 0L, 5L);
    }


    // [Visual] 거대 늑대 형상 (위치 수정: 더 뒤로, 더 위로)
    private void drawWolfAvatar(Player player) {
        // [수정] 등 뒤 4.5미터, 높이 5.5미터 (대각선 위)
        Location back = player.getLocation().add(player.getLocation().getDirection().multiply(-4.5));
        back.add(0, 5.5, 0);

        Vector right = player.getLocation().getDirection().getCrossProduct(new Vector(0,1,0)).normalize();

        // 눈 위치
        Location leftEye = back.clone().add(right.clone().multiply(-0.8));
        Location rightEye = back.clone().add(right.clone().multiply(0.8));

        ParticleBuilder eye = CoreProvider.createParticle()
                .setParticle(Particle.DUST)
                .setColor(255, 0, 0)
                .setSize(4.0f);

        CoreProvider.getParticleManager().spawn(leftEye, eye);
        CoreProvider.getParticleManager().spawn(rightEye, eye);

        // 오라
        player.getWorld().spawnParticle(
                Particle.DUST,
                back,
                20, 1.5, 1.5, 1.5, 0.0,
                new Particle.DustOptions(Color.fromRGB(100, 0, 0), 5.0f)
        );
    }

    // [Action] 공중 검기 (거대 에너지 포격 + 타격 판정 추가)
    private Location shootAirSlash(Player player, Element element, Vector dir) {
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 0.5f);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.5f);

        Location start = player.getEyeLocation();
        double maxDist = 12.0;

        // --- 파티클 설정 (기존 유지) ---
        ParticleBuilder coreDust = CoreProvider.createParticle()
                .setParticle(Particle.DUST)
                .setColor(element.getColor().getRed(), element.getColor().getGreen(), element.getColor().getBlue())
                .setSize(4.0f);

        ParticleBuilder darkDust = CoreProvider.createParticle()
                .setParticle(Particle.DUST)
                .setColor(0, 0, 0)
                .setSize(2.5f);

        // --- [핵심 수정] 충돌 감지 (블록 + 엔티티) ---
        // rayTrace(start, direction, maxDistance, fluidMode, ignorePassable, raySize, filter)
        // raySize 1.0: 검기 두께만큼 판정 범위 확대
        var result = start.getWorld().rayTrace(
                start,
                dir,
                maxDist,
                org.bukkit.FluidCollisionMode.NEVER,
                true,
                1.0,
                (entity) -> entity != player && entity instanceof LivingEntity
        );

        double dist = (result != null) ? start.distance(result.getHitPosition().toLocation(start.getWorld())) : maxDist;

        // --- 궤적 그리기 (기존 유지) ---
        Vector step = dir.clone().multiply(0.5);
        Location current = start.clone();
        Vector up = (Math.abs(dir.getY()) > 0.95) ? new Vector(1, 0, 0) : new Vector(0, 1, 0);
        Vector right = dir.getCrossProduct(up).normalize();

        for (double d = 0; d < dist; d += 0.5) {
            CoreProvider.getParticleManager().spawn(current, coreDust);

            double angle = d * 1.5;
            double radius = 0.8;

            Vector offset1 = right.clone().rotateAroundAxis(dir, angle).multiply(radius);
            Vector offset2 = right.clone().rotateAroundAxis(dir, angle + Math.PI).multiply(radius);

            CoreProvider.getParticleManager().spawn(current.clone().add(offset1), darkDust);
            CoreProvider.getParticleManager().spawn(current.clone().add(offset2), darkDust);

            if (Math.random() < 0.3) {
                player.getWorld().spawnParticle(Particle.SWEEP_ATTACK, current, 1, 0.2, 0.2, 0.2, 0);
            }
            current.add(step);
        }

        // --- [핵심 수정] 결과 처리 (대미지 or 균열 위치 반환) ---
        if (result != null) {
            // 1. 엔티티 명중 시
            if (result.getHitEntity() instanceof LivingEntity victim) {
                // 대미지 적용 (0.8배)
                applyDamageAndEffect(player, victim, element, 0.8);

                // 타격 이펙트
                victim.getWorld().spawnParticle(Particle.CRIT, victim.getEyeLocation(), 10);
                victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 1f);

                // 엔티티 발밑 좌표를 반환하여 거기에 균열 생성
                return victim.getLocation();
            }
            // 2. 블록 명중 시
            else if (result.getHitBlock() != null) {
                Location hitLoc = result.getHitBlock().getLocation().add(0, 1, 0);
                player.getWorld().spawnParticle(Particle.EXPLOSION, hitLoc, 1);
                return hitLoc;
            }
        }

        // 허공이면 최대 사거리 바닥 반환
        return start.getWorld().getHighestBlockAt(start.clone().add(dir.multiply(dist))).getLocation().add(0, 1, 0);
    }



    // --- Helper Methods ---

    private void drawAxeSlash(Location center, Vector dir, double size, double curve, double tilt, Element element) {
        // 도끼는 검보다 투박하게 (검정 + 빨강 위주)
        ParticleBuilder main = CoreProvider.createParticle().setParticle(Particle.DUST).setColor(element.getColor().getRed(), element.getColor().getGreen(), element.getColor().getBlue()).setSize(1.2f).setCount(1);
        ParticleBuilder blood = CoreProvider.createParticle().setParticle(Particle.DUST).setColor(150, 0, 0).setSize(1.5f).setCount(1);

        CoreProvider.getParticleManager().drawSlash(center, dir, size, curve, tilt, 15, main);
        CoreProvider.getParticleManager().drawSlash(center.clone().add(0, -0.2, 0), dir, size, curve, tilt, 15, blood);
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

    private void applyDamageAndEffect(Player attacker, LivingEntity victim, Element element, double multiplier) {
        DamageCalculator.DamageResult result = DamageCalculator.calculate(attacker, victim, multiplier, true);
        if (result.isCancelled()) return;
        double damage = result.damage();

        // 도끼 특성: 기본적으로 약간의 흡혈 (5%)
        double lifesteal = damage * 0.05;
        double maxHp = attacker.getAttribute(Attribute.MAX_HEALTH).getValue();
        attacker.setHealth(Math.min(maxHp, attacker.getHealth() + lifesteal));

        // 속성 효과
        switch (element) {
            case FIRE: victim.setFireTicks(60); damage *= 1.1; break;
            case ICE: victim.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS, 40, 1)); break;
            case STORM: damage += 10; break;
            // ...
        }
        CoreProvider.dealDamage(attacker, victim, damage, result.isCrit());
    }
}