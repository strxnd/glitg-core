package dev.glitg.core.persistence;

import java.sql.Connection;
import java.sql.SQLException;

public final class SqliteUniqueItemStore implements UniqueItemStore {
    private final Connection connection;

    public SqliteUniqueItemStore(SqliteDatabase database) {
        this.connection = database.connection();
    }

    @Override
    public synchronized Allocation allocate(String id, int limit, int quantity) {
        if (limit < 0 || quantity < 1) throw new IllegalArgumentException("invalid allocation");
        try {
            connection.setAutoCommit(false);
            int current = read(id);
            if (current + quantity > limit) {
                connection.rollback();
                return new Allocation(false, current, Math.max(0, limit - current));
            }
            int next = current + quantity;
            write(id, next);
            connection.commit();
            return new Allocation(true, next, limit - next);
        } catch (SQLException exception) {
            try { connection.rollback(); } catch (SQLException suppressed) { exception.addSuppressed(suppressed); }
            throw new IllegalStateException("atomic unique-item allocation failed", exception);
        } finally {
            try { connection.setAutoCommit(true); } catch (SQLException exception) { throw new IllegalStateException(exception); }
        }
    }

    @Override public synchronized int used(String id) {
        try { return read(id); } catch (SQLException exception) { throw new IllegalStateException(exception); }
    }

    @Override public synchronized void set(String id, int value) {
        if (value < 0) throw new IllegalArgumentException("value cannot be negative");
        try { write(id, value); } catch (SQLException exception) { throw new IllegalStateException(exception); }
    }

    private int read(String id) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT used FROM unique_crafts WHERE id=?")) {
            statement.setString(1, id);
            try (var result = statement.executeQuery()) { return result.next() ? result.getInt(1) : 0; }
        }
    }

    private void write(String id, int value) throws SQLException {
        try (var statement = connection.prepareStatement("INSERT INTO unique_crafts(id, used) VALUES(?, ?) ON CONFLICT(id) DO UPDATE SET used=excluded.used")) {
            statement.setString(1, id);
            statement.setInt(2, value);
            statement.executeUpdate();
        }
    }
}
