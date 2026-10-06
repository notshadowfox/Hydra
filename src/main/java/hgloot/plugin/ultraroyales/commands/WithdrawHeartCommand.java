package hgloot.plugin.ultraroyales.commands;

import hgloot.plugin.ultraroyales.Items;
import net.kyori.adventure.text.Component;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class WithdrawHeartCommand implements CommandExecutor {
    @Override
    public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String @NotNull [] strings) {
        if (!(commandSender instanceof Player player)) {
            commandSender.sendMessage(Component.text("Only players can use this command!"));
            return true;
        }

        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        maxHealth.setBaseValue(maxHealth.getBaseValue() - 2);
        player.give(Items.heartItem());

        return true;
    }
}

