package com.server.tower.system.dungeon;

import org.bukkit.World;
import org.bukkit.generator.ChunkGenerator;
import java.util.Random;

public class VoidChunkGenerator extends ChunkGenerator {
    // 아무것도 생성하지 않음 (완전한 빈 월드)
    // 1.17+ 버전에서는 ChunkGenerator 구현 방식이 조금 달라졌으나,
    // 기본적으로 메서드를 오버라이드하지 않으면 빈 청크가 생성됨.
}