package com.server.tower.game.wave;

import java.util.HashMap;
import java.util.Map;

public class WaveData {
    private final FloorType type;
    private final Map<String, Integer> monsters;
    private final int timeLimit;

    // [수정] 단일 String -> Map<ID, 개수> 로 변경
    private final Map<String, Integer> gimmicks;

    private WaveData(Builder builder) {
        this.type = builder.type;
        this.monsters = builder.monsters;
        this.timeLimit = builder.timeLimit;
        this.gimmicks = builder.gimmicks;
    }

    public FloorType getType() { return type; }
    public Map<String, Integer> getMonsters() { return monsters; }
    public int getTimeLimit() { return timeLimit; }

    // [수정] Getter 변경
    public Map<String, Integer> getGimmicks() { return gimmicks; }

    public static class Builder {
        private FloorType type = FloorType.NORMAL;
        private final Map<String, Integer> monsters = new HashMap<>();
        private int timeLimit = 0;

        // [수정] 기믹 맵 초기화
        private final Map<String, Integer> gimmicks = new HashMap<>();

        public Builder type(FloorType type) {
            this.type = type;
            return this;
        }

        public Builder addMob(String mobId, int amount) {
            monsters.put(mobId, monsters.getOrDefault(mobId, 0) + amount);
            return this;
        }

        public Builder timeLimit(int seconds) {
            this.timeLimit = seconds;
            return this;
        }

        // [수정] 기믹 추가 메서드 (개수 지정 가능)
        // 예: .addGimmick("CURSE_TOTEM", 3)
        public Builder addGimmick(String gimmickId, int amount) {
            gimmicks.put(gimmickId, gimmicks.getOrDefault(gimmickId, 0) + amount);
            return this;
        }

        // (호환성 유지용) 개수 안 적으면 1개
        public Builder gimmick(String gimmickId) {
            return addGimmick(gimmickId, 1);
        }

        public WaveData build() {
            return new WaveData(this);
        }
    }
}