package dev.glitg.core.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class SqliteDatabaseTest {
    @Test void initializesAndPersistsAtomicState(@TempDir Path directory) throws Exception {Path path=directory.resolve("state.db");UUID player=UUID.randomUUID();try(var database=new SqliteDatabase(path)){var store=new SqliteUniqueItemStore(database);assertTrue(store.allocate("one",1,1).allocated());assertFalse(store.allocate("one",1,1).allocated());database.putDeathBan(player,12345);database.putProtection(player,67890);database.putState("grace.ends-at","999");database.putState("dimension.end.unlock-at","123456");database.rememberPlayer(player,"Alex");database.resetKitClaim(player);assertTrue(database.kitResetEligible(player));database.markKitClaimed(player,42);assertTrue(database.kitClaimed(player));assertTrue(database.claimPolicyMigration(player,"mythical-v1",43));assertFalse(database.claimPolicyMigration(player,"mythical-v1",44));}try(var reopened=new SqliteDatabase(path)){assertEquals(1,new SqliteUniqueItemStore(reopened).used("one"));assertEquals(12345,reopened.deathBanExpiry(player));assertEquals(67890,reopened.protectionExpiry(player));assertEquals("999",reopened.state("grace.ends-at"));assertEquals("123456",reopened.state("dimension.end.unlock-at"));assertEquals(player,reopened.playerUuid("aLeX").orElseThrow());assertTrue(reopened.kitClaimed(player));assertFalse(reopened.claimPolicyMigration(player,"mythical-v1",45));}}

    @Test void ritualRewardStateIsClaimedOnceAndRecoverable(@TempDir Path directory) throws Exception {
        try (var database = new SqliteDatabase(directory.resolve("ritual.db"))) {
            UUID world = UUID.randomUUID();
            database.putAltar(new SqliteDatabase.AltarRow("altar", world, 1, 2, 3, "basic"));
            assertTrue(database.beginRitual("run", "example", "altar", 10, 20));
            assertTrue(database.claimRitualReward("run"));
            assertFalse(database.claimRitualReward("run"));
            assertEquals("REWARDING", database.recoverableRituals().getFirst().state());
            database.finishRitual("run");
            assertTrue(database.recoverableRituals().isEmpty());
        }
    }
}
