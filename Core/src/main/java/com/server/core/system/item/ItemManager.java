package com.server.core.system.item;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.server.core.CorePlugin;
import com.server.core.api.CoreAddon;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class ItemManager {

    private final CorePlugin plugin;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private final NamespacedKey KEY_ID;

    private final Map<Material, Integer> cmdCounters = new HashMap<>();
    private final List<CustomItemData> registeredResources = new ArrayList<>();
    private final List<String> registeredArmorSets = new ArrayList<>();
    private final Map<String, ItemStack> itemPresets = new LinkedHashMap<>();

    public record CustomItemData(
            String namespace,
            String id,
            String uniqueName,
            Material material,
            int cmd,
            String displayName
    ) {}

    public ItemManager(CorePlugin plugin) {
        this.plugin = plugin;
        this.KEY_ID = new NamespacedKey(plugin, "id");
    }

    // 1. 일반 아이템 등록
    public void registerItem(CoreAddon addon, String id, Material material, File textureFile, String displayName) {
        String safeId = id.toLowerCase();
        String safeNamespace = addon.getNamespace().toLowerCase();
        String uniqueName = safeNamespace + "_" + safeId;
        String fullId = safeNamespace + ":" + safeId;

        int cmd = cmdCounters.getOrDefault(material, 10000);
        cmdCounters.put(material, cmd + 1);

        String textureRelPath = "item/" + uniqueName + ".png";
        CorePlugin.getResourcePackManager().registerTexture("minecraft", textureRelPath, textureFile);

        CustomItemData data = new CustomItemData(safeNamespace, safeId, uniqueName, material, cmd, displayName);
        registeredResources.add(data);

        ItemStack baseItem = new ItemStack(material);
        ItemMeta meta = baseItem.getItemMeta();
        meta.displayName(Component.text(displayName));
        meta.setCustomModelData(cmd);
        meta.getPersistentDataContainer().set(KEY_ID, PersistentDataType.STRING, uniqueName);
        baseItem.setItemMeta(meta);

        registerPreset(fullId, baseItem);

        plugin.getLogger().info("⚔️ 아이템 등록: " + uniqueName + " (CMD: " + cmd + ")");
    }

    // 2. 블록 아이템 등록
    public void registerBlockItem(CoreAddon addon, String id, Material material, String displayName) {
        String safeId = id.toLowerCase();
        String safeNamespace = addon.getNamespace().toLowerCase();
        String uniqueName = safeNamespace + "_" + safeId;
        String fullId = safeNamespace + ":" + safeId;

        int cmd = cmdCounters.getOrDefault(material, 10000);
        cmdCounters.put(material, cmd + 1);

        CustomItemData data = new CustomItemData(safeNamespace, safeId, uniqueName, material, cmd, displayName);
        registeredResources.add(data);

        ItemStack baseItem = new ItemStack(material);
        ItemMeta meta = baseItem.getItemMeta();
        meta.displayName(Component.text(displayName));
        meta.setCustomModelData(cmd);
        meta.getPersistentDataContainer().set(KEY_ID, PersistentDataType.STRING, uniqueName);
        baseItem.setItemMeta(meta);

        registerPreset(fullId, baseItem);
    }

    // [신규] 3. 갑옷 세트 등록 (텍스처만)
    public void registerArmorSet(String setId, File layer1, File layer2) {
        CorePlugin.getResourcePackManager().registerArmorSetTexture(setId, layer1, layer2);
        if (!registeredArmorSets.contains(setId)) {
            registeredArmorSets.add(setId);
        }
    }

    // [신규] 4. 갑옷 아이템 등록 (아이콘 + 세트ID 연결)
    public void registerArmorItem(CoreAddon addon, String id, Material material, File iconFile, String displayName, String setId) {
        String safeId = id.toLowerCase();
        String safeNamespace = addon.getNamespace().toLowerCase();
        String uniqueName = safeNamespace + "_" + safeId;
        String fullId = safeNamespace + ":" + safeId;

        int cmd = cmdCounters.getOrDefault(material, 10000);
        cmdCounters.put(material, cmd + 1);

        // 아이콘 등록
        String iconPath = "item/" + uniqueName + ".png";
        CorePlugin.getResourcePackManager().registerTexture("minecraft", iconPath, iconFile);

        CustomItemData data = new CustomItemData(safeNamespace, safeId, uniqueName, material, cmd, displayName);
        registeredResources.add(data);

        // 프리셋 생성
        ItemStack baseItem = new ItemStack(material);
        ItemMeta meta = baseItem.getItemMeta();
        meta.displayName(Component.text(displayName));
        meta.setCustomModelData(cmd);
        meta.getPersistentDataContainer().set(KEY_ID, PersistentDataType.STRING, uniqueName);

        // [중요] ItemBuilder에서 NBT로 처리하므로 여기선 API 호출 안함

        baseItem.setItemMeta(meta);
        registerPreset(fullId, baseItem);

        plugin.getLogger().info("🛡️ 갑옷 아이템 등록: " + uniqueName + " (Set: " + setId + ")");
    }

    public void registerPreset(String fullId, ItemStack item) {
        itemPresets.put(fullId, item.clone());
    }

    public ItemStack getItem(String fullId) {
        if (itemPresets.containsKey(fullId)) {
            return itemPresets.get(fullId).clone();
        }
        return null;
    }

    public Map<String, ItemStack> getAllPresets() {
        return itemPresets;
    }

    public String getCustomId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(KEY_ID, PersistentDataType.STRING);
    }

    public boolean isCustomItem(ItemStack item, String fullId) {
        String storedId = getCustomId(item);
        if (storedId == null) return false;
        String expectedUniqueName = fullId.replace(":", "_");
        return storedId.equals(expectedUniqueName);
    }

    public void writeModelFiles(File assetsDir) {
        for (CustomItemData item : registeredResources) {
            createModelFile(assetsDir, item);
        }
        createItemDefinitions(assetsDir);
    }

    public void writeEquipmentFiles(File assetsDir) {
        File equipDir = new File(assetsDir, "minecraft/equipment");
        equipDir.mkdirs();

        for (String setId : registeredArmorSets) {
            JsonObject root = new JsonObject();
            JsonObject layers = new JsonObject();

            // 텍스처 참조: minecraft:infinity_tower_fire_layer_1
            String textureLayer1 = "minecraft:" + setId + "_layer_1";
            String textureLayer2 = "minecraft:" + setId + "_layer_2";

            // Humanoid Layer
            JsonArray humanoid = new JsonArray();
            JsonObject hObj = new JsonObject();
            hObj.addProperty("texture", textureLayer1);
            humanoid.add(hObj);
            layers.add("humanoid", humanoid);

            // Leggings Layer
            JsonArray leggings = new JsonArray();
            JsonObject lObj = new JsonObject();
            lObj.addProperty("texture", textureLayer2);
            leggings.add(lObj);
            layers.add("humanoid_leggings", leggings);

            root.add("layers", layers);

            try (Writer writer = new OutputStreamWriter(new FileOutputStream(new File(equipDir, setId + ".json")), StandardCharsets.UTF_8)) {
                gson.toJson(root, writer);
            } catch (Exception e) { e.printStackTrace(); }
        }
    }

    private boolean isArmor(Material m) {
        String name = m.name();
        return name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE") || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS");
    }

    private void createModelFile(File assetsDir, CustomItemData item) {
        File modelDir = new File(assetsDir, "minecraft/models/item");
        modelDir.mkdirs();

        JsonObject model = new JsonObject();

        if (item.material == Material.NOTE_BLOCK) {
            model.addProperty("parent", "minecraft:block/" + item.uniqueName);
        } else {
            String parent = item.material.toString().contains("SWORD") ? "minecraft:item/handheld" : "minecraft:item/generated";
            model.addProperty("parent", parent);
            JsonObject textures = new JsonObject();
            textures.addProperty("layer0", "minecraft:item/" + item.uniqueName);
            model.add("textures", textures);
        }

        try (Writer writer = new OutputStreamWriter(new FileOutputStream(new File(modelDir, item.uniqueName + ".json")), StandardCharsets.UTF_8)) {
            gson.toJson(model, writer);
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void createItemDefinitions(File assetsDir) {
        Map<Material, List<CustomItemData>> grouped = new HashMap<>();
        for (CustomItemData item : registeredResources) {
            grouped.computeIfAbsent(item.material, k -> new ArrayList<>()).add(item);
        }

        File itemsDir = new File(assetsDir, "minecraft/items");
        itemsDir.mkdirs();

        for (Map.Entry<Material, List<CustomItemData>> entry : grouped.entrySet()) {
            Material mat = entry.getKey();
            List<CustomItemData> items = entry.getValue();

            JsonObject root = new JsonObject();
            JsonObject model = new JsonObject();

            model.addProperty("type", "minecraft:range_dispatch");
            model.addProperty("property", "minecraft:custom_model_data");

            JsonArray entries = new JsonArray();
            for (CustomItemData item : items) {
                JsonObject entryObj = new JsonObject();
                entryObj.addProperty("threshold", item.cmd);

                JsonObject modelObj = new JsonObject();
                modelObj.addProperty("type", "minecraft:model");
                modelObj.addProperty("model", "minecraft:item/" + item.uniqueName);

                entryObj.add("model", modelObj);
                entries.add(entryObj);
            }
            model.add("entries", entries);

            JsonObject fallback = new JsonObject();
            fallback.addProperty("type", "minecraft:model");

            if (mat == Material.NOTE_BLOCK) {
                fallback.addProperty("model", "minecraft:block/note_block");
            } else {
                fallback.addProperty("model", "minecraft:item/" + mat.getKey().getKey());
            }

            model.add("fallback", fallback);
            root.add("model", model);

            try (Writer writer = new OutputStreamWriter(new FileOutputStream(new File(itemsDir, mat.getKey().getKey() + ".json")), StandardCharsets.UTF_8)) {
                gson.toJson(root, writer);
            } catch (Exception e) { e.printStackTrace(); }
        }
    }
}