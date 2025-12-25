package com.server.core.listener;

import com.server.core.CorePlugin;
import com.server.core.api.event.CustomBlockInteractEvent;
import com.server.core.system.block.BlockManager;
import com.server.core.system.block.DropItem; // Import 필수
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Fence;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.block.data.type.Wall;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.NotePlayEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.concurrent.ThreadLocalRandom; // 난수 생성용 Import

public class BlockListener implements Listener {

    // 1. 블록 설치
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        handlePlaceLogic(event.getBlockPlaced(), event.getItemInHand());
    }

    private void handlePlaceLogic(Block block, ItemStack handItem) {
        String customId = CorePlugin.getItemManager().getCustomId(handItem);
        if (customId == null) return;

        BlockManager.CustomBlockData blockData = null;
        for (BlockManager.CustomBlockData data : CorePlugin.getBlockManager().getAllBlocks()) {
            if (data.uniqueName.equals(customId)) {
                blockData = data;
                break;
            }
        }

        if (blockData != null) {
            block.setBlockData(blockData.blockData);
            block.getWorld().playSound(block.getLocation(), blockData.placeSound, 1f, 1f);
        }
    }

    // 2. 블록 파괴 및 드랍 (여기가 수정됨)
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        BlockManager.CustomBlockData blockData = CorePlugin.getBlockManager().getBlock(block);
        if (blockData == null) return;

        event.setDropItems(false);
        block.getWorld().playSound(block.getLocation(), blockData.breakSound, 1f, 1f);

        if (event.getPlayer().getGameMode() == GameMode.CREATIVE) return;

        ItemStack tool = event.getPlayer().getInventory().getItemInMainHand();
        boolean hasSilkTouch = tool.containsEnchantment(Enchantment.SILK_TOUCH);
        // 행운 레벨 (LOOT_BONUS_BLOCKS)
        int fortuneLevel = tool.getEnchantmentLevel(Enchantment.FORTUNE);

        if (hasSilkTouch) {
            // 실크터치 로직
            if (blockData.silkTouchDrop != null) {
                block.getWorld().dropItemNaturally(block.getLocation(), blockData.silkTouchDrop.clone());
            } else if (!blockData.requireSilkTouch) {
                // 실크터치 전용 드랍 없으면 일반 계산 (행운 0)
                calculateAndDrop(block, blockData, 0);
            }
        } else {
            // 일반 파괴 로직
            if (blockData.requireSilkTouch) return; // 실크터치 필수면 드랍 없음

            // [수정됨] 기존에 dropDefault를 호출하던 것을 calculateAndDrop으로 변경!
            calculateAndDrop(block, blockData, fortuneLevel);
        }
    }

    // [핵심] 드랍 테이블 계산 로직
    private void calculateAndDrop(Block block, BlockManager.CustomBlockData data, int fortuneLevel) {
         Bukkit.broadcast(Component.text("§e[Debug] 드랍 테이블 크기: " + data.drops.size()));

        // 1. 드랍 테이블이 비어있으면 -> 기본값(본체) 드랍
        if (data.drops.isEmpty()) {
            dropDefault(block, data);
            return;
        }

        // 2. 드랍 테이블 순회
        for (DropItem dropInfo : data.drops) {
            if (dropInfo.getItem() == null) {
                Bukkit.broadcast(Component.text("§c[Debug] 드랍 아이템이 NULL입니다! 등록되지 않은 아이템 ID를 사용했나요?"));
                continue;
            }


            // A. 확률 체크 (0.0 ~ 1.0)
            double roll = Math.random();
             Bukkit.broadcast(Component.text("§7[Debug] 확률 롤: " + String.format("%.2f", roll) + " / " + dropInfo.getChance()));
            if (roll > dropInfo.getChance()) continue; // 꽝

            // B. 수량 계산 (Min ~ Max)
            int amount = ThreadLocalRandom.current().nextInt(dropInfo.getMinAmount(), dropInfo.getMaxAmount() + 1);

            // C. 행운 보너스
            if (dropInfo.isApplyFortune() && fortuneLevel > 0) {
                // 행운 1당 최대 1개씩 추가 (단순 공식)
                int bonus = ThreadLocalRandom.current().nextInt(0, fortuneLevel + 1);
                amount += bonus;
                 Bukkit.broadcast(Component.text("§b[Debug] 행운 발동! (+" + bonus + ")"));
            }

            // D. 드랍 실행
            if (amount > 0) {
                ItemStack toDrop = dropInfo.getItem();
                toDrop.setAmount(amount);
                block.getWorld().dropItemNaturally(block.getLocation(), toDrop);
                 Bukkit.broadcast(Component.text("§a[Debug] 드랍 성공: " + toDrop.getType() + " x" + amount));

            }
        }
    }

    private void dropDefault(Block block, BlockManager.CustomBlockData data) {
        String fullId = data.namespace + ":" + data.id;
        ItemStack self = CorePlugin.getItemManager().getItem(fullId);
        if (self != null) block.getWorld().dropItemNaturally(block.getLocation(), self);
    }
    /*
    // 3. 우클릭 상호작용 및 설치 제어
    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) return;

        Player player = event.getPlayer();
        ItemStack handItem = event.getItem();

        boolean isNoteBlock = clickedBlock.getType() == Material.NOTE_BLOCK;
        BlockManager.CustomBlockData blockData = CorePlugin.getBlockManager().getBlock(clickedBlock);

        if (isNoteBlock) event.setCancelled(true);

        if (blockData != null) {
            CustomBlockInteractEvent customEvent = new CustomBlockInteractEvent(
                    player, clickedBlock, blockData, handItem, event.getAction(), event.getBlockFace()
            );
            Bukkit.getPluginManager().callEvent(customEvent);

            if (customEvent.isCancelled()) return;

        } else {
            if (isVanillaInteractable(clickedBlock) && !player.isSneaking()) {
                if (!isNoteBlock) event.setCancelled(false);
                return;
            }
        }

        if (handItem != null && handItem.getType().isBlock() && handItem.getType() != Material.AIR) {
            placeCustomBlockManually(event, clickedBlock, handItem);
        }
    }

     */

    private boolean isVanillaInteractable(Block block) {
        Material type = block.getType();
        if (!type.isInteractable()) return false;
        if (block.getBlockData() instanceof Stairs) return false;
        if (block.getBlockData() instanceof Fence) return false;
        if (block.getBlockData() instanceof Wall) return false;
        if (type == Material.PUMPKIN) return false;
        if (type == Material.NOTE_BLOCK) return false;
        return true;
    }

    private void placeCustomBlockManually(PlayerInteractEvent event, Block clickedBlock, ItemStack handItem) {
        Player player = event.getPlayer();
        BlockFace face = event.getBlockFace();
        Block targetPlace = clickedBlock.getRelative(face);

        if (targetPlace.getType().isAir() || targetPlace.isReplaceable()) {
            if (!targetPlace.getWorld().getNearbyEntities(targetPlace.getLocation().add(0.5, 0.5, 0.5), 0.5, 0.5, 0.5).isEmpty()) {
                return;
            }

            String customId = CorePlugin.getItemManager().getCustomId(handItem);

            if (customId != null) {
                BlockManager.CustomBlockData placeData = null;
                for (BlockManager.CustomBlockData d : CorePlugin.getBlockManager().getAllBlocks()) {
                    if (d.uniqueName.equals(customId)) {
                        placeData = d;
                        break;
                    }
                }

                if (placeData != null) {
                    targetPlace.setBlockData(placeData.blockData);
                    targetPlace.getWorld().playSound(targetPlace.getLocation(), placeData.placeSound, 1f, 1f);

                    if (player.getGameMode() != GameMode.CREATIVE) {
                        handItem.setAmount(handItem.getAmount() - 1);
                    }
                    player.swingMainHand();
                }
            } else {
                targetPlace.setType(handItem.getType(), true);
                if (player.getGameMode() != GameMode.CREATIVE) {
                    handItem.setAmount(handItem.getAmount() - 1);
                }
                player.swingMainHand();
            }
        }
    }

    @EventHandler
    public void onNotePlay(NotePlayEvent event) {
        if (CorePlugin.getBlockManager().getBlock(event.getBlock()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onCreativeClick(InventoryCreativeEvent event) {
        if (event.getCursor().getType() != Material.NOTE_BLOCK && event.getCurrentItem() != null && event.getCurrentItem().getType() != Material.NOTE_BLOCK) return;

        if (event.getWhoClicked() instanceof Player player) {
            Block target = player.getTargetBlockExact(6);
            if (target != null && target.getType() == Material.NOTE_BLOCK) {
                BlockManager.CustomBlockData blockData = CorePlugin.getBlockManager().getBlock(target);
                if (blockData != null) {
                    String fullId = blockData.namespace + ":" + blockData.id;
                    ItemStack customItem = CorePlugin.getItemManager().getItem(fullId);
                    if (customItem != null) {
                        event.setCursor(customItem);
                        event.setCurrentItem(customItem);
                        Bukkit.getScheduler().runTask(CorePlugin.getInstance(), player::updateInventory);
                    }
                }
            }
        }
    }
}