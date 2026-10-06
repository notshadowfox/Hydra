package hgloot.plugin.ultraroyales;

import dev.zxdzero.ZxdzeroEvents.registries.RecipeManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;
import java.util.stream.Collectors;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.joml.AxisAngle4f;

public final class OriginalLegendaryRecipes {
    private static final AxisAngle4f DEFAULT_ROTATION = new AxisAngle4f(0f, 0f, 0f, 1f);
    private static final AxisAngle4f FLAT_WEAPON_ROTATION = new AxisAngle4f((float) (Math.PI / 2), 1f, 0f, 0f);

    private static final List<LegendaryRecipeDefinition> ORIGINAL_EIGHT = List.of(
        new LegendaryRecipeDefinition(
            "spider_staff",
            "Spider Staff",
            Items::spiderStaff,
            null,
            List.of(
                ingredient(Material.NETHERITE_INGOT, 1, "Netherite Ingot"),
                ingredient(Material.FERMENTED_SPIDER_EYE, 4, "Fermented Spider Eyes"),
                ingredient(Material.GOLD_BLOCK, 4, "Gold Blocks")
            ),
            List.of(
                "Summons Cave Spiders."
            ),
            0.75f,
            DEFAULT_ROTATION,
            -0.4f
        ),
        new LegendaryRecipeDefinition(
            "dwarven_bow",
            "Dwarf Bow",
            Items::dwarvenBow,
            ingredient(Material.BOW, 1, "Bow"),
            List.of(
                ingredient(Material.EMERALD_BLOCK, 4, "Emerald Blocks"),
                ingredient(Material.AMETHYST_SHARD, 16, "Amethyst Shards"),
                ingredient(Material.DIAMOND, 8, "Diamonds")
            ),
            List.of(
                "Built to break through armor."
            ),
            0.5f,
            new AxisAngle4f((float) (Math.PI / 2), 1f, 1f, 1f),
            0f
        ),
        new LegendaryRecipeDefinition(
            "sponge_saber",
            "Sponge Saber",
            Items::spongeSaber,
            ingredient(Material.DIAMOND_SWORD, 1, "Diamond Sword"),
            List.of(
                ingredient(Material.SPONGE, 8, "Sponge"),
                ingredient(Material.WATER_BUCKET, 16, "Water Buckets"),
                ingredient(Material.PRISMARINE_CRYSTALS, 16, "Prismarine Crystals")
            ),
            List.of(
                "A powerful water based weapon."
            ),
            1.0f,
            DEFAULT_ROTATION,
            0f
        ),
        new LegendaryRecipeDefinition(
            "ghlochester",
            "Ghlochester",
            Items::ghlochester,
            ingredient(Material.FISHING_ROD, 1, "Fishing Rod"),
            List.of(
                ingredient(Material.HEAVY_CORE, 1, "Heavy Core"),
                ingredient(Material.BREEZE_ROD, 8, "Breeze Rods"),
                ingredient(Material.LEAD, 8, "Leads")
            ),
            List.of(
                "Disorients and slows enemies."
            ),
            0.6f,
            DEFAULT_ROTATION,
            0.15f
        ),
        new LegendaryRecipeDefinition(
            "knights_saddle",
            "Knight's Saddle",
            Items::knightsSaddle,
            ingredient(Material.DIAMOND_SWORD, 1, "Diamond Sword"),
            List.of(
                ingredient(Material.SADDLE, 1, "Saddle"),
                ingredient(Material.GOAT_HORN, 2, "Goat Horns"),
                ingredient(Material.ENCHANTED_GOLDEN_APPLE, 1, "Enchanted Golden Apple")
            ),
            List.of(
                "Combines mobility with damage."
            ),
            1.0f,
            DEFAULT_ROTATION,
            0f
        ),
        new LegendaryRecipeDefinition(
            "electric_conch",
            "Electric Conch",
            Items::electricConch,
            null,
            List.of(
                ingredient(Material.COPPER_BLOCK, 4, "Copper Blocks"),
                ingredient(Material.GLOW_INK_SAC, 4, "Glow Ink Sacs"),
                ingredient(Material.HEART_OF_THE_SEA, 1, "Heart of the Sea"),
                ingredient(Material.NAUTILUS_SHELL, 1, "Nautilus Shell")
            ),
            List.of(
                "Unleashes an area shock attack."
            ),
            1.0f,
            DEFAULT_ROTATION,
            0f
        ),
        new LegendaryRecipeDefinition(
            "skeletal_barber",
            "Skeletal Barber",
            Items::skeletalBarber,
            ingredient(Material.NETHERITE_SWORD, 1, "Netherite Sword"),
            List.of(
                ingredient(Material.WITHER_SKELETON_SKULL, 1, "Wither Skeleton Skull"),
                ingredient(Material.NETHERITE_SCRAP, 2, "Netherite Scrap"),
                ingredient(Material.BONE, 32, "Bones")
            ),
            List.of(
                "A critical hit focused weapon."
            ),
            0.5f,
            DEFAULT_ROTATION,
            -0.1f
        ),
        new LegendaryRecipeDefinition(
            "royal_roulette",
            "Royal Roulette",
            Items::royalRoulette,
            null,
            List.of(
                ingredient(Material.CRAFTER, 16, "Crafters"),
                ingredient(Material.DIAMOND_BLOCK, 4, "Diamond Blocks"),
                ingredient(Material.COMPASS, 1, "Compass"),
                ingredient(Material.REDSTONE_LAMP, 32, "Redstone Lamps")
            ),
            List.of(
                "Random legendary generator.",
                "High-cost, high-risk craft."
            ),
            1.0f,
            DEFAULT_ROTATION,
            0f
        )
    );
    private static final List<LegendaryRecipeDefinition> PRODUCTION_REWARDS = ORIGINAL_EIGHT.stream()
        .filter(recipe -> !recipe.id().equals("royal_roulette"))
        .toList();
    private static final List<LegendaryRecipeDefinition> ALL_PEDESTAL_RECIPES = buildAllPedestalRecipes();
    private static final Map<String, LegendaryRecipeDefinition> ALL_RECIPES_BY_ID = ALL_PEDESTAL_RECIPES.stream()
        .collect(Collectors.toUnmodifiableMap(LegendaryRecipeDefinition::id, recipe -> recipe));

    private OriginalLegendaryRecipes() {}

    public static List<LegendaryRecipeDefinition> originalEight() {
        return ORIGINAL_EIGHT;
    }

    /** The production Hydra legendary pool. Royal Roulette is a generator, not one of its rewards. */
    public static List<LegendaryRecipeDefinition> productionRewards() {
        return PRODUCTION_REWARDS;
    }

    public static ItemStack randomProductionReward(RandomGenerator random) {
        if (random == null || PRODUCTION_REWARDS.isEmpty()) {
            return null;
        }
        return PRODUCTION_REWARDS.get(random.nextInt(PRODUCTION_REWARDS.size())).resultSupplier().get();
    }

    public static List<LegendaryRecipeDefinition> allPedestalRecipes() {
        return ALL_PEDESTAL_RECIPES;
    }

    public static Optional<LegendaryRecipeDefinition> byId(String id) {
        return Optional.ofNullable(ALL_RECIPES_BY_ID.get(id));
    }

    private static LegendaryIngredient ingredient(Material material, int amount, String label) {
        return new LegendaryIngredient(new ItemStack(material, amount), label);
    }

    private static LegendaryIngredient ingredient(String materialKey, Material fallback, int amount, String label) {
        Material material = Material.matchMaterial(materialKey);
        return ingredient(material == null ? fallback : material, amount, label);
    }

    private static List<LegendaryRecipeDefinition> buildAllPedestalRecipes() {
        List<LegendaryRecipeDefinition> recipes = new ArrayList<>(ORIGINAL_EIGHT);
        recipes.add(new LegendaryRecipeDefinition(
            "memory_breaker",
            "Mnemosyne",
            Items::memoryBreaker,
            ingredient(Material.NETHERITE_AXE, 1, "Netherite Axe"),
            List.of(
                ingredient(Material.AMETHYST_CLUSTER, 4, "Amethyst Clusters"),
                ingredient(Material.ECHO_SHARD, 8, "Echo Shards"),
                ingredient(Material.ENDER_EYE, 8, "Eyes of Ender")
            ),
            List.of(
                "Scrambles a victim's hotbar.",
                "Mind-break utility weapon."
            ),
            0.8f,
            FLAT_WEAPON_ROTATION,
            -0.05f
        ));
        recipes.add(new LegendaryRecipeDefinition(
            "sonic_wrench",
            "Sculk Wrench",
            Items::sonicWrench,
            ingredient(Material.BREEZE_ROD, 1, "Breeze Rod"),
            List.of(
                ingredient(Material.ECHO_SHARD, 8, "Echo Shards"),
                ingredient(Material.SCULK_CATALYST, 2, "Sculk Catalysts"),
                ingredient(Material.IRON_BLOCK, 4, "Iron Blocks")
            ),
            List.of(
                "Echo charges detonate on follow-up hits.",
                "Precision burst tool."
            ),
            0.85f,
            DEFAULT_ROTATION,
            0.0f
        ));
        recipes.add(new LegendaryRecipeDefinition(
            "aegis",
            "Aegis",
            Items::aegis,
            ingredient(Material.SHIELD, 1, "Shield"),
            List.of(
                ingredient(Material.NETHERITE_INGOT, 1, "Netherite Ingot"),
                ingredient(Material.OBSIDIAN, 12, "Obsidian"),
                ingredient(Material.TURTLE_SCUTE, 8, "Turtle Scutes")
            ),
            List.of(
                "Retaliatory shock shield.",
                "Built for punish windows."
            ),
            1.0f,
            DEFAULT_ROTATION,
            0.0f
        ));
        recipes.add(new LegendaryRecipeDefinition(
            "frozen_chestpiece",
            "Frozen Chestpiece",
            Items::frozenChestpiece,
            ingredient(Material.NETHERITE_CHESTPLATE, 1, "Netherite Chestplate"),
            List.of(
                ingredient(Material.BLUE_ICE, 16, "Blue Ice"),
                ingredient(Material.PACKED_ICE, 32, "Packed Ice"),
                ingredient(Material.POWDER_SNOW_BUCKET, 4, "Powder Snow Buckets")
            ),
            List.of(
                "Turns the wearer into a glacier.",
                "Mobility tank relic."
            ),
            0.85f,
            DEFAULT_ROTATION,
            -0.1f
        ));
        recipes.add(new LegendaryRecipeDefinition(
            "melted_scythe",
            "Melted Scythe",
            Items::meltedScythe,
            ingredient(Material.NETHERITE_HOE, 1, "Netherite Hoe"),
            List.of(
                ingredient(Material.BLAZE_ROD, 8, "Blaze Rods"),
                ingredient(Material.MAGMA_BLOCK, 16, "Magma Blocks"),
                ingredient(Material.LAVA_BUCKET, 4, "Lava Buckets")
            ),
            List.of(
                "Builds heat into burst damage.",
                "Aggressive fire melee."
            ),
            0.8f,
            FLAT_WEAPON_ROTATION,
            -0.05f
        ));
        recipes.add(new LegendaryRecipeDefinition(
            "goblin_mask",
            "Goblin Mask",
            Items::goblinMask,
            ingredient(Material.DIAMOND_HELMET, 1, "Diamond Helmet"),
            List.of(
                ingredient(Material.EMERALD, 16, "Emeralds"),
                ingredient(Material.RABBIT_HIDE, 12, "Rabbit Hide"),
                ingredient(Material.SLIME_BALL, 8, "Slime Balls")
            ),
            List.of(
                "Shrinks the wearer permanently.",
                "Gremlin-sized survivability."
            ),
            0.95f,
            DEFAULT_ROTATION,
            0.0f
        ));
        recipes.add(new LegendaryRecipeDefinition(
            "hooked_grappler",
            "Hooked Grappler",
            Items::hookedGrappler,
            ingredient(Material.FISHING_ROD, 1, "Fishing Rod"),
            List.of(
                ingredient("CHAIN", Material.IRON_BARS, 8, "Chains"),
                ingredient(Material.WIND_CHARGE, 8, "Wind Charges"),
                ingredient(Material.HEAVY_CORE, 1, "Heavy Core")
            ),
            List.of(
                "Momentum grapple with wall cling.",
                "Traversal legendary."
            ),
            0.8f,
            DEFAULT_ROTATION,
            0.05f
        ));
        recipes.add(new LegendaryRecipeDefinition(
            "diamond_totem",
            "Diamond Totem",
            Items::diamondTotem,
            ingredient(Material.TOTEM_OF_UNDYING, 1, "Totem of Undying"),
            List.of(
                ingredient(Material.DIAMOND_BLOCK, 4, "Diamond Blocks"),
                ingredient(Material.ENCHANTED_GOLDEN_APPLE, 1, "Enchanted Golden Apple"),
                ingredient(Material.GHAST_TEAR, 8, "Ghast Tears")
            ),
            List.of(
                "Infinite revival with a long cooldown.",
                "Defensive endgame relic."
            ),
            0.95f,
            DEFAULT_ROTATION,
            0.0f
        ));
        recipes.add(new LegendaryRecipeDefinition(
            "wonder_pickaxe",
            "Wonder Pickaxe",
            Items::wonderPickaxe,
            ingredient(Material.DIAMOND_PICKAXE, 1, "Diamond Pickaxe"),
            List.of(
                ingredient(Material.EXPERIENCE_BOTTLE, 32, "Bottles o' Enchanting"),
                ingredient(Material.LAPIS_BLOCK, 4, "Lapis Blocks"),
                ingredient(Material.ENCHANTING_TABLE, 1, "Enchanting Table")
            ),
            List.of(
                "Adds random enchants as you mine.",
                "Scaling utility relic."
            ),
            0.8f,
            FLAT_WEAPON_ROTATION,
            -0.05f
        ));
        recipes.add(new LegendaryRecipeDefinition(
            "loki_scepter",
            "Loki's Scepter",
            Items::lokiScepter,
            ingredient(Material.BLAZE_ROD, 1, "Blaze Rod"),
            List.of(
                ingredient(Material.ENDER_PEARL, 16, "Ender Pearls"),
                ingredient(Material.FIREWORK_STAR, 8, "Firework Stars"),
                ingredient(Material.AMETHYST_SHARD, 16, "Amethyst Shards")
            ),
            List.of(
                "Blink tool with vanish payoff.",
                "Trickster mobility weapon."
            ),
            0.8f,
            DEFAULT_ROTATION,
            0.0f
        ));
        recipes.add(new LegendaryRecipeDefinition(
            "druids_daggers",
            "Druid's Daggers",
            Items::druidsDaggers,
            ingredient(Material.IRON_SWORD, 2, "Iron Swords"),
            List.of(
                ingredient(Material.MOSS_BLOCK, 12, "Moss Blocks"),
                ingredient(Material.GLOW_BERRIES, 16, "Glow Berries"),
                ingredient(Material.FERMENTED_SPIDER_EYE, 4, "Fermented Spider Eyes")
            ),
            List.of(
                "Twin blades with lifesteal and inversion.",
                "Nature rot dueling set."
            ),
            0.8f,
            FLAT_WEAPON_ROTATION,
            -0.05f
        ));
        recipes.add(new LegendaryRecipeDefinition(
            "architects_drill",
            "Architect's Drill",
            Items::architectsDrill,
            ingredient(Material.DIAMOND_SWORD, 1, "Diamond Sword"),
            List.of(
                ingredient(Material.REDSTONE_BLOCK, 4, "Redstone Blocks"),
                ingredient(Material.PISTON, 8, "Pistons"),
                ingredient(Material.COPPER_BULB, 8, "Copper Bulbs")
            ),
            List.of(
                "Drill mode ramps into a burst combo.",
                "Siege duelist weapon."
            ),
            0.8f,
            FLAT_WEAPON_ROTATION,
            -0.05f
        ));
        recipes.add(new LegendaryRecipeDefinition(
            "plasma_distorter",
            "Plasma Distorter",
            Items::enderious,
            ingredient(Material.SNOWBALL, 16, "Snowballs"),
            List.of(
                ingredient(Material.ENDER_EYE, 12, "Eyes of Ender"),
                ingredient(Material.PURPUR_BLOCK, 16, "Purpur Blocks"),
                ingredient(Material.FIRE_CHARGE, 8, "Fire Charges")
            ),
            List.of(
                "Fires unstable plasma projectiles.",
                "Projectile pressure weapon."
            ),
            0.95f,
            DEFAULT_ROTATION,
            0.0f
        ));
        recipes.add(new LegendaryRecipeDefinition(
            "scarabs_wrath",
            "Scarab's Wrath",
            Items::scarabsWrath,
            ingredient(Material.GOLDEN_SWORD, 1, "Golden Sword"),
            List.of(
                ingredient(Material.SANDSTONE, 16, "Sandstone"),
                ingredient(Material.BLAZE_POWDER, 12, "Blaze Powder"),
                ingredient(Material.FERMENTED_SPIDER_EYE, 6, "Fermented Spider Eyes")
            ),
            List.of(
                "Applies sun rot that bursts if ignored.",
                "Pressure blade of the dunes."
            ),
            0.8f,
            FLAT_WEAPON_ROTATION,
            -0.05f
        ));
        return List.copyOf(recipes);
    }

    public record LegendaryRecipeDefinition(
        String id,
        String title,
        Supplier<ItemStack> resultSupplier,
        LegendaryIngredient baseIngredient,
        List<LegendaryIngredient> ingredients,
        List<String> notes,
        float displayScale,
        AxisAngle4f displayRotation,
        float displayHeight
    ) {
        public RecipeManager.PedestalRecipe toPedestalRecipe() {
            List<ItemStack> stacks = new ArrayList<>();
            if (baseIngredient != null) {
                stacks.add(baseIngredient.toItemStack());
            }
            for (LegendaryIngredient ingredient : ingredients) {
                stacks.add(ingredient.toItemStack());
            }
            AxisAngle4f rotation = new AxisAngle4f(
                displayRotation.angle,
                displayRotation.x,
                displayRotation.y,
                displayRotation.z
            );
            return new RecipeManager.PedestalRecipe(resultSupplier.get(), stacks, displayScale, rotation, displayHeight);
        }

        public List<LegendaryIngredient> allIngredients() {
            List<LegendaryIngredient> stacks = new ArrayList<>();
            if (baseIngredient != null) {
                stacks.add(baseIngredient);
            }
            stacks.addAll(ingredients);
            return stacks;
        }
    }

    public record LegendaryIngredient(ItemStack stack, String label) {
        public ItemStack toItemStack() {
            return stack.clone();
        }

        public String toDisplayLine() {
            return stack.getAmount() + "x " + label;
        }
    }
}
