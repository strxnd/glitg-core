package dev.glitg.core.persistence;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;

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
        migrate();
    }

    private void migrate() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS schema_version (version INTEGER NOT NULL)");
            statement.executeUpdate("INSERT INTO schema_version(version) SELECT 0 WHERE NOT EXISTS (SELECT 1 FROM schema_version)");
        }
        int version;
        try (var statement = connection.createStatement(); var result = statement.executeQuery("SELECT version FROM schema_version")) {
            version = result.next() ? result.getInt(1) : 0;
        }
        if (version < 1) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("CREATE TABLE unique_crafts (id TEXT PRIMARY KEY, used INTEGER NOT NULL CHECK(used >= 0))");
                statement.executeUpdate("CREATE TABLE death_bans (player_uuid TEXT PRIMARY KEY, expires_at INTEGER NOT NULL)");
                statement.executeUpdate("CREATE TABLE state (key TEXT PRIMARY KEY, value TEXT NOT NULL)");
                statement.executeUpdate("CREATE TABLE altars (id TEXT PRIMARY KEY, world_uuid TEXT NOT NULL, x INTEGER NOT NULL, y INTEGER NOT NULL, z INTEGER NOT NULL, definition TEXT NOT NULL)");
                statement.executeUpdate("CREATE TABLE ritual_runs (id TEXT PRIMARY KEY, ritual_id TEXT NOT NULL, altar_id TEXT NOT NULL, state TEXT NOT NULL, started_at INTEGER NOT NULL, completes_at INTEGER NOT NULL)");
                statement.executeUpdate("UPDATE schema_version SET version=1");
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE UNIQUE INDEX IF NOT EXISTS ritual_one_active_per_altar ON ritual_runs(altar_id) WHERE state='RUNNING'");
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

    public synchronized void finishRitual(String runId) throws SQLException {
        try (var statement = connection.prepareStatement("UPDATE ritual_runs SET state='COMPLETE' WHERE id=? AND state='RUNNING'")) {
            statement.setString(1, runId); statement.executeUpdate();
        }
    }

    public synchronized List<RitualRunRow> runningRituals() throws SQLException {
        var rows = new ArrayList<RitualRunRow>();
        try (var statement = connection.createStatement(); var result = statement.executeQuery("SELECT id,ritual_id,altar_id,started_at,completes_at FROM ritual_runs WHERE state='RUNNING'")) {
            while (result.next()) rows.add(new RitualRunRow(result.getString(1), result.getString(2), result.getString(3), result.getLong(4), result.getLong(5)));
        }
        return List.copyOf(rows);
    }

    public record AltarRow(String id, UUID worldUuid, int x, int y, int z, String definition) {}
    public record RitualRunRow(String id, String ritualId, String altarId, long startedAt, long completesAt) {}

    @Override public synchronized void close() throws SQLException { connection.close(); }
}
