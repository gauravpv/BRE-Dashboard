package com.bredashboard.health.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "monitored_hosts_prod")
public class MonitoredHostProd {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String host;

    @Column(nullable = false)
    private int port;

    @Column(nullable = false)
    private String environment;

    @Column(nullable = false)
    private String region;

    @Column(name = "model_hub_environment_id")
    private String modelHubEnvironmentId;

    @Column(name = "actuator_path", nullable = false)
    private String actuatorPath;

    @Column(nullable = false)
    private boolean enabled = true;

    protected MonitoredHostProd() {
    }

    public MonitoredHostProd(
            String name,
            String host,
            int port,
            String environment,
            String region,
            String actuatorPath,
            String modelHubEnvironmentId
    ) {
        this.name = name;
        this.host = host;
        this.port = port;
        this.environment = environment;
        this.region = region;
        this.actuatorPath = actuatorPath;
        this.modelHubEnvironmentId = modelHubEnvironmentId;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getEnvironment() {
        return environment;
    }

    public String getRegion() {
        return region;
    }

    public String getActuatorPath() {
        return actuatorPath;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public void setActuatorPath(String actuatorPath) {
        this.actuatorPath = actuatorPath;
    }

    public String getModelHubEnvironmentId() {
        return modelHubEnvironmentId;
    }

    public void setModelHubEnvironmentId(String modelHubEnvironmentId) {
        this.modelHubEnvironmentId = modelHubEnvironmentId;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String baseUrl() {
        return "http://" + host + ":" + port;
    }
}
