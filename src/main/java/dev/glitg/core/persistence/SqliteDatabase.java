package dev.glitg.core.persistence;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class SqliteDatabase implements AutoCloseable {
    private final Connection connection;

    public SqliteDatabase(Path path) throws SQLException {
        connection = DriverManager.getConnection("jdbc:sqlite:" + path.toAbsolutePath());
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("PRAGMA synchronous=FULL");
            statement.execute("PRAGMA busy_timeout=5000");
            statement.execute("PRAGMA foreign_keys=ON");
        }
        initializeSchema();
    }

    private void initializeSchema() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS unique_crafts (id TEXT PRIMARY KEY, used INTEGER NOT NULL CHECK(used >= 0))");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS death_bans (player_uuid TEXT PRIMARY KEY, expires_at INTEGER NOT NULL)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS state (key TEXT PRIMARY KEY, value TEXT NOT NULL)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS altars (id TEXT PRIMARY KEY, world_uuid TEXT NOT NULL, x INTEGER NOT NULL, y INTEGER NOT NULL, z INTEGER NOT NULL, definition TEXT NOT NULL)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS ritual_runs (id TEXT PRIMARY KEY, ritual_id TEXT NOT NULL, altar_id TEXT NOT NULL, state TEXT NOT NULL, started_at INTEGER NOT NULL, completes_at INTEGER NOT NULL)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS player_protections (player_uuid TEXT PRIMARY KEY, expires_at INTEGER NOT NULL)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS player_profiles (player_uuid TEXT PRIMARY KEY, player_name TEXT NOT NULL COLLATE NOCASE UNIQUE)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS kit_claims (player_uuid TEXT PRIMARY KEY, claimed_at INTEGER NOT NULL)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS policy_migrations (player_uuid TEXT NOT NULL, migration TEXT NOT NULL, applied_at INTEGER NOT NULL, PRIMARY KEY(player_uuid, migration))");
            statement.executeUpdate("DROP INDEX IF EXISTS ritual_one_active_per_altar");
            statement.executeUpdate("CREATE UNIQUE INDEX IF NOT EXISTS ritual_one_active_per_altar ON ritual_runs(altar_id) WHERE state IN ('RUNNING','REWARDING')");
        }
    }

    public synchronized Connection connection() { return connection; }

    public synchronized void putDeathBan(UUID player, long expiresAtMillis) throws SQLException {
        try (var statement = connection.prepareStatement("INSERT INTO death_bans(player_uuid, expires_at) VALUES(?, ?) ON CONFLICT(player_uuid) DO UPDATE SET expires_at=excluded.expires_at")) {
            statement.setString(1, player.toString());
            statement.setLong(2, expiresAtMillis);
            statement.executeUpdate();
        }
    }

    public synchronized long deathBanExpiry(UUID player) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT expires_at FROM death_bans WHERE player_uuid=?")) {
            statement.setString(1, player.toString());
            try (var result = statement.executeQuery()) { return result.next() ? result.getLong(1) : 0L; }
        }
    }

    public synchronized void clearDeathBan(UUID player) throws SQLException {
        try (var statement = connection.prepareStatement("DELETE FROM death_bans WHERE player_uuid=?")) {
            statement.setString(1, player.toString());
            statement.executeUpdate();
        }
    }

    public synchronized void rememberPlayer(UUID player, String name) throws SQLException {
        try (var statement = connection.prepareStatement("INSERT INTO player_profiles(player_uuid, player_name) VALUES(?, ?) ON CONFLICT(player_uuid) DO UPDATE SET player_name=excluded.player_name")) {
            statement.setString(1, player.toString());
            statement.setString(2, name);
            statement.executeUpdate();
        }
    }

    public synchronized Optional<UUID> playerUuid(String name) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT player_uuid FROM player_profiles WHERE player_name=? COLLATE NOCASE")) {
            statement.setString(1, name);
            try (var result = statement.executeQuery()) {
                return result.next() ? Optional.of(UUID.fromString(result.getString(1))) : Optional.empty();
            }
        }
    }

    public synchronized boolean kitClaimed(UUID player) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT claimed_at FROM kit_claims WHERE player_uuid=?")) {
            statement.setString(1, player.toString());
            try (var result = statement.executeQuery()) { return result.next() && result.getLong(1) > 0; }
        }
    }

    public synchronized boolean kitResetEligible(UUID player) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT claimed_at FROM kit_claims WHERE player_uuid=?")) {
            statement.setString(1, player.toString());
            try (var result = statement.executeQuery()) { return result.next() && result.getLong(1) == 0; }
        }
    }

    public synchronized void markKitClaimed(UUID player, long claimedAtMillis) throws SQLException {
        try (var statement = connection.prepareStatement("INSERT INTO kit_claims(player_uuid, claimed_at) VALUES(?, ?) ON CONFLICT(player_uuid) DO UPDATE SET claimed_at=excluded.claimed_at")) {
            statement.setString(1, player.toString());
            statement.setLong(2, claimedAtMillis);
            statement.executeUpdate();
        }
    }

    public synchronized void resetKitClaim(UUID player) throws SQLException {
        try (var statement = connection.prepareStatement("INSERT INTO kit_claims(player_uuid, claimed_at) VALUES(?, 0) ON CONFLICT(player_uuid) DO UPDATE SET claimed_at=0")) {
            statement.setString(1, player.toString());
            statement.executeUpdate();
        }
    }

    /** Returns true exactly once per player and migration key. */
    public synchronized boolean claimPolicyMigration(UUID player, String migration, long appliedAtMillis) throws SQLException {
        try (var statement = connection.prepareStatement("INSERT OR IGNORE INTO policy_migrations(player_uuid, migration, applied_at) VALUES(?, ?, ?)")) {
            statement.setString(1, player.toString());
            statement.setString(2, migration);
            statement.setLong(3, appliedAtMillis);
            return statement.executeUpdate() == 1;
        }
    }

    public synchronized void putProtection(UUID player, long expiresAtMillis) throws SQLException {
        try (var statement = connection.prepareStatement("INSERT INTO player_protections(player_uuid, expires_at) VALUES(?, ?) ON CONFLICT(player_uuid) DO UPDATE SET expires_at=excluded.expires_at")) {
            statement.setString(1, player.toString());
            statement.setLong(2, expiresAtMillis);
            statement.executeUpdate();
        }
    }

    public synchronized long protectionExpiry(UUID player) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT expires_at FROM player_protections WHERE player_uuid=?")) {
            statement.setString(1, player.toString());
            try (var result = statement.executeQuery()) { return result.next() ? result.getLong(1) : 0L; }
        }
    }

    public synchronized void clearProtection(UUID player) throws SQLException {
        try (var statement = connection.prepareStatement("DELETE FROM player_protections WHERE player_uuid=?")) {
            statement.setString(1, player.toString());
            statement.executeUpdate();
        }
    }

    public synchronized void putState(String key, String value) throws SQLException {
        try (var statement = connection.prepareStatement("INSERT INTO state(key, value) VALUES(?, ?) ON CONFLICT(key) DO UPDATE SET value=excluded.value")) {
            statement.setString(1, key);
            statement.setString(2, value);
            statement.executeUpdate();
        }
    }

    public synchronized String state(String key) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT value FROM state WHERE key=?")) {
            statement.setString(1, key);
            try (var result = statement.executeQuery()) { return result.next() ? result.getString(1) : null; }
        }
    }

    public synchronized void putAltar(AltarRow altar) throws SQLException {
        try (var statement = connection.prepareStatement("INSERT INTO altars(id, world_uuid, x, y, z, definition) VALUES(?, ?, ?, ?, ?, ?) ON CONFLICT(id) DO UPDATE SET world_uuid=excluded.world_uuid,x=excluded.x,y=excluded.y,z=excluded.z,definition=excluded.definition")) {
            statement.setString(1, altar.id());
            statement.setString(2, altar.worldUuid().toString());
            statement.setInt(3, altar.x()); statement.setInt(4, altar.y()); statement.setInt(5, altar.z());
            statement.setString(6, altar.definition());
            statement.executeUpdate();
        }
    }

    public synchronized void removeAltar(String id) throws SQLException {
        try (var statement = connection.prepareStatement("DELETE FROM altars WHERE id=?")) {
            statement.setString(1, id); statement.executeUpdate();
        }
    }

    public synchronized List<AltarRow> altars() throws SQLException {
        var rows = new ArrayList<AltarRow>();
        try (var statement = connection.createStatement(); var result = statement.executeQuery("SELECT id,world_uuid,x,y,z,definition FROM altars")) {
            while (result.next()) rows.add(new AltarRow(result.getString(1), UUID.fromString(result.getString(2)), result.getInt(3), result.getInt(4), result.getInt(5), result.getString(6)));
        }
        return List.copyOf(rows);
    }

    public synchronized boolean beginRitual(String runId, String ritualId, String altarId, long startedAt, long completesAt) throws SQLException {
        try (var statement = connection.prepareStatement("INSERT OR IGNORE INTO ritual_runs(id,ritual_id,altar_id,state,started_at,completes_at) VALUES(?,?,?,'RUNNING',?,?)")) {
            statement.setString(1, runId); statement.setString(2, ritualId); statement.setString(3, altarId);
            statement.setLong(4, startedAt); statement.setLong(5, completesAt);
            return statement.executeUpdate() == 1;
        }
    }

    public synchronized boolean claimRitualReward(String runId) throws SQLException {
        try (var statement = connection.prepareStatement("UPDATE ritual_runs SET state='REWARDING' WHERE id=? AND state='RUNNING'")) {
            statement.setString(1, runId);
            return statement.executeUpdate() == 1;
        }
    }

    public synchronized void finishRitual(String runId) throws SQLException {
        try (var statement = connection.prepareStatement("UPDATE ritual_runs SET state='COMPLETE' WHERE id=? AND state='REWARDING'")) {
            statement.setString(1, runId);
            statement.executeUpdate();
        }
    }

    public synchronized List<RitualRunRow> recoverableRituals() throws SQLException {
        var rows = new ArrayList<RitualRunRow>();
        try (var statement = connection.createStatement(); var result = statement.executeQuery("SELECT id,ritual_id,altar_id,state,started_at,completes_at FROM ritual_runs WHERE state IN ('RUNNING','REWARDING')")) {
            while (result.next()) rows.add(new RitualRunRow(result.getString(1), result.getString(2), result.getString(3), result.getString(4), result.getLong(5), result.getLong(6)));
        }
        return List.copyOf(rows);
    }

    public synchronized List<RitualRunRow> runningRituals() throws SQLException {
        return recoverableRituals().stream().filter(row -> row.state().equals("RUNNING")).toList();
    }

    public record AltarRow(String id, UUID worldUuid, int x, int y, int z, String definition) {}
    public record RitualRunRow(String id, String ritualId, String altarId, String state, long startedAt, long completesAt) {}

    @Override public synchronized void close() throws SQLException { connection.close(); }
}
