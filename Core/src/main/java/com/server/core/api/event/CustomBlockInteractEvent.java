package com.server.core.api.event;

import com.server.core.system.block.BlockManager;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

public class CustomBlockInteractEvent extends Event implements Cancellable {

    private static final HandlerList handlers = new HandlerList();
    private boolean cancelled = false;

    private final Player player;
    private final Block block;
    private final BlockManager.CustomBlockData blockData; // 클릭한 커스텀 블록 데이터
    private final ItemStack itemInHand;
    private final Action action;
    private final BlockFace blockFace;

    public CustomBlockInteractEvent(Player player, Block block, BlockManager.CustomBlockData blockData, ItemStack itemInHand, Action action, BlockFace blockFace) {
        this.player = player;
        this.block = block;
        this.blockData = blockData;
        this.itemInHand = itemInHand;
        this.action = action;
        this.blockFace = blockFace;
    }

    public Player getPlayer() { return player; }
    public Block getClickedBlock() { return block; }
    public BlockManager.CustomBlockData getBlockData() { return blockData; }
    public ItemStack getItemInHand() { return itemInHand; }
    public Action getAction() { return action; }
    public BlockFace getBlockFace() { return blockFace; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancel) { this.cancelled = cancel; }

    @Override
    public @NotNull HandlerList getHandlers() { return handlers; }

    public static HandlerList getHandlerList() { return handlers; }
}