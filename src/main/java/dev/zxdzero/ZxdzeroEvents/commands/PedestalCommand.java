package dev.zxdzero.ZxdzeroEvents.commands;

import dev.zxdzero.ZxdzeroEvents.PedestalManager;
import dev.zxdzero.ZxdzeroEvents.registries.RecipeManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class PedestalCommand implements TabExecutor {
    private final PedestalManager pedestalManager;

    public PedestalCommand(PedestalManager pedestalManager) {
        this.pedestalManager = pedestalManager;
    }

    public boolean execute(CommandSender sender, String[] args) {
        return onCommand(sender, null, "pedestal", args);
    }

    public @NotNull List<String> complete(String[] args) {
        if (args.length == 1) {
            return Arrays.asList("place", "refill", "remove");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("place")) {
            return RecipeManager.getAllRecipeIds();
        }
        return Collections.emptyList();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command!");
            return true;
        }

        if (args.length == 0) {
            player.sendMessage("§cUsage: /pedestal <place|refill|remove> [item_type]");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "place":
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /pedestal place <item_type>");
                    return true;
                }

                try {
                    pedestalManager.placePedestal(player.getLocation(), args[1]);
                    player.sendMessage("§aPedestal placed for " + args[1]);
                } catch (IllegalArgumentException e) {
                    player.sendMessage("§cInvalid item type: " + args[1]);
                }
                break;

            case "refill":
                BlockDisplay closestPedestal = null;
                double closestDistance = Double.MAX_VALUE;

                for (Entity entity : player.getNearbyEntities(5, 5, 5)) {
                    if (entity instanceof BlockDisplay base) {
                        // Check if this is actually a pedestal
                        if (base.getPersistentDataContainer().has(PedestalManager.pedestalKey, PersistentDataType.STRING)) {
                            double distance = player.getLocation().distanceSquared(base.getLocation());
                            if (distance < closestDistance) {
                                closestDistance = distance;
                                closestPedestal = base;
                            }
                        }
                    }
                }

                if (closestPedestal != null) {
                    if (pedestalManager.refillPedestal(closestPedestal)) {
                        player.sendMessage("§aPedestal refilled!");
                    } else {
                        player.sendMessage("§cThis pedestal cannot be refilled!");
                    }
                } else {
                    player.sendMessage("§cNo pedestal found nearby!");
                }
                break;

            case "remove":
                if (pedestalManager.removePedestal(player.getLocation(), 5.0)) {
                    player.sendMessage("§aPedestal removed!");
                } else {
                    player.sendMessage("§cNo pedestal found nearby!");
                }
                break;

            default:
                player.sendMessage("§cUsage: /pedestal <place|refill|remove> [item_type]");
        }

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] args) {
        return complete(args);
    }
}
