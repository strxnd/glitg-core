package dev.glitg.core.domain;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class TimePolicyTest {
    @Test void cooldownUsesAbsoluteTimeWithoutTickDrift(){var clock=new MutableClock(Instant.EPOCH);var service=new CooldownService(clock);UUID player=UUID.randomUUID();assertTrue(service.tryAcquire(player,"pearl",Duration.ofSeconds(10)));assertFalse(service.tryAcquire(player,"pearl",Duration.ofSeconds(10)));clock.advance(Duration.ofMillis(9500));assertEquals(500,service.remaining(player,"pearl").toMillis());clock.advance(Duration.ofMillis(500));assertTrue(service.tryAcquire(player,"pearl",Duration.ofSeconds(10)));}
    @Test void combatTagsBothSidesAndExpires(){var clock=new MutableClock(Instant.EPOCH);var service=new CombatTagService(clock);UUID a=UUID.randomUUID(),b=UUID.randomUUID();service.tag(a,b,Duration.ofSeconds(15));assertTrue(service.isTagged(a));assertEquals(a,service.opponent(b));clock.advance(Duration.ofSeconds(16));assertFalse(service.isTagged(a));assertFalse(service.isTagged(b));}
    @Test void outgoingAttackRevokesProtection(){var clock=new MutableClock(Instant.EPOCH);var service=new ProtectionStateMachine(clock);UUID player=UUID.randomUUID();service.grant(player,Duration.ofMinutes(5));assertTrue(service.isProtected(player));service.revokeOnOutgoingAttack(player);assertFalse(service.isProtected(player));}
}
