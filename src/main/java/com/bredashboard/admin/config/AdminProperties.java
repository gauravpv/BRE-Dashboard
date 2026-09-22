package com.bredashboard.admin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "bredashboard.admin")
public class AdminProperties {

    /** dev = simulate SSH; live = real SSH connections. */
    private String mode = "dev";

    private final Ssh ssh = new Ssh();
    private final List<ServerSeed> servers = new ArrayList<>();

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public boolean isDevMode() {
        return !"live".equalsIgnoreCase(mode);
    }

    public Ssh getSsh() {
        return ssh;
    }

    public List<ServerSeed> getServers() {
        return servers;
    }

    public static class Ssh {
        private String privateKeyPath = "";
        private int connectTimeoutMs = 10000;
        private int commandTimeoutMs = 60000;

        public String getPrivateKeyPath() {
            return privateKeyPath;
        }

        public void setPrivateKeyPath(String privateKeyPath) {
            this.privateKeyPath = privateKeyPath;
        }

        public int getConnectTimeoutMs() {
            return connectTimeoutMs;
        }

        public void setConnectTimeoutMs(int connectTimeoutMs) {
            this.connectTimeoutMs = connectTimeoutMs;
        }

        public int getCommandTimeoutMs() {
            return commandTimeoutMs;
        }

        public void setCommandTimeoutMs(int commandTimeoutMs) {
            this.commandTimeoutMs = commandTimeoutMs;
        }
    }

    public static class ServerSeed {
        private String name;
        private String host;
        private int sshPort = 22;
        private String sshUser = "bredashboard";
        private List<ServiceSeed> services = new ArrayList<>();

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getSshPort() {
            return sshPort;
        }

        public void setSshPort(int sshPort) {
            this.sshPort = sshPort;
        }

        public String getSshUser() {
            return sshUser;
        }

        public void setSshUser(String sshUser) {
            this.sshUser = sshUser;
        }

        public List<ServiceSeed> getServices() {
            return services;
        }

        public void setServices(List<ServiceSeed> services) {
            this.services = services;
        }
    }

    public static class ServiceSeed {
        private String name;
        private String description;
        private String category = "General";
        private int port;
        private String startScript;
        private String stopScript;
        private String restartScript;
        private String propertiesPath;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getCategory() {
            return category;
        }

        public void setCategory(String category) {
            this.category = category;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public String getStartScript() {
            return startScript;
        }

        public void setStartScript(String startScript) {
            this.startScript = startScript;
        }

        public String getStopScript() {
            return stopScript;
        }

        public void setStopScript(String stopScript) {
            this.stopScript = stopScript;
        }

        public String getRestartScript() {
            return restartScript;
        }

        public void setRestartScript(String restartScript) {
            this.restartScript = restartScript;
        }

        public String getPropertiesPath() {
            return propertiesPath;
        }

        public void setPropertiesPath(String propertiesPath) {
            this.propertiesPath = propertiesPath;
        }
    }
}
