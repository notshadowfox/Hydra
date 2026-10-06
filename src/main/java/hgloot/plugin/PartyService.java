package hgloot.plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class PartyService {
    private static final int MAX_PARTY_SIZE = 4;
    private static final long INVITE_LIFETIME_MS = 60_000L;
    private static final long OFFLINE_GRACE_MS = 60_000L;

    private final Map<UUID, Party> partiesByMember = new HashMap<>();
    private final Map<UUID, Map<UUID, Long>> incomingInvites = new HashMap<>();
    private final Map<UUID, Long> disconnectedAt = new HashMap<>();
    private Predicate<UUID> mutationLocked = ignored -> false;
    private Predicate<UUID> inviteAllowed = ignored -> true;
    private BukkitTask cleanupTask;

    public PartyService() {}

    public synchronized void setInviteAllowed(Predicate<UUID> inviteAllowed) {
        this.inviteAllowed = inviteAllowed == null ? ignored -> true : inviteAllowed;
    }

    public synchronized void start(JavaPlugin plugin, Predicate<UUID> mutationLocked) {
        this.mutationLocked = mutationLocked == null ? ignored -> false : mutationLocked;
        if (cleanupTask != null) {
            cleanupTask.cancel();
        }
        cleanupTask = Bukkit.getScheduler().runTaskTimer(plugin, this::cleanupExpiredState, 20L, 20L);
    }

    public synchronized void shutdown() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
            cleanupTask = null;
        }
        partiesByMember.clear();
        incomingInvites.clear();
        disconnectedAt.clear();
    }

    public synchronized void markOffline(UUID playerId) {
        if (playerId != null && partiesByMember.containsKey(playerId)) {
            disconnectedAt.put(playerId, System.currentTimeMillis());
        }
    }

    public synchronized void markOnline(UUID playerId) {
        if (playerId != null) {
            disconnectedAt.remove(playerId);
        }
    }

    public synchronized boolean isInParty(UUID playerId) {
        return partiesByMember.containsKey(playerId);
    }

    public synchronized boolean isLeader(UUID playerId) {
        Party party = partiesByMember.get(playerId);
        return party != null && party.leader.equals(playerId);
    }

    public synchronized boolean isPartyLocked(UUID playerId) {
        Party party = partiesByMember.get(playerId);
        if (party == null) {
            return isMutationLocked(playerId);
        }
        return party.members.stream().anyMatch(this::isMutationLocked);
    }

    public synchronized int getPartySize(UUID playerId) {
        Party party = partiesByMember.get(playerId);
        return party == null ? 0 : party.members.size();
    }

    public int getMaxPartySize() {
        return MAX_PARTY_SIZE;
    }

    public synchronized UUID getLeader(UUID playerId) {
        Party party = partiesByMember.get(playerId);
        return party == null ? null : party.leader;
    }

    public synchronized List<UUID> getPartyMembers(UUID playerId) {
        Party party = partiesByMember.get(playerId);
        return party == null ? List.of() : List.copyOf(party.members);
    }

    public synchronized PartySnapshot snapshot(UUID playerId) {
        Party party = partiesByMember.get(playerId);
        return party == null ? null : new PartySnapshot(party.leader, List.copyOf(party.members), party.visibility);
    }

    /** Restores a proxy-owned party after its members return to this backend. */
    public synchronized void restoreNetworkParty(UUID leaderId, List<UUID> memberIds) {
        if (leaderId == null || memberIds == null || memberIds.isEmpty() || memberIds.size() > MAX_PARTY_SIZE
            || !memberIds.contains(leaderId)) {
            return;
        }
        Party existing = partiesByMember.get(leaderId);
        if (existing != null && existing.leader.equals(leaderId) && existing.members.equals(new LinkedHashSet<>(memberIds))) {
            for (UUID memberId : memberIds) {
                disconnectedAt.remove(memberId);
            }
            return;
        }
        for (UUID memberId : memberIds) {
            Party old = partiesByMember.get(memberId);
            if (old != null) {
                removeMemberInternal(old, memberId);
            }
        }
        Party restored = new Party(leaderId);
        restored.members.addAll(memberIds);
        for (UUID memberId : memberIds) {
            partiesByMember.put(memberId, restored);
            disconnectedAt.remove(memberId);
        }
    }

    public synchronized PartyVisibility getVisibility(UUID playerId) {
        Party party = partiesByMember.get(playerId);
        return party == null ? PartyVisibility.PRIVATE : party.visibility;
    }

    public synchronized VisibilityResult toggleVisibility(UUID leaderId) {
        Party party = partiesByMember.get(leaderId);
        if (party == null) return VisibilityResult.NOT_IN_PARTY;
        if (!party.leader.equals(leaderId)) return VisibilityResult.NOT_LEADER;
        if (isPartyLocked(leaderId)) return VisibilityResult.LOCKED;
        party.visibility = party.visibility == PartyVisibility.PUBLIC
            ? PartyVisibility.PRIVATE
            : PartyVisibility.PUBLIC;
        return party.visibility == PartyVisibility.PUBLIC
            ? VisibilityResult.MADE_PUBLIC
            : VisibilityResult.MADE_PRIVATE;
    }

    public synchronized PublicJoinResult joinPublic(UUID memberId, UUID leaderId) {
        if (memberId == null || leaderId == null || memberId.equals(leaderId)) return PublicJoinResult.SELF;
        if (isMutationLocked(memberId) || isPartyLocked(leaderId)) return PublicJoinResult.LOCKED;
        if (partiesByMember.containsKey(memberId)) return PublicJoinResult.ALREADY_IN_PARTY;
        Party party = partiesByMember.get(leaderId);
        if (party == null || !party.leader.equals(leaderId)) return PublicJoinResult.UNAVAILABLE;
        if (party.visibility != PartyVisibility.PUBLIC) return PublicJoinResult.PRIVATE;
        if (party.members.size() >= MAX_PARTY_SIZE) return PublicJoinResult.PARTY_FULL;
        party.members.add(memberId);
        partiesByMember.put(memberId, party);
        disconnectedAt.remove(memberId);
        incomingInvites.remove(memberId);
        return PublicJoinResult.JOINED;
    }

    public synchronized List<UUID> getIncomingInvites(UUID playerId) {
        pruneExpiredInvites(System.currentTimeMillis());
        Map<UUID, Long> invites = incomingInvites.get(playerId);
        return invites == null ? List.of() : List.copyOf(invites.keySet());
    }

    public synchronized CreateResult createParty(UUID leaderId) {
        if (isMutationLocked(leaderId)) {
            return CreateResult.LOCKED;
        }
        if (partiesByMember.containsKey(leaderId)) {
            return CreateResult.ALREADY_IN_PARTY;
        }
        Party party = new Party(leaderId);
        party.members.add(leaderId);
        partiesByMember.put(leaderId, party);
        return CreateResult.CREATED;
    }

    public synchronized InviteResult invite(UUID inviterId, UUID targetId) {
        if (inviterId.equals(targetId)) {
            return InviteResult.SELF;
        }
        Party inviterParty = partiesByMember.get(inviterId);
        if (inviterParty == null) {
            return InviteResult.NOT_IN_PARTY;
        }
        if (!inviterParty.leader.equals(inviterId)) {
            return InviteResult.NOT_LEADER;
        }
        if (inviterParty.members.size() >= MAX_PARTY_SIZE) {
            return InviteResult.PARTY_FULL;
        }

        Party targetParty = partiesByMember.get(targetId);
        if (targetParty != null) {
            return targetParty == inviterParty ? InviteResult.SAME_PARTY : InviteResult.TARGET_IN_PARTY;
        }
        if (!inviteAllowed.test(targetId)) return InviteResult.INVITES_DISABLED;

        pruneExpiredInvites(System.currentTimeMillis());
        Map<UUID, Long> pending = incomingInvites.computeIfAbsent(targetId, ignored -> new LinkedHashMap<>());
        if (pending.containsKey(inviterId)) {
            return InviteResult.ALREADY_INVITED;
        }
        pending.put(inviterId, System.currentTimeMillis() + INVITE_LIFETIME_MS);
        return InviteResult.SENT;
    }

    public synchronized InviteResponse acceptInvite(UUID targetId, UUID inviterId) {
        pruneExpiredInvites(System.currentTimeMillis());
        Map<UUID, Long> pending = incomingInvites.get(targetId);
        if (pending == null || !pending.containsKey(inviterId)) {
            return InviteResponse.NO_INVITE;
        }
        if (isMutationLocked(targetId) || isPartyLocked(inviterId)) {
            return InviteResponse.LOCKED;
        }
        if (partiesByMember.containsKey(targetId)) {
            removeInvite(targetId, inviterId);
            return InviteResponse.TARGET_IN_PARTY;
        }

        Party inviterParty = partiesByMember.get(inviterId);
        if (inviterParty == null || !inviterParty.leader.equals(inviterId)) {
            removeInvite(targetId, inviterId);
            return InviteResponse.INVITER_UNAVAILABLE;
        }
        if (inviterParty.members.size() >= MAX_PARTY_SIZE) {
            removeInvite(targetId, inviterId);
            return InviteResponse.PARTY_FULL;
        }

        inviterParty.members.add(targetId);
        partiesByMember.put(targetId, inviterParty);
        disconnectedAt.remove(targetId);
        incomingInvites.remove(targetId);
        return InviteResponse.ACCEPTED;
    }

    public synchronized InviteResponse denyInvite(UUID targetId, UUID inviterId) {
        pruneExpiredInvites(System.currentTimeMillis());
        Map<UUID, Long> pending = incomingInvites.get(targetId);
        if (pending == null || pending.remove(inviterId) == null) {
            return InviteResponse.NO_INVITE;
        }
        cleanupInviteSet(targetId);
        return InviteResponse.DENIED;
    }

    public synchronized LeaveResult leave(UUID playerId) {
        Party party = partiesByMember.get(playerId);
        if (party == null) {
            return LeaveResult.NOT_IN_PARTY;
        }
        if (isPartyLocked(playerId)) {
            return LeaveResult.LOCKED;
        }
        if (party.leader.equals(playerId)) {
            disbandInternal(party);
            return LeaveResult.LEADER_DISBANDED;
        }
        removeMemberInternal(party, playerId);
        return LeaveResult.LEFT;
    }

    public synchronized DisbandResult disband(UUID leaderId) {
        Party party = partiesByMember.get(leaderId);
        if (party == null) {
            return DisbandResult.NOT_IN_PARTY;
        }
        if (!party.leader.equals(leaderId)) {
            return DisbandResult.NOT_LEADER;
        }
        if (isPartyLocked(leaderId)) {
            return DisbandResult.LOCKED;
        }
        disbandInternal(party);
        return DisbandResult.DISBANDED;
    }

    public synchronized KickResult kick(UUID leaderId, UUID memberId) {
        Party party = partiesByMember.get(leaderId);
        if (party == null) {
            return KickResult.NOT_IN_PARTY;
        }
        if (!party.leader.equals(leaderId)) {
            return KickResult.NOT_LEADER;
        }
        if (leaderId.equals(memberId)) {
            return KickResult.CANNOT_KICK_SELF;
        }
        if (!party.members.contains(memberId)) {
            return KickResult.NOT_IN_YOUR_PARTY;
        }
        removeMemberInternal(party, memberId);
        return KickResult.KICKED;
    }

    public synchronized PromoteResult promote(UUID leaderId, UUID memberId) {
        Party party = partiesByMember.get(leaderId);
        if (party == null) {
            return PromoteResult.NOT_IN_PARTY;
        }
        if (!party.leader.equals(leaderId)) {
            return PromoteResult.NOT_LEADER;
        }
        if (isPartyLocked(leaderId)) {
            return PromoteResult.LOCKED;
        }
        if (leaderId.equals(memberId)) {
            return PromoteResult.CANNOT_PROMOTE_SELF;
        }
        if (!party.members.contains(memberId)) {
            return PromoteResult.NOT_IN_YOUR_PARTY;
        }
        party.leader = memberId;
        return PromoteResult.PROMOTED;
    }

    public String displayName(UUID playerId) {
        OfflinePlayer offline = Bukkit.getOfflinePlayer(playerId);
        String name = offline.getName();
        return name != null && !name.isBlank() ? name : playerId.toString().substring(0, 8);
    }

    private synchronized void cleanupExpiredState() {
        long now = System.currentTimeMillis();
        pruneExpiredInvites(now);
        Set<Party> parties = new HashSet<>(partiesByMember.values());
        for (Party party : parties) {
            for (UUID memberId : new ArrayList<>(party.members)) {
                Long disconnected = disconnectedAt.get(memberId);
                if (disconnected != null && now - disconnected >= OFFLINE_GRACE_MS) {
                    removeMemberInternal(party, memberId);
                }
            }
            if (!party.members.isEmpty() && !party.members.contains(party.leader)) {
                party.leader = party.members.iterator().next();
            }
        }
    }

    private boolean isMutationLocked(UUID playerId) {
        return playerId != null && mutationLocked.test(playerId);
    }

    private void removeMemberInternal(Party party, UUID memberId) {
        party.members.remove(memberId);
        partiesByMember.remove(memberId);
        disconnectedAt.remove(memberId);
        clearOutgoingInvites(Set.of(memberId));
        incomingInvites.remove(memberId);
        if (party.members.isEmpty()) {
            return;
        }
        if (party.leader.equals(memberId)) {
            party.leader = party.members.iterator().next();
        }
    }

    private void disbandInternal(Party party) {
        Set<UUID> members = new HashSet<>(party.members);
        for (UUID memberId : members) {
            partiesByMember.remove(memberId);
            incomingInvites.remove(memberId);
            disconnectedAt.remove(memberId);
        }
        clearOutgoingInvites(members);
    }

    private void clearOutgoingInvites(Set<UUID> inviters) {
        for (Map<UUID, Long> invites : incomingInvites.values()) {
            invites.keySet().removeIf(inviters::contains);
        }
        incomingInvites.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    private void pruneExpiredInvites(long now) {
        for (Map<UUID, Long> invites : incomingInvites.values()) {
            invites.entrySet().removeIf(entry -> entry.getValue() <= now);
        }
        incomingInvites.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    private void removeInvite(UUID targetId, UUID inviterId) {
        Map<UUID, Long> invites = incomingInvites.get(targetId);
        if (invites != null) {
            invites.remove(inviterId);
            cleanupInviteSet(targetId);
        }
    }

    private void cleanupInviteSet(UUID targetId) {
        Map<UUID, Long> invites = incomingInvites.get(targetId);
        if (invites != null && invites.isEmpty()) {
            incomingInvites.remove(targetId);
        }
    }

    public record PartySnapshot(UUID leaderId, List<UUID> members, PartyVisibility visibility) {
        public PartySnapshot {
            members = List.copyOf(members);
        }
    }

    private static final class Party {
        private UUID leader;
        private final LinkedHashSet<UUID> members = new LinkedHashSet<>();
        private PartyVisibility visibility = PartyVisibility.PRIVATE;

        private Party(UUID leader) {
            this.leader = leader;
        }
    }

    public enum CreateResult { CREATED, ALREADY_IN_PARTY, LOCKED }

    public enum InviteResult {
        SENT, SELF, NOT_IN_PARTY, NOT_LEADER, PARTY_FULL, TARGET_IN_PARTY, SAME_PARTY, ALREADY_INVITED, LOCKED,
        INVITES_DISABLED
    }

    public enum InviteResponse {
        ACCEPTED, DENIED, NO_INVITE, INVITER_UNAVAILABLE, TARGET_IN_PARTY, PARTY_FULL, LOCKED
    }

    public enum LeaveResult { LEFT, LEADER_DISBANDED, NOT_IN_PARTY, LOCKED }

    public enum DisbandResult { DISBANDED, NOT_IN_PARTY, NOT_LEADER, LOCKED }

    public enum KickResult { KICKED, NOT_IN_PARTY, NOT_LEADER, CANNOT_KICK_SELF, NOT_IN_YOUR_PARTY, LOCKED }

    public enum PromoteResult { PROMOTED, NOT_IN_PARTY, NOT_LEADER, CANNOT_PROMOTE_SELF, NOT_IN_YOUR_PARTY, LOCKED }

    public enum PartyVisibility { PUBLIC, PRIVATE }

    public enum VisibilityResult { MADE_PUBLIC, MADE_PRIVATE, NOT_IN_PARTY, NOT_LEADER, LOCKED }

    public enum PublicJoinResult { JOINED, SELF, LOCKED, ALREADY_IN_PARTY, UNAVAILABLE, PRIVATE, PARTY_FULL }
}
