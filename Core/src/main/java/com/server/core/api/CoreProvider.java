package com.server.core.api;

import com.server.core.CorePlugin;
import com.server.core.system.mob.MobManager;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import com.server.core.system.block.BlockManager;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import com.server.core.util.InventoryUtil;
import com.server.core.util.LoreUtil;

import java.io.File;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 애드온 개발자를 위한 핵심 API 모음
 * 모든 기능은 이 클래스를 통해 접근하는 것을 권장합니다.
 */
public class CoreProvider {

    // --- 1. 애드온 등록 ---
    public static void registerAddon(CoreAddon addon) {
        CorePlugin.registerAddon(addon);
    }

    // --- 2. 리소스 & 아이템 ---
    public static void registerItem(CoreAddon addon, String id, Material material, File textureFile, String name) {
        CorePlugin.getItemManager().registerItem(addon, id, material, textureFile, name);
    }

    public static void registerGlyph(CoreAddon addon, String id, File imageFile, int ascent, int height) {
        CorePlugin.getGlyphManager().registerGlyph(addon, id, imageFile, ascent, height);
    }

    public static ItemStack getItem(String fullId) {
        return CorePlugin.getItemManager().getItem(fullId);
    }

    // 완성된 아이템 프리셋으로 등록
    public static void registerItemPreset(String fullId, ItemStack item) {
        CorePlugin.getItemManager().registerPreset(fullId, item);
    }

    public static boolean isCustomItem(ItemStack item, String fullId) {
        return CorePlugin.getItemManager().isCustomItem(item, fullId);
    }

    // --- 3. HUD (화면 표시) ---
    public static void showHud(Player player, String layerId, int priority, Function<Player, Component> provider) {
        CorePlugin.getHudManager().registerLayer(player, layerId, priority, provider);
    }

    public static void clearHud(Player player) {
        CorePlugin.getHudManager().clearLayers(player);
    }

    public static Component getSpace(int pixels) {
        return CorePlugin.getGlyphManager().getSpaceComponent(pixels);
    }

    public static String getGlyphTag(String key) {
        return CorePlugin.getGlyphManager().getTag(key);
    }

    public static String parseTags(String text) {
        return CorePlugin.getGlyphManager().parseText(text);
    }

    // --- 4. GUI (인벤토리) ---
    public static Inventory openGui(Player player, String bgKey, String title, int offset, int rows, boolean locked) {
        return CorePlugin.getGuiManager().openGui(player, bgKey, title, offset, rows, locked);
    }

    public static void setGuiButton(Inventory inv, int slot, ItemStack item, Consumer<org.bukkit.event.inventory.InventoryClickEvent> action) {
        CorePlugin.getGuiManager().setButton(inv, slot, item, action);
    }

    // 여기서 부터는 1.1v 업데이트 내역


    // --- 5. 사운드 ---
    public static void registerSound(CoreAddon addon, String path, File oggFile) {
        CorePlugin.getResourcePackManager().registerSoundFile(addon, path, oggFile);
    }

    public static void registerSoundConfig(CoreAddon addon, File jsonFile) {
        CorePlugin.getResourcePackManager().registerSoundConfig(addon, jsonFile);
    }

    // --- 6. 디스플레이  ---

    public static void showBossBar(Player player, String id, Component title, float progress, BossBar.Color color, BossBar.Overlay style) {
        CorePlugin.getBossBarManager().setBossBar(player, id, title, progress, color, style);
    }

    public static void removeBossBar(Player player, String id) {
        CorePlugin.getBossBarManager().removeBossBar(player, id);
    }

    public static void sendTitle(Player player, Component title, Component subtitle, int fadeIn, int stay, int fadeOut, int priority) {
        CorePlugin.getTitleManager().sendTitle(player, title, subtitle, fadeIn, stay, fadeOut, priority);
    }

    public static void setSidebar(Player player, String id, Component title, java.util.List<Component> lines, int priority) {
        CorePlugin.getSidebarManager().setSidebar(player, id, title, lines, priority);
    }

    public static void removeSidebar(Player player, String id) {
        CorePlugin.getSidebarManager().removeSidebar(player, id);
    }

    // --- 7. 데이터 (NBT) ---
    public static void setItemData(ItemStack item, String key, String value) {
        CorePlugin.getDataManager().setString(item, key, value);
    }
    public static String getItemDataString(ItemStack item, String key) {
        return CorePlugin.getDataManager().getString(item, key);
    }

    public static void setItemData(ItemStack item, String key, int value) {
        CorePlugin.getDataManager().setInt(item, key, value);
    }
    public static int getItemDataInt(ItemStack item, String key) {
        return CorePlugin.getDataManager().getInt(item, key);
    }
    public static String getCustomId(ItemStack item) {
        return CorePlugin.getItemManager().getCustomId(item);
    }


    // 여기서부터는 1.2 업데이트 내역

    // --- 8. 커스텀 블록 ---
    public static BlockManager.CustomBlockData registerBlock(CoreAddon addon, String id, File textureFile, float hardness, int toolLevel) {
        return CorePlugin.getBlockManager().registerBlock(addon, id, textureFile, hardness, toolLevel);
    }

    // --- 9. 데이터베이스 (V1.3) ---

    // 저장 (비동기)
    public static void saveDB(String uuid, String key, Object data) {
        CorePlugin.getDatabaseManager().saveData(uuid, key, data);
    }

    // 로드 (동기 - 주의해서 사용)
    public static <T> T loadDB(String uuid, String key, Class<T> type) {
        return CorePlugin.getDatabaseManager().loadData(uuid, key, type);
    }

    // 로드 (비동기 - 권장)
    public static <T> java.util.concurrent.CompletableFuture<T> loadDBAsync(String uuid, String key, Class<T> type) {
        return CorePlugin.getDatabaseManager().loadDataAsync(uuid, key, type);
    }

    // --- 10. 스탯 시스템 (V1.3) ---
    public static void setBaseStat(Player player, String key, double value) {
        CorePlugin.getStatManager().setBaseStat(player, key, value);
    }

    public static void addSessionStat(Player player, String key, double value) {
        CorePlugin.getStatManager().addSessionStat(player, key, value);
    }

    public static double getStat(Player player, String key) {
        return CorePlugin.getStatManager().getStat(player, key);
    }

    public static void recalculateStats(Player player) {
        CorePlugin.getStatManager().recalculate(player);
    }

    // --- 11. 세션 (V1.3) ---
    public static void startSession(Player player) {
        CorePlugin.getSessionManager().startSession(player);
    }

    public static void endSession(Player player) {
        CorePlugin.getSessionManager().endSession(player);
    }

    public static boolean isInSession(Player player) {
        return CorePlugin.getSessionManager().isInSession(player);
    }

    // --- 12. 몹 (V1.3) ---
    public static MobManager.CustomMobData registerMob(CoreAddon addon, String id, EntityType type, String name) {
        return CorePlugin.getMobManager().registerMob(addon, id, type, name);
    }

    public static void spawnMob(org.bukkit.Location loc, String fullId) {
        CorePlugin.getMobManager().spawnMob(loc, fullId);
    }

    // --- 13. 쿨타임 (V1.3) ---
    public static void setCooldown(Player player, String key, long ticks) {
        CorePlugin.getCooldownManager().setCooldown(player, key, ticks);
    }

    public static boolean hasCooldown(Player player, String key) {
        return CorePlugin.getCooldownManager().hasCooldown(player, key);
    }

    public static double getCooldown(Player player, String key) {
        return CorePlugin.getCooldownManager().getRemainingSeconds(player, key);
    }

    // --- 14. 투사체 (V1.3) ---
    public static void shootProjectile(org.bukkit.entity.LivingEntity shooter, ItemStack item, double speed, double range, Consumer<org.bukkit.entity.Entity> onHit) {
        CorePlugin.getProjectileManager().shoot(shooter, item, speed, 0.0, range, onHit); // 중력 0 (직사)
    }

    // --- 15. 인벤토리 유틸리티 (V1.3) ---
    public static boolean hasItem(Player player, String customId, int amount) {
        return InventoryUtil.hasItem(player, customId, amount);
    }

    public static boolean removeItem(Player player, String customId, int amount) {
        return InventoryUtil.removeItem(player, customId, amount);
    }

    // --- 16. 로어 유틸리티 (V1.3) ---
    public static void updateLore(ItemStack item, String target, String replacement) {
        LoreUtil.updateLore(item, target, replacement);
    }

    //첫번째 줄만 교체
    public static void updateFirstLore(ItemStack item, String target, String replacement) {
        LoreUtil.updateFirstLore(item, target, replacement);
    }

    // --- 17. 전투 (V1.3) ---
    public static boolean isDamageProcessing(java.util.UUID uuid) {
        return CorePlugin.getDamageManager().isProcessing(uuid);
    }

    public static void dealDamage(LivingEntity attacker, LivingEntity victim, double damage, boolean isCrit) {
        CorePlugin.getDamageManager().dealDamage(attacker, victim, damage, isCrit, false);
    }

    // 방어 무시 옵션 포함
    public static void dealDamage(LivingEntity attacker, LivingEntity victim, double damage, boolean isCrit, boolean ignoreArmor) {
        CorePlugin.getDamageManager().dealDamage(attacker, victim, damage, isCrit, ignoreArmor);
    }
}