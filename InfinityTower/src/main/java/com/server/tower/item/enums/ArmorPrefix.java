package com.server.tower.item.enums;

public enum ArmorPrefix {

    // 너프 개열 접두사
    BROKEN("부서진", 0.5, 0.8, 0),
    CRUDE("조잡한", 0.7, 0.9,0),
    WEAK("약한", 0.8, 1.0,0),
    DAMAGED("손상된", 0.9, 0.95,0),
    DULL("무딘", 0.9, 1.0,0),
    MESSY("엉망인", 0.6, 0.6,-10),
    RUSTY("녹슨", 0.7, 1.0,0),
    // 일반
    NORMAL("평범한", 1.0, 1.0,0),
    COMMON("흔한", 1.0, 1.0,0),

    //버프 개열
    SOLID("단단한", 1.3, 1.0, -5),    // 방어 높음, 느려짐
    LIGHT("가벼운", 0.8, 0.8, 10),    // 방어/체력 낮음, 빨라짐
    THICK("두꺼운", 1.0, 1.5, -10),   // 체력 대폭 증가, 느려짐
    ANCIENT("고대의", 1.2, 1.2, 0),   // 밸런스형
    STURDY("튼튼한", 1.0, 1.0,0),
    MASTERWORK("명품", 1.15, 1.1,+10),
    HEAVY("무거운", 1.5, 1.0, -15);   // 방어 대폭 증가, 많이 느려짐

    private final String name;
    private final double defMult;
    private final double hpMult;
    private final int speedAdd;

    ArmorPrefix(String name, double defMult, double hpMult, int speedAdd) {
        this.name = name;
        this.defMult = defMult;
        this.hpMult = hpMult;
        this.speedAdd = speedAdd;
    }

    public String getName() { return name; }
    public double getDefMult() { return defMult; }
    public double getHpMult() { return hpMult; }
    public int getSpeedAdd() { return speedAdd; }
}