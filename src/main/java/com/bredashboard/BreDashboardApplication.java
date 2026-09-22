package com.bredashboard;

import com.bredashboard.admin.config.AdminProperties;
import com.bredashboard.auth.config.AuthProperties;
import com.bredashboard.health.config.HealthProperties;
import com.bredashboard.tester.config.BajajTesterProperties;
import com.bredashboard.transactions.config.TransactionDashboardProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({
        HealthProperties.class,
        AuthProperties.class,
        AdminProperties.class,
        TransactionDashboardProperties.class,
        BajajTesterProperties.class
})
public class BreDashboardApplication {

    public static void main(String[] args) {
        SpringApplication.run(BreDashboardApplication.class, args);
    }
}
