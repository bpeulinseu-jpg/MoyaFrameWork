package com.server.addon.test;

public class PlayerDataDTO {
    public int gold;
    public int str;
    public int dex;
    public String lastLogin;

    // 기본 생성자
    public PlayerDataDTO() {}

    public PlayerDataDTO(int gold, int str, int dex, String lastLogin) {
        this.gold = gold;
        this.str = str;
        this.dex = dex;
        this.lastLogin = lastLogin;
    }
}