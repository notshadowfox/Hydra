package dev.zxdzero.ZxdzeroEvents;

import dev.zxdzero.ZxdzeroEvents.registries.RecipeManager;
import dev.zxdzero.ZxdzeroEvents.registries.IngredientConsumptionPlanner;
import hgloot.plugin.HGLootPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.UUID;

public class PedestalManager implements Listener {

    private static final String NAMESPACE = "zxdzeroevents";
    public static final NamespacedKey pedestalKey = new NamespacedKey(NAMESPACE, "pedestal_id");
    private final NamespacedKey itemIdKey = new NamespacedKey(NAMESPACE, "item_type");
    private final NamespacedKey textDisplayKey = new NamespacedKey(NAMESPACE, "text_display_uuid");
    private final NamespacedKey interactionKey = new NamespacedKey(NAMESPACE, "interaction_uuid");
    private final NamespacedKey itemDisplayKey = new NamespacedKey(NAMESPACE, "item_display_uuid");
    private final NamespacedKey pedestalBaseKey = new NamespacedKey(NAMESPACE, "pedestal_base_uuid");
    private final NamespacedKey freeRewardKey = new NamespacedKey(NAMESPACE, "free_reward");
    private final JavaPlugin plugin;

    public PedestalManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void placePedestal(Location location, String id) {
        placePedestal(location, id, false);
    }

    public void placeRewardPedestal(Location location, String id) {
        placePedestal(location, id, true);
    }

    private void placePedestal(Location location, String id, boolean freeReward) {
        RecipeManager.PedestalRecipe recipe = RecipeManager.getRecipe(id);
        if (recipe == null) {
            throw new IllegalArgumentException("Invalid pedestal id");
        }

        UUID pedestalId = UUID.randomUUID();

        BlockDisplay base = (BlockDisplay) location.getWorld().spawnEntity(location, EntityType.BLOCK_DISPLAY);
        base.setGravity(false);

        Transformation transform = new Transformation(
                new Vector3f(-0.25f, 0, -0.25f),           // translation (offset)
                new AxisAngle4f(0, 0, 0, 1),     // left rotation (no rotation)
                new Vector3f(0.5f, 1.0f, 0.5f),  // scale
                new AxisAngle4f(0, 0, 0, 1)      // right rotation (no rotation)
        );
        base.setTransformation(transform);
        base.setRotation(0,0);
        base.setBlock(Material.QUARTZ_PILLAR.createBlockData());

        base.addScoreboardTag("pedestal");
        base.getPersistentDataContainer().set(pedestalKey, PersistentDataType.STRING, pedestalId.toString());
        base.getPersistentDataContainer().set(itemIdKey, PersistentDataType.STRING, id);
        if (freeReward) {
            base.getPersistentDataContainer().set(freeRewardKey, PersistentDataType.BYTE, (byte) 1);
        }

        // Create spinning item display
        Location itemLoc = location.clone().add(0, 1.8, 0);
        ItemDisplay itemDisplay = createItemDisplay(itemLoc, recipe);

        // Create hologram text
        Location textLoc = location.clone().add(0, 2.5, 0);
        TextDisplay textDisplay = createTextDisplay(textLoc,
                freeReward ? rewardText(recipe) : recipe.getRecipeText());

        // Create interaction entity
        Location interactionLoc = location.clone().add(0, 1, 0);
        Interaction interaction = (Interaction) location.getWorld().spawnEntity(interactionLoc, EntityType.INTERACTION);
        interaction.setInteractionWidth(1.5f);
        interaction.setInteractionHeight(2.0f);
        interaction.getPersistentDataContainer().set(pedestalBaseKey, PersistentDataType.STRING, base.getUniqueId().toString());

        // Store entity UUIDs in pedestal base NBT
        base.getPersistentDataContainer().set(textDisplayKey, PersistentDataType.STRING, textDisplay.getUniqueId().toString());
        base.getPersistentDataContainer().set(interactionKey, PersistentDataType.STRING, interaction.getUniqueId().toString());
        base.getPersistentDataContainer().set(itemDisplayKey, PersistentDataType.STRING, itemDisplay.getUniqueId().toString());
    }

    /**
     * Creates a TextDisplay entity
     */
    private TextDisplay createTextDisplay(Location location, Component text) {
        TextDisplay textDisplay = (TextDisplay) location.getWorld().spawnEntity(location, EntityType.TEXT_DISPLAY);
        textDisplay.text(text);
        textDisplay.setBillboard(TextDisplay.Billboard.CENTER);
        textDisplay.setGravity(false);
        return textDisplay;
    }

    private Component rewardText(RecipeManager.PedestalRecipe recipe) {
        return Component.text("Click here to grab ", NamedTextColor.YELLOW)
                .append(recipe.result().displayName().color(NamedTextColor.GOLD));
    }

    /**
     * Creates an ItemDisplay entity with custom scaling and rotation based on recipe properties.
     */
    private ItemDisplay createItemDisplay(Location location, RecipeManager.PedestalRecipe recipe) {
        ItemDisplay itemDisplay = (ItemDisplay) location.getWorld().spawnEntity(location, EntityType.ITEM_DISPLAY);
        itemDisplay.setItemStack(recipe.result());
        itemDisplay.setGravity(false);

        itemDisplay.setBillboard(TextDisplay.Billboard.CENTER);



        float scale = recipe.getDisplayScale();
        Transformation transformation = new Transformation(
                new Vector3f(0f, recipe.getDisplayHeight(), 0f),
                recipe.getDisplayRotation(),
                new Vector3f(scale, scale, scale),
                new AxisAngle4f(0, 0, 0, 1)
        );
        itemDisplay.setTransformation(transformation);

        return itemDisplay;
    }

    public boolean refillPedestal(BlockDisplay base) {
        String pedestalIdStr = base.getPersistentDataContainer().get(pedestalKey, PersistentDataType.STRING);
        String itemType = base.getPersistentDataContainer().get(itemIdKey, PersistentDataType.STRING);

        if (pedestalIdStr == null || itemType == null) return false;

        // Check if pedestal already has entities (is already filled)
        String textDisplayIdStr = base.getPersistentDataContainer().get(textDisplayKey, PersistentDataType.STRING);
        if (textDisplayIdStr != null) {
            // Already filled, don't refill
            return false;
        }

        try {
            RecipeManager.PedestalRecipe recipe = RecipeManager.getRecipe(itemType);
            if (recipe == null) return false;

            Location location = base.getLocation();

            // Create spinning item display
            Location itemLoc = location.clone().add(0, 1.8, 0);
            ItemDisplay itemDisplay = createItemDisplay(itemLoc, recipe);

            // Create hologram text
            Location textLoc = location.clone().add(0, 2.5, 0);
            TextDisplay textDisplay = createTextDisplay(textLoc, recipe.getRecipeText());

            // Create interaction entity
            Location interactionLoc = location.clone().add(0, 1, 0);
            Interaction interaction = (Interaction) location.getWorld().spawnEntity(interactionLoc, EntityType.INTERACTION);
            interaction.setInteractionWidth(1.5f);
            interaction.setInteractionHeight(2.0f);
            interaction.getPersistentDataContainer().set(pedestalBaseKey, PersistentDataType.STRING, base.getUniqueId().toString());

            // Store entity UUIDs in pedestal base NBT
            base.getPersistentDataContainer().set(textDisplayKey, PersistentDataType.STRING, textDisplay.getUniqueId().toString());
            base.getPersistentDataContainer().set(interactionKey, PersistentDataType.STRING, interaction.getUniqueId().toString());
            base.getPersistentDataContainer().set(itemDisplayKey, PersistentDataType.STRING, itemDisplay.getUniqueId().toString());

            return true;

        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid item type stored in pedestal: " + itemType);
            return false;
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Interaction interaction)) return;

        String pedestalBaseIdStr = interaction.getPersistentDataContainer().get(pedestalBaseKey, PersistentDataType.STRING);
        if (pedestalBaseIdStr == null) return;

        UUID pedestalBaseId = UUID.fromString(pedestalBaseIdStr);
        BlockDisplay base = (BlockDisplay) Bukkit.getEntity(pedestalBaseId);
        if (base == null) return;

        String itemType = base.getPersistentDataContainer().get(itemIdKey, PersistentDataType.STRING);
        if (itemType == null) return;

        Player player = event.getPlayer();
        RecipeManager.PedestalRecipe recipe = RecipeManager.getRecipe(itemType);
        if (recipe == null) return;

        boolean freeReward = base.getPersistentDataContainer().has(freeRewardKey, PersistentDataType.BYTE);
        IngredientConsumptionPlanner.ConsumptionPlan consumptionPlan = freeReward
                ? IngredientConsumptionPlanner.plan(player.getInventory().getContents(), java.util.List.of()).orElse(null)
                : IngredientConsumptionPlanner.plan(player.getInventory().getContents(), recipe.ingredients()).orElse(null);
        if (consumptionPlan != null && consumptionPlan.apply(player.getInventory())) {

            // Give result item
            player.getInventory().addItem(recipe.result());

            // Get and remove entities using stored UUIDs
            String textDisplayIdStr = base.getPersistentDataContainer().get(textDisplayKey, PersistentDataType.STRING);
            String interactionIdStr = base.getPersistentDataContainer().get(interactionKey, PersistentDataType.STRING);
            String itemDisplayIdStr = base.getPersistentDataContainer().get(itemDisplayKey, PersistentDataType.STRING);

            if (textDisplayIdStr != null) {
                TextDisplay textDisplay = (TextDisplay) Bukkit.getEntity(UUID.fromString(textDisplayIdStr));
                if (textDisplay != null) textDisplay.remove();
            }

            if (interactionIdStr != null) {
                Interaction interactionEntity = (Interaction) Bukkit.getEntity(UUID.fromString(interactionIdStr));
                if (interactionEntity != null) interactionEntity.remove();
            }

            if (itemDisplayIdStr != null) {
                ItemDisplay itemDisplay = (ItemDisplay) Bukkit.getEntity(UUID.fromString(itemDisplayIdStr));
                if (itemDisplay != null) itemDisplay.remove();
            }

            if (freeReward) {
                // One-shot reward pedestals disappear; crafting pedestals retain their refillable base.
                base.remove();
            } else {
                base.getPersistentDataContainer().remove(textDisplayKey);
                base.getPersistentDataContainer().remove(interactionKey);
                base.getPersistentDataContainer().remove(itemDisplayKey);
            }

            player.sendMessage(freeReward ? "§aLegendary claimed!" : "§aCrafting successful!");
            Component claimMessage = Component.text()
                    .append(Component.text(player.getName(), NamedTextColor.GREEN))
                    .append(Component.text(freeReward ? " claimed " : " has successfully crafted a ", NamedTextColor.YELLOW))
                    .append(recipe.result().displayName().color(NamedTextColor.GREEN)
                    .append(Component.text("!", NamedTextColor.YELLOW))).build();
            if (!(plugin instanceof HGLootPlugin hydra) || hydra.bossRushService() == null
                    || !hydra.bossRushService().broadcastEncounterMessage(player, claimMessage)) {
                player.getWorld().sendMessage(claimMessage);
            }
        } else {
            player.sendMessage("§cYou don't have all the required ingredients!");
        }
    }

    public boolean removePedestal(BlockDisplay base) {
        // Verify this is actually a pedestal base
        String pedestalIdStr = base.getPersistentDataContainer().get(pedestalKey, PersistentDataType.STRING);
        if (pedestalIdStr == null) {
            return false; // Not a pedestal
        }

        // Get and remove associated entities using stored UUIDs
        String textDisplayIdStr = base.getPersistentDataContainer().get(textDisplayKey, PersistentDataType.STRING);
        String interactionIdStr = base.getPersistentDataContainer().get(interactionKey, PersistentDataType.STRING);
        String itemDisplayIdStr = base.getPersistentDataContainer().get(itemDisplayKey, PersistentDataType.STRING);

        // Remove text display
        if (textDisplayIdStr != null) {
            TextDisplay textDisplay = (TextDisplay) Bukkit.getEntity(UUID.fromString(textDisplayIdStr));
            if (textDisplay != null) {
                textDisplay.remove();
            }
        }

        // Remove interaction entity
        if (interactionIdStr != null) {
            Interaction interaction = (Interaction) Bukkit.getEntity(UUID.fromString(interactionIdStr));
            if (interaction != null) {
                interaction.remove();
            }
        }

        // Remove item display
        if (itemDisplayIdStr != null) {
            ItemDisplay itemDisplay = (ItemDisplay) Bukkit.getEntity(UUID.fromString(itemDisplayIdStr));
            if (itemDisplay != null) {
                itemDisplay.remove();
            }
        }

        // Finally remove the base itself
        base.remove();

        return true; // Successfully removed
    }

    // Overloaded method to remove pedestal by location (finds nearest pedestal base)
    public boolean removePedestal(Location location, double radius) {
        BlockDisplay closestPedestal = null;
        double closestDistance = Double.MAX_VALUE;

        for (Entity entity : location.getWorld().getNearbyEntities(location, radius, radius, radius)) {
            if (entity instanceof BlockDisplay stand) {
                String pedestalIdStr = stand.getPersistentDataContainer().get(pedestalKey, PersistentDataType.STRING);
                if (pedestalIdStr != null) {
                    double distance = location.distanceSquared(stand.getLocation());
                    if (distance < closestDistance) {
                        closestDistance = distance;
                        closestPedestal = stand;
                    }
                }
            }
        }

        if (closestPedestal != null) {
            return removePedestal(closestPedestal);
        }

        return false; // No pedestal found in radius
    }
}
