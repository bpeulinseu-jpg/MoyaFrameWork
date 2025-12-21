package com.server.core.api.builder;

import com.server.core.CorePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ItemBuilder {

    private final ItemStack item;
    private ItemMeta meta;

    // [변수] 갑옷 모델 ID 저장
    private String armorModelId = null;

    public ItemBuilder(Material material) {
        this.item = new ItemStack(material);
        this.meta = item.getItemMeta();
    }

    public ItemBuilder(ItemStack item) {
        this.item = item.clone();
        this.meta = this.item.getItemMeta();
    }

    public static ItemBuilder from(String customId) {
        ItemStack customItem = CorePlugin.getItemManager().getItem(customId);
        if (customItem == null) {
            ItemStack error = new ItemStack(Material.BARRIER);
            ItemMeta m = error.getItemMeta();
            m.displayName(Component.text("§cError: " + customId));
            error.setItemMeta(m);
            return new ItemBuilder(error);
        }
        return new ItemBuilder(customItem);
    }

    public ItemBuilder name(String name) {
        this.meta.displayName(Component.text(name).decoration(TextDecoration.ITALIC, false));
        return this;
    }

    public ItemBuilder lore(String... lines) {
        List<Component> lore = new ArrayList<>();
        for (String line : lines) {
            lore.add(Component.text(line).decoration(TextDecoration.ITALIC, false));
        }
        this.meta.lore(lore);
        return this;
    }

    public ItemBuilder addLore(String line) {
        List<Component> lore = this.meta.hasLore() ? this.meta.lore() : new ArrayList<>();
        if (lore == null) lore = new ArrayList<>();
        lore.add(Component.text(line).decoration(TextDecoration.ITALIC, false));
        this.meta.lore(lore);
        return this;
    }

    public ItemBuilder amount(int amount) {
        this.item.setAmount(amount);
        return this;
    }

    public ItemBuilder glow() {
        this.meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        this.meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        return this;
    }

    public ItemBuilder model(int cmd) {
        this.meta.setCustomModelData(cmd);
        return this;
    }

    // [설정] 변수에 값 저장 (실제 적용은 build()에서)
    public ItemBuilder setArmorModel(String modelId) {
        this.armorModelId = modelId;
        return this;
    }

    public ItemBuilder addAttribute(Attribute attribute, double amount, EquipmentSlot slot) {
        AttributeModifier modifier = new AttributeModifier(
                new NamespacedKey(CorePlugin.getInstance(), "custom_modifier_" + UUID.randomUUID().toString().substring(0, 8)),
                amount,
                AttributeModifier.Operation.ADD_NUMBER,
                getSlotGroup(slot)
        );
        this.meta.addAttributeModifier(attribute, modifier);
        return this;
    }

    private EquipmentSlotGroup getSlotGroup(EquipmentSlot slot) {
        switch (slot) {
            case HAND: return EquipmentSlotGroup.MAINHAND;
            case OFF_HAND: return EquipmentSlotGroup.OFFHAND;
            case FEET: return EquipmentSlotGroup.FEET;
            case LEGS: return EquipmentSlotGroup.LEGS;
            case CHEST: return EquipmentSlotGroup.CHEST;
            case HEAD: return EquipmentSlotGroup.HEAD;
            case BODY: return EquipmentSlotGroup.ARMOR;
            default: return EquipmentSlotGroup.ANY;
        }
    }

    public ItemBuilder setData(String key, String value) {
        NamespacedKey nsKey = new NamespacedKey(CorePlugin.getInstance(), key);
        this.meta.getPersistentDataContainer().set(nsKey, PersistentDataType.STRING, value);
        return this;
    }

    public ItemBuilder setData(String key, int value) {
        NamespacedKey nsKey = new NamespacedKey(CorePlugin.getInstance(), key);
        this.meta.getPersistentDataContainer().set(nsKey, PersistentDataType.INTEGER, value);
        return this;
    }

    // [최종 수정] NBT 주입 로직 (1.21.4 호환)
    @SuppressWarnings("deprecation")
    public ItemStack build() {
        this.item.setItemMeta(this.meta);

        if (this.armorModelId != null) {
            try {
                String slotName = "head";
                String typeName = this.item.getType().name();
                if (typeName.endsWith("_CHESTPLATE")) slotName = "chest";
                else if (typeName.endsWith("_LEGGINGS")) slotName = "legs";
                else if (typeName.endsWith("_BOOTS")) slotName = "feet";

                // [중요] 아이템ID[컴포넌트] 형식으로 작성해야 함
                // 그리고 컴포넌트 내부에 slot과 asset_id를 모두 포함
                String itemKey = this.item.getType().getKey().toString(); // minecraft:iron_helmet

                String commandString = String.format(
                        "%s[minecraft:equippable={slot:\"%s\", asset_id:\"minecraft:%s\"}]",
                        itemKey, slotName, this.armorModelId
                );

                return org.bukkit.Bukkit.getUnsafe().modifyItemStack(this.item, commandString);

            } catch (Throwable e) {
                e.printStackTrace();
            }
        }

        return this.item;
    }
}