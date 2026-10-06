package hgloot.plugin;

import java.util.Objects;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Public entrypoint for the editable NPC dialogue named "guide". */
public final class GuideService {
    private final NpcDialogueService dialogueService;

    public GuideService(NpcDialogueService dialogueService) {
        this.dialogueService = Objects.requireNonNull(dialogueService, "dialogueService");
    }

    public boolean handleCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use the Guide.");
            return true;
        }
        dialogueService.open(player, "guide");
        return true;
    }
}
