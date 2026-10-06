package hgloot.plugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;

/** The HG team marker and Tab ordering shared by team-based match presentations. */
final class MatchTeamPresentation {
    private MatchTeamPresentation() {
    }

    static Component tabName(Component base, Integer colorRgb) {
        return colorRgb == null ? base
            : Component.text("◇ ", TextColor.color(colorRgb)).append(base);
    }

    static int tabOrder(Integer teamIndex, int rankOrder) {
        return teamIndex == null ? Math.max(0, 1000 - rankOrder)
            : 100_000 - teamIndex * 100 - rankOrder;
    }
}
