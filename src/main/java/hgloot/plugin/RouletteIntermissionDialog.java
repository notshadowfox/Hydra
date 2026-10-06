package hgloot.plugin;

import hgloot.plugin.ManagedMinigameRound.Game;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

final class RouletteIntermissionDialog {
    private static final int BODY_WIDTH = 420;

    private RouletteIntermissionDialog() {
    }

    static Dialog create(Snapshot snapshot, RouletteTeam viewerTeam, int teamPlayers, int currentPlace) {
        Component roundHeading = snapshot.nextRoundNumber() > 4
            ? Component.text("INTERMISSION - FINAL RESULTS", NamedTextColor.GRAY)
            : Component.text("INTERMISSION - ROUND " + snapshot.nextRoundNumber() + "/4", NamedTextColor.GRAY);
        Component summary = Component.text()
            .append(Component.text("LAST ROUND: ", NamedTextColor.GRAY))
            .append(Component.text(snapshot.lastRound().displayName(), NamedTextColor.WHITE))
            .appendNewline()
            .append(Component.text("NEXT ROUND: ", NamedTextColor.GRAY))
            .append(Component.text(snapshot.nextRoundName(), NamedTextColor.WHITE))
            .appendNewline()
            .append(Component.text("STARTING IN: ", NamedTextColor.GRAY))
            .append(Component.text("15s", NamedTextColor.AQUA))
            .build();

        Component standings = Component.text("CURRENT STANDINGS", NamedTextColor.AQUA, TextDecoration.BOLD)
            .appendNewline()
            .append(teamStanding(snapshot, RouletteTeam.RED))
            .appendNewline()
            .append(teamStanding(snapshot, RouletteTeam.BLUE))
            .appendNewline()
            .append(teamStanding(snapshot, RouletteTeam.GREEN))
            .appendNewline()
            .append(teamStanding(snapshot, RouletteTeam.YELLOW));

        Component yourTeam = Component.text("YOUR TEAM", NamedTextColor.AQUA, TextDecoration.BOLD)
            .appendNewline()
            .append(Component.text("Team: ", NamedTextColor.GRAY))
            .append(teamMarker(viewerTeam))
            .append(Component.text(" " + viewerTeam.displayName(), teamColor(viewerTeam)))
            .appendNewline()
            .append(Component.text("Players: ", NamedTextColor.GRAY))
            .append(Component.text(teamPlayers, NamedTextColor.WHITE))
            .appendNewline()
            .append(Component.text("Current Place: ", NamedTextColor.GRAY))
            .append(Component.text(ordinal(currentPlace), NamedTextColor.WHITE));

        Component results = Component.text("LAST ROUND RESULTS", NamedTextColor.AQUA, TextDecoration.BOLD);
        if (snapshot.results().isEmpty()) {
            results = results.appendNewline().append(Component.text(snapshot.resultNote(), NamedTextColor.YELLOW));
        } else {
            for (ResultLine line : snapshot.results()) {
                results = results.appendNewline()
                    .append(Component.text(line.placement() + ". ", NamedTextColor.GRAY))
                    .append(Component.text(line.name(), NamedTextColor.WHITE))
                    .append(Component.text("  ", NamedTextColor.GRAY))
                    .append(line.teamResult() ? Component.empty() : teamMarker(line.team()))
                    .append(line.teamResult() ? Component.empty()
                        : Component.text(" " + line.team().displayName() + " Team", teamColor(line.team())))
                    .append(Component.text("  +" + line.totalPoints(), NamedTextColor.GREEN))
                    .appendNewline()
                    .append(Component.text("   " + scoreBreakdown(snapshot.lastRound(), line), NamedTextColor.GRAY));
            }
        }

        List<DialogBody> body = new ArrayList<>();
        body.add(DialogBody.plainMessage(roundHeading, BODY_WIDTH));
        body.add(DialogBody.plainMessage(summary, BODY_WIDTH));
        body.add(DialogBody.plainMessage(standings, BODY_WIDTH));
        body.add(DialogBody.plainMessage(yourTeam, BODY_WIDTH));
        body.add(DialogBody.plainMessage(results, BODY_WIDTH));
        body.add(DialogBody.plainMessage(Component.text(
            "The next match starts automatically.\nClose this menu or wait for the countdown.",
            NamedTextColor.GRAY), BODY_WIDTH));

        ActionButton close = ActionButton.create(Component.text("Close", NamedTextColor.WHITE), null, 160, null);
        return Dialog.create(factory -> factory.empty()
            .base(DialogBase.builder(Component.text("MINIGAME ROULETTE", NamedTextColor.AQUA, TextDecoration.BOLD))
                .canCloseWithEscape(true)
                .pause(false)
                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                .body(body)
                .build())
            .type(DialogType.notice(close)));
    }

    private static Component teamStanding(Snapshot snapshot, RouletteTeam team) {
        return teamMarker(team)
            .append(Component.text(" " + team.displayName() + "  ", teamColor(team)))
            .append(Component.text(snapshot.teamPoints().getOrDefault(team, 0), NamedTextColor.WHITE));
    }

    private static Component teamMarker(RouletteTeam team) {
        return Component.text("■", teamColor(team));
    }

    private static NamedTextColor teamColor(RouletteTeam team) {
        return switch (team) {
            case RED -> NamedTextColor.RED;
            case BLUE -> NamedTextColor.BLUE;
            case GREEN -> NamedTextColor.GREEN;
            case YELLOW -> NamedTextColor.YELLOW;
        };
    }

    static String ordinal(int value) {
        int mod100 = value % 100;
        if (mod100 >= 11 && mod100 <= 13) return value + "th";
        return value + switch (value % 10) {
            case 1 -> "st";
            case 2 -> "nd";
            case 3 -> "rd";
            default -> "th";
        };
    }

    static String scoreBreakdown(ResultLine line) {
        return scoreBreakdown(Game.BRIDGE, line);
    }

    static String scoreBreakdown(Game game, ResultLine line) {
        if (line.teamResult()) {
            if (game == Game.BOSS_RUSH) {
                return ordinal(line.placement()) + " Place +" + line.placementPoints()
                    + " | Boss Damage " + line.eliminations();
            }
            return ordinal(line.placement()) + " Place +" + line.placementPoints() + " | "
                + line.eliminations() + " Kills | Kill Bonus +" + line.killBonus();
        }
        String kills = line.eliminations() + " Kills";
        if (line.killBonus() > 0) kills += " +" + line.killBonus();
        return ordinal(line.placement()) + " Place +" + line.placementPoints() + " | " + kills;
    }

    record Snapshot(
        Game lastRound,
        String nextRoundName,
        int nextRoundNumber,
        Map<RouletteTeam, Integer> teamPoints,
        List<ResultLine> results,
        String resultNote
    ) {
        Snapshot {
            teamPoints = Map.copyOf(teamPoints);
            results = List.copyOf(results);
            resultNote = resultNote == null ? "" : resultNote;
        }
    }

    record ResultLine(
        int placement,
        String name,
        RouletteTeam team,
        int totalPoints,
        int placementPoints,
        int eliminations,
        int killBonus,
        boolean teamResult
    ) {
    }
}
