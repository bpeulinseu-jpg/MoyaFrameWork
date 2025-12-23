package com.server.tower.game.wave;

public enum FloorType {
    NORMAL("일반 웨이브"),
    TIME_ATTACK("타임 어택"),
    ELITE("정예 토벌"),
    REST("안전 지대"), // 휴게실
    BOSS("챕터 보스");

    private final String displayName;

    FloorType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}