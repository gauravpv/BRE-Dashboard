package com.bredashboard.auth.security;

import com.bredashboard.auth.config.AuthProperties;
import com.bredashboard.auth.service.AzureOidcUserService;
import com.bredashboard.auth.service.BreUserDetailsService;

import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final AuthProperties authProperties;
    private final OAuth2UserService<OidcUserRequest, OidcUser> oidcUserService;
    private final AuthenticationSuccessHandler loginSuccessHandler;
    private final AzureLoginFailureHandler azureLoginFailureHandler;
    private final UserDetailsService userDetailsService;

    public SecurityConfig(
            AuthProperties authProperties,
            AzureOidcUserService oidcUserService,
            LoginSuccessHandler loginSuccessHandler,
            AzureLoginFailureHandler azureLoginFailureHandler,
            BreUserDetailsService userDetailsService
    ) {
        this.authProperties = authProperties;
        this.oidcUserService = oidcUserService;
        this.loginSuccessHandler = loginSuccessHandler;
        this.azureLoginFailureHandler = azureLoginFailureHandler;
        this.userDetailsService = userDetailsService;
    }

    @Bean
    @Order(1)
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/login",
                                "/login/process",
                                "/error",
                                "/css/**",
                                "/js/**",
                                "/favicon.ico"
                        ).permitAll()
                        .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll()
                        .requestMatchers(EndpointRequest.to("health", "info")).permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .anyRequest().authenticated()
                )
                .csrf(csrf -> {
                    CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
                    CsrfTokenRequestAttributeHandler requestHandler = new CsrfTokenRequestAttributeHandler();
                    requestHandler.setCsrfRequestAttributeName("_csrf");
                    csrf.csrfTokenRepository(repository)
                            .csrfTokenRequestHandler(requestHandler)
                            .ignoringRequestMatchers("/h2-console/**", "/login/process");
                })
                .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
                .headers(headers -> headers
                        .frameOptions(frame -> frame.sameOrigin())
                        .contentTypeOptions(contentType -> {})
                )
                .logout(logout -> logout
                        .logoutRequestMatcher(new AntPathRequestMatcher("/logout"))
                        .logoutSuccessUrl("/login?logout")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                )
                .sessionManagement(session -> session
                        .maximumSessions(1)
                        .maxSessionsPreventsLogin(false)
                        .expiredUrl("/login?expired")
                        .sessionRegistry(sessionRegistry())
                );

        if (authProperties.isAzureOAuthConfigured()) {
            http.oauth2Login(oauth -> oauth
                    .loginPage("/login")
                    .userInfoEndpoint(userInfo -> userInfo.oidcUserService(oidcUserService))
                    .successHandler(loginSuccessHandler)
                    .failureHandler(azureLoginFailureHandler)
            );
        }

        if (authProperties.isDevMode()) {
            http.userDetailsService(userDetailsService);
            http.formLogin(form -> form
                    .loginPage("/login")
                    .loginProcessingUrl("/login/process")
                    .usernameParameter("email")
                    .passwordParameter("password")
                    .successHandler(loginSuccessHandler)
                    .failureUrl("/login?error")
                    .permitAll()
            );
        }

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }
}
