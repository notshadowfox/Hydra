package hgloot.plugin.ultraroyales.listeners;

import dev.zxdzero.ZxdzeroEvents.registries.CooldownRegistry;
import hgloot.plugin.HydraMessages;
import hgloot.plugin.ultraroyales.UltraRoyales;
import hgloot.plugin.util.HydraIcons;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

public class LegendaryRechargeBarListener implements Listener {

    private static BukkitTask task;
    private static final int HUD_SEGMENTS = 18;
    private static final Map<String, CooldownSpec> SPECS = new HashMap<>();
    private static final Map<UUID, Map<String, Integer>> LAST_REMAINING = new HashMap<>();

    static {
        register("ultraroyales:knightssaddle", "Knight's Saddle", () -> UltraRoyales.saddleCooldown, 45,
            HydraMessages.ready("Knight's Saddle"));
        register("ultraroyales:spiderstaff", "Spider Staff", () -> UltraRoyales.staffCooldown, 45,
            HydraMessages.ready("Spider Staff"));
        register("ultraroyales:ghlochester", "Ghlochester", () -> UltraRoyales.ghlochesterCooldown, 12,
            HydraMessages.ready("Ghlochester"));
        registerElectricConch("ultraroyales:electricconch");
        registerElectricConch("ultraroyales:electric_conch");
        registerElectricConch("ultraroyales:electricconch_futuristic");
        registerElectricConch("ultraroyales:electric_conch_futuristic");
        registerElectricConch("ultraroyales:electricconch_obsidian");
        registerElectricConch("ultraroyales:electric_conch_obsidian");
        register("ultraroyales:sonic_wrench", "Sculk Wrench", () -> UltraRoyales.sonicWrenchCooldown, 6,
            HydraMessages.ready("Sculk Wrench"));
        register("ultraroyales:aegis", "Aegis", () -> UltraRoyales.aegisCooldown, 120,
            HydraMessages.ready("Aegis"));
        register("ultraroyales:frozen_chestpiece", "Frozen Chestpiece", () -> UltraRoyales.frozenChestpieceCooldown, 120,
            HydraMessages.ready("Frozen Chestpiece"));
        register("ultraroyales:hoglin_horn", "Hoglin Horn", () -> UltraRoyales.hoglinHornCooldown, 15,
            HydraMessages.ready("Hoglin Horn"));
        register("ultraroyales:last_breath", "Last Breath", () -> UltraRoyales.lastBreathCooldown, 11,
            HydraMessages.ready("Last Breath"));
    }

    public LegendaryRechargeBarListener() {
        startTask();
    }

    private static void startTask() {
        if (task != null && !task.isCancelled()) {
            return;
        }

        task = Bukkit.getScheduler().runTaskTimer(UltraRoyales.getPlugin(), () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                tickRechargeMessages(player);
                updateMiniHud(player);
            }
        }, 10L, 10L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        LAST_REMAINING.remove(event.getPlayer().getUniqueId());
    }

    private static void tickRechargeMessages(Player player) {
        Map<String, Integer> state = LAST_REMAINING.computeIfAbsent(player.getUniqueId(), id -> new HashMap<>());
        for (Entry<String, CooldownSpec> entry : SPECS.entrySet()) {
            String model = entry.getKey();
            CooldownSpec spec = entry.getValue();
            NamespacedKey key = spec.keySupplier.get();
            if (key == null) {
                continue;
            }

            int current = CooldownRegistry.getCooldown(player, key);
            int previous = state.getOrDefault(model, 0);
            if (previous > 0 && current <= 0) {
                player.sendMessage(spec.readyMessage);
            }
            state.put(model, current);
        }
    }

    private static void updateMiniHud(Player player) {
        CooldownSelection selection = selectionFor(player);
        if (selection == null) {
            return;
        }

        CooldownSpec spec = selection.spec;
        NamespacedKey key = spec.keySupplier.get();
        if (key == null) {
            return;
        }

        int remaining = CooldownRegistry.getCooldown(player, key);
        if (remaining <= 0) {
            return;
        }

        double progress = 1.0 - (remaining / (double) Math.max(1, spec.totalSeconds));
        UltraRoyales.holdBossRushDamageActionBar(player, 1500L);
        player.sendActionBar(buildCooldownHud(spec.label, remaining, progress));
    }

    private static CooldownSelection selectionFor(Player player) {
        ItemStack main = player.getInventory().getItemInMainHand();
        ItemStack off = player.getInventory().getItemInOffHand();

        if (main != null && main.hasItemMeta()) {
            ItemMeta meta = main.getItemMeta();
            if (meta.hasCustomModelDataComponent() && !meta.getCustomModelDataComponent().getStrings().isEmpty()) {
                String model = meta.getCustomModelDataComponent().getStrings().get(0);
                CooldownSpec spec = SPECS.get(model);
                if (spec != null) {
                    return new CooldownSelection(model, spec);
                }
            }
        }

        if (off != null && off.hasItemMeta()) {
            ItemMeta meta = off.getItemMeta();
            if (meta.hasCustomModelDataComponent() && !meta.getCustomModelDataComponent().getStrings().isEmpty()) {
                String model = meta.getCustomModelDataComponent().getStrings().get(0);
                CooldownSpec spec = SPECS.get(model);
                if (spec != null) {
                    return new CooldownSelection(model, spec);
                }
            }
        }

        return null;
    }

    private static Component buildCooldownHud(String label, int remaining, double progress) {
        if ("Electric Conch".equals(label)) {
            return HydraIcons.spongeSaberWaterBar(0)
                .append(Component.text("  CD " + remaining + "s", NamedTextColor.AQUA));
        }

        int filledSegments = Math.max(0, Math.min(HUD_SEGMENTS, (int) Math.round(progress * HUD_SEGMENTS)));
        Component bar = Component.empty();
        for (int i = 0; i < HUD_SEGMENTS; i++) {
            bar = bar.append(Component.text(
                i < filledSegments ? "━" : "─",
                i < filledSegments ? HydraMessages.HYDRA_NEON_CYAN : HydraMessages.HYDRA_SOFT_DARK
            ));
        }

        return Component.text("⏳ ", HydraMessages.WARNING_ORANGE)
            .append(Component.text(label, HydraMessages.HYDRA_DEEP_CYAN))
            .append(Component.text("  ", HydraMessages.HYDRA_MUTED_AQUA))
            .append(bar)
            .append(Component.text("  ", HydraMessages.HYDRA_MUTED_AQUA))
            .append(Component.text(remaining + "s", HydraMessages.INFO_BLUE));
    }

    private static void register(
        String modelId,
        String label,
        Supplier<NamespacedKey> keySupplier,
        int totalSeconds,
        Component readyMessage
    ) {
        SPECS.put(modelId, new CooldownSpec(label, keySupplier, totalSeconds, readyMessage));
    }

    private static void registerElectricConch(String modelId) {
        register(modelId, "Electric Conch", () -> UltraRoyales.conchCooldown, 30,
            HydraMessages.ready("Electric Conch"));
    }

    private record CooldownSpec(
        String label,
        Supplier<NamespacedKey> keySupplier,
        int totalSeconds,
        Component readyMessage
    ) {
    }

    private record CooldownSelection(String modelId, CooldownSpec spec) {
    }
}
