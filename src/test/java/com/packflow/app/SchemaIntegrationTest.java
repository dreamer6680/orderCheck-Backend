package com.packflow.app;

import com.packflow.app.support.PostgresIntegrationTest;
import java.time.OffsetDateTime;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SchemaIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void createsAllRequiredTables() {
        Set<String> tableNames = Set.copyOf(jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables "
                        + "WHERE table_schema = 'public' AND table_type = 'BASE TABLE'",
                String.class));

        assertThat(tableNames).contains(
                "app_user",
                "product",
                "inventory",
                "sales_order",
                "sales_order_item",
                "inbound_record",
                "outbound_record");
    }

    @Test
    void rejectsNegativeInventoryQuantity() {
        Long productId = jdbcTemplate.queryForObject(
                "INSERT INTO product (sku, name, unit, safety_stock, enabled, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id",
                Long.class,
                "negative-inventory-test",
                "Negative inventory test product",
                "piece",
                0,
                true,
                OffsetDateTime.now(),
                OffsetDateTime.now());

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO inventory (product_id, quantity, version, updated_at) VALUES (?, ?, ?, ?)",
                productId,
                -1,
                0,
                OffsetDateTime.now()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
