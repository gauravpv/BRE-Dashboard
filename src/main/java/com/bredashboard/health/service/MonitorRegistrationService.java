package com.bredashboard.health.service;

import com.bredashboard.health.config.HealthProperties;
import com.bredashboard.health.domain.HealthDeploymentTier;
import com.bredashboard.health.domain.MonitoredHost;
import com.bredashboard.health.domain.MonitoredHostProd;
import com.bredashboard.health.dto.HealthRefreshResponse;
import com.bredashboard.health.dto.MonitorDetailsResponse;
import com.bredashboard.health.dto.RegisterMonitorRequest;
import com.bredashboard.health.exception.MonitorRegistrationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
public class MonitorRegistrationService {

    private final MonitoredHostCatalog catalog;
    private final SystemHealthMonitor healthMonitor;
    private final HealthProperties healthProperties;

    public MonitorRegistrationService(
            MonitoredHostCatalog catalog,
            SystemHealthMonitor healthMonitor,
            HealthProperties healthProperties
    ) {
        this.catalog = catalog;
        this.healthMonitor = healthMonitor;
        this.healthProperties = healthProperties;
    }

    public MonitorDetailsResponse get(HealthDeploymentTier tier, Long id) {
        return tier == HealthDeploymentTier.PROD
                ? MonitorDetailsResponse.fromProd(catalog.findProd(id))
                : MonitorDetailsResponse.fromUat(catalog.findUat(id));
    }

    public List<MonitorDetailsResponse> listAll() {
        List<MonitorDetailsResponse> results = new ArrayList<>();
        catalog.findAllUat().forEach(host -> results.add(MonitorDetailsResponse.fromUat(host)));
        catalog.findAllProd().forEach(host -> results.add(MonitorDetailsResponse.fromProd(host)));
        return results;
    }

    @Transactional
    public HealthRefreshResponse register(RegisterMonitorRequest request) {
        HealthDeploymentTier tier = resolveTier(request);
        ResolvedMonitorFields fields = resolveFields(request, tier);
        ensureUniqueHostPort(tier, fields.host(), fields.port(), null);

        if (tier == HealthDeploymentTier.PROD) {
            catalog.saveProd(new MonitoredHostProd(
                    fields.name(),
                    fields.host(),
                    fields.port(),
                    fields.environment(),
                    fields.region(),
                    fields.actuatorPath(),
                    fields.modelHubEnvironmentId()
            ));
        } else {
            catalog.saveUat(new MonitoredHost(
                    fields.name(),
                    fields.host(),
                    fields.port(),
                    fields.environment(),
                    fields.region(),
                    fields.actuatorPath(),
                    fields.modelHubEnvironmentId()
            ));
        }

        healthMonitor.refresh();
        return refreshResponse();
    }

    @Transactional
    public HealthRefreshResponse update(HealthDeploymentTier tier, Long id, RegisterMonitorRequest request) {
        ResolvedMonitorFields fields = resolveFields(request, tier);
        ensureUniqueHostPort(tier, fields.host(), fields.port(), id);

        if (tier == HealthDeploymentTier.PROD) {
            MonitoredHostProd host = catalog.findProd(id);
            applyFieldsProd(host, fields);
            catalog.saveProd(host);
        } else {
            MonitoredHost host = catalog.findUat(id);
            applyFieldsUat(host, fields);
            catalog.saveUat(host);
        }

        healthMonitor.refresh();
        return refreshResponse();
    }

    @Transactional
    public HealthRefreshResponse remove(HealthDeploymentTier tier, Long id) {
        catalog.delete(tier, id);
        healthMonitor.refresh();
        return refreshResponse();
    }

    private static void applyFieldsUat(MonitoredHost host, ResolvedMonitorFields fields) {
        host.setName(fields.name());
        host.setHost(fields.host());
        host.setPort(fields.port());
        host.setEnvironment(fields.environment());
        host.setRegion(fields.region());
        host.setActuatorPath(fields.actuatorPath());
        host.setModelHubEnvironmentId(fields.modelHubEnvironmentId());
    }

    private static void applyFieldsProd(MonitoredHostProd host, ResolvedMonitorFields fields) {
        host.setName(fields.name());
        host.setHost(fields.host());
        host.setPort(fields.port());
        host.setEnvironment(fields.environment());
        host.setRegion(fields.region());
        host.setActuatorPath(fields.actuatorPath());
        host.setModelHubEnvironmentId(fields.modelHubEnvironmentId());
    }

    private HealthDeploymentTier resolveTier(RegisterMonitorRequest request) {
        if (StringUtils.hasText(request.tier())) {
            return HealthDeploymentTier.fromId(request.tier());
        }
        if (StringUtils.hasText(request.environment())) {
            return tierFromEnvironment(request.environment());
        }
        throw new MonitorRegistrationException("Environment is required (UAT or Prod)");
    }

    public static HealthDeploymentTier tierFromEnvironment(String environment) {
        return switch (normalizeEnvironment(environment)) {
            case "Production" -> HealthDeploymentTier.PROD;
            case "UAT" -> HealthDeploymentTier.UAT;
            default -> throw new MonitorRegistrationException("Environment must be UAT or Prod");
        };
    }

    private void ensureUniqueHostPort(HealthDeploymentTier tier, String host, int port, Long excludeId) {
        if (catalog.existsByHostAndPort(tier, host, port, excludeId)) {
            throw new MonitorRegistrationException("A service is already registered for " + host + ":" + port + " in " + environmentLabel(tier));
        }
    }

    private static String environmentLabel(HealthDeploymentTier tier) {
        return tier == HealthDeploymentTier.PROD ? "Prod" : "UAT";
    }

    private ResolvedMonitorFields resolveFields(RegisterMonitorRequest request, HealthDeploymentTier tier) {
        String name = requireText(request.name(), "Service name is required");
        String host = requireText(request.host(), "Host / IP is required");
        int port = requirePort(request.port());
        String environment = tier == HealthDeploymentTier.PROD ? "Production" : "UAT";
        String region = requireText(request.region(), "Group name is required");

        String actuatorPath = StringUtils.hasText(request.actuatorPath())
                ? request.actuatorPath().trim()
                : healthProperties.getHealth().getDefaultActuatorPath();

        return new ResolvedMonitorFields(name, host, port, environment, region, actuatorPath, null);
    }

    private HealthRefreshResponse refreshResponse() {
        return new HealthRefreshResponse(
                healthMonitor.getSystems(),
                healthMonitor.summary(),
                healthMonitor.getLastRefreshedAt()
        );
    }

    private static String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new MonitorRegistrationException(message);
        }
        return value.trim();
    }

    private static String normalizeOptional(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static int requirePort(Integer port) {
        if (port == null) {
            throw new MonitorRegistrationException("Port is required");
        }
        if (port < 1 || port > 65535) {
            throw new MonitorRegistrationException("Port must be between 1 and 65535");
        }
        return port;
    }

    public static String normalizeEnvironment(String raw) {
        return switch (raw.trim().toLowerCase()) {
            case "prod", "production" -> "Production";
            case "uat" -> "UAT";
            default -> raw.trim();
        };
    }

    private record ResolvedMonitorFields(
            String name,
            String host,
            int port,
            String environment,
            String region,
            String actuatorPath,
            String modelHubEnvironmentId
    ) {
    }
}
