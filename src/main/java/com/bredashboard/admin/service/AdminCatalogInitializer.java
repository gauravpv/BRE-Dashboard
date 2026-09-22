package com.bredashboard.admin.service;

import com.bredashboard.admin.config.AdminProperties;
import com.bredashboard.admin.domain.ManagedServer;
import com.bredashboard.admin.domain.ManagedService;
import com.bredashboard.admin.repository.ManagedServerRepository;
import com.bredashboard.admin.repository.ManagedServiceRepository;
import com.bredashboard.admin.util.AdminPathValidator;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AdminCatalogInitializer implements ApplicationRunner {

    private final ManagedServerRepository serverRepository;
    private final ManagedServiceRepository serviceRepository;
    private final AdminProperties properties;

    public AdminCatalogInitializer(
            ManagedServerRepository serverRepository,
            ManagedServiceRepository serviceRepository,
            AdminProperties properties
    ) {
        this.serverRepository = serverRepository;
        this.serviceRepository = serviceRepository;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (serviceRepository.count() > 0) {
            return;
        }
        if (serverRepository.count() > 0) {
            serverRepository.deleteAll();
        }
        for (AdminProperties.ServerSeed seed : properties.getServers()) {
            ManagedServer server = serverRepository.save(new ManagedServer(
                    seed.getName(),
                    seed.getHost(),
                    seed.getSshPort(),
                    seed.getSshUser()
            ));
            for (AdminProperties.ServiceSeed serviceSeed : seed.getServices()) {
                AdminPathValidator.validateScriptPath(serviceSeed.getStartScript(), "startScript");
                AdminPathValidator.validateScriptPath(serviceSeed.getStopScript(), "stopScript");
                AdminPathValidator.validateScriptPath(serviceSeed.getRestartScript(), "restartScript");
                AdminPathValidator.validatePropertiesPath(serviceSeed.getPropertiesPath());
                serviceRepository.save(new ManagedService(
                        server,
                        serviceSeed.getName(),
                        serviceSeed.getDescription(),
                        serviceSeed.getCategory(),
                        serviceSeed.getPort(),
                        serviceSeed.getStartScript(),
                        serviceSeed.getStopScript(),
                        serviceSeed.getRestartScript(),
                        serviceSeed.getPropertiesPath()
                ));
            }
        }
    }
}
