package com.bredashboard.tester.config;

import com.bredashboard.tester.domain.BajajEnvironment;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "bredashboard.bajaj-tester")
public class BajajTesterProperties {

    private EnvironmentConfig uat = new EnvironmentConfig();
    private EnvironmentConfig prod = new EnvironmentConfig();

    public EnvironmentConfig getUat() {
        return uat;
    }

    public void setUat(EnvironmentConfig uat) {
        this.uat = uat;
    }

    public EnvironmentConfig getProd() {
        return prod;
    }

    public void setProd(EnvironmentConfig prod) {
        this.prod = prod;
    }

    public EnvironmentConfig config(BajajEnvironment environment) {
        return environment == BajajEnvironment.PROD ? prod : uat;
    }

    public static class EnvironmentConfig {
        private String baseUrl = "https://sauat.bajajfinserv.in/apis";
        private String operationListPath = "operationallist";
        private String tokenPath = "oauth-token";
        private long tokenTtlSeconds = 600;
        /** How long a fetched operation list (hashcode/salt per API) is reused. Bajaj rotates it daily. */
        private long operationListTtlSeconds = 86_400;
        /**
         * API invokes post the Base64 ciphertext wrapped in JSON quotes ({@code "abc..."}),
         * matching the per-API Postman pre-request script. The operation-list and oauth-token
         * calls post it bare, so this only applies to {@link #apiUrl(String)} requests.
         */
        private boolean quoteEncryptedBody = true;
        private String encryptionKey = "";
        private String encryptionIv = "";
        private Map<String, String> headers = new LinkedHashMap<>();
        private Map<String, String> apiHeaders = new LinkedHashMap<>();
        private Map<String, String> requestBody = new LinkedHashMap<>();
        private Map<String, String> tokenRequestBody = new LinkedHashMap<>();

        /**
         * Headers for token/API calls: the shared {@code headers} with {@code api-headers}
         * layered on top (the operation-list call keeps using {@code headers} as-is).
         */
        public Map<String, String> effectiveApiHeaders() {
            Map<String, String> merged = new LinkedHashMap<>(headers);
            merged.putAll(apiHeaders);
            return merged;
        }

        public String operationListUrl() {
            String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
            String path = operationListPath.startsWith("/") ? operationListPath.substring(1) : operationListPath;
            return base + "/" + path;
        }

        public String tokenUrl() {
            return apiUrl(tokenPath);
        }

        public String apiUrl(String publicUrl) {
            String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
            String path = publicUrl.startsWith("/") ? publicUrl.substring(1) : publicUrl;
            return base + "/" + path;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getOperationListPath() {
            return operationListPath;
        }

        public void setOperationListPath(String operationListPath) {
            this.operationListPath = operationListPath;
        }

        public String getTokenPath() {
            return tokenPath;
        }

        public void setTokenPath(String tokenPath) {
            this.tokenPath = tokenPath;
        }

        public long getTokenTtlSeconds() {
            return tokenTtlSeconds;
        }

        public void setTokenTtlSeconds(long tokenTtlSeconds) {
            this.tokenTtlSeconds = tokenTtlSeconds;
        }

        public long getOperationListTtlSeconds() {
            return operationListTtlSeconds;
        }

        public void setOperationListTtlSeconds(long operationListTtlSeconds) {
            this.operationListTtlSeconds = operationListTtlSeconds;
        }

        public boolean isQuoteEncryptedBody() {
            return quoteEncryptedBody;
        }

        public void setQuoteEncryptedBody(boolean quoteEncryptedBody) {
            this.quoteEncryptedBody = quoteEncryptedBody;
        }

        public String getEncryptionKey() {
            return encryptionKey;
        }

        public void setEncryptionKey(String encryptionKey) {
            this.encryptionKey = encryptionKey;
        }

        public String getEncryptionIv() {
            return encryptionIv;
        }

        public void setEncryptionIv(String encryptionIv) {
            this.encryptionIv = encryptionIv;
        }

        public Map<String, String> getHeaders() {
            return headers;
        }

        public void setHeaders(Map<String, String> headers) {
            this.headers = headers;
        }

        public Map<String, String> getApiHeaders() {
            return apiHeaders;
        }

        public void setApiHeaders(Map<String, String> apiHeaders) {
            this.apiHeaders = apiHeaders;
        }

        public Map<String, String> getTokenRequestBody() {
            return tokenRequestBody;
        }

        public void setTokenRequestBody(Map<String, String> tokenRequestBody) {
            this.tokenRequestBody = tokenRequestBody;
        }

        public Map<String, String> getRequestBody() {
            return requestBody;
        }

        public void setRequestBody(Map<String, String> requestBody) {
            this.requestBody = requestBody;
        }
    }
}