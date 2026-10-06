package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class NpcDialogueServiceTest {
    @Test
    void parsesEverySupportedTypedAction() {
        assertEquals(NpcDialogueService.DialogueActionType.PAGE,
            NpcDialogueService.DialogueAction.parse("page:games").type());
        assertEquals("games", NpcDialogueService.DialogueAction.parse("page:games").value());
        assertEquals(NpcDialogueService.DialogueActionType.CLOSE,
            NpcDialogueService.DialogueAction.parse("close").type());
        assertEquals(NpcDialogueService.DialogueActionType.PLAYER_COMMAND,
            NpcDialogueService.DialogueAction.parse("player:navigator").type());
        assertEquals(NpcDialogueService.DialogueActionType.CONSOLE_COMMAND,
            NpcDialogueService.DialogueAction.parse("console:give {player} stone").type());
    }

    @Test
    void rejectsMalformedOrUnknownActions() {
        assertNull(NpcDialogueService.DialogueAction.parse(null));
        assertNull(NpcDialogueService.DialogueAction.parse(""));
        assertNull(NpcDialogueService.DialogueAction.parse("page:"));
        assertNull(NpcDialogueService.DialogueAction.parse("url:https://example.com"));
    }

    @Test
    void dialogueAndPageMapsAreImmutableSnapshots() {
        Map<Integer, NpcDialogueService.DialogueButton> mutableButtons = new LinkedHashMap<>();
        mutableButtons.put(1, new NpcDialogueService.DialogueButton("Games", "page:games"));
        NpcDialogueService.DialoguePage page = new NpcDialogueService.DialoguePage(
            "main", "Text", 3, mutableButtons, "", "Next");
        mutableButtons.clear();
        assertEquals(1, page.buttons().size());
        assertThrows(UnsupportedOperationException.class,
            () -> page.buttons().put(2, new NpcDialogueService.DialogueButton("Close", "close")));

        Map<String, NpcDialogueService.DialoguePage> mutablePages = new LinkedHashMap<>();
        mutablePages.put("main", page);
        NpcDialogueService.DialogueDefinition definition = new NpcDialogueService.DialogueDefinition(
            "guide", "Guide", "main", mutablePages);
        mutablePages.clear();
        assertEquals(1, definition.pages().size());
        assertThrows(UnsupportedOperationException.class, () -> definition.pages().clear());
    }
}
