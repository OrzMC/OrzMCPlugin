package com.jokerhub.paper.plugin.orzmc;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import io.papermc.paper.datacomponent.DataComponentType;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.tag.Tag;
import io.papermc.paper.registry.tag.TagKey;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import net.kyori.adventure.text.Component;
import org.bukkit.FeatureFlag;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.BlockType;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.HumanEntity;
import org.bukkit.inventory.CreativeCategory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemRarity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ItemType;
import org.bukkit.inventory.MenuType;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.view.builder.InventoryViewBuilder;
import org.jetbrains.annotations.Nullable;

/**
 * Minimal {@link RegistryAccess} implementation for unit tests.
 * <p>
 * Paper 26.1+ uses {@link java.util.ServiceLoader} to discover {@link RegistryAccess}.
 * This class registers itself via {@code META-INF/services/} so the registry works
 * in test environments without a running Paper server.
 * <p>
 * Registries are lazily created to avoid circular initialization: the
 * {@link RegistryAccessHolder} needs {@code TestRegistryAccess} to finish constructing
 * before {@code Registry.MENU} is resolved, but constructing some registries
 * triggers loading {@code Registry.MENU}. Lazy creation breaks this cycle.
 */
public class TestRegistryAccess implements RegistryAccess {

    private volatile Registry<MenuType> menuRegistry;
    private volatile Registry<ItemType> itemRegistry;
    private volatile Registry<Enchantment> enchantmentRegistry;
    private volatile Registry<BlockType> blockRegistry;

    @Override
    @Deprecated
    public <T extends Keyed> Registry<T> getRegistry(final Class<T> type) {
        return emptyRegistry();
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends Keyed> Registry<T> getRegistry(final RegistryKey<T> registryKey) {
        if (registryKey == RegistryKey.MENU) {
            return (Registry<T>) getMenuRegistry();
        }
        if (registryKey == RegistryKey.ITEM) {
            return (Registry<T>) getItemRegistry();
        }
        if (registryKey == RegistryKey.ENCHANTMENT) {
            return (Registry<T>) getEnchantmentRegistry();
        }
        if (registryKey == RegistryKey.BLOCK) {
            return (Registry<T>) getBlockRegistry();
        }
        return emptyRegistry();
    }

    // ---------------------------------------------------------------
    // lazy menu registry
    // ---------------------------------------------------------------

    private Registry<MenuType> getMenuRegistry() {
        Registry<MenuType> r = this.menuRegistry;
        if (r == null) {
            synchronized (this) {
                r = this.menuRegistry;
                if (r == null) {
                    r = createMenuRegistry();
                    this.menuRegistry = r;
                }
            }
        }
        return r;
    }

    private static final String[] COMMON_MENU_KEYS = {
        "generic_9x1",
        "generic_9x2",
        "generic_9x3",
        "generic_9x4",
        "generic_9x5",
        "generic_9x6",
        "hopper",
        "furnace",
        "brewing_stand",
        "beacon",
        "anvil",
        "smithing",
        "crafting",
        "enchantment",
        "merchant",
        "loom",
        "stonecutter",
        "cartography_table",
        "grindstone",
        "lectern",
        "smoker",
        "blast_furnace",
        "crafter",
        "shulker_box",
        "generator"
    };

    private static Registry<MenuType> createMenuRegistry() {
        final Map<NamespacedKey, MenuType> entries = new HashMap<>();
        for (final String key : COMMON_MENU_KEYS) {
            final NamespacedKey nsKey = NamespacedKey.minecraft(key);
            entries.put(nsKey, new StubMenuType(nsKey));
        }
        return new MapBackedRegistry<>(entries);
    }

    // ---------------------------------------------------------------
    // lazy item registry
    // ---------------------------------------------------------------

    private Registry<ItemType> getItemRegistry() {
        Registry<ItemType> r = this.itemRegistry;
        if (r == null) {
            synchronized (this) {
                r = this.itemRegistry;
                if (r == null) {
                    r = createItemRegistry();
                    this.itemRegistry = r;
                }
            }
        }
        return r;
    }

    /** The item keys that the tests currently reference via {@code new ItemStack(Material.xxx)}. */
    private static final String[] COMMON_ITEM_KEYS = {
        "air",
        "stone",
        "diamond",
        "bow",
        "written_book",
        "writable_book",
        "book",
        "feather",
        "ink_sac",
        "stick",
        "string",
        "paper",
    };

    private static Registry<ItemType> createItemRegistry() {
        final Map<NamespacedKey, ItemType> entries = new HashMap<>();
        for (final String key : COMMON_ITEM_KEYS) {
            final NamespacedKey nsKey = NamespacedKey.minecraft(key);
            entries.put(nsKey, new StubItemType(nsKey));
        }
        return new MapBackedRegistry<>(entries);
    }

    // ---------------------------------------------------------------
    // lazy enchantment registry
    // ---------------------------------------------------------------

    private Registry<Enchantment> getEnchantmentRegistry() {
        Registry<Enchantment> r = this.enchantmentRegistry;
        if (r == null) {
            synchronized (this) {
                r = this.enchantmentRegistry;
                if (r == null) {
                    r = createEnchantmentRegistry();
                    this.enchantmentRegistry = r;
                }
            }
        }
        return r;
    }

    private static final String[] COMMON_ENCHANTMENT_KEYS = {
        "protection",
        "fire_protection",
        "feather_falling",
        "blast_protection",
        "projectile_protection",
        "respiration",
        "aqua_affinity",
        "thorns",
        "depth_strider",
        "frost_walker",
        "binding_curse",
        "sharpness",
        "smite",
        "bane_of_arthropods",
        "knockback",
        "fire_aspect",
        "looting",
        "sweeping_edge",
        "efficiency",
        "silk_touch",
        "unbreaking",
        "fortune",
        "power",
        "punch",
        "flame",
        "infinity",
        "luck_of_the_sea",
        "lure",
        "loyalty",
        "impaling",
        "riptide",
        "channeling",
        "multishot",
        "quick_charge",
        "piercing",
        "density",
        "breach",
        "wind_burst",
        "mending",
        "vanishing_curse",
        "soul_speed",
        "swift_sneak",
        "lunge",
    };

    private static Registry<Enchantment> createEnchantmentRegistry() {
        final Map<NamespacedKey, Enchantment> entries = new HashMap<>();
        for (final String key : COMMON_ENCHANTMENT_KEYS) {
            final NamespacedKey nsKey = NamespacedKey.minecraft(key);
            entries.put(nsKey, new StubEnchantment(nsKey));
        }
        return new MapBackedRegistry<>(entries);
    }

    // ---------------------------------------------------------------
    // stub menu type
    // ---------------------------------------------------------------

    private static final class StubMenuType
            implements MenuType.Typed<InventoryView, InventoryViewBuilder<InventoryView>> {
        private final NamespacedKey key;

        StubMenuType(final NamespacedKey key) {
            this.key = key;
        }

        @Override
        public NamespacedKey getKey() {
            return key;
        }

        @Override
        public Class<? extends InventoryView> getInventoryViewClass() {
            return InventoryView.class;
        }

        @Override
        public MenuType.Typed<InventoryView, InventoryViewBuilder<InventoryView>> typed() {
            return this;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <V extends InventoryView, B extends InventoryViewBuilder<V>> MenuType.Typed<V, B> typed(
                final Class<V> viewClass) {
            return (MenuType.Typed<V, B>) this;
        }

        @Override
        public InventoryViewBuilder<InventoryView> builder() {
            throw new UnsupportedOperationException("StubMenuType does not support builder()");
        }

        @Override
        public InventoryView create(
                final HumanEntity player, final @Nullable net.kyori.adventure.text.Component title) {
            throw new UnsupportedOperationException("StubMenuType does not support create()");
        }

        @Override
        public InventoryView create(final HumanEntity player, final @Nullable String title) {
            throw new UnsupportedOperationException("StubMenuType does not support create(String)");
        }

        @Override
        public String toString() {
            return "StubMenuType{" + key + "}";
        }
    }

    // ---------------------------------------------------------------
    // stub item type
    // ---------------------------------------------------------------

    private static final class StubItemType implements ItemType {
        private final NamespacedKey key;
        private final @Nullable Material material;

        StubItemType(final NamespacedKey key) {
            this.key = key;
            this.material = resolveMaterial(key);
        }

        private static @Nullable Material resolveMaterial(final NamespacedKey key) {
            if (!"minecraft".equals(key.namespace())) {
                return null;
            }
            return Material.getMaterial(key.getKey().toUpperCase(Locale.ROOT));
        }

        @Override
        public NamespacedKey getKey() {
            return key;
        }

        @Override
        public String translationKey() {
            return "item.minecraft." + key.getKey();
        }

        @Override
        @Deprecated
        public String getTranslationKey() {
            return translationKey();
        }

        @Override
        public Set<FeatureFlag> requiredFeatures() {
            return Collections.emptySet();
        }

        @Override
        public <T> @Nullable T getDefaultData(final DataComponentType.Valued<T> type) {
            return null;
        }

        @Override
        public boolean hasDefaultData(final DataComponentType type) {
            return false;
        }

        @Override
        public Set<DataComponentType> getDefaultDataTypes() {
            return Collections.emptySet();
        }

        // ---- ItemType abstract methods ----

        @Override
        public ItemType.Typed<ItemMeta> typed() {
            return null;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <M extends ItemMeta> ItemType.Typed<M> typed(final Class<M> viewClass) {
            return null;
        }

        private org.bukkit.inventory.meta.ItemMeta createMockItemMeta() {
            org.bukkit.inventory.meta.ItemMeta meta;
            if (material == Material.WRITTEN_BOOK || material == Material.WRITABLE_BOOK) {
                meta = org.mockito.Mockito.mock(org.bukkit.inventory.meta.BookMeta.class);
            } else {
                meta = org.mockito.Mockito.mock(org.bukkit.inventory.meta.ItemMeta.class);
            }
            // Provide a working PersistentDataContainer to avoid NPE in TeleportBowService
            final org.bukkit.persistence.PersistentDataContainer pdc =
                    org.mockito.Mockito.mock(org.bukkit.persistence.PersistentDataContainer.class);
            org.mockito.Mockito.when(meta.getPersistentDataContainer()).thenReturn(pdc);
            return meta;
        }

        @Override
        public ItemStack createItemStack() {
            return createItemStack(1);
        }

        @Override
        public ItemStack createItemStack(final int amount) {
            final org.mockito.MockSettings settings =
                    org.mockito.Mockito.withSettings().stubOnly();
            final ItemStack stack = org.mockito.Mockito.mock(ItemStack.class, settings);
            if (material != null) {
                org.mockito.Mockito.when(stack.getType()).thenReturn(material);
                org.mockito.Mockito.when(stack.isEmpty()).thenReturn(material == Material.AIR);
            }
            org.mockito.Mockito.when(stack.getAmount()).thenReturn(amount);
            // Create a properly-mocked ItemMeta with PersistentDataContainer support
            final org.bukkit.inventory.meta.ItemMeta meta = createMockItemMeta();
            org.mockito.Mockito.when(stack.getItemMeta()).thenReturn(meta);
            org.mockito.Mockito.when(stack.setItemMeta(meta)).thenReturn(true);
            return stack;
        }

        @Override
        public boolean hasBlockType() {
            return false;
        }

        @Override
        public @Nullable BlockType getBlockType() {
            return null;
        }

        @Override
        public Class<? extends ItemMeta> getItemMetaClass() {
            return ItemMeta.class;
        }

        @Override
        public int getMaxStackSize() {
            return 64;
        }

        @Override
        public short getMaxDurability() {
            return 0;
        }

        @Override
        public boolean isEdible() {
            return false;
        }

        @Override
        public boolean isRecord() {
            return false;
        }

        @Override
        public boolean isFuel() {
            return false;
        }

        @Override
        public int getBurnDuration() {
            return 0;
        }

        @Override
        public boolean isCompostable() {
            return false;
        }

        @Override
        public float getCompostChance() {
            return 0;
        }

        @Override
        public @Nullable ItemType getCraftingRemainingItem() {
            return null;
        }

        @Override
        public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers() {
            return ImmutableMultimap.of();
        }

        @Override
        public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(
                final org.bukkit.inventory.EquipmentSlot slot) {
            return ImmutableMultimap.of();
        }

        @Override
        public @Nullable CreativeCategory getCreativeCategory() {
            return null;
        }

        @Override
        public boolean isEnabledByFeature(final org.bukkit.World world) {
            return true;
        }

        @Override
        public @Nullable Material asMaterial() {
            return null;
        }

        @Override
        public ItemRarity getItemRarity() {
            return ItemRarity.COMMON;
        }

        @Override
        public String toString() {
            return "StubItemType{" + key + "}";
        }
    }

    // ---------------------------------------------------------------
    // lazy block type registry
    // ---------------------------------------------------------------

    private Registry<BlockType> getBlockRegistry() {
        Registry<BlockType> r = this.blockRegistry;
        if (r == null) {
            synchronized (this) {
                r = this.blockRegistry;
                if (r == null) {
                    r = createBlockRegistry();
                    this.blockRegistry = r;
                }
            }
        }
        return r;
    }

    private static final String[] COMMON_BLOCK_KEYS = {
        "air", "stone", "dirt", "grass_block", "bedrock",
    };

    private static Registry<BlockType> createBlockRegistry() {
        final Map<NamespacedKey, BlockType> entries = new HashMap<>();
        for (final String key : COMMON_BLOCK_KEYS) {
            final NamespacedKey nsKey = NamespacedKey.minecraft(key);
            entries.put(nsKey, new StubBlockType(nsKey));
        }
        return new MapBackedRegistry<>(entries);
    }

    // ---------------------------------------------------------------
    // stub block type
    // ---------------------------------------------------------------

    private static final class StubBlockType implements BlockType {
        private final NamespacedKey key;

        StubBlockType(final NamespacedKey key) {
            this.key = key;
        }

        @Override
        public NamespacedKey getKey() {
            return key;
        }

        @Override
        public String translationKey() {
            return "block.minecraft." + key.getKey();
        }

        @Override
        @Deprecated
        public String getTranslationKey() {
            return translationKey();
        }

        @Override
        public java.util.Set<FeatureFlag> requiredFeatures() {
            return java.util.Collections.emptySet();
        }

        @Override
        public boolean isAir() {
            return "air".equals(key.getKey());
        }

        @Override
        public boolean hasCollision() {
            return !"air".equals(key.getKey());
        }

        @Override
        public boolean isSolid() {
            return false;
        }

        @Override
        public boolean isFlammable() {
            return false;
        }

        @Override
        public boolean isBurnable() {
            return false;
        }

        @Override
        public boolean isOccluding() {
            return false;
        }

        @Override
        public boolean hasGravity() {
            return false;
        }

        @Override
        public boolean isInteractable() {
            return false;
        }

        @Override
        public float getHardness() {
            return 0;
        }

        @Override
        public float getBlastResistance() {
            return 0;
        }

        @Override
        public float getSlipperiness() {
            return 0.6f;
        }

        @Override
        public boolean isEnabledByFeature(final org.bukkit.World world) {
            return true;
        }

        @Override
        public @Nullable Material asMaterial() {
            return StubItemType.resolveMaterial(key);
        }

        @Override
        public boolean hasItemType() {
            return false;
        }

        @Override
        public @Nullable ItemType getItemType() {
            return null;
        }

        @Override
        public org.bukkit.block.BlockType.Typed<org.bukkit.block.data.BlockData> typed() {
            return null;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <B extends org.bukkit.block.data.BlockData> org.bukkit.block.BlockType.Typed<B> typed(
                final Class<B> blockDataClass) {
            return null;
        }

        @Override
        public Class<? extends org.bukkit.block.data.BlockData> getBlockDataClass() {
            return org.bukkit.block.data.BlockData.class;
        }

        @Override
        public org.bukkit.block.data.BlockData createBlockData() {
            throw new UnsupportedOperationException("StubBlockType does not support createBlockData()");
        }

        @Override
        public java.util.Collection<? extends org.bukkit.block.data.BlockData> createBlockDataStates() {
            return java.util.Collections.emptyList();
        }

        @Override
        public org.bukkit.block.data.BlockData createBlockData(final String data) {
            throw new UnsupportedOperationException("StubBlockType does not support createBlockData(String)");
        }

        @Override
        public String toString() {
            return "StubBlockType{" + key + "}";
        }
    }

    // ---------------------------------------------------------------
    // stub enchantment
    // ---------------------------------------------------------------

    private static final class StubEnchantment extends Enchantment {
        private final NamespacedKey key;

        StubEnchantment(final NamespacedKey key) {
            this.key = key;
        }

        @Override
        public NamespacedKey getKey() {
            return key;
        }

        @Override
        public String translationKey() {
            return "enchantment.minecraft." + key.getKey();
        }

        @Override
        @Deprecated
        public String getTranslationKey() {
            return translationKey();
        }

        @Override
        public String getName() {
            return key.getKey();
        }

        @Override
        public int getMaxLevel() {
            return 1;
        }

        @Override
        public int getStartLevel() {
            return 1;
        }

        @Override
        public org.bukkit.enchantments.EnchantmentTarget getItemTarget() {
            return org.bukkit.enchantments.EnchantmentTarget.ALL;
        }

        @Override
        public boolean isTreasure() {
            return false;
        }

        @Override
        public boolean isCursed() {
            return false;
        }

        @Override
        public boolean conflictsWith(final Enchantment other) {
            return false;
        }

        @Override
        public boolean canEnchantItem(final org.bukkit.inventory.ItemStack item) {
            return true;
        }

        @Override
        public Component displayName(final int level) {
            return Component.text(key.getKey());
        }

        @Override
        public boolean isTradeable() {
            return false;
        }

        @Override
        public boolean isDiscoverable() {
            return false;
        }

        @Override
        public int getMinModifiedCost(final int level) {
            return 0;
        }

        @Override
        public int getMaxModifiedCost(final int level) {
            return 0;
        }

        @Override
        public int getAnvilCost() {
            return 0;
        }

        @Override
        public io.papermc.paper.enchantments.EnchantmentRarity getRarity() {
            return io.papermc.paper.enchantments.EnchantmentRarity.COMMON;
        }

        @Override
        public float getDamageIncrease(final int level, final org.bukkit.entity.EntityCategory entityCategory) {
            return 0;
        }

        @Override
        public float getDamageIncrease(final int level, final org.bukkit.entity.EntityType entityType) {
            return 0;
        }

        @Override
        public java.util.Set<org.bukkit.inventory.EquipmentSlotGroup> getActiveSlotGroups() {
            return java.util.Collections.emptySet();
        }

        @Override
        public Component description() {
            return Component.empty();
        }

        @Override
        public io.papermc.paper.registry.set.RegistryKeySet<org.bukkit.inventory.ItemType> getSupportedItems() {
            return io.papermc.paper.registry.set.RegistrySet.keySet(
                    io.papermc.paper.registry.RegistryKey.ITEM, java.util.Collections.emptyList());
        }

        @Override
        public io.papermc.paper.registry.set.RegistryKeySet<org.bukkit.inventory.ItemType> getPrimaryItems() {
            return io.papermc.paper.registry.set.RegistrySet.keySet(
                    io.papermc.paper.registry.RegistryKey.ITEM, java.util.Collections.emptyList());
        }

        @Override
        public int getWeight() {
            return 0;
        }

        @Override
        public io.papermc.paper.registry.set.RegistryKeySet<Enchantment> getExclusiveWith() {
            return io.papermc.paper.registry.set.RegistrySet.keySet(
                    io.papermc.paper.registry.RegistryKey.ENCHANTMENT, java.util.Collections.emptyList());
        }

        @Override
        public String toString() {
            return "StubEnchantment{" + key + "}";
        }
    }

    // ---------------------------------------------------------------
    // map-backed registry
    // ---------------------------------------------------------------

    private static final class MapBackedRegistry<T extends Keyed> implements Registry<T> {
        private final Map<NamespacedKey, T> entries;

        MapBackedRegistry(final Map<NamespacedKey, T> entries) {
            this.entries = new HashMap<>(entries);
        }

        @Override
        public Iterator<T> iterator() {
            return entries.values().iterator();
        }

        @Override
        public @Nullable T get(final NamespacedKey key) {
            T result = entries.get(key);
            // Fallback: if key is a known menu key, create a stub on the fly
            if (result == null && key.namespace().equals("minecraft")) {
                result = tryCreateStub(key);
            }
            return result;
        }

        @SuppressWarnings("unchecked")
        private @Nullable T tryCreateStub(final NamespacedKey key) {
            if (!entries.isEmpty()) {
                final Object first = entries.values().iterator().next();
                if (first instanceof MenuType) {
                    final MenuType stub = new StubMenuType(key);
                    entries.put(key, (T) stub);
                    return (T) stub;
                }
                if (first instanceof ItemType) {
                    final ItemType stub = new StubItemType(key);
                    entries.put(key, (T) stub);
                    return (T) stub;
                }
                if (first instanceof BlockType) {
                    final BlockType stub = new StubBlockType(key);
                    entries.put(key, (T) stub);
                    return (T) stub;
                }
            }
            return null;
        }

        @Override
        public NamespacedKey getKey(final T value) {
            return value.getKey();
        }

        @Override
        public boolean hasTag(final TagKey<T> key) {
            return false;
        }

        @Override
        public Tag<T> getTag(final TagKey<T> key) {
            throw new UnsupportedOperationException("Tags not supported in test registry");
        }

        @Override
        public Collection<Tag<T>> getTags() {
            return Collections.emptyList();
        }

        @Override
        public Stream<T> stream() {
            return entries.values().stream();
        }

        @Override
        public Stream<NamespacedKey> keyStream() {
            return entries.keySet().stream();
        }

        @Override
        public int size() {
            return entries.size();
        }

        @Override
        public @Nullable T match(final String input) {
            final NamespacedKey key = NamespacedKey.fromString(input.toLowerCase(Locale.ROOT));
            return key != null ? entries.get(key) : null;
        }

        @Override
        public String toString() {
            return "MapBackedRegistry{" + entries.size() + " entries}";
        }
    }

    // ---------------------------------------------------------------
    // empty fallback registry
    // ---------------------------------------------------------------

    @SuppressWarnings("rawtypes")
    private static final Registry EMPTY = new Registry<Keyed>() {
        @Override
        public Iterator<Keyed> iterator() {
            return Collections.emptyIterator();
        }

        @Override
        public @Nullable Keyed get(final NamespacedKey key) {
            return null;
        }

        @Override
        public NamespacedKey getKey(final Keyed value) {
            return value.getKey();
        }

        @Override
        public boolean hasTag(final TagKey<Keyed> key) {
            return false;
        }

        @Override
        public Tag<Keyed> getTag(final TagKey<Keyed> key) {
            throw new UnsupportedOperationException("Tags not supported in test registry");
        }

        @Override
        public Collection<Tag<Keyed>> getTags() {
            return Collections.emptyList();
        }

        @Override
        public Stream<Keyed> stream() {
            return Stream.empty();
        }

        @Override
        public Stream<NamespacedKey> keyStream() {
            return Stream.empty();
        }

        @Override
        public int size() {
            return 0;
        }

        @Override
        public @Nullable Keyed match(final String input) {
            return null;
        }
    };

    @SuppressWarnings("unchecked")
    private static <T extends Keyed> Registry<T> emptyRegistry() {
        return (Registry<T>) EMPTY;
    }
}
