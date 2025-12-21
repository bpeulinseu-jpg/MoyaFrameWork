package com.server.tower.system.transcendence;

import java.util.Random;

public enum UniqueAbility {
    THUNDER_STRIKE("뇌제", "§e타격 시 30% 확률로 적에게 낙뢰를 떨어뜨립니다."),
    INFERNO("염화", "§c타격 시 적을 5초간 불태웁니다."),
    WIND_WALKER("신속", "§b적 처치 시 3초간 이동속도가 대폭 증가합니다.");

    private final String name;
    private final String description;
    private static final Random random = new Random();

    UniqueAbility(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public String getName() { return name; }
    public String getDescription() { return description; }

    // 랜덤으로 능력 하나 뽑기
    public static UniqueAbility pickRandom() {
        return values()[random.nextInt(values().length)];
    }
}