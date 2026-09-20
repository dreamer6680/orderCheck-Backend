package com.packflow.app;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

/** Verify an already-upgraded V8 database survives removal of its old migration files. */
class LegacyDataMigrationTest {

    @Test
    void existingVersionEightHistoryRemainsValidAndBusinessRowsArePreserved() throws Exception {
        var postgres = new PostgreSQLContainer<>("postgres:17");
        postgres.start();
        try {
            var initial = Flyway.configure()
                    .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .locations("classpath:db/migration")
                    .load();
            initial.migrate();

            try (Connection db = DriverManager.getConnection(
                    postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                 Statement sql = db.createStatement()) {
                // Simulate an existing fully-upgraded schema with real business rows.
                sql.executeUpdate("""
                    INSERT INTO app_user (id, username, password_hash, display_name, role, enabled)
                    VALUES (9001, 'existing-manager', 'unused-hash', 'Existing manager', 'MANAGER', true)
                    """);
                sql.executeUpdate("""
                    INSERT INTO product (id, sku, name, unit, safety_stock, enabled)
                    VALUES (9002, 'BOX-LEGACY', 'Legacy carton', 'piece', 0, true)
                    """);
                sql.executeUpdate("""
                    INSERT INTO sales_order
                        (id, order_no, customer_name, status, exception_reason, abnormal_type, created_by)
                    VALUES (9003, 'SO-LEGACY', 'Existing customer', 'ABNORMAL',
                            'Stock check showed insufficient inventory', 'STOCK_SHORTAGE', 9001)
                    """);
                sql.executeUpdate("""
                    INSERT INTO sales_order_item (id, order_id, product_id, ordered_quantity)
                    VALUES (9004, 9003, 9002, 100)
                    """);

                // Replace the fresh V9 migration record with the legacy V1..V8
                // versioned history that an existing database would already have.
                sql.executeUpdate("""
                    UPDATE flyway_schema_history
                    SET installed_rank = 8, version = '8', description = 'supplemental outbound',
                        type = 'SQL', script = 'V8__supplemental_outbound.sql', checksum = 12345678
                    WHERE script = 'V9__current_schema.sql'
                    """);
                for (int version = 1; version <= 7; version++) {
                    sql.executeUpdate("""
                        INSERT INTO flyway_schema_history
                          (installed_rank, version, description, type, script, checksum,
                           installed_by, execution_time, success)
                        VALUES (%d, '%d', 'historical migration', 'SQL', 'V%d__historical.sql',
                                12345678, current_user, 0, true)
                        """.formatted(version, version, version));
                }
            }

            var current = Flyway.configure()
                    .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .locations("classpath:db/migration")
                    .ignoreMigrationPatterns("*:missing")
                    .load();
            current.validate();
            current.migrate();

            try (Connection db = DriverManager.getConnection(
                    postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                 Statement sql = db.createStatement()) {
                assertThat(scalar(sql, "SELECT count(*) FROM flyway_schema_history WHERE success = true"))
                        .isEqualTo("8");
                assertThat(scalar(sql, "SELECT customer_name FROM sales_order WHERE id = 9003"))
                        .isEqualTo("Existing customer");
                assertThat(scalar(sql, "SELECT ordered_quantity FROM sales_order_item WHERE id = 9004"))
                        .isEqualTo("100.000");
                assertThat(scalar(sql, "SELECT count(*) FROM order_event")).isEqualTo("0");
            }
        } finally {
            postgres.stop();
        }
    }

    private static String scalar(Statement sql, String query) throws Exception {
        try (ResultSet result = sql.executeQuery(query)) {
            result.next();
            return result.getString(1);
        }
    }
}
