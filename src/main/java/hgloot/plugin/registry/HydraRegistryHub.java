package hgloot.plugin.registry;

public final class HydraRegistryHub {
    private final HydraItemRegistry itemRegistry = new HydraItemRegistry();
    private final HydraRecipeRegistry recipeRegistry = new HydraRecipeRegistry();

    public HydraItemRegistry itemRegistry() {
        return itemRegistry;
    }

    public HydraRecipeRegistry recipeRegistry() {
        return recipeRegistry;
    }
}
