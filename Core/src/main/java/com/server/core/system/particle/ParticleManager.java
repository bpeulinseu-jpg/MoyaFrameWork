package com.server.core.system.particle;

import com.server.core.CorePlugin;
import com.server.core.api.builder.ItemBuilder;
import com.server.core.system.projectile.CustomProjectile;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Vector3f;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ParticleManager {

    private final CorePlugin plugin;

    private final Map<String, List<Vector>> imageCache = new HashMap<>();
    private final Map<String, List<ColoredPoint>> coloredImageCache = new HashMap<>();

    public record ColoredPoint(Vector offset, Color color) {}

    public ParticleManager(CorePlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 단일 지점에 파티클 소환 (가장 기초)
     */
    public void spawn(Location loc, ParticleBuilder builder) {
        // 1. 커스텀 텍스처 처리 (Particle.ITEM)
        if (builder.getCustomModelData() != -1) {
            // Paper에 CMD를 입혀서 파티클 데이터로 사용
            ItemStack icon = new ItemBuilder(builder.getItemMaterial())
                    .model(builder.getCustomModelData())
                    .build();

            loc.getWorld().spawnParticle(Particle.ITEM, loc, builder.getCount(),
                    builder.getOffsetX(), builder.getOffsetY(), builder.getOffsetZ(),
                    builder.getSpeed(), icon);
            return;
        }

        // 2. 색상 파티클 처리 (Particle.DUST)
        if (builder.getParticle() == Particle.DUST) {
            Particle.DustOptions options = new Particle.DustOptions(builder.getColor(), builder.getSize());
            loc.getWorld().spawnParticle(Particle.DUST, loc, builder.getCount(),
                    builder.getOffsetX(), builder.getOffsetY(), builder.getOffsetZ(),
                    builder.getSpeed(), options);
            return;
        }

        // 3. 일반 파티클 처리
        loc.getWorld().spawnParticle(builder.getParticle(), loc, builder.getCount(),
                builder.getOffsetX(), builder.getOffsetY(), builder.getOffsetZ(),
                builder.getSpeed());
    }

    // --- 도형 그리기 (Math) ---

    /**
     * 선 그리기 (Line)
     * start에서 end까지 파티클을 촘촘하게 배치
     */
    public void drawLine(Location start, Location end, double density, ParticleBuilder builder) {
        double distance = start.distance(end);
        Vector dir = end.clone().subtract(start).toVector().normalize();

        // density: 1블록당 파티클 개수 (예: 5.0이면 0.2칸마다 찍음)
        double step = 1.0 / density;

        for (double i = 0; i < distance; i += step) {
            Location point = start.clone().add(dir.clone().multiply(i));
            spawn(point, builder);
        }
    }

    /**
     * 원 그리기 (Circle)
     */
    public void drawCircle(Location center, double radius, int points, ParticleBuilder builder) {
        for (int i = 0; i < points; i++) {
            double angle = 2 * Math.PI * i / points;
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;

            // center에 더해서 위치 잡기
            spawn(center.clone().add(x, 0, z), builder);
        }
    }

    /**
     * 나선 그리기 (Helix) - 회오리 모양
     */
    public void drawHelix(Location center, double radius, double height, int points, ParticleBuilder builder) {
        for (int i = 0; i < points; i++) {
            double angle = 2 * Math.PI * i / (points / height); // 높이에 따라 회전수 조절
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;
            double y = (double) i / points * height; // 위로 올라감

            spawn(center.clone().add(x, y, z), builder);
        }
    }

    // 초승달 그리기
    /**
     * 초승달/검기 그리기 (Crescent Slash)
     * 플레이어가 바라보는 방향을 기준으로 호(Arc)를 그립니다.
     *
     * @param center 중심 위치 (보통 플레이어 눈높이)
     * @param direction 날아가는 방향 (loc.getDirection())
     * @param radius 검기의 반지름 (크기)
     * @param arcAngle 호의 넓이 (도 단위, 예: 120.0 = 120도 만큼 베기)
     * @param tiltAngle 검기의 기울기 (도 단위, 0 = 수평, 90 = 수직)
     * @param points 파티클 개수
     * @param builder 파티클 설정
     */

    public void drawCrescent(Location center, Vector direction, double radius, double arcAngle, double tiltAngle, int points, ParticleBuilder builder) {
        Vector dir = direction.clone().normalize();

        // 1. 기준 축 잡기 (바라보는 방향에 수직인 벡터 구하기)
        // 기본적으로 '위(Up)'를 기준으로 잡고, 만약 위를 보고 있다면 '오른쪽(Right)'을 기준으로 함
        Vector up = new Vector(0, 1, 0);
        if (Math.abs(dir.getY()) > 0.95) {
            up = new Vector(1, 0, 0);
        }

        // dir과 up의 외적 = 플레이어 기준 '오른쪽' 방향 (검기의 평면)
        Vector right = dir.getCrossProduct(up).normalize();

        // 2. 기울기(Tilt) 적용
        // 오른쪽 벡터를 바라보는 방향(dir) 축으로 회전시켜서 베는 각도를 설정
        // (Paper API 1.21.4는 Vector.rotateAroundAxis 지원)
        right.rotateAroundAxis(dir, Math.toRadians(tiltAngle));

        // 3. 호 그리기
        double startAngle = -arcAngle / 2.0; // 중앙을 기준으로 대칭되게
        double angleStep = arcAngle / points;

        for (int i = 0; i <= points; i++) {
            double currentAngle = Math.toRadians(startAngle + (i * angleStep));

            // 로드리게스 회전 공식 응용 (벡터를 축 기준으로 회전)
            // right 벡터를 dir 축으로 currentAngle 만큼 회전시킴
            Vector pointOffset = right.clone().rotateAroundAxis(dir, currentAngle).multiply(radius);

            // 최종 소환
            spawn(center.clone().add(pointOffset), builder);
        }
    }

    /**
     * 구체 그리기 (Fibonacci Sphere)
     * 파티클이 뭉치지 않고 표면에 균일하게 분포됩니다.
     *
     * @param center 중심 위치
     * @param radius 반지름
     * @param points 파티클 개수 (밀도)
     * @param builder 파티클 설정
     */
    public void drawSphere(Location center, double radius, int points, ParticleBuilder builder) {
        // 황금각 (Golden Angle) = (3 - sqrt(5)) * PI
        double phi = Math.PI * (3.0 - Math.sqrt(5.0));

        for (int i = 0; i < points; i++) {
            // Y좌표: 1에서 -1까지 균일하게 내려감
            double y = 1.0 - (i / (double) (points - 1)) * 2.0;

            // 해당 Y 높이에서의 원의 반지름
            double radiusAtY = Math.sqrt(1.0 - y * y);

            // 황금각만큼 회전
            double theta = phi * i;

            double x = Math.cos(theta) * radiusAtY;
            double z = Math.sin(theta) * radiusAtY;

            // 반지름 적용
            Vector offset = new Vector(x, y, z).multiply(radius);

            spawn(center.clone().add(offset), builder);
        }
    }

    /**
     * 검기/참격 그리기 (Improved Slash)
     * 플레이어 전방에 곡선 형태의 검기를 그립니다.
     *
     * @param center 중심 위치 (플레이어 눈높이 + 전방 오프셋)
     * @param direction 바라보는 방향
     * @param size 검기의 길이 (좌우 폭)
     * @param curvature 곡률 (휘어짐 정도, 0이면 직선, 높을수록 많이 휨)
     * @param tiltAngle 기울기 (0=수평, 90=수직)
     * @param points 파티클 개수 (밀도)
     * @param builder 파티클 설정
     */
    public void drawSlash(Location center, Vector direction, double size, double curvature, double tiltAngle, int points, ParticleBuilder builder) {
        Vector dir = direction.clone().normalize();

        // 기준 축 잡기 (Right, Up)
        Vector up = new Vector(0, 1, 0);
        if (Math.abs(dir.getY()) > 0.95) up = new Vector(1, 0, 0);
        Vector right = dir.getCrossProduct(up).normalize();

        // Up 벡터 재계산 (정확한 수직 평면을 위해)
        Vector trueUp = right.getCrossProduct(dir).normalize();

        // 기울기(Tilt) 적용 (회전)
        double angleRad = Math.toRadians(tiltAngle);
        // 로드리게스 회전 공식 등으로 축 회전 (간소화: right, trueUp을 회전)
        Vector rotatedRight = right.clone().multiply(Math.cos(angleRad)).add(trueUp.clone().multiply(Math.sin(angleRad))).normalize();
        Vector rotatedUp = trueUp.clone().multiply(Math.cos(angleRad)).subtract(right.clone().multiply(Math.sin(angleRad))).normalize();

        // 곡선 그리기 (-0.5 ~ 0.5 범위)
        for (int i = 0; i <= points; i++) {
            double t = (double) i / points; // 0.0 ~ 1.0
            double x = (t - 0.5) * size;    // 좌우 길이

            // 곡선 공식 (Parabola): y = -a * x^2
            // 중앙이 튀어나오고 양 끝이 뒤로(또는 위로) 처지는 모양
            // 여기서는 '진행 방향의 반대' 또는 '위아래'로 휘게 할 수 있음.
            // 검기 모양( ) )을 위해선, 중앙이 앞으로 튀어나오게(dir 방향) 하거나, 위로 솟게 해야 함.
            // 여기서는 단순하게 평면상에서 위/아래로 휘게 만듦.

            // 곡률 적용: 중앙(x=0)일 때 0, 끝일 때 최대
            double curveOffset = curvature * (1.0 - (4 * (t - 0.5) * (t - 0.5)));

            // 위치 계산
            // center + (우측 * x) + (진행방향 * 곡률) -> 활 모양
            Vector offset = rotatedRight.clone().multiply(x)
                    .add(dir.clone().multiply(curveOffset)); // 진행 방향으로 휘게 함 ( ) 모양 )

            spawn(center.clone().add(offset), builder);
        }
    }
    /**
     * 이미지 파일을 읽어서 파티클로 그리기
     * @param center 중심 위치 (플레이어 눈앞)
     * @param direction 바라보는 방향
     *  이미지 파일 이름 (예: "spear_shape.png")
     * @param scale 크기 배율 (0.1 ~ 0.5 추천)
     * @param rotationAngle 회전 각도 (0 = 정방향)
     * @param builder 파티클 설정
     */
    public void drawImage(Location center, Vector direction, File imageFile, double scale, double rotationAngle, ParticleBuilder builder) {
        // 캐시 키를 파일 절대 경로로 사용
        String key = imageFile.getAbsolutePath();

        if (!imageCache.containsKey(key)) {
            loadImage(imageFile);
        }

        List<Vector> points = imageCache.get(key);
        if (points == null || points.isEmpty()) return;

        // 2. 기준 축 계산 (플레이어 시점 기준)
        Vector dir = direction.clone().normalize();
        Vector up = new Vector(0, 1, 0);
        if (Math.abs(dir.getY()) > 0.95) up = new Vector(1, 0, 0);
        Vector right = dir.getCrossProduct(up).normalize();
        Vector trueUp = right.getCrossProduct(dir).normalize();

        // 회전 적용 (Z축 회전)
        if (rotationAngle != 0) {
            double rad = Math.toRadians(rotationAngle);
            Vector newRight = right.clone().multiply(Math.cos(rad)).add(trueUp.clone().multiply(Math.sin(rad)));
            Vector newUp = trueUp.clone().multiply(Math.cos(rad)).subtract(right.clone().multiply(Math.sin(rad)));
            right = newRight.normalize();
            trueUp = newUp.normalize();
        }

        // 3. 점 찍기
        for (Vector point : points) {
            // 이미지의 X -> Right(좌우), Y -> Forward(앞뒤) 또는 Up(위아래)
            // 여기서는 그림을 "바닥에 놓고 보는" 관점이 아니라 "세워서 보는" 관점으로 매핑
            // 이미지 X: 좌우 (Right)
            // 이미지 Y: 위아래 (TrueUp)

            Vector offset = right.clone().multiply(point.getX() * scale)
                    .add(trueUp.clone().multiply(point.getY() * scale));

            // center에서 offset만큼 떨어진 곳에 소환
            spawn(center.clone().add(offset), builder);
        }
    }

    private void loadImage(File file) {
        if (!file.exists()) return;

        try {
            BufferedImage image = ImageIO.read(file);
            List<Vector> points = new ArrayList<>();

            int width = image.getWidth();
            int height = image.getHeight();

            // 중심점 (이미지의 정중앙을 (0,0)으로 맞춤)
            double centerX = width / 2.0;
            double centerY = height / 2.0;

            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    int pixel = image.getRGB(x, y);
                    // 투명하지 않은 픽셀만 추출 (Alpha값 체크)
                    if ((pixel >> 24) != 0x00) {
                        // Y축 뒤집기 (이미지는 아래로 갈수록 Y가 증가하지만, 게임은 위가 Y+)
                        // X, Y 좌표 저장
                        points.add(new Vector(x - centerX, -(y - centerY), 0));
                    }
                }
            }
            imageCache.put(file.getAbsolutePath(), points);
            plugin.getLogger().info("이미지 로드: " + file.getName());

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * [신규] 이미지의 색상까지 그대로 파티클로 그리기
     * (주의: Particle.DUST만 지원합니다)
     */
    public void drawColoredImage(Location center, Vector direction, File imageFile, double scale, double rotationAngle, float particleSize) {
        String key = imageFile.getAbsolutePath();

        if (!coloredImageCache.containsKey(key)) {
            loadColoredImage(imageFile);
        }

        List<ColoredPoint> points = coloredImageCache.get(key);
        if (points == null || points.isEmpty()) return;

        // 축 계산 (기존과 동일)
        Vector dir = direction.clone().normalize();
        Vector up = (Math.abs(dir.getY()) > 0.95) ? new Vector(1, 0, 0) : new Vector(0, 1, 0);
        Vector right = dir.getCrossProduct(up).normalize();
        Vector trueUp = right.getCrossProduct(dir).normalize();

        // 회전 적용
        if (rotationAngle != 0) {
            double rad = Math.toRadians(rotationAngle);
            Vector newRight = right.clone().multiply(Math.cos(rad)).add(trueUp.clone().multiply(Math.sin(rad)));
            Vector newUp = trueUp.clone().multiply(Math.cos(rad)).subtract(right.clone().multiply(Math.sin(rad)));
            right = newRight.normalize();
            trueUp = newUp.normalize();
        }

        // 그리기
        for (ColoredPoint point : points) {
            // 위치 계산
            Vector offset = right.clone().multiply(point.offset.getX() * scale)
                    .add(trueUp.clone().multiply(point.offset.getY() * scale));

            Location spawnLoc = center.clone().add(offset);

            // [핵심] 픽셀 고유의 색상 적용
            Particle.DustOptions options = new Particle.DustOptions(point.color, particleSize);

            // DUST 파티클 소환
            spawnLoc.getWorld().spawnParticle(Particle.DUST, spawnLoc, 1, 0, 0, 0, 0, options);
        }
    }

    // 컬러 이미지 로딩 로직
    private void loadColoredImage(File file) {
        if (!file.exists()) return;

        try {
            BufferedImage image = ImageIO.read(file);
            List<ColoredPoint> points = new ArrayList<>();

            int width = image.getWidth();
            int height = image.getHeight();
            double centerX = width / 2.0;
            double centerY = height / 2.0;

            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    int pixel = image.getRGB(x, y);

                    // 투명도 체크 (Alpha)
                    if ((pixel >> 24) != 0x00) {
                        // AWT Color -> Bukkit Color 변환
                        java.awt.Color awtColor = new java.awt.Color(pixel, true);
                        Color bukkitColor = Color.fromRGB(awtColor.getRed(), awtColor.getGreen(), awtColor.getBlue());

                        Vector offset = new Vector(x - centerX, -(y - centerY), 0);
                        points.add(new ColoredPoint(offset, bukkitColor));
                    }
                }
            }
            coloredImageCache.put(file.getAbsolutePath(), points);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}