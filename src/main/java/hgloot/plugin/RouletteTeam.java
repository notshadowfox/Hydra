package hgloot.plugin;

import org.bukkit.ChatColor;

public enum RouletteTeam {
    RED("Red", ChatColor.RED),
    BLUE("Blue", ChatColor.BLUE),
    GREEN("Green", ChatColor.GREEN),
    YELLOW("Yellow", ChatColor.YELLOW);

    private final String displayName;
    private final ChatColor color;

    RouletteTeam(String displayName, ChatColor color) {
        this.displayName = displayName;
        this.color = color;
    }

    public String displayName() {
        return displayName;
    }

    public ChatColor color() {
        return color;
    }
}
