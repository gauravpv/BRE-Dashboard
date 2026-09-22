package com.bredashboard.admin;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import com.bredashboard.admin.domain.ManagedServer;
import com.bredashboard.admin.domain.ManagedService;
import com.bredashboard.admin.repository.ManagedServerRepository;
import com.bredashboard.admin.repository.ManagedServiceRepository;
@SpringBootTest
class AdminCatalogInitializerTest {

    @Autowired
    private ManagedServerRepository serverRepository;

    @Autowired
    private ManagedServiceRepository serviceRepository;

    @Test
    void seedsServersAndServicesFromYamlWhenEmpty() {
        assertThat(serverRepository.count()).isGreaterThan(0);
        assertThat(serviceRepository.count()).isGreaterThanOrEqualTo(2);

        ManagedServer server = serverRepository.findAll().stream()
                .filter(s -> "dev-linux-01".equals(s.getName()))
                .findFirst()
                .orElseThrow();
        assertThat(server.getHost()).isEqualTo("127.0.0.1");

        assertThat(serviceRepository.findByServer_IdAndEnabledTrueOrderByCategoryAscNameAsc(server.getId()))
                .extracting(ManagedService::getName)
                .contains("payment-api", "auth-core");
    }
}
