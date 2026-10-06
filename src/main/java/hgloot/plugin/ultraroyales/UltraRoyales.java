package hgloot.plugin.ultraroyales;

import hgloot.plugin.recipe.RecipeBookCatalog;
import hgloot.plugin.HGLootPlugin;
import hgloot.plugin.module.HydraModule;
import hgloot.plugin.registry.HydraRegistryHub;
import hgloot.plugin.ultraroyales.commands.WithdrawHeartCommand;
import hgloot.plugin.ultraroyales.listeners.*;
import dev.zxdzero.ZxdzeroEvents.registries.CooldownRegistry;
import dev.zxdzero.ZxdzeroEvents.registries.RecipeManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.command.PluginCommand;

import java.util.logging.Level;

public final class UltraRoyales implements HydraModule {

    private static JavaPlugin plugin;
    private final JavaPlugin owner;
    private final HydraRegistryHub registries;
    private DruidDaggersCombatHandler druidDaggersCombatHandler;
    private boolean enabled;

    public UltraRoyales(JavaPlugin owner, HydraRegistryHub registries) {
        this.owner = owner;
        this.registries = registries;
    }

    public static JavaPlugin getPlugin() { return plugin; }

    /** Preserve an item HUD over the ambient Boss Rush damage-race line. */
    public static void holdBossRushDamageActionBar(org.bukkit.entity.Player player, long millis) {
        if (plugin instanceof HGLootPlugin hydra && hydra.bossRushService() != null) {
            hydra.bossRushService().holdDamageActionBar(player, millis);
        }
    }

    public static NamespacedKey saddleCooldown;
    public static NamespacedKey staffCooldown;
    public static NamespacedKey saberCooldown;
    public static NamespacedKey conchCooldown;
    public static NamespacedKey ghlochesterCooldown;
    public static NamespacedKey sonicWrenchCooldown;
    public static NamespacedKey aegisCooldown;
    public static NamespacedKey frozenChestpieceCooldown;
    public static NamespacedKey hookedGrapplerCooldown;
    public static NamespacedKey diamondTotemCooldown;
    public static NamespacedKey lokiScepterCooldown;
    public static NamespacedKey enderiousCooldown;
    public static NamespacedKey enderiousItemKey;
    public static NamespacedKey hoglinHornCooldown;
    public static NamespacedKey lastBreathCooldown;
    public static NamespacedKey ghostKnifeCooldown;
    public static NamespacedKey lokiPearlKey;

    @Override
    public String key() {
        return "ultraroyales";
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void enable() {
        if (enabled) {
            return;
        }
        try {
            plugin = owner;
            PluginManager pluginManager = owner.getServer().getPluginManager();

            saddleCooldown = new NamespacedKey(plugin, "knights_saddle_cooldown");
            staffCooldown = new NamespacedKey(plugin, "spider_staff_cooldown");
            saberCooldown = new NamespacedKey(plugin, "sponge_saber_cooldown");
            conchCooldown = new NamespacedKey(plugin, "electric_conch_cooldown");
            ghlochesterCooldown = new NamespacedKey(plugin, "ghlochester_cooldown");
            sonicWrenchCooldown = new NamespacedKey(plugin, "sonic_wrench_cooldown");
            aegisCooldown = new NamespacedKey(plugin, "aegis_cooldown");
            frozenChestpieceCooldown = new NamespacedKey(plugin, "frozen_chestpiece_cooldown");
            hookedGrapplerCooldown = new NamespacedKey(plugin, "hooked_grappler_cooldown");
            diamondTotemCooldown = new NamespacedKey(plugin, "diamond_totem_cooldown");
            lokiScepterCooldown = new NamespacedKey(plugin, "loki_scepter_cooldown");
            enderiousCooldown = new NamespacedKey(plugin, "enderious_cooldown");
            enderiousItemKey = new NamespacedKey(plugin, "enderious_item");
            hoglinHornCooldown = new NamespacedKey(plugin, "hoglin_horn_cooldown");
            lastBreathCooldown = new NamespacedKey(plugin, "last_breath_cooldown");
            ghostKnifeCooldown = new NamespacedKey(plugin, "ghost_knife_cooldown");
            lokiPearlKey = new NamespacedKey(plugin, "loki_scepter_pearl");

            registerItemDefinitions();
            Items.registerBehavior();
            registerRecipes();

            pluginManager.registerEvents(new KnightsSaddleListener(), owner);
            pluginManager.registerEvents(new DwarvenBowListener(), owner);
            pluginManager.registerEvents(new GhlochesterListener(), owner);
            pluginManager.registerEvents(new SpiderStaffListener(), owner);
            pluginManager.registerEvents(new SpongeSaberListener(), owner);
            pluginManager.registerEvents(new SkeletalBarberListener(), owner);
            pluginManager.registerEvents(new ElectricConchListener(), owner);
            pluginManager.registerEvents(new MemoryBreakerListener(), owner);
            pluginManager.registerEvents(new SonicWrenchListener(), owner);
            pluginManager.registerEvents(new AegisListener(), owner);
            pluginManager.registerEvents(new FrozenChestpieceListener(), owner);
            pluginManager.registerEvents(new MeltedScytheListener(), owner);
            pluginManager.registerEvents(new GildedSwordEnchantListener(), owner);
            pluginManager.registerEvents(new BookOfWeldingListener(), owner);
            pluginManager.registerEvents(new LegendaryRechargeBarListener(), owner);
            pluginManager.registerEvents(new BingoTheClownListener(), owner);
            pluginManager.registerEvents(new LifeElixirListener(), owner);
            pluginManager.registerEvents(new BetaLegendaryListener(), owner);
            druidDaggersCombatHandler = new DruidDaggersCombatHandler((HGLootPlugin) owner);
            pluginManager.registerEvents(druidDaggersCombatHandler, owner);
            pluginManager.registerEvents(new MonarchWingsListener(), owner);
            pluginManager.registerEvents(new HoglinHornListener(), owner);
            pluginManager.registerEvents(new GhostKnifeListener((HGLootPlugin) owner), owner);
            pluginManager.registerEvents(new PodRecorder(), owner);
            pluginManager.registerEvents(new SpiderAIController(), owner);

            setCommandExecutor("withdrawheart", new WithdrawHeartCommand());

            CooldownRegistry.registerCooldown(saddleCooldown, Material.SADDLE);
            CooldownRegistry.registerCooldown(staffCooldown, Material.NAUTILUS_SHELL);
            CooldownRegistry.registerCooldown(saberCooldown, Material.DIAMOND_SWORD);
            CooldownRegistry.registerCooldown(conchCooldown, Material.TRIDENT);
            CooldownRegistry.registerCooldown(ghlochesterCooldown, Material.FISHING_ROD);
            CooldownRegistry.registerCooldown(sonicWrenchCooldown, Material.BREEZE_ROD);
            CooldownRegistry.registerCooldown(aegisCooldown, Material.SHIELD);
            CooldownRegistry.registerCooldown(frozenChestpieceCooldown, Material.NETHERITE_CHESTPLATE);
            CooldownRegistry.registerCooldown(hookedGrapplerCooldown, Material.FISHING_ROD);
            CooldownRegistry.registerCooldown(diamondTotemCooldown, Material.TOTEM_OF_UNDYING);
            CooldownRegistry.registerCooldown(lokiScepterCooldown, Material.BLAZE_ROD);
            CooldownRegistry.registerCooldown(enderiousCooldown, Material.SNOWBALL);
            CooldownRegistry.registerCooldown(hoglinHornCooldown, Material.GOAT_HORN);
            CooldownRegistry.registerCooldown(lastBreathCooldown, Material.CROSSBOW);
            CooldownRegistry.registerCooldown(ghostKnifeCooldown, Material.IRON_SWORD);

            Bukkit.removeRecipe(NamespacedKey.minecraft("mace"));
            enabled = true;
            owner.getLogger().info("[UltraRoyales] Module enabled.");
        } catch (Throwable throwable) {
            owner.getLogger().log(Level.SEVERE, "[UltraRoyales] Failed to enable module.", throwable);
            enabled = false;
        }
    }

    @Override
    public void disable() {
        if (!enabled) {
            return;
        }
        try {
            if (druidDaggersCombatHandler != null) {
                druidDaggersCombatHandler.shutdown();
                druidDaggersCombatHandler = null;
            }
            SpiderAIController.shutdown();
        } catch (Throwable throwable) {
            owner.getLogger().log(Level.SEVERE, "[UltraRoyales] Error while disabling module.", throwable);
        } finally {
            enabled = false;
        }
    }

    private void setCommandExecutor(String commandName, CommandExecutor executor) {
        PluginCommand command = owner.getCommand(commandName);
        if (command == null) {
            owner.getLogger().warning("[UltraRoyales] Command '" + commandName + "' is not defined in plugin.yml.");
            return;
        }
        command.setExecutor(executor);
        if (executor instanceof TabCompleter completer) {
            command.setTabCompleter(completer);
        }
    }

    private void registerItemDefinitions() {
        registries.itemRegistry().register("ultraroyales:knights_saddle", Items::knightsSaddle);
        registries.itemRegistry().register("ultraroyales:dwarven_bow", Items::dwarvenBow);
        registries.itemRegistry().register("ultraroyales:ghlochester", Items::ghlochester);
        registries.itemRegistry().register("ultraroyales:spider_staff", Items::spiderStaff);
        registries.itemRegistry().register("ultraroyales:sponge_saber", Items::spongeSaber);
        registries.itemRegistry().register("ultraroyales:wet_sponge_saber", Items::wetSpongeSaber);
        registries.itemRegistry().register("ultraroyales:skeletal_barber", Items::skeletalBarber);
        registries.itemRegistry().register("ultraroyales:electric_conch", Items::electricConch);
        registries.itemRegistry().register("ultraroyales:memory_breaker", Items::memoryBreaker);
        registries.itemRegistry().register("ultraroyales:sonic_wrench", Items::sonicWrench);
        registries.itemRegistry().register("ultraroyales:aegis", Items::aegis);
        registries.itemRegistry().register("ultraroyales:frozen_chestpiece", Items::frozenChestpiece);
        registries.itemRegistry().register("ultraroyales:melted_scythe", Items::meltedScythe);
        registries.itemRegistry().register("ultraroyales:royal_roulette", Items::royalRoulette);
        registries.itemRegistry().register("ultraroyales:heart", Items::heartItem);
        registries.itemRegistry().register("ultraroyales:life_elixir", () -> Items.lifeElixir(1));
        registries.itemRegistry().register("ultraroyales:book_of_welding", Items::bookOfWelding);
        registries.itemRegistry().register("ultraroyales:bingo_spawn_egg", Items::bingoSpawnEgg);
        registries.itemRegistry().register("ultraroyales:goblin_mask", Items::goblinMask);
        registries.itemRegistry().register("ultraroyales:hooked_grappler", Items::hookedGrappler);
        registries.itemRegistry().register("ultraroyales:diamond_totem", Items::diamondTotem);
        registries.itemRegistry().register("ultraroyales:wonder_pickaxe", Items::wonderPickaxe);
        registries.itemRegistry().register("ultraroyales:loki_scepter", Items::lokiScepter);
        registries.itemRegistry().register("ultraroyales:druids_daggers", Items::druidsDaggers);
        registries.itemRegistry().register("ultraroyales:architects_drill", Items::architectsDrill);
        registries.itemRegistry().register("ultraroyales:seventh_sword", Items::seventhSword);
        registries.itemRegistry().register("ultraroyales:enderious", Items::enderious);
        registries.itemRegistry().register("ultraroyales:scarabs_wrath", Items::scarabsWrath);
        registries.itemRegistry().register("ultraroyales:monarch_wings", Items::monarchWings);
        registries.itemRegistry().register("ultraroyales:hoglin_horn", Items::hoglinHorn);
        registries.itemRegistry().register("ultraroyales:ghost_knife", Items::ghostKnife);
    }

    private void registerRecipes() {
        for (OriginalLegendaryRecipes.LegendaryRecipeDefinition recipe : OriginalLegendaryRecipes.allPedestalRecipes()) {
            registerRecipe(recipe.id(), recipe.toPedestalRecipe());
        }
        registerCraftingRecipes();
    }

    private void registerCraftingRecipes() {
        registries.recipeRegistry().register("ultraroyales:life_elixir_crafting");
        for (Material material : RecipeBookCatalog.GILDED_SWORD_MATERIALS) {
            registries.recipeRegistry().register("hydra:gilded_" + material.name().toLowerCase());
        }
        // Ghost Knife is intentionally NOT craftable; it is admin-only via the Beta Legendaries menu.
    }

    private void registerRecipe(String id, RecipeManager.PedestalRecipe recipe) {
        registries.recipeRegistry().register("ultraroyales:" + id);
        RecipeManager.registerRecipe(plugin, id, recipe);
    }
}
