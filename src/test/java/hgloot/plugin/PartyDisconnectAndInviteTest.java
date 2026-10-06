package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PartyDisconnectAndInviteTest {
    @Test
    void disconnectedMembersExpireAfterOneMinuteEvenWhenPartyWasGameLocked() throws Exception {
        String source = Files.readString(Path.of("src/main/java/hgloot/plugin/PartyService.java"));

        assertTrue(source.contains("OFFLINE_GRACE_MS = 60_000L"));
        assertTrue(source.contains("runTaskTimer(plugin, this::cleanupExpiredState, 20L, 20L)"));
        assertFalse(source.contains("if (party.members.stream().anyMatch(this::isMutationLocked))"));
        assertTrue(source.contains("now - disconnected >= OFFLINE_GRACE_MS"));
    }

    @Test
    void partyInviteUsesClickableAuthoritativeAcceptCommand() throws Exception {
        String source = Files.readString(Path.of("src/main/java/hgloot/plugin/HGLootPlugin.java"));

        assertTrue(source.contains("[Click here to Join]"));
        assertTrue(source.contains("ClickEvent.runCommand(\"/party accept \" + player.getName())"));
        assertTrue(source.contains("partyService.acceptInvite(playerId, inviterId)"));
    }
}
