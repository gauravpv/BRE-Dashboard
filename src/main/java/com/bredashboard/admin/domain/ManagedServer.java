package com.bredashboard.admin.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "managed_servers")
public class ManagedServer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 120)
    private String name;

    @Column(nullable = false, length = 255)
    private String host;

    @Column(name = "ssh_port", nullable = false)
    private int sshPort = 22;

    @Column(name = "ssh_user", nullable = false, length = 64)
    private String sshUser;

    @Column(nullable = false)
    private boolean enabled = true;

    @OneToMany(mappedBy = "server")
    private List<ManagedService> services = new ArrayList<>();

    protected ManagedServer() {
    }

    public ManagedServer(String name, String host, int sshPort, String sshUser) {
        this.name = name;
        this.host = host;
        this.sshPort = sshPort;
        this.sshUser = sshUser;
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

    public int getSshPort() {
        return sshPort;
    }

    public String getSshUser() {
        return sshUser;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public List<ManagedService> getServices() {
        return services;
    }
}
