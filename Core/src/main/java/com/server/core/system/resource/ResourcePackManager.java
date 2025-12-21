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
    private final List<SoundFileRegistration> soundFileQueue = new ArrayList<>();
    private final List<File> soundConfigQueue = new ArrayList<>();
    private final List<String> registeredSoundKeys = new ArrayList<>();

    public record TextureRegistration(String path, File sourceFile) {}
    public record SoundFileRegistration(String namespace, String path, File sourceFile) {}

    public ResourcePackManager(CorePlugin plugin) {
        this.plugin = plugin;
    }

    public void registerTexture(CoreAddon addon, String path, File file) {
        registerTexture(addon.getNamespace(), path, file);
    }

    public void registerTexture(String namespace, String path, File file) {
        if (!file.exists()) return;
        String safePath = path.replace("\\", "/");
        textureQueue.add(new TextureRegistration(safePath, file));
    }

    // [중요] 갑옷 세트 텍스처 등록 (humanoid 폴더 사용)
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

    public void generatePack() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            plugin.getLogger().info("📦 리소스팩 생성을 시작합니다...");
            File dataFolder = plugin.getDataFolder();
            File buildDir = new File(dataFolder, "build_cache");
            File outputZip = new File(dataFolder, "resourcepack.zip");

            try {
                if (buildDir.exists()) deleteDirectory(buildDir.toPath());
                buildDir.mkdirs();

                for (TextureRegistration reg : textureQueue) {
                    File dest = new File(buildDir, "assets/minecraft/textures/" + reg.path());
                    dest.getParentFile().mkdirs();
                    Files.copy(reg.sourceFile().toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }

                for (SoundFileRegistration reg : soundFileQueue) {
                    File dest = new File(buildDir, "assets/" + reg.namespace() + "/sounds/" + reg.path());
                    dest.getParentFile().mkdirs();
                    Files.copy(reg.sourceFile().toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }

                File assetsDir = new File(buildDir, "assets");

                if (CorePlugin.getItemManager() != null) {
                    CorePlugin.getItemManager().writeModelFiles(assetsDir);
                    CorePlugin.getItemManager().writeEquipmentFiles(assetsDir);
                }
                if (CorePlugin.getGlyphManager() != null) CorePlugin.getGlyphManager().writeFontFile(assetsDir);
                if (CorePlugin.getBlockManager() != null) CorePlugin.getBlockManager().writeBlockResources(assetsDir);

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