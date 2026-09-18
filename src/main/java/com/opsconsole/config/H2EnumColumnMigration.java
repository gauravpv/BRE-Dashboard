package com.opsconsole.config;

import com.opsconsole.auth.domain.AppTab;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
/**
 * H2 persists Hibernate {@code @Enumerated} columns as ENUM types with a fixed value set.
 * When {@link AppTab} gains a new constant, alter the column to VARCHAR so inserts succeed.
 */
@Component
@Order(Integer.MIN_VALUE)
@ConditionalOnProperty(name = "spring.datasource.driver-class-name", havingValue = "org.h2.Driver")
public class H2EnumColumnMigration implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    public H2EnumColumnMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbcTemplate.execute("ALTER TABLE role_tab_access ALTER COLUMN tab VARCHAR(40) NOT NULL");
        } catch (Exception ignored) {
            // Table may not exist yet on first bootstrap, or column is already VARCHAR.
        }
    }
}
