package hgloot.plugin.network;

import java.util.List;
import java.util.UUID;

public final class NetworkDtos {
    private NetworkDtos() {}

    public record Member(UUID uuid, String name) {}

    public record QueueJoinRequest(
        UUID requestId,
        UUID leaderId,
        String mode,
        UUID partyId,
        List<Member> members
    ) {}

    public record QueueJoinResponse(boolean accepted, String message, String queueId) {}

    public record PlayerAssignment(
        String state,
        String targetServer,
        String reservationId,
        String queueId,
        String mode,
        UUID partyId,
        List<Member> members
    ) {
        public boolean queued() {
            return "QUEUED".equalsIgnoreCase(state) || "RESERVED".equalsIgnoreCase(state);
        }

        public boolean assignedToMatch() {
            return "RESERVED".equalsIgnoreCase(state) || "ACTIVE".equalsIgnoreCase(state);
        }
    }

    public record Reservation(
        String reservationId,
        String state,
        String mode,
        List<Member> members,
        List<Team> teams
    ) {}

    public record Team(UUID teamId, List<UUID> members) {}

    public record BasicResponse(boolean accepted, String message) {}
}
