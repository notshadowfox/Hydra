package hgloot.plugin;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

final class RoulettePodiumDialog {
    private static final int BODY_WIDTH = 420;

    private RoulettePodiumDialog() {
    }

    static Dialog create(Snapshot snapshot, RouletteTeam viewerTeam) {
        List<DialogBody> body = new ArrayList<>();
        if (snapshot.preview()) {
            body.add(DialogBody.plainMessage(
                Component.text("STAFF PREVIEW - SAMPLE RESULTS", NamedTextColor.YELLOW), BODY_WIDTH));
        }
        body.add(DialogBody.plainMessage(champions(snapshot), BODY_WIDTH));
        body.add(DialogBody.plainMessage(standings(snapshot), BODY_WIDTH));
        body.add(DialogBody.plainMessage(viewerResult(snapshot, viewerTeam), BODY_WIDTH));
        body.add(DialogBody.plainMessage(Component.text(
            "The tournament is complete. Thanks for playing!", NamedTextColor.GRAY),
            BODY_WIDTH));

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

    private static Component champions(Snapshot snapshot) {
        Component result = Component.text("CHAMPIONS", NamedTextColor.GOLD, TextDecoration.BOLD);
        for (RouletteTeam winner : snapshot.winners()) {
            result = result.appendNewline()
                .append(teamMarker(winner))
                .append(Component.text(" " + winner.displayName() + " Team", teamColor(winner), TextDecoration.BOLD));
        }
        if (snapshot.winners().size() > 1) {
            result = result.appendNewline().append(Component.text("Shared Victory", NamedTextColor.YELLOW));
        }
        return result;
    }

    private static Component standings(Snapshot snapshot) {
        Component result = Component.text("FINAL STANDINGS", NamedTextColor.AQUA, TextDecoration.BOLD);
        for (Standing standing : snapshot.standings()) {
            result = result.appendNewline()
                .append(Component.text(standing.placement() + ". ", NamedTextColor.GRAY))
                .append(teamMarker(standing.team()))
                .append(Component.text(" " + standing.team().displayName() + " Team", teamColor(standing.team())))
                .append(Component.text("  " + standing.points() + " pts", NamedTextColor.WHITE))
                .append(Component.text(" | " + standing.roundWins() + " "
                    + (standing.roundWins() == 1 ? "win" : "wins"), NamedTextColor.GRAY));
        }
        return result;
    }

    private static Component viewerResult(Snapshot snapshot, RouletteTeam viewerTeam) {
        Standing standing = snapshot.standings().stream()
            .filter(candidate -> candidate.team() == viewerTeam)
            .findFirst()
            .orElse(null);
        Component result = Component.text("YOUR RESULT", NamedTextColor.AQUA, TextDecoration.BOLD)
            .appendNewline()
            .append(Component.text("Team: ", NamedTextColor.GRAY))
            .append(teamMarker(viewerTeam))
            .append(Component.text(" " + viewerTeam.displayName(), teamColor(viewerTeam)));
        if (standing == null) return result;
        result = result.appendNewline()
            .append(Component.text("Final Place: ", NamedTextColor.GRAY))
            .append(Component.text(RouletteIntermissionDialog.ordinal(standing.placement()), NamedTextColor.WHITE))
            .appendNewline()
            .append(Component.text("Tournament Points: ", NamedTextColor.GRAY))
            .append(Component.text(standing.points(), NamedTextColor.WHITE));
        if (snapshot.winners().contains(viewerTeam)) {
            result = result.appendNewline().append(Component.text(
                "Winner Reward: 800 XP, 700 Coins, 4 Shard Fragments, 1 Win", NamedTextColor.GREEN));
        }
        return result;
    }

    private static Component teamMarker(RouletteTeam team) {
        return Component.text("\u25a0", teamColor(team));
    }

    private static NamedTextColor teamColor(RouletteTeam team) {
        return switch (team) {
            case RED -> NamedTextColor.RED;
            case BLUE -> NamedTextColor.BLUE;
            case GREEN -> NamedTextColor.GREEN;
            case YELLOW -> NamedTextColor.YELLOW;
        };
    }

    record Snapshot(List<Standing> standings, List<RouletteTeam> winners, boolean preview) {
        Snapshot {
            standings = List.copyOf(standings);
            winners = List.copyOf(winners);
        }
    }

    record Standing(int placement, RouletteTeam team, int points, int roundWins) {
    }
}
