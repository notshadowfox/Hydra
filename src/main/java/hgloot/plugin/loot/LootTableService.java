package hgloot.plugin.loot;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.stream.Collectors;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionType;

@SuppressWarnings("deprecation")
public final class LootTableService {
    private static final List<String> EXTRA_TABLE_IDS = List.of(
        "menu_standard_loot_chest_1",
        "hg_single",
        "hg_refill",
        "br_mid",
        "br_high",
        "br_special",
        "br_ocean",
        "br_underground"
    );
    private static final List<String> FORCE_SYNC_EMBEDDED_TABLE_IDS = List.of(
        "hg_single",
        "hg_refill"
    );
    private static final Map<String, Material> BLOCK_ITEM_ALIASES = Map.ofEntries(
        Map.entry("CARROTS", Material.CARROT),
        Map.entry("POTATOES", Material.POTATO),
        Map.entry("BEETROOTS", Material.BEETROOT),
        Map.entry("COCOA", Material.COCOA_BEANS),
        Map.entry("SWEET_BERRY_BUSH", Material.SWEET_BERRIES)
    );

    private final JavaPlugin plugin;
    private final Map<String, LootTable> tables = new HashMap<>();
    private final Random random = new Random();
    private final Path externalLootDir;
    private int loadedFromExternal;
    private int loadedFromEmbedded;
    private int missingTableCount;

    public LootTableService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.externalLootDir = plugin.getDataFolder().toPath().resolve("loot_tables");
    }

    public void loadAllTables() {
        tables.clear();
        loadedFromExternal = 0;
        loadedFromEmbedded = 0;
        missingTableCount = 0;
        ensureExternalLootDir();
        for (LootCategory category : LootCategory.values()) {
            for (int i = 1; i <= category.chestCount(); i++) {
                String tableId = category.tableId(i);
                bootstrapExternalTable(tableId);
                LootTable table = loadTable(tableId);
                if (table != null) {
                    tables.put(tableId, table);
                } else {
                    missingTableCount++;
                }
            }
        }
        for (String tableId : EXTRA_TABLE_IDS) {
            bootstrapExternalTable(tableId);
            LootTable table = loadTable(tableId);
            if (table != null) {
                tables.put(tableId, table);
            } else {
                missingTableCount++;
            }
        }
        plugin.getLogger().info("Loot table sources: external=" + loadedFromExternal
            + ", embedded=" + loadedFromEmbedded + ", missing=" + missingTableCount);
    }

    public int loadedTableCount() {
        return tables.size();
    }

    public int loadedFromExternalCount() {
        return loadedFromExternal;
    }

    public int loadedFromEmbeddedCount() {
        return loadedFromEmbedded;
    }

    public int missingTableCount() {
        return missingTableCount;
    }

    public String externalLootFolderPath() {
        return externalLootDir.toString();
    }

    public boolean hasTable(String tableId) {
        return tables.containsKey(tableId);
    }

    public int fillInventory(Inventory inventory, String tableId) {
        List<ItemStack> generated = generateLoot(tableId);
        if (!tables.containsKey(tableId)) {
            plugin.getLogger().warning("Attempted to fill inventory with missing table: " + tableId);
        } else if (generated.isEmpty()) {
            plugin.getLogger().warning("Generated 0 items for table: " + tableId);
        }
        inventory.clear();
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < inventory.getSize(); i++) {
            slots.add(i);
        }
        Collections.shuffle(slots, random);

        int limit = Math.min(generated.size(), slots.size());
        for (int i = 0; i < limit; i++) {
            inventory.setItem(slots.get(i), generated.get(i));
        }
        return generated.size();
    }

    public List<ItemStack> generateLoot(String tableId) {
        return roll(tableId);
    }

    public Map<String, Integer> simulateCounts(String tableId, int iterations) {
        Map<String, Integer> counts = new HashMap<>();
        for (int i = 0; i < iterations; i++) {
            List<ItemStack> generated = generateLoot(tableId);
            for (ItemStack item : generated) {
                String key = Objects.requireNonNull(item.getType().getKey(), "Material key is null").toString();
                int existing = counts.getOrDefault(key, 0);
                counts.put(key, existing + item.getAmount());
            }
        }
        return counts;
    }

    public ItemStack createAuditBook(String tableId) {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        if (meta == null) {
            return book;
        }

        LootTable table = tables.get(tableId);
        meta.setTitle("Audit " + tableId);
        meta.setAuthor("HGLootPlugin");

        if (table == null) {
            meta.setPages(List.of("Missing table: " + tableId));
            book.setItemMeta(meta);
            return book;
        }

        List<String> lines = new ArrayList<>();
        lines.add("Table: " + tableId);
        lines.add("Pools: " + table.pools.size());

        int poolIndex = 1;
        for (Pool pool : table.pools) {
            lines.add("");
            lines.add("Pool " + poolIndex + " (rolls " + pool.rolls.min + "-" + pool.rolls.max + ")");
            for (Entry entry : pool.entries) {
                if (entry.empty) {
                    lines.add("- empty (w" + entry.weight + ")");
                    continue;
                }
                String base = "- " + entry.materialKey + " (w" + entry.weight + ")";
                if (!entry.conditions.isEmpty()) {
                    String cond = entry.conditions.stream()
                        .map(condition -> "chance=" + trimDouble(condition.randomChance))
                        .collect(Collectors.joining(","));
                    base += " [" + cond + "]";
                }
                lines.add(base);
            }
            poolIndex++;
        }

        List<String> pages = paginate(lines, 13);
        meta.setPages(pages);
        book.setItemMeta(meta);
        return book;
    }

    private List<ItemStack> roll(String tableId) {
        LootTable table = tables.get(tableId);
        if (table == null) {
            return List.of();
        }

        List<ItemStack> output = new ArrayList<>();
        for (Pool pool : table.pools) {
            int rolls = Math.max(0, pool.rolls.sample(random));
            for (int i = 0; i < rolls; i++) {
                List<Entry> entries = pool.entries;
                if (!pool.tiers.isEmpty()) {
                    Tier selectedTier = pickTier(pool.tiers);
                    entries = selectedTier == null ? List.of() : selectedTier.entries;
                }
                Entry entry = pickEntry(entries);
                if (entry == null || entry.empty) {
                    continue;
                }

                Material material = materialFromKey(entry.materialKey);
                if (material == null || material == Material.AIR) {
                    continue;
                }

                ItemStack item;
                try {
                    item = new ItemStack(material, 1);
                } catch (IllegalArgumentException ex) {
                    plugin.getLogger().warning("Skipping non-item loot material " + entry.materialKey
                        + " in table " + tableId + ": " + ex.getMessage());
                    continue;
                }
                for (LootFunction function : entry.functions) {
                    applyFunction(item, function);
                }
                if (item.getAmount() <= 0) {
                    continue;
                }
                int clampedAmount = Math.min(64, item.getAmount());
                item.setAmount(clampedAmount);
                output.add(item);
            }
        }
        return output;
    }

    private Entry pickEntry(List<Entry> entries) {
        List<Entry> eligible = entries.stream().filter(this::passesConditions).toList();
        if (eligible.isEmpty()) {
            return null;
        }

        int total = eligible.stream().mapToInt(entry -> Math.max(1, entry.weight)).sum();
        int roll = random.nextInt(total);
        int running = 0;
        for (Entry entry : eligible) {
            running += Math.max(1, entry.weight);
            if (roll < running) {
                return entry;
            }
        }
        return null;
    }

    private Tier pickTier(List<Tier> tiers) {
        int total = tiers.stream().mapToInt(tier -> Math.max(1, tier.weight)).sum();
        if (total <= 0) {
            return null;
        }
        int selected = random.nextInt(total);
        for (Tier tier : tiers) {
            selected -= Math.max(1, tier.weight);
            if (selected < 0) {
                return tier;
            }
        }
        return tiers.getLast();
    }

    private boolean passesConditions(Entry entry) {
        for (Condition condition : entry.conditions) {
            if (random.nextDouble() > condition.randomChance) {
                return false;
            }
        }
        return true;
    }

    private void applyFunction(ItemStack item, LootFunction function) {
        switch (function.type) {
            case "minecraft:set_count" -> item.setAmount(Math.max(0, function.value.sample(random)));
            case "minecraft:enchant_with_levels" -> enchantWithLevels(item, function.value.sample(random));
            case "minecraft:set_enchantments" -> setEnchantments(item, function.enchantments);
            case "minecraft:enchant_randomly" -> enchantRandomly(item);
            case "minecraft:set_potion" -> setPotion(item, function.potion);
            default -> {
                // Unsupported function types are ignored intentionally.
            }
        }
    }

    private void enchantWithLevels(ItemStack item, int levels) {
        int scaled = Math.max(1, levels / 4 + 1);
        applyRandomEnchant(item, scaled);
    }

    private void enchantRandomly(ItemStack item) {
        applyRandomEnchant(item, 3);
    }

    private void applyRandomEnchant(ItemStack item, int maxLevelHint) {
        List<Enchantment> all = new ArrayList<>();
        for (Enchantment enchantment : Registry.ENCHANTMENT) {
            if (enchantment != null) {
                all.add(enchantment);
            }
        }
        all = all.stream()
            .filter(enchantment -> enchantment.canEnchantItem(item)
                || item.getType() == Material.BOOK
                || item.getType() == Material.ENCHANTED_BOOK)
            .toList();
        if (all.isEmpty()) {
            return;
        }
        Enchantment selected = all.get(random.nextInt(all.size()));
        int max = Math.max(1, Math.min(selected.getMaxLevel(), maxLevelHint));
        int level = 1 + random.nextInt(max);
        addEnchantment(item, selected, level);
    }

    private void setEnchantments(ItemStack item, Map<String, Integer> enchants) {
        for (Map.Entry<String, Integer> entry : enchants.entrySet()) {
            NamespacedKey key = NamespacedKey.minecraft(entry.getKey().replace("minecraft:", ""));
            Enchantment enchantment = Registry.ENCHANTMENT.get(key);
            if (enchantment == null) {
                continue;
            }
            addEnchantment(item, enchantment, Math.max(1, entry.getValue()));
        }
    }

    private void addEnchantment(ItemStack item, Enchantment enchantment, int level) {
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof EnchantmentStorageMeta storageMeta) {
            storageMeta.addStoredEnchant(enchantment, level, true);
            item.setItemMeta(storageMeta);
            return;
        }
        item.addUnsafeEnchantment(enchantment, level);
    }

    private void setPotion(ItemStack item, String potionKey) {
        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof PotionMeta potionMeta)) {
            return;
        }

        String cleaned = potionKey.replace("minecraft:", "").toUpperCase(Locale.ROOT);
        try {
            PotionType potionType = PotionType.valueOf(cleaned);
            potionMeta.setBasePotionType(potionType);
            item.setItemMeta(potionMeta);
        } catch (IllegalArgumentException ignored) {
            // Ignore unknown potion names.
        }
    }

    private LootTable loadTable(String tableId) {
        Path externalPath = externalLootDir.resolve(tableId + ".json");
        if (Files.isRegularFile(externalPath)) {
            try (var reader = Files.newBufferedReader(externalPath, StandardCharsets.UTF_8)) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                LootTable parsed = parseLootTable(root);
                if (parsed.pools.isEmpty()) {
                    plugin.getLogger().warning("External loot table has no pools: " + tableId
                        + " (" + externalPath + ")");
                    LootTable fallback = loadEmbeddedTable(tableId);
                    if (fallback != null) {
                        plugin.getLogger().warning("Using embedded fallback for table: " + tableId);
                    }
                    return fallback;
                }
                loadedFromExternal++;
                return parsed;
            } catch (Exception ex) {
                plugin.getLogger().warning("Failed to parse external loot table " + tableId + ": " + ex.getMessage());
                LootTable fallback = loadEmbeddedTable(tableId);
                if (fallback != null) {
                    plugin.getLogger().warning("Using embedded fallback for table: " + tableId);
                }
                return fallback;
            }
        }

        return loadEmbeddedTable(tableId);
    }

    private LootTable loadEmbeddedTable(String tableId) {
        String resourcePath = "loot_tables/" + tableId + ".json";
        try (InputStream stream = plugin.getResource(resourcePath)) {
            if (stream == null) {
                plugin.getLogger().warning("Missing resource: " + resourcePath);
                return null;
            }
            try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                LootTable parsed = parseLootTable(root);
                if (parsed.pools.isEmpty()) {
                    plugin.getLogger().warning("Embedded loot table has no pools: " + tableId
                        + " (" + resourcePath + ")");
                    return null;
                }
                loadedFromEmbedded++;
                return parsed;
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed to parse loot table " + tableId + ": " + ex.getMessage());
            return null;
        }
    }

    private void ensureExternalLootDir() {
        try {
            if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
                plugin.getLogger().warning("Could not create plugin data folder: " + plugin.getDataFolder());
                return;
            }
            Files.createDirectories(externalLootDir);
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed to create loot table folder " + externalLootDir + ": " + ex.getMessage());
        }
    }

    private void bootstrapExternalTable(String tableId) {
        try {
            Path externalPath = externalLootDir.resolve(tableId + ".json");
            if (Files.exists(externalPath) && !FORCE_SYNC_EMBEDDED_TABLE_IDS.contains(tableId)) {
                return;
            }
            String resourcePath = "loot_tables/" + tableId + ".json";
            try (InputStream stream = plugin.getResource(resourcePath)) {
                if (stream == null) {
                    return;
                }
                if (Files.exists(externalPath)) {
                    Files.delete(externalPath);
                }
                Files.copy(stream, externalPath);
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed to bootstrap external loot table " + tableId + ": " + ex.getMessage());
        }
    }

    private static LootTable parseLootTable(JsonObject root) {
        List<Pool> pools = new ArrayList<>();
        JsonArray poolsJson = root.getAsJsonArray("pools");
        if (poolsJson == null) {
            return new LootTable(pools);
        }

        for (JsonElement poolElem : poolsJson) {
            JsonObject poolObj = poolElem.getAsJsonObject();
            NumberRange rolls = NumberRange.parse(poolObj.get("rolls"));
            List<Entry> entries = parseEntries(poolObj.getAsJsonArray("entries"));
            List<Tier> tiers = new ArrayList<>();
            JsonArray tiersJson = poolObj.getAsJsonArray("tiers");
            if (tiersJson != null) {
                for (JsonElement tierElement : tiersJson) {
                    JsonObject tierObject = tierElement.getAsJsonObject();
                    int weight = tierObject.has("weight") ? tierObject.get("weight").getAsInt() : 1;
                    tiers.add(new Tier(Math.max(1, weight), parseEntries(tierObject.getAsJsonArray("entries"))));
                }
            }
            pools.add(new Pool(rolls, entries, tiers));
        }
        return new LootTable(pools);
    }

    private static List<Entry> parseEntries(JsonArray entriesJson) {
        List<Entry> entries = new ArrayList<>();
        if (entriesJson == null) {
            return entries;
        }
        for (JsonElement entryElem : entriesJson) {
            JsonObject entryObj = entryElem.getAsJsonObject();
            String type = str(entryObj, "type");
            boolean empty = "minecraft:empty".equals(type);
            String materialKey = str(entryObj, "name");
            int weight = entryObj.has("weight") ? entryObj.get("weight").getAsInt() : 1;
            List<LootFunction> functions = new ArrayList<>();
            JsonArray functionsJson = entryObj.getAsJsonArray("functions");
            if (functionsJson != null) {
                for (JsonElement fnElem : functionsJson) {
                    JsonObject fnObj = fnElem.getAsJsonObject();
                    NumberRange value = fnObj.has("count") ? NumberRange.parse(fnObj.get("count"))
                        : fnObj.has("levels") ? NumberRange.parse(fnObj.get("levels")) : NumberRange.constant(1);
                    Map<String, Integer> enchants = new HashMap<>();
                    if (fnObj.has("enchantments") && fnObj.get("enchantments").isJsonObject()) {
                        for (Map.Entry<String, JsonElement> enchantment : fnObj.getAsJsonObject("enchantments").entrySet()) {
                            enchants.put(enchantment.getKey(), enchantment.getValue().getAsInt());
                        }
                    }
                    functions.add(new LootFunction(str(fnObj, "function"), value, Map.copyOf(enchants), str(fnObj, "id")));
                }
            }
            List<Condition> conditions = new ArrayList<>();
            JsonArray conditionsJson = entryObj.getAsJsonArray("conditions");
            if (conditionsJson != null) {
                for (JsonElement conditionElement : conditionsJson) {
                    JsonObject condition = conditionElement.getAsJsonObject();
                    if ("minecraft:random_chance".equals(str(condition, "condition")) && condition.has("chance")) {
                        conditions.add(new Condition(condition.get("chance").getAsDouble()));
                    }
                }
            }
            entries.add(new Entry(empty, materialKey, weight, functions, conditions));
        }
        return entries;
    }

    private static Material materialFromKey(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        String cleaned = key.replace("minecraft:", "");
        String normalized = cleaned.toUpperCase(Locale.ROOT);
        Material aliased = BLOCK_ITEM_ALIASES.get(normalized);
        if (aliased != null) {
            return aliased;
        }
        Material material = Material.getMaterial(normalized);
        if (material != null && material.isItem()) {
            return material;
        }
        material = Material.matchMaterial(key, false);
        if (material != null && material.isItem()) {
            return material;
        }
        return BLOCK_ITEM_ALIASES.get(normalized);
    }

    private static String str(JsonObject obj, String key) {
        return obj.has(key) ? obj.get(key).getAsString() : "";
    }

    private static String trimDouble(double value) {
        if (value == (long) value) {
            return Long.toString((long) value);
        }
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private static List<String> paginate(List<String> lines, int linesPerPage) {
        List<String> pages = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int lineCount = 0;

        for (String line : lines) {
            if (lineCount >= linesPerPage) {
                pages.add(current.toString());
                current = new StringBuilder();
                lineCount = 0;
            }
            if (current.length() > 0) {
                current.append('\n');
            }
            current.append(line);
            lineCount++;
        }
        if (!current.isEmpty()) {
            pages.add(current.toString());
        }
        if (pages.isEmpty()) {
            pages.add("No audit data.");
        }
        return pages;
    }

    private record LootTable(List<Pool> pools) {
    }

    private record Pool(NumberRange rolls, List<Entry> entries, List<Tier> tiers) {
    }

    private record Tier(int weight, List<Entry> entries) {
    }

    private record Entry(
        boolean empty,
        String materialKey,
        int weight,
        List<LootFunction> functions,
        List<Condition> conditions
    ) {
    }

    private record LootFunction(
        String type,
        NumberRange value,
        Map<String, Integer> enchantments,
        String potion
    ) {
    }

    private record Condition(double randomChance) {
    }

    private record NumberRange(double min, double max) {
        static NumberRange constant(double value) {
            return new NumberRange(value, value);
        }

        int sample(Random random) {
            int minInt = (int) Math.floor(Math.min(min, max));
            int maxInt = (int) Math.floor(Math.max(min, max));
            if (maxInt <= minInt) {
                return minInt;
            }
            return minInt + random.nextInt(maxInt - minInt + 1);
        }

        static NumberRange parse(JsonElement element) {
            if (element == null || element.isJsonNull()) {
                return constant(1);
            }
            if (element.isJsonPrimitive()) {
                return constant(element.getAsDouble());
            }
            JsonObject obj = element.getAsJsonObject();
            if (obj.has("min") && obj.has("max")) {
                return new NumberRange(obj.get("min").getAsDouble(), obj.get("max").getAsDouble());
            }
            if (obj.has("type") && "minecraft:uniform".equals(str(obj, "type")) && obj.has("min") && obj.has("max")) {
                return new NumberRange(obj.get("min").getAsDouble(), obj.get("max").getAsDouble());
            }
            if (obj.has("value")) {
                return constant(obj.get("value").getAsDouble());
            }
            return constant(1);
        }
    }
}
