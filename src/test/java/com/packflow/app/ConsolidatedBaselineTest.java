package com.packflow.app;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

/** A fresh database uses the consolidated B8 migration, not V1-V8 one at a time. */
class ConsolidatedBaselineTest {

    @Test
    void initializesFreshDatabaseWithOneBaselineAndFullCurrentSchema() throws Exception {
        var postgres = new PostgreSQLContainer<>("postgres:17");
        postgres.start();
        try {
            Flyway flyway = Flyway.configure()
                    .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .locations("classpath:db/migration")
                    .load();
            flyway.migrate();
            flyway.validate();

            try (Connection db = DriverManager.getConnection(
                    postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                 Statement sql = db.createStatement()) {
                assertThat(scalar(sql, "SELECT count(*) FROM flyway_schema_history WHERE success = true"))
                        .isEqualTo("1");
                assertThat(scalar(sql,
                        "SELECT script FROM flyway_schema_history WHERE success = true"))
                        .isEqualTo("B8__current_schema.sql");
                assertThat(scalar(sql,
                        "SELECT count(*) FROM information_schema.columns "
                                + "WHERE table_schema='public' AND table_name='app_user' "
                                + "AND column_name='token_version'")).isEqualTo("1");
                assertThat(scalar(sql,
                        "SELECT count(*) FROM information_schema.columns "
                                + "WHERE table_schema='public' AND table_name='sales_order' "
                                + "AND column_name IN ('delivery_date','abnormal_type')")).isEqualTo("2");
                assertThat(scalar(sql,
                        "SELECT count(*) FROM information_schema.columns "
                                + "WHERE table_schema='public' AND table_name='outbound_record' "
                                + "AND column_name IN ('planned_outbound_date','shipment_type')"))
                        .isEqualTo("2");
                assertThat(scalar(sql,
                        "SELECT count(*) FROM information_schema.tables "
                                + "WHERE table_schema='public' AND table_name='order_event'"))
                        .isEqualTo("1");
                // A new production database must not receive any published demo passwords.
                assertThat(scalar(sql, "SELECT count(*) FROM app_user")).isEqualTo("0");
                // An order line can now have an initial and a supplemental outbound record.
                assertThat(scalar(sql,
                        "SELECT count(*) FROM pg_constraint WHERE conrelid='outbound_record'::regclass "
                                + "AND contype='u' AND conkey = ARRAY["
                                + "(SELECT attnum FROM pg_attribute "
                                + " WHERE attrelid='outbound_record'::regclass AND attname='order_item_id')]::smallint[]"))
                        .isEqualTo("0");
            }
        } finally {
            postgres.stop();
        }
    }

    private static String scalar(Statement statement, String query) throws Exception {
        try (ResultSet result = statement.executeQuery(query)) {
            result.next();
            return result.getString(1);
        }
    }
}
