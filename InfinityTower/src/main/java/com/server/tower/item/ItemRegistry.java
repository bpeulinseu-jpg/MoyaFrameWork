package com.server.tower.item;

import com.server.core.api.CoreProvider;
import com.server.core.api.builder.ItemBuilder;
import com.server.tower.TowerPlugin;
import com.server.tower.item.enums.ArmorElement;
import com.server.tower.item.enums.ArmorType;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class ItemRegistry {

    private final TowerPlugin plugin;

    public ItemRegistry(TowerPlugin plugin) {
        this.plugin = plugin;
    }

    public void registerAll() {
        // 스크롤
        register("scroll_weapon", Material.PAPER, "§c무기 강화 주문서");
        register("scroll_armor", Material.PAPER, "§b방어구 강화 주문서");
        // 강화 관련 기타템
        register("protection_charm", Material.PAPER, "§e파괴 방지권");
        register("lucky_stone", Material.FLINT, "§a행운의 돌"); // 확률 +10%
        // 무기
        register("sword", Material.IRON_SWORD, "검");
        register("axe", Material.IRON_AXE, "도끼");
        register("greatsword", Material.IRON_SWORD, "대검");
        register("spear", Material.IRON_SWORD, "창");
        register("wand", Material.IRON_SHOVEL, "지팡이");
        register("greatstaff", Material.IRON_SHOVEL, "큰 지팡이");

        register("gem_str", Material.EMERALD, "§c힘의 보석");
        register("gem_int", Material.LAPIS_LAZULI, "§b지능의 보석");

        saveBaseTextures();

        for (ArmorElement element : ArmorElement.values()) {
            // 1. 세트 ID 결정
            String setId = (element == ArmorElement.NONE)
                    ? "infinity_tower_base"
                    : "infinity_tower_" + element.name().toLowerCase();

            // 2. 세트 텍스처 준비
            String layer1Name = (element == ArmorElement.NONE) ? "base_layer_1.png" : element.name().toLowerCase() + "_layer_1.png";
            String layer2Name = (element == ArmorElement.NONE) ? "base_layer_2.png" : element.name().toLowerCase() + "_layer_2.png";

            File layer1 = new File(plugin.getDataFolder(), layer1Name);
            File layer2 = new File(plugin.getDataFolder(), layer2Name);

            File baseLayer1 = new File(plugin.getDataFolder(), "base_layer_1.png");
            File baseLayer2 = new File(plugin.getDataFolder(), "base_layer_2.png");

            saveOrCopy(layer1Name, baseLayer1, layer1);
            saveOrCopy(layer2Name, baseLayer2, layer2);

            // 3. 세트 등록
            if (layer1.exists() || layer2.exists()) {
                com.server.core.CorePlugin.getItemManager().registerArmorSet(setId, layer1, layer2);
            }

            // 4. 아이템 등록
            for (ArmorType type : ArmorType.values()) {
                String itemId = (element == ArmorElement.NONE)
                        ? type.name().toLowerCase()
                        : element.name().toLowerCase() + "_" + type.name().toLowerCase();

                String iconName = itemId + ".png";
                String baseIconName = type.name().toLowerCase() + ".png";

                File iconFile = new File(plugin.getDataFolder(), iconName);
                File baseIconFile = new File(plugin.getDataFolder(), baseIconName);

                saveOrCopy(iconName, baseIconFile, iconFile);

                if (iconFile.exists()) {
                    // [수정됨] registerArmor -> registerArmorItem 호출
                    com.server.core.CorePlugin.getItemManager().registerArmorItem(
                            plugin, itemId, type.getMaterial(), iconFile, type.getName(), setId
                    );
                }
            }
        }

        plugin.getLogger().info("⚔️ 아이템 등록 완료.");
    }

    private void saveOrCopy(String resourceName, File baseFile, File targetFile) {
        try {
            plugin.saveResource(resourceName, true);
        } catch (IllegalArgumentException e) {
            if (!targetFile.exists() && baseFile.exists()) {
                try {
                    Files.copy(baseFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                } catch (Exception ignored) {}
            }
        }
    }

    public ItemStack createGem(String type, int value) {
        String id = "infinity_tower:gem_" + type;
        String name = (type.equals("str") ? "§c힘" : "§b지능") + "의 보석 (+" + value + ")";
        return ItemBuilder.from(id)
                .name(name)
                .lore("§7무기의 빈 소켓에 장착하여", "§7능력치를 올릴 수 있습니다.")
                .setData("is_gem", 1)
                .setData("gem_type", type)
                .setData("gem_value", value)
                .build();
    }

    private void register(String id, Material material, String name) {

        String pngName = id + ".png";
        String jsonName = id + ".json";

        // [추가된 부분 1] JAR 안에 있는 리소스를 밖으로 꺼내기 (덮어쓰기 모드)
        // 텍스처 추출 시도
        try {
            plugin.saveResource(pngName, true); // true: 기존 파일이 있어도 덮어씀 (업데이트 반영)
        } catch (IllegalArgumentException e) {
            // JAR 안에 파일이 없으면 무시 (개발자가 안 넣은 경우)
        }

        // 모델 JSON 추출 시도
        try {
            plugin.saveResource(jsonName, true);
        } catch (IllegalArgumentException e) {
            // JSON이 없으면 2D 아이템이라는 뜻
        }

        // 1. 파일 객체 생성
        File textureFile = new File(plugin.getDataFolder(), id + ".png");
        File modelFile = new File(plugin.getDataFolder(), id + ".json");

        // 2. 리소스가 아예 없으면 경고 후 스킵 (또는 기본 아이템 등록)
        if (!textureFile.exists()) {
            plugin.getLogger().warning("⚠️ 리소스 없음: " + id + ".png");
            return;
        }

        // 3. JSON 모델 파일이 있으면 -> 3D 모델로 등록!
        if (modelFile.exists()) {
            // CoreProvider에 새로 만든 메서드 호출
            CoreProvider.registerModelItem(plugin, id, material, textureFile, modelFile, name);
            plugin.getLogger().info("✅ 3D 모델 등록: " + id);
        }
        // 4. JSON이 없으면 -> 기존 방식(2D)대로 등록
        else {
            CoreProvider.registerItem(plugin, id, material, textureFile, name);
        }
    }

    private void saveBaseTextures() {
        saveResourceForce("base_layer_1.png");
        saveResourceForce("base_layer_2.png");
        saveResourceForce("helmet.png");
        saveResourceForce("chestplate.png");
        saveResourceForce("leggings.png");
        saveResourceForce("boots.png");
    }

    private void saveResourceForce(String name) {
        try { plugin.saveResource(name, true); } catch (Exception ignored) {}
    }
}