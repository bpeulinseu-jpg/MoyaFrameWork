package com.server.core.system.data;

import com.server.core.CorePlugin;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public class DataManager {

    private final CorePlugin plugin;

    public DataManager(CorePlugin plugin) {
        this.plugin = plugin;
    }

    // --- String Data ---
    public void setString(ItemStack item, String key, String value) {
        if (item == null || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        NamespacedKey nsKey = new NamespacedKey(plugin, key);
        meta.getPersistentDataContainer().set(nsKey, PersistentDataType.STRING, value);
        item.setItemMeta(meta);
    }

    public String getString(ItemStack item, String key) {
        if (item == null || !item.hasItemMeta()) return null;
        NamespacedKey nsKey = new NamespacedKey(plugin, key);
        return item.getItemMeta().getPersistentDataContainer().get(nsKey, PersistentDataType.STRING);
    }

    // --- Integer Data ---
    public void setInt(ItemStack item, String key, int value) {
        if (item == null || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        NamespacedKey nsKey = new NamespacedKey(plugin, key);
        meta.getPersistentDataContainer().set(nsKey, PersistentDataType.INTEGER, value);
        item.setItemMeta(meta);
    }

    public int getInt(ItemStack item, String key) {
        if (item == null || !item.hasItemMeta()) return 0;
        NamespacedKey nsKey = new NamespacedKey(plugin, key);
        return item.getItemMeta().getPersistentDataContainer().getOrDefault(nsKey, PersistentDataType.INTEGER, 0);
    }

    // --- Boolean Data (Byte로 저장) ---
    public void setBoolean(ItemStack item, String key, boolean value) {
        if (item == null || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        NamespacedKey nsKey = new NamespacedKey(plugin, key);
        meta.getPersistentDataContainer().set(nsKey, PersistentDataType.BYTE, (byte) (value ? 1 : 0));
        item.setItemMeta(meta);
    }

    public boolean getBoolean(ItemStack item, String key) {
        if (item == null || !item.hasItemMeta()) return false;
        NamespacedKey nsKey = new NamespacedKey(plugin, key);
        Byte result = item.getItemMeta().getPersistentDataContainer().get(nsKey, PersistentDataType.BYTE);
        return result != null && result == 1;
    }

    // 키 존재 여부 확인
    public boolean hasData(ItemStack item, String key) {
        if (item == null || !item.hasItemMeta()) return false;
        NamespacedKey nsKey = new NamespacedKey(plugin, key);
        return item.getItemMeta().getPersistentDataContainer().has(nsKey, PersistentDataType.STRING) ||
                item.getItemMeta().getPersistentDataContainer().has(nsKey, PersistentDataType.INTEGER) ||
                item.getItemMeta().getPersistentDataContainer().has(nsKey, PersistentDataType.BYTE);
    }
}