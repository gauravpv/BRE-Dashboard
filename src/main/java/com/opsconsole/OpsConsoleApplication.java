package com.opsconsole;

import com.opsconsole.admin.config.AdminProperties;
import com.opsconsole.auth.config.AuthProperties;
import com.opsconsole.health.config.HealthProperties;
import com.opsconsole.transactions.config.TransactionDashboardProperties;
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
        com.opsconsole.tester.config.BajajTesterProperties.class,
        com.opsconsole.config.OpsConsoleFeaturesProperties.class
})
public class OpsConsoleApplication {

    public static void main(String[] args) {
        SpringApplication.run(OpsConsoleApplication.class, args);
    }
}
