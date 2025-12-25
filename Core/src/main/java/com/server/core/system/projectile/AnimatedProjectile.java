package com.server.core.system.projectile;

import com.server.core.api.builder.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.function.Consumer;

public class AnimatedProjectile extends CustomProjectile {

    private final int startCmd;
    private final int frameCount;
    private final int tickPerFrame;
    private final boolean loop; // [추가] 반복 여부

    private int currentFrame = 0;
    private int tickCounter = 0;

    public AnimatedProjectile(LivingEntity shooter, ItemDisplay visual, Vector direction, double speed, double range,
                              int startCmd, int frameCount, int tickPerFrame, boolean loop, // 인자 추가
                              Consumer<org.bukkit.entity.Entity> onHitEntity) {
        super(shooter, visual, direction, speed, 0.0, range, onHitEntity, null, null);
        this.startCmd = startCmd;
        this.frameCount = frameCount;
        this.tickPerFrame = tickPerFrame;
        this.loop = loop;
    }

    @Override
    public void tick() {
        super.tick();

        if (isDead()) return;

        tickCounter++;
        if (tickCounter >= tickPerFrame) {
            tickCounter = 0;
            currentFrame++;

            // [수정] 프레임 종료 처리
            if (currentFrame >= frameCount) {
                if (loop) {
                    currentFrame = 0; // 반복
                } else {
                    remove(); // 반복 아니면 삭제 (애니메이션 끝)
                    return;
                }
            }

            updateVisual();
        }
    }

    private void updateVisual() {
        if (getVisual() instanceof ItemDisplay display) {
            int nextCmd = startCmd + currentFrame;
            ItemStack nextFrameItem = new ItemBuilder(Material.SNOWBALL).model(nextCmd).build();
            display.setItemStack(nextFrameItem);
        }
    }
}