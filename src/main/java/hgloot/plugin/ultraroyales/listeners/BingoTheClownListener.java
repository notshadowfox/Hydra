package hgloot.plugin.ultraroyales.listeners;

import hgloot.plugin.ultraroyales.Items;
import hgloot.plugin.ultraroyales.UltraRoyales;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Skeleton;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class BingoTheClownListener implements Listener {
    public static final NamespacedKey BINGO_TRIES = new NamespacedKey("ultra_royals", "bingo_tries");

    private static final Random random = new Random();
    private static final Map<UUID, BukkitRunnable> runnableMap = new HashMap<>();
    private static final Map<UUID, BukkitRunnable> runnableMapF = new HashMap<>();

    private static final int MAX_TRIES = 5 + 1; // Actual max tries + 1
    private static final int delayBetweenTasks = 20 * 3;

    String[] messages = {
            "Let's see what you get...",
            "...",
    };

    public static void startMove(Skeleton skeleton) {
        BukkitRunnable runnable = new BukkitRunnable() {
            @Override
            public void run() {
                if (!skeleton.isValid() || skeleton.isDead()) {
                    cancel();
                    runnableMap.remove(skeleton.getUniqueId());
                    return;
                }

                Location location = skeleton.getLocation();
                double dx = random.nextInt(7) - 3;
                double dz = random.nextInt(7) - 3;
                Location target = location.clone().add(dx, 0, dz);
                skeleton.getPathfinder().moveTo(target);
            }
        };
        runnable.runTaskTimer(UltraRoyales.getPlugin(), 0L, 80L);
        runnableMap.put(skeleton.getUniqueId(), runnable);
    }

    public static void startFriendly(Skeleton skeleton) {
        BukkitRunnable runnable = new BukkitRunnable() {
            @Override
            public void run() {
                if (!skeleton.isValid() || skeleton.isDead()) {
                    cancel();
                    runnableMapF.remove(skeleton.getUniqueId());
                    return;
                }

                skeleton.setTarget(null);
                skeleton.setAggressive(false);
            }
        };
        runnable.runTaskTimer(UltraRoyales.getPlugin(), 0L, 0L);
        runnableMapF.put(skeleton.getUniqueId(), runnable);
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Skeleton skeleton)) return;
        if (skeleton.getEquipment().getHelmet().getType() != Material.DIRT) return;
        event.getDrops().clear();
        event.setDroppedExp(0);
        BukkitRunnable runnable = runnableMap.remove(skeleton.getUniqueId());
        BukkitRunnable runnableF = runnableMapF.remove(skeleton.getUniqueId());
        if (runnable != null) {
            runnable.cancel();
        }
        if (runnableF != null) {
            runnableF.cancel();
        }
    }

    @EventHandler
    public void onPlayerInteractAtEntityEvent(PlayerInteractAtEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        Entity entity = event.getRightClicked();

        if (!(entity instanceof Skeleton skeleton)) return;
        if (skeleton.getEquipment().getHelmet().getType() != Material.DIRT) return;

        if (getTriesCount(player) == 0) {
            player.sendMessage(bingoComponent("I’m Bingo! With a heart in your hand Right-click me to wager it for a chance to double the amount. You only get 3 tries—choose wisely!"));
            increaseTriesCount(player);
            return;
        }

        ItemStack item = player.getInventory().getItemInMainHand();

        if (getTriesCount(player) > 0 && item.hasItemMeta() && item.getItemMeta().hasCustomModelDataComponent() && item.getItemMeta().getCustomModelDataComponent().equals(Items.heartItem().getItemMeta().getCustomModelDataComponent())) {
            if (getTriesCount(player) < MAX_TRIES) {
                item.setAmount(item.getAmount() - 1);
                increaseTriesCount(player);

                for (int i = 0; i < messages.length; i++) {
                    final int taskNumber = i;
                    int delay = delayBetweenTasks * i;

                    Bukkit.getScheduler().runTaskLater(UltraRoyales.getPlugin(), () -> {
                        player.sendMessage(bingoComponent(messages[taskNumber]));
                    }, delay);
                }

                Random rand = new Random();
                boolean win = rand.nextDouble() < 0.66;

                int lastDelay = delayBetweenTasks * messages.length;

                Bukkit.getScheduler().runTaskLater(UltraRoyales.getPlugin(), () -> {
                    if (win) {
                        ItemStack reward = Items.heartItem();
                        reward.setAmount(2);
                        player.give(reward);
                        player.sendMessage(bingoComponent("Wow! You actually won!"));
                    } else {
                        player.sendMessage(bingoComponent("HAHA, You lose!"));
                    }
                }, lastDelay);
            } else {
                player.sendMessage(bingoComponent("You ran out of tries... I hope you don't regret it!"));
            }
        } else if (getTriesCount(player) > 0) {
            if (getTriesCount(player) < MAX_TRIES) {
                player.sendMessage(bingoComponent("Don't fool me! The item you holding is not a heart!"));
            } else {
                player.sendMessage(bingoComponent("You ran out of tries... I hope you don't regret it!"));
            }
        }
    }

    private static Component bingoComponent(String text) {
        return Component.text("").append(Component.text("Bingo: ").decoration(TextDecoration.BOLD, true)).append(Component.text(text).decoration(TextDecoration.BOLD, false));
    }

    private static int getTriesCount(Player player) {
        return player.getPersistentDataContainer().getOrDefault(BINGO_TRIES, PersistentDataType.INTEGER, 0);
    }

    private static void increaseTriesCount(Player player) {
        int tries = player.getPersistentDataContainer().getOrDefault(BINGO_TRIES, PersistentDataType.INTEGER, 0);
        tries++;
        player.getPersistentDataContainer().set(BINGO_TRIES, PersistentDataType.INTEGER, tries);
    }
}

