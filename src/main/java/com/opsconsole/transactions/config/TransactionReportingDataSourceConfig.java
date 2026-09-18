package com.opsconsole.transactions.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
public class TransactionReportingDataSourceConfig {

    @Bean(name = "transactionReportingDataSource")
    @ConditionalOnExpression("T(org.springframework.util.StringUtils).hasText('${opsconsole.transaction-dashboard.jdbc-url:}')")
    public DataSource transactionReportingDataSource(TransactionDashboardProperties properties) {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setPoolName("txn-reporting");
        dataSource.setJdbcUrl(properties.getJdbcUrl());
        dataSource.setUsername(properties.getUsername());
        dataSource.setPassword(properties.getPassword());
        dataSource.setReadOnly(true);
        dataSource.setMaximumPoolSize(5);
        dataSource.setMinimumIdle(1);
        dataSource.setConnectionTimeout(10_000);
        return dataSource;
    }
}
