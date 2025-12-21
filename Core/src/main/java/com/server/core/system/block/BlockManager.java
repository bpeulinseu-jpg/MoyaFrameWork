package com.server.core.system.block;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.server.core.CorePlugin;
import com.server.core.api.CoreAddon;
import org.bukkit.Instrument;
import org.bukkit.Material;
import org.bukkit.Note;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.NoteBlock;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BlockManager {

    private final CorePlugin plugin;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final Map<String, CustomBlockData> idMap = new HashMap<>();
    private final Map<String, String> stateMap = new HashMap<>();
    private final List<CustomBlockData> registeredBlocks = new ArrayList<>();

    private int currentInstrumentIndex = 0;
    private int currentNote = 1;

    // [중요] record가 아니라 class로 변경됨 (필드 직접 접근 허용)
    public static class CustomBlockData {
        public final String namespace;
        public final String id;
        public final String uniqueName;
        public final BlockData blockData;
        public final String texturePath;
        public final float hardness;
        public final int minToolLevel;

        // 추가된 속성들 (기본값 설정)
        public Sound placeSound = Sound.BLOCK_STONE_PLACE;
        public Sound breakSound = Sound.BLOCK_STONE_BREAK;
        public List<DropItem> drops = new ArrayList<>();
        public boolean requireSilkTouch = false;
        public ItemStack silkTouchDrop = null;
        public int expToDrop = 0;

        public CustomBlockData(String namespace, String id, String uniqueName, BlockData blockData, String texturePath, float hardness, int minToolLevel) {
            this.namespace = namespace;
            this.id = id;
            this.uniqueName = uniqueName;
            this.blockData = blockData;
            this.texturePath = texturePath;
            this.hardness = hardness;
            this.minToolLevel = minToolLevel;

        }


        // 설정 체이닝 메소드
        public CustomBlockData setSounds(Sound place, Sound breakSound) {
            this.placeSound = place;
            this.breakSound = breakSound;
            return this;
        }

        public CustomBlockData addDrop(ItemStack item, double chance, int min, int max, boolean fortune) {
            this.drops.add(new DropItem(item, chance, min, max, fortune));
            return this;
        }

        public CustomBlockData setSilkTouch(boolean required, ItemStack drop) {
            this.requireSilkTouch = required;
            this.silkTouchDrop = drop;
            return this;
        }

        public CustomBlockData setExp(int amount) {
            this.expToDrop = amount;
            return this;
        }
    }

    public BlockManager(CorePlugin plugin) {
        this.plugin = plugin;
    }

    // 반환타입 변경: void -> CustomBlockData
    public CustomBlockData registerBlock(CoreAddon addon, String id, File textureFile, float hardness, int toolLevel) {
        String safeId = id.toLowerCase();
        String safeNamespace = addon.getNamespace().toLowerCase();
        String uniqueName = safeNamespace + "_" + safeId;
        String fullId = safeNamespace + ":" + safeId;

        NoteBlock noteBlock = allocateNextState();
        if (noteBlock == null) {
            plugin.getLogger().severe("❌ 커스텀 블록 한도 초과!");
            return null;
        }

        String textureRelPath = "block/" + uniqueName + ".png";
        CorePlugin.getResourcePackManager().registerTexture("minecraft", textureRelPath, textureFile);

        CorePlugin.getItemManager().registerBlockItem(addon, id, Material.NOTE_BLOCK, "§f" + id);

        CustomBlockData data = new CustomBlockData(
                safeNamespace,
                safeId,
                uniqueName,
                noteBlock,
                "block/" + uniqueName,
                hardness,
                toolLevel
        );

        idMap.put(fullId, data);
        stateMap.put(noteBlock.getAsString(), fullId);

        NoteBlock powered = (NoteBlock) noteBlock.clone();
        powered.setPowered(true);
        stateMap.put(powered.getAsString(), fullId);

        registeredBlocks.add(data);

        plugin.getLogger().info("🧱 블록 등록: " + fullId);
        return data;
    }

    private NoteBlock allocateNextState() {
        Instrument[] instruments = Instrument.values();
        if (currentInstrumentIndex >= instruments.length) return null;

        NoteBlock noteBlock = (NoteBlock) Material.NOTE_BLOCK.createBlockData();
        noteBlock.setInstrument(instruments[currentInstrumentIndex]);
        noteBlock.setNote(new Note(currentNote));
        noteBlock.setPowered(false);

        currentNote++;
        if (currentNote > 24) {
            currentNote = 1;
            currentInstrumentIndex++;
        }
        return noteBlock;
    }

    public void writeBlockResources(File assetsDir) {
        createBlockModels(assetsDir);
        createBlockState(assetsDir);
    }

    private void createBlockModels(File assetsDir) {
        File modelDir = new File(assetsDir, "minecraft/models/block");
        modelDir.mkdirs();
        for (CustomBlockData block : registeredBlocks) {
            JsonObject model = new JsonObject();
            model.addProperty("parent", "minecraft:block/cube_all");
            JsonObject textures = new JsonObject();
            textures.addProperty("all", "minecraft:block/" + block.uniqueName);
            model.add("textures", textures);
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(new File(modelDir, block.uniqueName + ".json")), StandardCharsets.UTF_8)) {
                gson.toJson(model, writer);
            } catch (Exception e) { e.printStackTrace(); }
        }
    }

    private void createBlockState(File assetsDir) {
        File statesDir = new File(assetsDir, "minecraft/blockstates");
        statesDir.mkdirs();
        JsonObject root = new JsonObject();
        JsonObject variants = new JsonObject();

        // 이걸 안 하면 기본 노트블록이 깨져 보임
        JsonObject vanillaModel = new JsonObject();
        vanillaModel.addProperty("model", "minecraft:block/note_block");

        // Harp, Note 0, Powered false/true 모두 바닐라 모델로 설정
        variants.add("instrument=harp,note=0,powered=false", vanillaModel);
        variants.add("instrument=harp,note=0,powered=true", vanillaModel);

        for (CustomBlockData block : registeredBlocks) {
            String fullString = block.blockData.getAsString();
            String properties = fullString.substring(fullString.indexOf('[') + 1, fullString.indexOf(']'));
            JsonObject model = new JsonObject();
            model.addProperty("model", "minecraft:block/" + block.uniqueName);
            variants.add(properties, model);
            String poweredProperties = properties.replace("powered=false", "powered=true");
            variants.add(poweredProperties, model);
        }
        root.add("variants", variants);
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(new File(statesDir, "note_block.json")), StandardCharsets.UTF_8)) {
            gson.toJson(root, writer);
        } catch (Exception e) { e.printStackTrace(); }
    }

    public CustomBlockData getBlock(String fullId) { return idMap.get(fullId); }

    public CustomBlockData getBlock(Block block) {
        if (block.getType() != Material.NOTE_BLOCK) return null;
        String fullId = stateMap.get(block.getBlockData().getAsString());
        return (fullId != null) ? idMap.get(fullId) : null;
    }

    public List<CustomBlockData> getAllBlocks() { return new ArrayList<>(registeredBlocks); }
}