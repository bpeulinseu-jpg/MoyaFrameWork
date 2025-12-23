package com.server.core.system.glyph;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.server.core.CorePlugin;
import com.server.core.api.CoreAddon;
import net.kyori.adventure.text.Component;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GlyphManager {

    private final CorePlugin plugin;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private int nextCode = 0xE001;
    private final Map<String, Character> tagMap = new HashMap<>();
    private final List<JsonObject> providers = new ArrayList<>();
    private final Pattern TAG_PATTERN = Pattern.compile(":([a-z0-9_]+):([a-z0-9_]+):");

    public GlyphManager(CorePlugin plugin) {
        this.plugin = plugin;
        generateSpaceCharacters(); // 공백 문자 생성
    }

    public void registerGlyph(CoreAddon addon, String key, File file, int ascent, int height) {
        if (file == null || !file.exists() || file.length() == 0) {
            plugin.getLogger().warning("❌ 글리프 등록 실패 (파일 없음/손상): " + key);
            return; // 파일이 없으면 JSON 등록도 중단해야 함!
        }
        String fullKey = addon.getNamespace() + ":" + key;

        char unicode = (char) nextCode++;
        tagMap.put(fullKey, unicode);

        // [핵심] 파일명 충돌 방지: test_addon_heart.png
        String uniqueFileName = addon.getNamespace().toLowerCase() + "_" + key.toLowerCase() + ".png";

        // 1. 텍스처 등록 (ResourcePackManager에게 위임)
        // 저장 경로: assets/minecraft/textures/font/test_addon_heart.png
        String texturePath = "font/" + uniqueFileName;
        CorePlugin.getResourcePackManager().registerTexture("minecraft", texturePath, file);

        // 2. JSON Provider 데이터 생성
        JsonObject provider = new JsonObject();
        provider.addProperty("type", "bitmap");
        // 참조 경로: minecraft:font/test_addon_heart.png
        provider.addProperty("file", "minecraft:font/" + uniqueFileName);
        provider.addProperty("ascent", ascent);
        provider.addProperty("height", height);
        JsonArray chars = new JsonArray();
        chars.add(String.valueOf(unicode));
        provider.add("chars", chars);

        providers.add(provider);

        plugin.getLogger().info("🔣 글리프 등록: " + fullKey + " -> " + String.format("\\u%04X", (int)unicode));
    }

    private void generateSpaceCharacters() {
        JsonObject provider = new JsonObject();
        provider.addProperty("type", "space");
        JsonObject advances = new JsonObject();

        int startCode = 0xF801;
        // 음수 여백 (-1 ~ -64)
        for (int i = 1; i <= 64; i++) {
            int code = startCode + (i - 1);
            String key = String.valueOf((char) code);
            advances.addProperty(key, -i);
        }
        // 양수 여백 (+1 ~ +64) - 필요시 사용
        int positiveStart = 0xF841;
        for (int i = 1; i <= 64; i++) {
            int code = positiveStart + (i - 1);
            String key = String.valueOf((char) code);
            advances.addProperty(key, i);
        }

        provider.add("advances", advances);
        providers.add(0, provider); // 맨 앞에 추가
    }

    // [핵심] default.json 파일 생성
    public void writeFontFile(File assetsDir) {
        // 경로: assets/minecraft/font/default.json
        File fontDir = new File(assetsDir, "minecraft/font");
        fontDir.mkdirs();

        JsonObject root = new JsonObject();
        JsonArray providersArray = new JsonArray();
        providers.forEach(providersArray::add);
        root.add("providers", providersArray);

        File outputFile = new File(fontDir, "default.json");
        plugin.getLogger().info("📝 폰트 파일 생성 중: " + outputFile.getAbsolutePath());

        try (Writer writer = new OutputStreamWriter(new FileOutputStream(outputFile), StandardCharsets.UTF_8)) {
            gson.toJson(root, writer);
        } catch (Exception e) {
            plugin.getLogger().severe("❌ 폰트 파일 생성 실패!");
            e.printStackTrace();
        }
    }

    public String getTag(String fullKey) {
        if (tagMap.containsKey(fullKey)) {
            return String.valueOf(tagMap.get(fullKey));
        }
        return "";
    }

    public Component getSpaceComponent(int pixels) {
        if (pixels == 0) return Component.empty();
        StringBuilder sb = new StringBuilder();
        int remaining = Math.abs(pixels);
        int startCode = (pixels < 0) ? 0xF801 : 0xF841;

        while (remaining > 0) {
            int move = Math.min(remaining, 64);
            sb.append((char) (startCode + (move - 1)));
            remaining -= move;
        }
        return Component.text(sb.toString());
    }

    // 등록된 모든 태그와 유니코드 맵 반환 (브라우저용)
    public Map<String, Character> getAllGlyphs() {
        return new HashMap<>(tagMap); // 안전하게 복사본 반환
    }

    // 텍스트 내의 모든 태그를 찾아 유니코드로 변환
    public String parseText(String text) {
        if (text == null || text.isEmpty()) return "";

        Matcher matcher = TAG_PATTERN.matcher(text);
        StringBuilder sb = new StringBuilder();

        while (matcher.find()) {
            // 그룹 1: namespace, 그룹 2: key
            String fullKey = matcher.group(1) + ":" + matcher.group(2);

            if (tagMap.containsKey(fullKey)) {
                // 이미지는 흰색(§f)이어야 원본 색이 나오므로 색상 코드 추가
                // 변환 후 원래 색상으로 돌아가려면 뒤에 §r 등을 붙여야 할 수도 있음 (여기선 단순 변환)
                String replacement = "\u00A7f" + tagMap.get(fullKey);
                matcher.appendReplacement(sb, replacement);
            } else {
                // 등록되지 않은 태그는 그대로 둠
                matcher.appendReplacement(sb, ":" + fullKey + ":");
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}