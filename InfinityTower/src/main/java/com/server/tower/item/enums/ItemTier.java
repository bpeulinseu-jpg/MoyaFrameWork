package com.server.tower.item.enums;

public enum ItemTier {
    COMMON("§7[일반]", 1.0, 0, 1, false, 5),
    RARE("§9[희귀]", 1.2, 1, 2, false, 7),
    EPIC("§5[영웅]", 1.5, 2, 3, true, 10),
    LEGENDARY("§6§l[전설]", 2.0, 3, 3, true, 15),
    // 고유 등급 (드랍으론 안나옴)
    UNIQUE("§d§l[고유]", 3.0, 4, 4, true, 15);

    private final String prefix;
    private final double statMultiplier; // 스탯 배율
    private final int minSockets;
    private final int maxSockets;
    private final boolean glow;
    private final int maxEnhanceLevel;

    ItemTier(String prefix, double statMultiplier, int minSockets, int maxSockets, boolean glow, int maxEnhanceLevel) {
        this.prefix = prefix;
        this.statMultiplier = statMultiplier;
        this.minSockets = minSockets;
        this.maxSockets = maxSockets;
        this.glow = glow;
        this.maxEnhanceLevel = maxEnhanceLevel;
    }

    public String getPrefix() { return prefix; }
    public double getStatMultiplier() { return statMultiplier; }
    public int getMinSockets() { return minSockets; }
    public int getMaxSockets() { return maxSockets; }
    public boolean isGlow() { return glow; }
    public int getMaxEnhanceLevel() { return maxEnhanceLevel; }
}