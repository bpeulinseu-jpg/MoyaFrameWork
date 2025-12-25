package com.server.core.system.particle;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;

public class ParticleBuilder {
    // 기본 설정
    private Particle particle = Particle.DUST; // 기본값: 레드스톤
    private int count = 1;
    private double speed = 0.0;
    private double offsetX = 0.0;
    private double offsetY = 0.0;
    private double offsetZ = 0.0;

    // 옵션: 색상 (REDSTONE, SPELL_MOB 등)
    private Color color = Color.RED;
    private float size = 1.0f;

    // 옵션: 커스텀 텍스처 (ITEM 파티클용)
    private int customModelData = -1; // -1이면 사용 안 함
    private Material itemMaterial = Material.SNOWBALL;   // 텍스처 입힐 아이템

    // --- 생성자 및 설정 메서드 (Chaining) ---
    public static ParticleBuilder create() { return new ParticleBuilder(); }

    public ParticleBuilder setParticle(Particle particle) {
        this.particle = particle;
        return this;
    }

    public ParticleBuilder setCount(int count) {
        this.count = count;
        return this;
    }

    public ParticleBuilder setSpeed(double speed) {
        this.speed = speed;
        return this;
    }

    public ParticleBuilder setOffset(double x, double y, double z) {
        this.offsetX = x;
        this.offsetY = y;
        this.offsetZ = z;
        return this;
    }

    // 색상 설정 (RGB)
    public ParticleBuilder setColor(int r, int g, int b) {
        this.color = Color.fromRGB(r, g, b);
        return this;
    }

    public ParticleBuilder setSize(float size) {
        this.size = size;
        return this;
    }

    public ParticleBuilder setCustomTexture(String textureId) {
        int cmd = com.server.core.api.CoreProvider.getParticleModelData(textureId);
        if (cmd != -1) {
            this.customModelData = cmd;
            this.particle = Particle.ITEM;
            // [수정] 재질 변경
            this.itemMaterial = Material.SNOWBALL;
        }
        return this;
    }

    // --- Getters ---
    public Particle getParticle() { return particle; }
    public int getCount() { return count; }
    public double getSpeed() { return speed; }
    public double getOffsetX() { return offsetX; }
    public double getOffsetY() { return offsetY; }
    public double getOffsetZ() { return offsetZ; }
    public Color getColor() { return color; }
    public float getSize() { return size; }
    public int getCustomModelData() { return customModelData; }
    public Material getItemMaterial() { return itemMaterial; }
}