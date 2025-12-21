package com.server.tower.item.enums;

public enum ItemElement {
    FIRE("§c[화염]", "skill_fire"),
    ICE("§b[혹한]", "skill_ice"),
    STORM("§e[폭풍]", "skill_lightning"),
    LIGHT("§f[빛]", "skill_light"),
    DARK("§5[어둠]", "skill_dark");

    private final String prefix;
    private final String skillId; // 추후 스킬 구현 시 사용

    ItemElement(String prefix, String skillId) {
        this.prefix = prefix;
        this.skillId = skillId;
    }

    public String getPrefix() { return prefix; }
    public String getSkillId() { return skillId; }
}