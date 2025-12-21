package com.server.tower.item.enums;

public enum ArmorElement {
    NONE("§7", "none", ""),
    FIRE("§c[화염]", "stat_phys_atk", "물리 공격력"), // 힘 대신 물리 공격력 직접 증가로 변경
    ICE("§b[혹한]", "stat_def", "방어력"),
    STORM("§e[폭풍]", "stat_move_speed", "이동 속도"), //
    LIGHT("§f[빛]", "stat_mag_atk", "마법 공격력"),    //
    DARK("§5[어둠]", "stat_crit_damage", "치명타 피해"), //
    WIND("§e[바람]", "stat_dodge", "회피 확률");

    private final String prefix;
    private final String statKey; // NBT 키 (stat_ 접두사 포함)
    private final String statName; // Lore 표시용 이름

    ArmorElement(String prefix, String statKey, String statName) {
        this.prefix = prefix;
        this.statKey = statKey;
        this.statName = statName;
    }

    public String getPrefix() { return prefix; }
    public String getStatKey() { return statKey; }
    public String getStatName() { return statName; }
}