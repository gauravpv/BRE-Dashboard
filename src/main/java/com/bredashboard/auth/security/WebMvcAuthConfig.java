package com.bredashboard.auth.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcAuthConfig implements WebMvcConfigurer {

    private final NavAccessInterceptor navAccessInterceptor;

    public WebMvcAuthConfig(NavAccessInterceptor navAccessInterceptor) {
        this.navAccessInterceptor = navAccessInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(navAccessInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/login", "/login/process", "/login/oauth2/**", "/logout", "/oauth2/**", "/auth/**",
                        "/error", "/css/**", "/js/**", "/h2-console/**", "/api/**"
                );
    }
}
