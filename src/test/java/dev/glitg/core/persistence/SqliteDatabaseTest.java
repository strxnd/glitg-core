package dev.glitg.core.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class SqliteDatabaseTest {
    @Test void migratesAndPersistsAtomicState(@TempDir Path directory) throws Exception {Path path=directory.resolve("state.db");UUID player=UUID.randomUUID();try(var database=new SqliteDatabase(path)){var store=new SqliteUniqueItemStore(database);assertTrue(store.allocate("one",1,1).allocated());assertFalse(store.allocate("one",1,1).allocated());database.putDeathBan(player,12345);database.putState("grace.ends-at","999");}try(var reopened=new SqliteDatabase(path)){assertEquals(1,new SqliteUniqueItemStore(reopened).used("one"));assertEquals(12345,reopened.deathBanExpiry(player));assertEquals("999",reopened.state("grace.ends-at"));}}
}
