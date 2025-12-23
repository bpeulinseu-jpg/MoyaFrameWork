package com.server.core.system.gimmick;

import com.server.core.CorePlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class GimmickManager implements Listener {

    private final CorePlugin plugin;
    private final Map<UUID, GimmickInstance> gimmicks = new ConcurrentHashMap<>();

    public GimmickManager(CorePlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    // --- 기믹 데이터 클래스 ---
    public static class GimmickInstance {
        public final Interaction interaction; // 히트박스
        public final BlockDisplay display;    // 보여지는 모습
        public final boolean isDestructible;  // 파괴 가능 여부
        public org.bukkit.entity.TextDisplay healthBar; // 체력바

        public double maxHp;
        public double currentHp;
        public Runnable onDestroy;            // 파괴 시 콜백
        public Consumer<Player> onInteract;   // 우클릭 시 콜백

        public GimmickInstance(Interaction interaction, BlockDisplay display, boolean isDestructible) {
            this.interaction = interaction;
            this.display = display;
            this.isDestructible = isDestructible;
        }
    }

    // --- 1. 파괴 가능한 토템 생성 (체력 있음, 좌클릭) ---
    public UUID createDestructible(Location loc, Material material, double hp, Runnable onDestroy) {
        return spawnGimmick(loc, material, true, hp, onDestroy, null);
    }

    // --- 2. 상호작용 가능한 제단 생성 (체력 없음, 우클릭) ---
    public UUID createInteractable(Location loc, Material material, Consumer<Player> onInteract) {
        return spawnGimmick(loc, material, false, 0, null, onInteract);
    }

    // --- 내부 소환 로직 ---
    private UUID spawnGimmick(Location loc, Material material, boolean destructible, double hp, Runnable onDestroy, Consumer<Player> onInteract) {
        // 1. 시각 효과 (BlockDisplay)
        BlockDisplay display = (BlockDisplay) loc.getWorld().spawnEntity(loc, EntityType.BLOCK_DISPLAY);
        display.setBlock(material.createBlockData());
        // 크기 및 위치 조정 (중앙 정렬)
        display.setTransformation(new Transformation(
                new Vector3f(-0.5f, 0, -0.5f), // translation (블록 중앙으로 이동)
                new AxisAngle4f(0, 0, 0, 1),
                new Vector3f(1f, 1f, 1f),      // scale
                new AxisAngle4f(0, 0, 0, 1)
        ));

        // 2. 히트박스 (Interaction)
        Interaction interaction = (Interaction) loc.getWorld().spawnEntity(loc, EntityType.INTERACTION);
        interaction.setInteractionHeight(1.0f);
        interaction.setInteractionWidth(1.0f);
        interaction.setResponsive(true); // 공격 시 반응함

        // 3. 데이터 등록
        GimmickInstance instance = new GimmickInstance(interaction, display, destructible);
        if (destructible) {
            instance.maxHp = hp;
            instance.currentHp = hp;
            instance.onDestroy = onDestroy;
            // [추가] 체력바 생성
            org.bukkit.entity.TextDisplay hpBar = (org.bukkit.entity.TextDisplay) loc.getWorld().spawnEntity(loc.clone().add(0, 1.2, 0), EntityType.TEXT_DISPLAY);
            hpBar.text(net.kyori.adventure.text.Component.text("§c♥ " + (int)hp + " / " + (int)hp));
            hpBar.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
            hpBar.setBackgroundColor(org.bukkit.Color.fromARGB(0,0,0,0));
            instance.healthBar = hpBar;
        } else {
            instance.onInteract = onInteract;
        }

        gimmicks.put(interaction.getUniqueId(), instance);
        return interaction.getUniqueId();
    }

    // --- 이벤트 처리 ---

    // 1. 좌클릭 (공격/파괴)
    @EventHandler
    public void onAttack(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Interaction interaction)) return;

        GimmickInstance gimmick = gimmicks.get(interaction.getUniqueId());
        if (gimmick == null) return;

        // 파괴 불가능한 기믹(제단 등)은 대미지 무시
        if (!gimmick.isDestructible) {
            event.setCancelled(true);
            return;
        }

        // 파괴 가능한 기믹(토템) 처리
        gimmick.currentHp -= event.getDamage();

        // [추가] 체력바 갱신
        if (gimmick.healthBar != null) {
            gimmick.healthBar.text(net.kyori.adventure.text.Component.text("§c♥ " + (int)Math.max(0, gimmick.currentHp) + " / " + (int)gimmick.maxHp));
        }

        // [추가] 블록 파괴 파티클 (타격감)
        interaction.getWorld().spawnParticle(
                org.bukkit.Particle.BLOCK,
                interaction.getLocation().add(0, 0.5, 0),
                10, 0.3, 0.3, 0.3,
                gimmick.display.getBlock() // 현재 블록 재질 사용
        );

        // 피격 효과 (잠깐 빨개지거나 소리)
        interaction.getWorld().playSound(interaction.getLocation(), Sound.BLOCK_STONE_HIT, 1f, 1f);

        if (gimmick.currentHp <= 0) {
            // 파괴됨
            if (gimmick.onDestroy != null) gimmick.onDestroy.run();
            removeGimmick(interaction.getUniqueId());

            interaction.getWorld().playSound(interaction.getLocation(), Sound.BLOCK_STONE_BREAK, 1f, 0.8f);
            interaction.getWorld().spawnParticle(org.bukkit.Particle.BLOCK, interaction.getLocation().add(0, 0.5, 0), 20, 0.3, 0.3, 0.3, gimmick.display.getBlock());
        }
    }

    // 2. 우클릭 (상호작용)
    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Interaction interaction)) return;

        GimmickInstance gimmick = gimmicks.get(interaction.getUniqueId());
        if (gimmick == null) return;

        if (gimmick.onInteract != null) {
            gimmick.onInteract.accept(event.getPlayer());
            // 상호작용 효과음
            event.getPlayer().playSound(interaction.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
        }
    }

    // 제거 로직
    public void removeGimmick(UUID uuid) {
        GimmickInstance gimmick = gimmicks.remove(uuid);
        if (gimmick != null) {
            if (gimmick.interaction != null) gimmick.interaction.remove();
            if (gimmick.display != null) gimmick.display.remove();
            // [추가] 체력바 삭제
            if (gimmick.healthBar != null) gimmick.healthBar.remove();
        }
    }

    // 전체 제거 (서버 종료 시 등)
    public void removeAll() {
        for (UUID uuid : gimmicks.keySet()) {
            removeGimmick(uuid);
        }
    }
}