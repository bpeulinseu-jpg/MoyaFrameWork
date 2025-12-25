package com.server.tower.game.wave;

import java.util.HashMap;
import java.util.Map;

public class WaveManager {

    // 층수(1~10) -> 데이터
    private final Map<Integer, WaveData> waveMap = new HashMap<>();

    public WaveManager() {
        initializeChapter1();
    }

    public WaveData getWaveData(int floor) {
        // 11층, 21층... 도 1~10층 데이터를 반복해서 사용 (GameManager에서 스펙 스케일링)
        int cycleFloor = (floor - 1) % 10 + 1;
        return waveMap.get(cycleFloor);
    }

    private void initializeChapter1() {
        // ==========================================
        // [1F ~ 3F] 일반 웨이브 (Hack & Slash)
        // 목표: 다수의 약한 몬스터 쓸어담기
        // ==========================================

        // 1F: 고블린 군단 (튜토리얼 느낌)
        waveMap.put(1, new WaveData.Builder()
                .type(FloorType.NORMAL)
                .addMob("infinity_tower:goblin", 15) // 물량 공세
                .build());

        // 2F: 원거리 견제 추가
        waveMap.put(2, new WaveData.Builder()
                .type(FloorType.NORMAL)
                .addMob("infinity_tower:goblin", 10)
                .addMob("infinity_tower:skeleton_sniper", 4) // 뒤에서 쏘는 애들
                .build());

        // 3F: 기믹 등장 (피의 제단)
        waveMap.put(3, new WaveData.Builder()
                .type(FloorType.NORMAL)
                .addMob("infinity_tower:goblin", 12)
                .addMob("infinity_tower:goblin_shaman", 2) // 버퍼 등장
                .build());


        // ==========================================
        // [4F] 타임 어택 (Time Attack)
        // 목표: 60초 내에 돌파 (긴장감)
        // ==========================================
        waveMap.put(4, new WaveData.Builder()
                .type(FloorType.TIME_ATTACK)
                .timeLimit(60) // 60초 제한
                .addMob("infinity_tower:toxic_slime", 20) // 잘 죽지만 자폭해서 까다로운 슬라임 떼거리
                .build());


        // ==========================================
        // [5F] 중간 보스 (Elite)
        // 목표: 소수 정예, 패턴 파악
        // ==========================================
        waveMap.put(5, new WaveData.Builder()
                .type(FloorType.ELITE)
                .addMob("infinity_tower:living_armor", 1)  // 튼튼한 중간보스
                .addMob("infinity_tower:goblin_shaman", 2) // 힐러 2마리 (먼저 잡아야 함)
                .addMob("infinity_tower:skeleton_sniper", 2) // 호위 사수
                .build());


        // ==========================================
        // [6F] 휴게실 (Rest Area)
        // 목표: 정비 및 선택
        // ==========================================
        waveMap.put(6, new WaveData.Builder()
                .type(FloorType.REST)
                .build()); // 몬스터 없음


        // ==========================================
        // [7F ~ 9F] 강화 웨이브 (Hard Mode)
        // 목표: 까다로운 조합과 환경 패널티
        // ==========================================

        // 7F: 철벽 방어 (탱커 + 원거리)
        waveMap.put(7, new WaveData.Builder()
                .type(FloorType.NORMAL)
                .addMob("infinity_tower:living_armor", 3) // 앞라인
                .addMob("infinity_tower:skeleton_sniper", 5) // 뒷라인
                .build());

        // 8F: 혼돈의 늪 (은신 + 자폭 + 저주 토템)
        waveMap.put(8, new WaveData.Builder()
                .type(FloorType.NORMAL)
                .addMob("infinity_tower:shadow_wraith", 4) // 은신 암살자
                .addMob("infinity_tower:toxic_slime", 8)   // 자폭병
                .addGimmick("CURSE_TOTEM", 1)  // [기믹] 몬스터 무적 토템 (우선 파괴 강제)
                .build());

        // 9F: 총력전 (보스 전 마지막 관문)
        waveMap.put(9, new WaveData.Builder()
                .type(FloorType.NORMAL)
                .addMob("infinity_tower:goblin", 10)
                .addMob("infinity_tower:living_armor", 2)
                .addMob("infinity_tower:goblin_shaman", 2)
                .addMob("infinity_tower:shadow_wraith", 2)
                .build());


        // ==========================================
        // [10F] 챕터 보스 (Boss)
        // ==========================================
        waveMap.put(10, new WaveData.Builder()
                .type(FloorType.BOSS)
                .addMob("infinity_tower:orc_chief", 1) // 오크 대장 (추후 MobRegistry에 추가 필요)
                .build());
    }
}