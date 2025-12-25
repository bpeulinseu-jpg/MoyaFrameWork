package com.server.core.system.particle;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.server.core.CorePlugin;
import com.server.core.api.CoreAddon;

import java.io.File;
import java.io.FileWriter;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;

public class ParticleTextureManager {

    private final CorePlugin plugin;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final Map<String, Integer> textureMap = new HashMap<>();
    private final Map<String, String> namespaceMap = new HashMap<>();

    private int nextId = 20001;

    public ParticleTextureManager(CorePlugin plugin) {
        this.plugin = plugin;
    }

    public int registerSequence(CoreAddon addon, String idBase, int frameCount) {
        int startCmd = nextId;
        for (int i = 0; i < frameCount; i++) {
            String id = idBase + "_" + i;
            File file = new File(addon.getPlugin().getDataFolder(), "particles/slash/" + id + ".png");
            register(addon, id, file);
        }
        return startCmd;
    }

    public void register(CoreAddon addon, String id, File pngFile) {
        if (!pngFile.exists()) {
            plugin.getLogger().warning("⚠️ 텍스처 파일 없음: " + pngFile.getAbsolutePath());
            return;
        }

        int cmd = nextId++;
        textureMap.put(id, cmd);
        namespaceMap.put(id, addon.getNamespace());

        // 1. 텍스처 저장: assets/minecraft/textures/item/<id>.png
        String texturePath = "item/" + id + ".png";
        CorePlugin.getResourcePackManager().registerTexture("minecraft", texturePath, pngFile);
    }

    public int getModelData(String id) {
        return textureMap.getOrDefault(id, -1);
    }

    public void generateModels(File assetsDir) {
        // 2. 개별 모델 저장: assets/minecraft/models/item/<id>.json
        File modelsDir = new File(assetsDir, "minecraft/models/item");
        modelsDir.mkdirs();

        for (String id : textureMap.keySet()) {
            JsonObject model = new JsonObject();

            // [수정 1] 부모 모델에 'minecraft:' 네임스페이스 명시 (필수)
            model.addProperty("parent", "minecraft:item/generated");

            JsonObject textures = new JsonObject();
            // [수정 2] 텍스처 경로 명시
            textures.addProperty("layer0", "minecraft:item/" + id);
            model.add("textures", textures);

            try (Writer writer = new FileWriter(new File(modelsDir, id + ".json"))) {
                gson.toJson(model, writer);
            } catch (Exception e) { e.printStackTrace(); }
        }

        generateSnowballItemDefinition(assetsDir);
    }

    private void generateSnowballItemDefinition(File assetsDir) {
        // 1. items 폴더의 snowball.json 생성
        File itemsDir = new File(assetsDir, "minecraft/items");
        itemsDir.mkdirs();
        File itemFile = new File(itemsDir, "snowball.json");

        JsonObject root = new JsonObject();
        JsonObject model = new JsonObject();

        // [수정] type을 range_dispatch로 변경 (숫자 데이터용 표준)
        model.addProperty("type", "minecraft:range_dispatch");
        model.addProperty("property", "minecraft:custom_model_data");

        // [중요] range_dispatch는 threshold 순서가 중요하므로, CMD 기준으로 오름차순 정렬
        java.util.List<Map.Entry<String, Integer>> sortedEntries = new java.util.ArrayList<>(textureMap.entrySet());
        sortedEntries.sort(java.util.Map.Entry.comparingByValue());

        JsonArray entries = new JsonArray();

        for (Map.Entry<String, Integer> entry : sortedEntries) {
            String id = entry.getKey();
            int cmd = entry.getValue();

            JsonObject entryObj = new JsonObject();
            // [수정] when -> threshold (이 값 이상일 때 적용됨)
            entryObj.addProperty("threshold", cmd);

            JsonObject modelRef = new JsonObject();
            modelRef.addProperty("type", "minecraft:model");
            // 모델 경로: minecraft:item/<id>
            modelRef.addProperty("model", "minecraft:item/" + id);

            entryObj.add("model", modelRef);
            entries.add(entryObj);
        }

        // [수정] cases -> entries
        model.add("entries", entries);

        // Fallback (기본값: 눈덩이)
        JsonObject fallback = new JsonObject();
        fallback.addProperty("type", "minecraft:model");
        fallback.addProperty("model", "minecraft:item/snowball");
        model.add("fallback", fallback);

        root.add("model", model);

        try (Writer writer = new FileWriter(itemFile)) {
            gson.toJson(root, writer);
            plugin.getLogger().info("📝 [1.21.4] items/snowball.json (range_dispatch) 생성 완료");
        } catch (Exception e) { e.printStackTrace(); }
    }
}