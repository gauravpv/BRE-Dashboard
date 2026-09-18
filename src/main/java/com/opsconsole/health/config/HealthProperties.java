package com.opsconsole.health.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@ConfigurationProperties(prefix = "opsconsole")
public class HealthProperties {

    private final Health health = new Health();
    private final List<MonitorSeed> monitors = new ArrayList<>();

    public Health getHealth() {
        return health;
    }

    public ModelHub getModelHub() {
        return health.getModelHub();
    }

    public List<MonitorSeed> getMonitors() {
        return monitors;
    }

    public static class Health {
        private int refreshSeconds = 30;
        private int refreshIntervalMs = 30000;
        private int connectTimeoutMs = 3000;
        private int readTimeoutMs = 5000;
        private String defaultActuatorPath = "/actuator";
        private final ModelHub modelHub = new ModelHub();

        public ModelHub getModelHub() {
            return modelHub;
        }

        public int getRefreshSeconds() {
            return refreshSeconds;
        }

        public void setRefreshSeconds(int refreshSeconds) {
            this.refreshSeconds = refreshSeconds;
        }

        public int getRefreshIntervalMs() {
            return refreshIntervalMs;
        }

        public void setRefreshIntervalMs(int refreshIntervalMs) {
            this.refreshIntervalMs = refreshIntervalMs;
        }

        public int getConnectTimeoutMs() {
            return connectTimeoutMs;
        }

        public void setConnectTimeoutMs(int connectTimeoutMs) {
            this.connectTimeoutMs = connectTimeoutMs;
        }

        public int getReadTimeoutMs() {
            return readTimeoutMs;
        }

        public void setReadTimeoutMs(int readTimeoutMs) {
            this.readTimeoutMs = readTimeoutMs;
        }

        public String getDefaultActuatorPath() {
            return defaultActuatorPath;
        }

        public void setDefaultActuatorPath(String defaultActuatorPath) {
            this.defaultActuatorPath = defaultActuatorPath;
        }
    }

    public static class ModelHub {
        private boolean enabled = false;
        private final HubTarget uat = new HubTarget();
        private final HubTarget prod = new HubTarget();
        private final OAuth oauth = new OAuth();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public HubTarget getUat() {
            return uat;
        }

        public HubTarget getProd() {
            return prod;
        }

        public OAuth getOauth() {
            return oauth;
        }

        /** Backward-compatible default: UAT base URL. */
        public String getBaseUrl() {
            return uat.getBaseUrl();
        }

        public void setBaseUrl(String baseUrl) {
            uat.setBaseUrl(baseUrl);
        }
    }

    public static class OAuth {
        private String tokenUrl = "https://login.microsoftonline.com/710de1d3-2901-4647-89e7-3b01f1c2806d/oauth2/v2.0/token";
        private String clientId = "91becc5d-213e-4d9b-b0dc-e481c31fde4c";
        private String grantType = "password";
        private String username = "";
        private String password = "";
        private String scope = "email openid profile offline_access api://91becc5d-213e-4d9b-b0dc-e481c31fde4c/default";

        public String getTokenUrl() {
            return tokenUrl;
        }

        public void setTokenUrl(String tokenUrl) {
            this.tokenUrl = tokenUrl;
        }

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getGrantType() {
            return grantType;
        }

        public void setGrantType(String grantType) {
            this.grantType = grantType;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getScope() {
            return scope;
        }

        public void setScope(String scope) {
            this.scope = scope;
        }
    }

    public static class HubTarget {
        private String baseUrl = "https://emicardbre.bajajfinserv.in";
        private List<String> baseUrls = new ArrayList<>();

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public List<String> getBaseUrls() {
            return baseUrls;
        }

        public void setBaseUrls(List<String> baseUrls) {
            this.baseUrls = baseUrls != null ? baseUrls : new ArrayList<>();
        }

        /** `base-url` plus extra `base-urls`, order preserved, blanks removed. */
        public List<String> resolvedBaseUrls() {
            Set<String> urls = new LinkedHashSet<>();
            addNormalized(urls, baseUrl);
            for (String extra : baseUrls) {
                addNormalized(urls, extra);
            }
            return List.copyOf(urls);
        }

        private static void addNormalized(Set<String> urls, String value) {
            if (!StringUtils.hasText(value)) {
                return;
            }
            String normalized = value.trim();
            if (normalized.endsWith("/")) {
                normalized = normalized.substring(0, normalized.length() - 1);
            }
            urls.add(normalized);
        }
    }

    public static class MonitorSeed {
        private String name;
        private String host;
        private int port;
        private String environment;
        private String region;
        private String actuatorPath;
        private String modelHubEnvironmentId;

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

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public String getEnvironment() {
            return environment;
        }

        public void setEnvironment(String environment) {
            this.environment = environment;
        }

        public String getRegion() {
            return region;
        }

        public void setRegion(String region) {
            this.region = region;
        }

        public String getActuatorPath() {
            return actuatorPath;
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
    }
}
