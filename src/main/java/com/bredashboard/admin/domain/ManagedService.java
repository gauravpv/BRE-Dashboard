package com.bredashboard.admin.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "managed_services")
public class ManagedService {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "server_id", nullable = false)
    private ManagedServer server;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 255)
    private String description;

    @Column(nullable = false, length = 80)
    private String category;

    @Column(nullable = false)
    private int port;

    @Column(name = "start_script", nullable = false, length = 512)
    private String startScript;

    @Column(name = "stop_script", nullable = false, length = 512)
    private String stopScript;

    @Column(name = "restart_script", nullable = false, length = 512)
    private String restartScript;

    @Column(name = "properties_path", nullable = false, length = 512)
    private String propertiesPath;

    @Column(nullable = false)
    private boolean enabled = true;

    protected ManagedService() {
    }

    public ManagedService(
            ManagedServer server,
            String name,
            String description,
            String category,
            int port,
            String startScript,
            String stopScript,
            String restartScript,
            String propertiesPath
    ) {
        this.server = server;
        this.name = name;
        this.description = description;
        this.category = category;
        this.port = port;
        this.startScript = startScript;
        this.stopScript = stopScript;
        this.restartScript = restartScript;
        this.propertiesPath = propertiesPath;
    }

    public Long getId() {
        return id;
    }

    public ManagedServer getServer() {
        return server;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getCategory() {
        return category;
    }

    public int getPort() {
        return port;
    }

    public String getStartScript() {
        return startScript;
    }

    public String getStopScript() {
        return stopScript;
    }

    public String getRestartScript() {
        return restartScript;
    }

    public String getPropertiesPath() {
        return propertiesPath;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
