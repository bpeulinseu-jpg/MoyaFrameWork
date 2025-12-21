package com.server.tower.item.enums;

public enum ItemPrefix {
    // 너프 개열 접두사
    BROKEN("부서진", 0.5, 0.8),
    CRUDE("조잡한", 0.7, 0.9),
    WEAK("약한", 0.8, 1.0),
    DAMAGED("손상된", 0.9, 0.95),
    DULL("무딘", 0.9, 1.0),
    MESSY("엉망인", 0.6, 0.6),
    RUSTY("녹슨", 0.7, 1.0),

    //버프 개열
    SUPERIOR("우월한", 1.2, 1.1),
    DEADLY("치명적인", 1.1, 1.1),
    SHARP("뾰족한", 1.1, 1.0),
    MASTERWORK("명품", 1.15, 1.1),

    // 밸런스 개열
    NORMAL("평범한", 1.0, 1.0),
    COMMON("흔한", 1.0, 1.0),
    SLOW("느린", 1.1, 0.7),
    UNWIELDY("다루기 힘든", 1.3, 0.6),
    FAST("빠른", 0.9, 1.2),
    HEAVY("무거운", 1.4, 0.8),
    LIGHT("가벼운", 0.85, 1.25);

    private final String name;
    private final double damageMult;
    private final double speedMult;

    ItemPrefix(String name, double damageMult, double speedMult) {
        this.name = name;
        this.damageMult = damageMult;
        this.speedMult = speedMult;
    }

    public String getName() { return name; }
    public double getDamageMult() { return damageMult; }
    public double getSpeedMult() { return speedMult; }
}