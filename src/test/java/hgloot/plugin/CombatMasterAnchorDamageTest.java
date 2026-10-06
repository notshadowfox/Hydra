package hgloot.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.World;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.WitherSkull;
import org.bukkit.entity.WitherSkeleton;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.junit.jupiter.api.Test;

class CombatMasterAnchorDamageTest {
    @Test
    void maximumExposureIsCappedAtSixHeartsForBossRushParticipant() throws Exception {
        Fixture fixture = new Fixture();
        EntityDamageEvent hit = fixture.event(fixture.boss, DamageCause.ENTITY_EXPLOSION, 56.1D);
        fixture.service.onAnchorExplosionDamage(hit);
        assertTrue(hit.getFinalDamage() <= 12.0D);
        assertTrue(hit.getFinalDamage() > 11.99D);
        assertFalse(hit.isCancelled());
    }

    @Test
    void capTargetsFinalDamageUnderNonlinearDiamondArmorMitigation() {
        EntityDamageEvent hit = mock(EntityDamageEvent.class);
        AtomicReference<Double> raw = new AtomicReference<>(56.1D);
        when(hit.getDamage()).thenAnswer(ignored -> raw.get());
        when(hit.getFinalDamage()).thenAnswer(ignored -> {
            double armor = Math.max(4.0D, Math.min(20.0D, 20.0D - raw.get() / 4.0D));
            return raw.get() * (1.0D - armor / 25.0D) * 0.84D;
        });
        org.mockito.Mockito.doAnswer(call -> {
            raw.set(call.getArgument(0));
            return null;
        }).when(hit).setDamage(org.mockito.ArgumentMatchers.anyDouble());

        assertTrue(hit.getFinalDamage() > 20.0D);
        CombatMasterService.capAnchorFinalDamage(hit);
        assertTrue(hit.getFinalDamage() <= 12.0D);
        assertTrue(hit.getFinalDamage() > 11.99D);
    }

    @Test
    void weakerVanillaAnchorHitIsUnchanged() throws Exception {
        Fixture fixture = new Fixture();
        EntityDamageEvent hit = fixture.event(fixture.boss, DamageCause.ENTITY_EXPLOSION, 7.5D);
        fixture.service.onAnchorExplosionDamage(hit);
        assertEquals(7.5D, hit.getDamage());
        assertEquals(7.5D, hit.getFinalDamage());
    }

    @Test
    void highRawDamageWithAlreadyMitigatedFinalDamageIsNotRaisedOrRewritten() {
        EntityDamageEvent hit = mock(EntityDamageEvent.class);
        when(hit.getFinalDamage()).thenReturn(9.0D);
        CombatMasterService.capAnchorFinalDamage(hit);
        verify(hit, never()).setDamage(org.mockito.ArgumentMatchers.anyDouble());
    }

    @Test
    void unrelatedExplosionsAndNonparticipantsRemainUnchanged() throws Exception {
        Fixture fixture = new Fixture();
        WitherSkeleton otherBoss = mock(WitherSkeleton.class);
        when(otherBoss.getUniqueId()).thenReturn(UUID.randomUUID());
        EntityDamageEvent otherSource = fixture.event(otherBoss, DamageCause.ENTITY_EXPLOSION, 56.1D);
        fixture.service.onAnchorExplosionDamage(otherSource);
        assertEquals(56.1D, otherSource.getFinalDamage());

        Player outsider = mock(Player.class);
        when(outsider.getUniqueId()).thenReturn(UUID.randomUUID());
        when(outsider.getWorld()).thenReturn(fixture.world);
        EntityDamageEvent otherPlayer = fixture.event(outsider, fixture.boss,
            DamageCause.ENTITY_EXPLOSION, 56.1D);
        fixture.service.onAnchorExplosionDamage(otherPlayer);
        assertEquals(56.1D, otherPlayer.getFinalDamage());
    }

    @Test
    void witherSkullBlastAndDirectHitAreNotCapped() throws Exception {
        Fixture fixture = new Fixture();
        WitherSkull skull = mock(WitherSkull.class);
        when(skull.getUniqueId()).thenReturn(UUID.randomUUID());
        EntityDamageEvent blast = fixture.event(skull, DamageCause.ENTITY_EXPLOSION, 22.5D);
        fixture.service.onAnchorExplosionDamage(blast);
        assertEquals(22.5D, blast.getFinalDamage());

        EntityDamageEvent direct = fixture.event(skull, DamageCause.PROJECTILE, 12.0D);
        fixture.service.onAnchorExplosionDamage(direct);
        assertEquals(12.0D, direct.getFinalDamage());
    }

    @Test
    void bothPhasesUseTheSameCappedAnchorDetonation() throws Exception {
        String source = Files.readString(Path.of("src/main/java/hgloot/plugin/CombatMasterService.java"));
        assertTrue(source.contains("if (a.cdB <= 0) { placeAnchor(a, t.getLocation()); a.cdB = 400; }"));
        assertTrue(source.contains("if (a.cdC <= 0) { placeAnchor(a, t.getLocation()); a.cdC = 320; }"));
        assertTrue(source.contains("explodeAnchor(a, stand.getLocation(), 2.6f)"));
        assertTrue(source.contains("explodeAnchor(a, stand.getLocation(), 2.8f)"));
    }

    private static final class Fixture {
        private final UUID bossId = UUID.randomUUID();
        private final UUID participantId = UUID.randomUUID();
        private final World world = mock(World.class);
        private final Player participant = mock(Player.class);
        private final WitherSkeleton boss = mock(WitherSkeleton.class);
        private final CombatMasterService service = new CombatMasterService(null);

        private Fixture() throws Exception {
            when(participant.getUniqueId()).thenReturn(participantId);
            when(participant.getWorld()).thenReturn(world);
            when(participant.getHealth()).thenReturn(20.0D);
            when(boss.getUniqueId()).thenReturn(bossId);
            Field field = CombatMasterService.class.getDeclaredField("activeAnchorExplosion");
            field.setAccessible(true);
            field.set(service, new CombatMasterService.AnchorExplosionContext(bossId, world,
                Set.of(participantId)));
        }

        private EntityDamageEvent event(Entity source, DamageCause cause, double damage) {
            return event(participant, source, cause, damage);
        }

        private EntityDamageEvent event(Player victim, Entity source, DamageCause cause, double damage) {
            DamageSource damageSource = mock(DamageSource.class);
            when(damageSource.getDirectEntity()).thenReturn(source);
            return new EntityDamageEvent(victim, cause, damageSource, damage);
        }
    }
}
