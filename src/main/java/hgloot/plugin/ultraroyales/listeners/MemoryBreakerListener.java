package hgloot.plugin.ultraroyales.listeners;

import hgloot.plugin.HydraMessages;
import hgloot.plugin.ultraroyales.UltraRoyales;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class MemoryBreakerListener implements Listener {

    private static final String MODEL_ID = "ultraroyales:memory_breaker";
    private static final int SCRAMBLE_DURATION_TICKS = 80;
    private final Map<UUID, ScrambleState> scrambledPlayers = new HashMap<>();

    @EventHandler
    public void onPlayerHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) {
            return;
        }
        if (!(event.getEntity() instanceof Player target)) {
            return;
        }
        if (!isMemoryBreaker(attacker.getInventory().getItemInMainHand())) {
            return;
        }
        scrambleHotbar(target);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        clearState(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        clearState(event.getPlayer().getUniqueId());
    }

    private void scrambleHotbar(Player target) {
        UUID targetId = target.getUniqueId();
        if (scrambledPlayers.containsKey(targetId)) {
            return;
        }

        ItemStack[] original = new ItemStack[9];
        List<ItemStack> shuffled = new ArrayList<>(9);
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = cloneOrNull(target.getInventory().getItem(slot));
            original[slot] = stack;
            shuffled.add(cloneOrNull(stack));
        }
        Collections.shuffle(shuffled);
        for (int slot = 0; slot < 9; slot++) {
            target.getInventory().setItem(slot, cloneOrNull(shuffled.get(slot)));
        }
        target.updateInventory();
        target.getWorld().spawnParticle(Particle.TRIAL_OMEN, target.getLocation().add(0.0D, 1.0D, 0.0D), 22, 0.35D, 0.45D, 0.35D, 0.0D);
        target.getWorld().spawnParticle(Particle.ENCHANT, target.getLocation().add(0.0D, 1.0D, 0.0D), 18, 0.45D, 0.55D, 0.45D, 0.1D);
        target.sendMessage(HydraMessages.error("Mnemosyne scrambled your hotbar"));
        target.playSound(target.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.9f, 0.7f);

        int taskId = Bukkit.getScheduler().runTaskLater(UltraRoyales.getPlugin(), () -> restoreHotbar(targetId), SCRAMBLE_DURATION_TICKS).getTaskId();
        scrambledPlayers.put(targetId, new ScrambleState(original, taskId));
    }

    private void restoreHotbar(UUID targetId) {
        ScrambleState state = scrambledPlayers.remove(targetId);
        if (state == null) {
            return;
        }
        Player target = Bukkit.getPlayer(targetId);
        if (target == null || !target.isOnline()) {
            return;
        }
        for (int slot = 0; slot < 9; slot++) {
            target.getInventory().setItem(slot, cloneOrNull(state.originalHotbar[slot]));
        }
        target.updateInventory();
        target.getWorld().spawnParticle(Particle.END_ROD, target.getLocation().add(0.0D, 1.0D, 0.0D), 18, 0.35D, 0.45D, 0.35D, 0.02D);
        target.sendMessage(HydraMessages.success("Your hotbar has been restored"));
        target.playSound(target.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.35f);
    }

    private void clearState(UUID playerId) {
        ScrambleState state = scrambledPlayers.remove(playerId);
        if (state == null) {
            return;
        }
        Bukkit.getScheduler().cancelTask(state.restoreTaskId);
    }

    private boolean isMemoryBreaker(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta.hasCustomModelDataComponent()
            && meta.getCustomModelDataComponent().getStrings().contains(MODEL_ID);
    }

    private ItemStack cloneOrNull(ItemStack stack) {
        return stack == null ? null : stack.clone();
    }

    private static final class ScrambleState {
        private final ItemStack[] originalHotbar;
        private final int restoreTaskId;

        private ScrambleState(ItemStack[] originalHotbar, int restoreTaskId) {
            this.originalHotbar = originalHotbar;
            this.restoreTaskId = restoreTaskId;
        }
    }
}
