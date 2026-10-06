package hgloot.plugin;

import java.util.Map;
import org.bukkit.inventory.ItemStack;

/** Slot choices for Boss Rush's existing fixed loadout; BossRushService owns the items. */
public final class BossRushKitDefinition implements KitDefinition {
    public static final String ID = "boss_rush";
    private static final Map<String, Integer> DEFAULT_SLOTS = Map.of(
        "sword", 0, "axe", 1, "bow", 2, "food", 6,
        "golden_apples", 7, "arrows", 8, "shield", KitLayout.OFFHAND_SLOT
    );

    @Override public String id() { return ID; }

    @Override public String displayName() { return "BOSS RUSH"; }

    @Override public Map<String, Integer> defaultSlots() { return DEFAULT_SLOTS; }

    @Override public Map<String, ItemStack> previewItems() { return BossRushService.standardKitItems(); }

    @Override public ItemStack[] previewArmor() { return BossRushService.standardKitArmor(); }

    @Override public boolean allowsOffhand(String itemId) { return "shield".equals(itemId); }
}
