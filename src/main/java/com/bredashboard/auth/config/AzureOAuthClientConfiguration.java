package com.bredashboard.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.util.StringUtils;

@Configuration
public class AzureOAuthClientConfiguration {

    @Bean
    @Conditional(AzureOAuthConfiguredCondition.class)
    public ClientRegistrationRepository azureClientRegistrationRepository(AuthProperties authProperties) {
        AuthProperties.Azure azure = authProperties.getAzure();
        String tenant = azure.getTenantId();
        ClientRegistration registration = ClientRegistration.withRegistrationId("azure")
                .clientId(azure.getClientId())
                .clientSecret(azure.getClientSecret())
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("openid", "profile", "email")
                .authorizationUri("https://login.microsoftonline.com/" + tenant + "/oauth2/v2.0/authorize")
                .tokenUri("https://login.microsoftonline.com/" + tenant + "/oauth2/v2.0/token")
                .jwkSetUri("https://login.microsoftonline.com/" + tenant + "/discovery/v2.0/keys")
                .issuerUri("https://login.microsoftonline.com/" + tenant + "/v2.0")
                .userNameAttributeName("preferred_username")
                .clientName("Microsoft Entra ID")
                .build();
        return new InMemoryClientRegistrationRepository(registration);
    }

    static final class AzureOAuthConfiguredCondition implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            Environment env = context.getEnvironment();
            return StringUtils.hasText(env.getProperty("bredashboard.auth.azure.client-id"))
                    && StringUtils.hasText(env.getProperty("bredashboard.auth.azure.client-secret"))
                    && StringUtils.hasText(env.getProperty("bredashboard.auth.azure.tenant-id"));
        }
    }
}
