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
        String fileName = id + ".png";
        File file = new File(plugin.getDataFolder(), fileName);

        // 1. JAR에 파일이 있는지 확인하고 있으면 저장 (덮어쓰기)
        try {
            plugin.saveResource(fileName, true);
        } catch (IllegalArgumentException e) {
            // JAR에 파일이 없으면 아무것도 안 함
        }

        // 2. 파일이 존재하는 경우에만 Core에 등록
        if (file.exists()) {
            CoreProvider.registerItem(plugin, id, material, file, name);
        } else {
            // [수정] 파일이 없으면 null을 넘기지 않고 경고만 띄움
            plugin.getLogger().warning("⚠️ 리소스 없음: " + fileName + " (아이템 등록 건너뜀)");
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