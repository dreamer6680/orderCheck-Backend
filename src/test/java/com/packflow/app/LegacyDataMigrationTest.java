package com.packflow.app;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import static org.assertj.core.api.Assertions.assertThat;

/** Verify historical rows survive the incremental V5-V8 upgrade, not just clean installation. */
class LegacyDataMigrationTest {

    @Test
    void upgradesExistingShortageAndPartiallyShippedOrdersWithoutLosingHistory() throws Exception {
        var postgres = new PostgreSQLContainer<>("postgres:17");
        postgres.start();
        try {
            // Start from a real V1 schema with a Flyway history at version 1.
            // The new B8 baseline is only for empty databases; applying B8 here
            // would skip V2-V4 and hide regressions in historical upgrades.
            try (Connection db = DriverManager.getConnection(
                    postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
                ScriptUtils.executeSqlScript(db, new ClassPathResource("db/migration/V1__schema.sql"));
            }
            Flyway.configure()
                    .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .locations("classpath:db/migration")
                    .baselineVersion("1")
                    .load().baseline();

            Flyway.configure()
                    .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .locations("classpath:db/migration")
                    .target(MigrationVersion.fromVersion("4"))
                    .load().migrate();

            try (Connection db = DriverManager.getConnection(
                    postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                 Statement sql = db.createStatement()) {
                sql.executeUpdate("""
                    INSERT INTO app_user (id, username, password_hash, display_name, role, enabled)
                    VALUES (9001, 'migration-manager', 'unused-hash', 'Migration manager', 'MANAGER', true)
                    """);
                sql.executeUpdate("""
                    INSERT INTO product (id, sku, name, unit, safety_stock, enabled)
                    VALUES (9002, 'BOX-MIGRATION', 'Migration carton', 'piece', 0, true)
                    """);
                sql.executeUpdate("""
                    INSERT INTO sales_order (id, order_no, customer_name, status, exception_reason, created_by)
                    VALUES (9003, 'SO-LEGACY-STOCK', 'Stock customer', 'ABNORMAL',
                            'BOX-MIGRATION: 需要 100, 可用 20', 9001),
                           (9005, 'SO-LEGACY-SHORT', 'Shipment customer', 'ABNORMAL',
                            'BOX-MIGRATION: planned 100, actual 80', 9001)
                    """);
                sql.executeUpdate("""
                    INSERT INTO sales_order_item (id, order_id, product_id, ordered_quantity)
                    VALUES (9004, 9003, 9002, 100), (9006, 9005, 9002, 100)
                    """);
                sql.executeUpdate("""
                    INSERT INTO outbound_record
                        (id, record_no, order_id, order_item_id, product_id, planned_quantity,
                         actual_quantity, status, difference_reason, operator_id, completed_at)
                    VALUES (9007, 'OUT-LEGACY-80', 9005, 9006, 9002, 100,
                            80, 'COMPLETED', 'Actual shipment short by 20', 9001, CURRENT_TIMESTAMP)
                    """);
            }

            Flyway.configure()
                    .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .locations("classpath:db/migration")
                    .load().migrate();

            try (Connection db = DriverManager.getConnection(
                    postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                 Statement sql = db.createStatement()) {
                assertThat(scalar(sql, "SELECT abnormal_type FROM sales_order WHERE id = 9003"))
                        .isEqualTo("STOCK_SHORTAGE");
                assertThat(scalar(sql, "SELECT abnormal_type FROM sales_order WHERE id = 9005"))
                        .isEqualTo("SHORT_DELIVERY");
                assertThat(scalar(sql, "SELECT delivery_date FROM sales_order WHERE id = 9003"))
                        .isNull();
                assertThat(scalar(sql, "SELECT shipment_type FROM outbound_record WHERE id = 9007"))
                        .isEqualTo("INITIAL");
                assertThat(scalar(sql, "SELECT actual_quantity FROM outbound_record WHERE id = 9007"))
                        .isEqualTo("80.000");
                assertThat(scalar(sql, """
                        SELECT count(*) FROM order_event
                        WHERE order_id = 9005 AND event_type = 'OUTBOUND_SHORTAGE'
                        """)).isEqualTo("1");
                assertThat(scalar(sql, """
                        SELECT count(*) FROM order_event
                        WHERE order_id = 9003 AND event_type = 'STOCK_SHORTAGE'
                        """)).isEqualTo("1");

                // The old unique order-item constraint must be gone for supplemental shipments.
                sql.executeUpdate("""
                    INSERT INTO outbound_record
                        (record_no, order_id, order_item_id, product_id, planned_quantity,
                         planned_outbound_date, status, shipment_type)
                    VALUES ('OUT-SUPPLEMENT-20', 9005, 9006, 9002, 20,
                            CURRENT_DATE, 'PENDING', 'SUPPLEMENTAL')
                    """);
                assertThat(scalar(sql, "SELECT count(*) FROM outbound_record WHERE order_item_id = 9006"))
                        .isEqualTo("2");
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
