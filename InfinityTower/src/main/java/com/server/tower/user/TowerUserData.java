package com.server.tower.user;

public class TowerUserData {
    // 재화
    public long gold = 0;
    public long ether = 0; // 특수 재화

    // 영구 스탯 (로비에서 강화하는 것)
    public int statStr = 0; // 힘 (공격력/물리 스킬 데미지 및 방어력)
    public int statVit = 0; // 활력 (최대 hp 및 hp 재생량)
    public int statDex = 0; // 민첩 (이속 / 회피확률)
    public int statInt = 0; // 지능 (마나/마법 스킬 데미지 및 쿨타임 감소)
    public int statLuk = 0; // 행운 (치명타 확률 및 치명타 피해 증가)
    // 진행도
    public int maxFloor = 0;

    // 기본 생성자 (Gson 필수)
    public TowerUserData() {}
}