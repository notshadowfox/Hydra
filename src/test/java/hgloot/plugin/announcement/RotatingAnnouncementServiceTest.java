package hgloot.plugin.announcement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hgloot.plugin.HydraMessages;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class RotatingAnnouncementServiceTest {
    @Test
    void lockedTipsUseOneCompactHydraPrefix() {
        assertEquals(10, RotatingAnnouncementService.DEFAULT_MESSAGES.size());
        assertEquals("Disconnected mid-match? Use /rejoin to jump back in.",
            RotatingAnnouncementService.DEFAULT_MESSAGES.getFirst());
        assertEquals("Found a bug? Report what happened, what mode you were in, and how to reproduce it!",
            RotatingAnnouncementService.DEFAULT_MESSAGES.getLast());
        for (String tip : RotatingAnnouncementService.DEFAULT_MESSAGES) {
            Component formatted = RotatingAnnouncementService.formatTip(tip);
            assertEquals("[!] " + tip, PlainTextComponentSerializer.plainText().serialize(formatted));
            List<Span> spans = new ArrayList<>();
            collectSpans(formatted, null, spans);
            for (Span span : spans) {
                if (span.text().isEmpty()) continue;
                TextColor expected = span.text().equals("[!]") ? NamedTextColor.AQUA
                    : span.text().matches("/[A-Za-z][A-Za-z0-9_-]*") ? HydraMessages.INFO_BLUE
                    : NamedTextColor.WHITE;
                assertEquals(expected, span.color(), "Wrong color for " + span.text());
            }
        }
    }

    @Test
    void colorsOnlyCommandTokensWhenSeveralAppearWithPunctuation() {
        Component formatted = RotatingAnnouncementService.formatTip("Use /pc, then /tc!");
        List<Span> spans = new ArrayList<>();
        collectSpans(formatted, null, spans);
        assertEquals(List.of(
            new Span("[!]", NamedTextColor.AQUA),
            new Span(" ", NamedTextColor.WHITE),
            new Span("Use ", NamedTextColor.WHITE),
            new Span("/pc", HydraMessages.INFO_BLUE),
            new Span(", then ", NamedTextColor.WHITE),
            new Span("/tc", HydraMessages.INFO_BLUE),
            new Span("!", NamedTextColor.WHITE)
        ), spans.stream().filter(span -> !span.text().isEmpty()).toList());
    }

    @Test
    void migratesOnlyTheOldDefaultPairAndPreservesCadence() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("announcements.rotating.interval-seconds", 300);
        config.set("announcements.rotating.messages", List.of(
            "&bUse /rejoin if you disconnect from an active match.",
            "&eReport bugs in Discord so they can be fixed before launch."
        ));

        assertTrue(RotatingAnnouncementService.migrateLegacyDefaults(config));
        assertEquals(RotatingAnnouncementService.DEFAULT_MESSAGES,
            config.getStringList("announcements.rotating.messages"));
        assertEquals(300, config.getInt("announcements.rotating.interval-seconds"));
        assertFalse(RotatingAnnouncementService.migrateLegacyDefaults(config));
    }

    @Test
    void customTipListsRemainEditable() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("announcements.rotating.messages", List.of("Custom tip"));

        assertFalse(RotatingAnnouncementService.migrateLegacyDefaults(config));
        assertEquals(List.of("Custom tip"), config.getStringList("announcements.rotating.messages"));
    }

    @Test
    void previousDefaultTipsUpdateToSingleWordCommands() {
        YamlConfiguration config = new YamlConfiguration();
        List<String> previous = new java.util.ArrayList<>(RotatingAnnouncementService.DEFAULT_MESSAGES);
        previous.set(2, "Use /party chat to chat with your party, then use it again to return to public chat!");
        previous.set(3, "Use /team chat in team modes to chat only with your teammates!");
        config.set("announcements.rotating.messages", previous);

        assertTrue(RotatingAnnouncementService.migrateLegacyDefaults(config));
        assertEquals(RotatingAnnouncementService.DEFAULT_MESSAGES,
            config.getStringList("announcements.rotating.messages"));
        assertTrue(RotatingAnnouncementService.DEFAULT_MESSAGES.get(2).contains("/partychat"));
        assertTrue(RotatingAnnouncementService.DEFAULT_MESSAGES.get(3).contains("/teamchat"));
    }

    private static void collectSpans(Component component, TextColor inherited, List<Span> spans) {
        TextColor color = component.color() == null ? inherited : component.color();
        if (component instanceof TextComponent text && !text.content().isEmpty()) {
            spans.add(new Span(text.content(), color));
        }
        for (Component child : component.children()) {
            collectSpans(child, color, spans);
        }
    }

    private record Span(String text, TextColor color) {
    }
}
