package com.bredashboard.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * The transaction-reporting datasource is also a {@link javax.sql.DataSource} bean, which makes Spring Boot's
 * {@code DataSourceAutoConfiguration} back off (its pooled datasource is {@code @ConditionalOnMissingBean(DataSource.class)}).
 * Declaring the bre_dashboard datasource explicitly guarantees it always exists and is the one JPA uses.
 */
@Configuration
public class PrimaryDataSourceConfig {

    @Bean
    @Primary
    @ConfigurationProperties(prefix = "spring.datasource.hikari")
    public HikariDataSource dataSource(DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }
}
