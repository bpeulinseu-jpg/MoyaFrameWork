package com.server.core.system.resource;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.server.core.CorePlugin;
import com.server.core.api.CoreAddon;
import org.bukkit.Bukkit;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ResourcePackManager {

    private final CorePlugin plugin;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final List<TextureRegistration> textureQueue = new ArrayList<>();
    private final List<ModelRegistration> modelQueue = new ArrayList<>(); // [추가] 모델 큐
    private final List<SoundFileRegistration> soundFileQueue = new ArrayList<>();
    private final List<File> soundConfigQueue = new ArrayList<>();
    private final List<String> registeredSoundKeys = new ArrayList<>();

    public record TextureRegistration(String path, File sourceFile) {}
    // [추가] 모델 등록 레코드 (텍스처 경로 자동 매핑을 위해 texturePath도 받을 수 있음)
    public record ModelRegistration(String namespace, String modelPath, File jsonFile, String texturePath) {}
    public record SoundFileRegistration(String namespace, String path, File sourceFile) {}

    public ResourcePackManager(CorePlugin plugin) {
        this.plugin = plugin;
    }

    // --- 텍스처 등록 ---
    public void registerTexture(CoreAddon addon, String path, File file) {
        registerTexture(addon.getNamespace(), path, file);
    }

    public void registerTexture(String namespace, String path, File file) {
        if (!file.exists()) return;
        // 텍스처는 보통 assets/minecraft/textures/... 로 통합 관리하거나
        // assets/<namespace>/textures/... 로 관리함.
        // 여기서는 편의상 입력받은 path 그대로 사용
        String safePath = path.replace("\\", "/");
        textureQueue.add(new TextureRegistration(safePath, file));
    }

    // --- [신규 기능] 모델(JSON) 등록 ---
    /**
     * @param addon 애드온 인스턴스
     * @param modelPath 생성될 모델의 경로 (예: item/sword)
     * @param jsonFile Blockbench에서 나온 JSON 파일
     * @param texturePath 이 모델이 사용할 텍스처의 경로 (예: item/sword.png)
     */
    public void registerModel(CoreAddon addon, String modelPath, File jsonFile, String texturePath) {
        if (!jsonFile.exists()) return;
        String safeModelPath = modelPath.replace("\\", "/");
        if (safeModelPath.endsWith(".json")) safeModelPath = safeModelPath.replace(".json", "");

        String safeTexturePath = texturePath.replace("\\", "/");
        if (safeTexturePath.endsWith(".png")) safeTexturePath = safeTexturePath.replace(".png", "");

        modelQueue.add(new ModelRegistration(addon.getNamespace(), safeModelPath, jsonFile, safeTexturePath));
    }

    // ... (기존 registerArmorSetTexture, registerSound 등 생략) ...
    public void registerArmorSetTexture(String setId, File layer1, File layer2) {
        if (layer1.exists()) {
            String path1 = "entity/equipment/humanoid/" + setId + "_layer_1.png";
            textureQueue.add(new TextureRegistration(path1, layer1));
        }
        if (layer2.exists()) {
            String path2 = "entity/equipment/humanoid_leggings/" + setId + "_layer_2.png";
            textureQueue.add(new TextureRegistration(path2, layer2));
        }
    }

    public void registerSoundFile(CoreAddon addon, String path, File file) {
        if (!file.exists()) return;
        soundFileQueue.add(new SoundFileRegistration(addon.getNamespace(), path, file));
    }

    public void registerSoundConfig(CoreAddon addon, File jsonFile) {
        if (!jsonFile.exists()) return;
        soundConfigQueue.add(jsonFile);
    }

    // --- 리소스팩 생성 ---
    public void generatePack() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            plugin.getLogger().info("📦 리소스팩 생성을 시작합니다...");
            File dataFolder = plugin.getDataFolder();
            File buildDir = new File(dataFolder, "build_cache");
            File outputZip = new File(dataFolder, "resourcepack.zip");

            try {
                if (buildDir.exists()) deleteDirectory(buildDir.toPath());
                buildDir.mkdirs();
                File assetsDir = new File(buildDir, "assets"); // assets 변수 미리 정의

                // 1. 텍스처 복사 (기존 유지)
                for (TextureRegistration reg : textureQueue) {
                    File dest = new File(buildDir, "assets/minecraft/textures/" + reg.path());
                    dest.getParentFile().mkdirs();
                    Files.copy(reg.sourceFile().toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }

                // 2. 사운드 복사 (기존 유지)
                for (SoundFileRegistration reg : soundFileQueue) {
                    File dest = new File(buildDir, "assets/" + reg.namespace() + "/sounds/" + reg.path());
                    dest.getParentFile().mkdirs();
                    Files.copy(reg.sourceFile().toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }

                // [중요] 3. Core의 기본 파일 생성 (순서 변경됨: 먼저 실행!)
                // ItemManager가 여기서 'infinity_tower_sword.json'을 기본 형태(2D)로 생성함.
                if (CorePlugin.getItemManager() != null) {
                    CorePlugin.getItemManager().writeModelFiles(assetsDir);
                    CorePlugin.getItemManager().writeEquipmentFiles(assetsDir);
                }
                if (CorePlugin.getGlyphManager() != null) CorePlugin.getGlyphManager().writeFontFile(assetsDir);
                if (CorePlugin.getBlockManager() != null) CorePlugin.getBlockManager().writeBlockResources(assetsDir);


                // [핵심 수정] 4. 커스텀 모델 JSON 처리 및 병합 (덮어쓰기)
                // ItemManager가 만든 파일을 우리가 만든 Blockbench JSON으로 교체합니다.
                for (ModelRegistration reg : modelQueue) {
                    try {
                        // 원본 JSON 읽기
                        JsonObject originalJson = gson.fromJson(new FileReader(reg.jsonFile()), JsonObject.class);
                        JsonObject finalJson = new JsonObject();

                        // A. Parent 설정
                        if (originalJson.has("parent")) {
                            finalJson.add("parent", originalJson.get("parent"));
                        } else {
                            finalJson.addProperty("parent", "item/handheld");
                        }

                        // B. Textures 경로 자동 수정
                        JsonObject textures = new JsonObject();

                        // ID 추출 (예: item/sword -> sword)
                        String idOnly = reg.modelPath();
                        if (idOnly.contains("/")) idOnly = idOnly.substring(idOnly.lastIndexOf("/") + 1);

                        // [핵심 수정] minecraft:item/<namespace>_<id> 형식으로 변경
                        // 예: minecraft:item/infinity_tower_sword
                        String newTextureVal = "minecraft:item/" + reg.namespace() + "_" + idOnly;

                        if (originalJson.has("textures")) {
                            JsonObject oldTextures = originalJson.getAsJsonObject("textures");
                            for (String key : oldTextures.keySet()) {
                                // 기존 키(layer0 등)는 유지하고 값만 우리가 만든 경로로 덮어쓰기
                                textures.addProperty(key, newTextureVal);
                            }
                        } else {
                            // 텍스처 필드가 없으면 layer0으로 강제 생성
                            textures.addProperty("layer0", newTextureVal);
                        }
                        finalJson.add("textures", textures);

                        // C. Display & Elements 복사 (Blockbench 핵심 데이터)
                        if (originalJson.has("display")) finalJson.add("display", originalJson.get("display"));
                        if (originalJson.has("elements")) finalJson.add("elements", originalJson.get("elements"));

                        // [경로 수정] assets/minecraft/models/item/<namespace>_<id>.json
                        // 예: item/sword -> sword 추출
                        if (idOnly.contains("/")) idOnly = idOnly.substring(idOnly.lastIndexOf("/") + 1);

                        // 최종 파일명: infinity_tower_sword.json
                        String fileName = reg.namespace() + "_" + idOnly + ".json";

                        File dest = new File(buildDir, "assets/minecraft/models/item/" + fileName);
                        dest.getParentFile().mkdirs();

                        // 파일 쓰기 (기존 파일 덮어쓰기)
                        try (Writer writer = new FileWriter(dest)) {
                            gson.toJson(finalJson, writer);
                        }

                        // plugin.getLogger().info("모델 병합 완료: " + fileName);

                    } catch (Exception e) {
                        plugin.getLogger().warning("모델 생성 실패: " + reg.modelPath());
                        e.printStackTrace();
                    }
                }

                mergeSoundConfigs(assetsDir);
                createPackMeta(buildDir);

                if (outputZip.exists()) outputZip.delete();
                zipDirectory(buildDir, outputZip);

                plugin.getLogger().info("✅ 리소스팩 생성 완료!");

                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (CorePlugin.getWebServerManager() != null) CorePlugin.getWebServerManager().start();
                });

            } catch (Exception e) { e.printStackTrace(); }
        });
    }

    // ... (mergeSoundConfigs, createPackMeta, deleteDirectory, zipDirectory 등 기존 코드 유지) ...
    private void mergeSoundConfigs(File assetsDir) {
        if (soundConfigQueue.isEmpty()) return;
        JsonObject mergedSounds = new JsonObject();
        registeredSoundKeys.clear();

        for (File configFile : soundConfigQueue) {
            try (Reader reader = new FileReader(configFile)) {
                JsonObject current = gson.fromJson(reader, JsonObject.class);
                for (Map.Entry<String, JsonElement> entry : current.entrySet()) {
                    mergedSounds.add(entry.getKey(), entry.getValue());
                    registeredSoundKeys.add(entry.getKey());
                }
            } catch (Exception e) {}
        }
        File output = new File(assetsDir, "minecraft/sounds.json");
        output.getParentFile().mkdirs();
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(output), StandardCharsets.UTF_8)) {
            gson.toJson(mergedSounds, writer);
        } catch (IOException e) { e.printStackTrace(); }
    }

    private void createPackMeta(File buildDir) throws IOException {
        JsonObject pack = new JsonObject();
        pack.addProperty("pack_format", 46);
        pack.addProperty("description", "CoreFramework Pack");
        JsonObject root = new JsonObject();
        root.add("pack", pack);
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(new File(buildDir, "pack.mcmeta")), StandardCharsets.UTF_8)) {
            gson.toJson(root, writer);
        }
    }

    private void deleteDirectory(Path path) throws IOException {
        if (!Files.exists(path)) return;
        try (java.util.stream.Stream<Path> walk = Files.walk(path)) {
            walk.sorted((a, b) -> b.compareTo(a)).map(Path::toFile).forEach(File::delete);
        }
    }

    private void zipDirectory(File sourceFolder, File zipFile) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(zipFile);
             java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(fos)) {
            Path sourcePath = sourceFolder.toPath();
            try (java.util.stream.Stream<Path> walk = Files.walk(sourcePath)) {
                walk.filter(path -> !Files.isDirectory(path)).forEach(path -> {
                    ZipEntry zipEntry = new ZipEntry(sourcePath.relativize(path).toString().replace("\\", "/"));
                    try {
                        zos.putNextEntry(zipEntry);
                        Files.copy(path, zos);
                        zos.closeEntry();
                    } catch (IOException e) { e.printStackTrace(); }
                });
            }
        }
    }

    public List<String> getAllSoundKeys() {
        return new ArrayList<>(registeredSoundKeys);
    }
}