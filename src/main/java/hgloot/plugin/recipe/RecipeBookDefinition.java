package hgloot.plugin.recipe;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public record RecipeBookDefinition(
    NamespacedKey id,
    RecipeLayout layout,
    EnumMap<RecipeMode, ModeRecipeView> modes
) {
    public RecipeBookDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(layout, "layout");
        Objects.requireNonNull(modes, "modes");
        modes = new EnumMap<>(modes);
    }

    public enum RecipeMode {
        BATTLE_ROYALE,
        HUNGER_GAMES
    }

    public enum RecipeCategory {
        BASIC,
        LEGENDARY
    }

    public record RecipePlacement(RecipeCategory category, int order) {
        public RecipePlacement {
            Objects.requireNonNull(category, "category");
            if (order < 0) {
                throw new IllegalArgumentException("Recipe order cannot be negative");
            }
        }
    }

    public record RecipePresentation(Component title, List<Component> notes) {
        public RecipePresentation {
            Objects.requireNonNull(title, "title");
            notes = notes == null ? List.of() : List.copyOf(notes);
        }
    }

    public sealed interface ModeRecipeView permits AvailableView, DisabledView, HiddenView {
    }

    public record AvailableView(
        RecipePresentation presentation,
        ResultPrototype result,
        RecipePlacement placement
    ) implements ModeRecipeView {
        public AvailableView {
            Objects.requireNonNull(presentation, "presentation");
            Objects.requireNonNull(result, "result");
            Objects.requireNonNull(placement, "placement");
        }
    }

    public record DisabledView(
        RecipePresentation presentation,
        PreviewPrototype preview,
        Component disabledReason,
        RecipePlacement placement
    ) implements ModeRecipeView {
        public DisabledView {
            Objects.requireNonNull(presentation, "presentation");
            Objects.requireNonNull(preview, "preview");
            Objects.requireNonNull(disabledReason, "disabledReason");
            Objects.requireNonNull(placement, "placement");
        }
    }

    public record HiddenView() implements ModeRecipeView {
    }

    public static final class ResultPrototype {
        private final ItemStack template;
        private final ResultIdentity identity;

        public ResultPrototype(ItemStack template) {
            this.template = normalizedTemplate(template, false);
            this.identity = ResultIdentity.from(this.template);
        }

        public ItemStack createItem() {
            return template.clone();
        }

        public ResultIdentity identity() {
            return identity;
        }
    }

    public static final class PreviewPrototype {
        private final ItemStack template;

        public PreviewPrototype(ItemStack template) {
            this.template = normalizedTemplate(template, true);
        }

        public ItemStack createItem() {
            return template.clone();
        }
    }

    public record ResultIdentity(
        Material material,
        int amount,
        Optional<String> displayName,
        Optional<NamespacedKey> itemModel,
        List<String> customModelStrings,
        List<String> enchantments,
        List<String> persistentDataKeys
    ) {
        public ResultIdentity {
            Objects.requireNonNull(material, "material");
            Objects.requireNonNull(displayName, "displayName");
            Objects.requireNonNull(itemModel, "itemModel");
            customModelStrings = List.copyOf(customModelStrings);
            enchantments = List.copyOf(enchantments);
            persistentDataKeys = List.copyOf(persistentDataKeys);
        }

        static ResultIdentity from(ItemStack item) {
            ItemMeta meta = item.getItemMeta();
            Optional<String> displayName = meta != null && meta.hasDisplayName()
                ? Optional.of(PlainTextComponentSerializer.plainText().serialize(meta.displayName()))
                : Optional.empty();
            Optional<NamespacedKey> itemModel = meta != null && meta.hasItemModel()
                ? Optional.ofNullable(meta.getItemModel())
                : Optional.empty();
            List<String> modelStrings = meta == null
                ? List.of()
                : List.copyOf(meta.getCustomModelDataComponent().getStrings());
            List<String> enchantments = item.getEnchantments().entrySet().stream()
                .map(entry -> entry.getKey().getKey().asString() + "=" + entry.getValue())
                .sorted()
                .toList();
            List<String> pdcKeys = meta == null
                ? List.of()
                : meta.getPersistentDataContainer().getKeys().stream()
                    .map(NamespacedKey::asString)
                    .sorted()
                    .toList();
            return new ResultIdentity(
                item.getType(),
                item.getAmount(),
                displayName,
                itemModel,
                modelStrings,
                enchantments,
                pdcKeys
            );
        }
    }

    public sealed interface RecipeLayout permits ShapedLayout, ShapelessLayout {
        List<IngredientSpec> ingredients();
    }

    public record GridPosition(int row, int column) {
        public GridPosition {
            if (row < 0 || column < 0) {
                throw new IllegalArgumentException("Grid coordinates cannot be negative");
            }
        }
    }

    public record ShapedLayout(int width, int height, Map<GridPosition, IngredientSpec> cells)
        implements RecipeLayout {
        public ShapedLayout {
            if (width < 1 || width > 3 || height < 1 || height > 3) {
                throw new IllegalArgumentException("Shaped recipe must fit a 3x3 grid");
            }
            Objects.requireNonNull(cells, "cells");
            Map<GridPosition, IngredientSpec> copy = new HashMap<>();
            cells.forEach((position, ingredient) -> {
                Objects.requireNonNull(position, "position");
                Objects.requireNonNull(ingredient, "ingredient");
                if (position.row() >= height || position.column() >= width) {
                    throw new IllegalArgumentException("Ingredient lies outside shaped recipe bounds");
                }
                copy.put(position, ingredient);
            });
            cells = Map.copyOf(copy);
        }

        @Override
        public List<IngredientSpec> ingredients() {
            return cells.entrySet().stream()
                .sorted(Comparator.comparingInt(entry -> entry.getKey().row() * width + entry.getKey().column()))
                .map(Map.Entry::getValue)
                .toList();
        }
    }

    public record ShapelessLayout(List<IngredientSpec> ingredients) implements RecipeLayout {
        public ShapelessLayout {
            ingredients = List.copyOf(ingredients);
            if (ingredients.isEmpty()) {
                throw new IllegalArgumentException("Shapeless recipe must contain ingredients");
            }
        }
    }

    public record IngredientSpec(IngredientChoice choice, int requiredAmount) {
        public IngredientSpec {
            Objects.requireNonNull(choice, "choice");
            if (requiredAmount <= 0) {
                throw new IllegalArgumentException("Ingredient amount must be positive");
            }
        }
    }

    public record IngredientDisplay(PreviewPrototype icon, Component title, List<Component> qualifiers) {
        public IngredientDisplay {
            Objects.requireNonNull(icon, "icon");
            Objects.requireNonNull(title, "title");
            qualifiers = qualifiers == null ? List.of() : List.copyOf(qualifiers);
        }
    }

    public record FingerprintNode(String domain, List<String> values) {
        public FingerprintNode {
            Objects.requireNonNull(domain, "domain");
            values = List.copyOf(values);
        }
    }

    public sealed interface IngredientChoice permits MaterialChoice, MaterialSetChoice, ExactItemChoice, PdcItemChoice {
        boolean matches(ItemStack candidate);

        IngredientDisplay display();

        FingerprintNode fingerprint();
    }

    public record MaterialChoice(Material material) implements IngredientChoice {
        public MaterialChoice {
            validateMaterial(material);
        }

        @Override
        public boolean matches(ItemStack candidate) {
            return candidate != null && candidate.getType() == material;
        }

        @Override
        public IngredientDisplay display() {
            return new IngredientDisplay(
                new PreviewPrototype(new ItemStack(material)),
                Component.translatable(material.translationKey()),
                List.of()
            );
        }

        @Override
        public FingerprintNode fingerprint() {
            return new FingerprintNode("material", List.of("material:" + material.getKey().asString()));
        }
    }

    public static final class MaterialSetChoice implements IngredientChoice {
        private static final int DISPLAY_EXAMPLE_LIMIT = 3;
        private final Set<Material> materials;

        public MaterialSetChoice(Collection<Material> materials) {
            if (materials == null || materials.isEmpty()) {
                throw new IllegalArgumentException("Material set cannot be empty");
            }
            EnumSet<Material> copy = EnumSet.noneOf(Material.class);
            materials.forEach(material -> {
                validateMaterial(material);
                copy.add(material);
            });
            this.materials = Set.copyOf(copy);
        }

        public Set<Material> materials() {
            return materials;
        }

        @Override
        public boolean matches(ItemStack candidate) {
            return candidate != null && materials.contains(candidate.getType());
        }

        @Override
        public IngredientDisplay display() {
            List<Material> sorted = sortedMaterials(materials);
            List<Component> qualifiers = new ArrayList<>();
            qualifiers.add(Component.text("Any of these materials:"));
            sorted.stream().limit(DISPLAY_EXAMPLE_LIMIT)
                .map(material -> Component.translatable(material.translationKey()))
                .forEach(qualifiers::add);
            if (sorted.size() > DISPLAY_EXAMPLE_LIMIT) {
                qualifiers.add(Component.text("+" + (sorted.size() - DISPLAY_EXAMPLE_LIMIT) + " more"));
            }
            Material example = sorted.getFirst();
            return new IngredientDisplay(
                new PreviewPrototype(new ItemStack(example)),
                Component.translatable(example.translationKey()),
                qualifiers
            );
        }

        @Override
        public FingerprintNode fingerprint() {
            return new FingerprintNode(
                "material_set",
                sortedMaterials(materials).stream()
                    .map(material -> "material:" + material.getKey().asString())
                    .toList()
            );
        }
    }

    public static final class ExactItemChoice implements IngredientChoice {
        private final ItemStack template;
        private final byte[] serialized;

        public ExactItemChoice(ItemStack template) {
            this.template = normalizedTemplate(template, true);
            // Byte equality is intentionally scoped to one server/runtime version.
            this.serialized = this.template.serializeAsBytes().clone();
        }

        @Override
        public boolean matches(ItemStack candidate) {
            if (candidate == null || candidate.getType().isAir()) {
                return false;
            }
            ItemStack normalized = candidate.clone();
            normalized.setAmount(1);
            return Arrays.equals(serialized, normalized.serializeAsBytes());
        }

        @Override
        public IngredientDisplay display() {
            return new IngredientDisplay(
                new PreviewPrototype(template),
                template.displayName(),
                List.of(Component.text("Exact item required"))
            );
        }

        @Override
        public FingerprintNode fingerprint() {
            return new FingerprintNode(
                "exact_item",
                List.of(
                    "material:" + template.getType().getKey().asString(),
                    "display:" + PlainTextComponentSerializer.plainText().serialize(template.displayName()),
                    "runtime-version-local-sha256:" + sha256(serialized)
                )
            );
        }
    }

    public record PdcItemChoice(
        Set<Material> allowedMaterials,
        List<PdcRequirement> requirements,
        PreviewPrototype preview
    ) implements IngredientChoice {
        public PdcItemChoice {
            Objects.requireNonNull(allowedMaterials, "allowedMaterials");
            Objects.requireNonNull(requirements, "requirements");
            Objects.requireNonNull(preview, "preview");
            EnumSet<Material> materials = EnumSet.noneOf(Material.class);
            allowedMaterials.forEach(material -> {
                validateMaterial(material);
                materials.add(material);
            });
            allowedMaterials = Set.copyOf(materials);
            if (allowedMaterials.isEmpty()) {
                throw new IllegalArgumentException("PDC choice requires at least one allowed material");
            }
            requirements = requirements.stream()
                .sorted(Comparator.comparing(requirement -> requirement.key().asString() + ":" + requirement.type()))
                .toList();
            if (requirements.isEmpty()) {
                throw new IllegalArgumentException("PDC choice requires at least one requirement");
            }
            Set<String> requirementKeys = new TreeSet<>();
            for (PdcRequirement requirement : requirements) {
                String key = requirement.key().asString();
                if (!requirementKeys.add(key)) {
                    throw new IllegalArgumentException("Duplicate or contradictory PDC requirement: " + key);
                }
            }
            ItemStack previewItem = preview.createItem();
            if (!matchesInternal(previewItem, allowedMaterials, requirements)) {
                throw new IllegalArgumentException("PDC preview does not satisfy its own matcher");
            }
            Set<String> previewKeys = previewItem.getItemMeta().getPersistentDataContainer().getKeys().stream()
                .map(NamespacedKey::asString)
                .collect(java.util.stream.Collectors.toSet());
            if (!previewKeys.equals(requirementKeys)) {
                throw new IllegalArgumentException(
                    "PDC preview contains undeclared data keys: expected=" + requirementKeys + " actual=" + previewKeys
                );
            }
        }

        @Override
        public boolean matches(ItemStack candidate) {
            return matchesInternal(candidate, allowedMaterials, requirements);
        }

        @Override
        public IngredientDisplay display() {
            ItemStack item = preview.createItem();
            return new IngredientDisplay(
                preview,
                item.displayName(),
                List.of(Component.text("Specific custom item required"))
            );
        }

        @Override
        public FingerprintNode fingerprint() {
            List<String> values = new ArrayList<>();
            sortedMaterials(allowedMaterials).forEach(material -> values.add("material:" + material.getKey().asString()));
            requirements.forEach(requirement -> values.add(requirement.canonicalValue()));
            return new FingerprintNode("pdc_item", values);
        }
    }

    public sealed interface PdcRequirement permits StringPdcRequirement, IntegerPdcRequirement, LongPdcRequirement, BytePdcRequirement {
        NamespacedKey key();

        String type();

        boolean matches(PersistentDataContainer container);

        String canonicalValue();
    }

    public record StringPdcRequirement(NamespacedKey key, String expected) implements PdcRequirement {
        public StringPdcRequirement {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(expected, "expected");
        }

        @Override public String type() { return "string"; }
        @Override public boolean matches(PersistentDataContainer container) {
            return expected.equals(container.get(key, PersistentDataType.STRING));
        }
        @Override public String canonicalValue() { return "pdc-string:" + key.asString() + "=" + expected; }
    }

    public record IntegerPdcRequirement(NamespacedKey key, int expected) implements PdcRequirement {
        public IntegerPdcRequirement { Objects.requireNonNull(key, "key"); }
        @Override public String type() { return "int"; }
        @Override public boolean matches(PersistentDataContainer container) {
            return Objects.equals(expected, container.get(key, PersistentDataType.INTEGER));
        }
        @Override public String canonicalValue() { return "pdc-int:" + key.asString() + "=" + expected; }
    }

    public record LongPdcRequirement(NamespacedKey key, long expected) implements PdcRequirement {
        public LongPdcRequirement { Objects.requireNonNull(key, "key"); }
        @Override public String type() { return "long"; }
        @Override public boolean matches(PersistentDataContainer container) {
            return Objects.equals(expected, container.get(key, PersistentDataType.LONG));
        }
        @Override public String canonicalValue() { return "pdc-long:" + key.asString() + "=" + expected; }
    }

    public record BytePdcRequirement(NamespacedKey key, byte expected) implements PdcRequirement {
        public BytePdcRequirement { Objects.requireNonNull(key, "key"); }
        @Override public String type() { return "byte"; }
        @Override public boolean matches(PersistentDataContainer container) {
            return Objects.equals(expected, container.get(key, PersistentDataType.BYTE));
        }
        @Override public String canonicalValue() { return "pdc-byte:" + key.asString() + "=" + expected; }
    }

    private static boolean matchesInternal(
        ItemStack candidate,
        Set<Material> allowedMaterials,
        List<PdcRequirement> requirements
    ) {
        if (candidate == null || candidate.getType().isAir()) {
            return false;
        }
        if (!allowedMaterials.isEmpty() && !allowedMaterials.contains(candidate.getType())) {
            return false;
        }
        ItemMeta meta = candidate.getItemMeta();
        if (meta == null) {
            return false;
        }
        PersistentDataContainer container = meta.getPersistentDataContainer();
        return requirements.stream().allMatch(requirement -> requirement.matches(container));
    }

    private static ItemStack normalizedTemplate(ItemStack item, boolean forceSingle) {
        if (item == null || item.getType().isAir()) {
            throw new IllegalArgumentException("Item prototype cannot be empty");
        }
        ItemStack copy = item.clone();
        if (forceSingle) {
            copy.setAmount(1);
        }
        return copy;
    }

    private static void validateMaterial(Material material) {
        if (material == null || material.isAir() || !material.isItem()) {
            throw new IllegalArgumentException("Invalid ingredient material: " + material);
        }
    }

    private static List<Material> sortedMaterials(Collection<Material> materials) {
        return materials.stream().sorted(Comparator.comparing(material -> material.getKey().asString())).toList();
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
