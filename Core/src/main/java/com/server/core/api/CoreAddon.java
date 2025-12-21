package com.server.core.api;

import org.bukkit.plugin.java.JavaPlugin;

public interface CoreAddon {

    // [필수] 애드온 식별자 (예: fishing)
    String getNamespace();

    // [필수] 플러그인 인스턴스
    JavaPlugin getPlugin();

    // [선택] Core가 리소스팩 생성을 마치고 준비되었을 때 호출됨
    // HUD 등록, 레시피 등록 등은 여기서 해야 안전함.
    default void onCoreReady() {}

    // [선택] /core reload 명령어 실행 시 호출됨
    // config.yml 다시 읽기 등을 여기서 처리.
    default void onReload() {}

    // [선택] 애드온이 비활성화될 때 호출됨
    default void onDisable() {}
}