package com.server.tower.game.wave;

import java.util.HashMap;
import java.util.Map;

public class WaveData {
    private final FloorType type;
    private final Map<String, Integer> monsters; // MobID -> 마리수
    private final int timeLimit; // 초 단위 (0이면 제한 없음)
    private final String gimmickId; // 기믹 ID (예: "TOTEM", "ALTAR") - 없으면 null

    private WaveData(Builder builder) {
        this.type = builder.type;
        this.monsters = builder.monsters;
        this.timeLimit = builder.timeLimit;
        this.gimmickId = builder.gimmickId;
    }

    public FloorType getType() { return type; }
    public Map<String, Integer> getMonsters() { return monsters; }
    public int getTimeLimit() { return timeLimit; }
    public String getGimmickId() { return gimmickId; }

    // --- Builder Pattern ---
    public static class Builder {
        private FloorType type = FloorType.NORMAL;
        private final Map<String, Integer> monsters = new HashMap<>();
        private int timeLimit = 0;
        private String gimmickId = null;

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

        public Builder gimmick(String gimmickId) {
            this.gimmickId = gimmickId;
            return this;
        }

        public WaveData build() {
            return new WaveData(this);
        }
    }
}