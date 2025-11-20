package com.dharaneesh.video_meeting.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/api/**", "/ws/**", "/css/**", "/js/**", "/images/**", "/favicon.ico");

                // I DON'T exclude /signin, /signup because:
                // 1. Layout.html needs sessionAuthenticated on ALL pages
                // 2. Interceptor only ADDS data, doesn't block access
                // 3. Controllers handle authentication logic, not interceptors
    }
}