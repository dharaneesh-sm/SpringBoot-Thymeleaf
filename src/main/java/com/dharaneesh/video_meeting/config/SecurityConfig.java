package com.dharaneesh.video_meeting.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // Disable CSRF for API endpoints and WebSocket
                .csrf(csrf -> csrf
                        .ignoringRequestMatchers("/api/**", "/ws/**", "/h2-console/**"))

                // Configure authorization
//                .authorizeHttpRequests(authz -> authz
//                        .requestMatchers("/", "/create-meeting", "/join-meeting",
//                                "/meeting/**", "/api/**", "/ws/**",
//                                "/css/**", "/js/**", "/images/**", "/favicon.ico",
//                                "/h2-console/**").permitAll()
//                        .anyRequest().authenticated())

                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())

                // Disable frame options for H2 console
                .headers(headers -> headers.frameOptions().disable())

                // Disable HTTP Basic authentication
                .httpBasic(httpBasic -> httpBasic.disable())

                // Disable form login
                .formLogin(formLogin -> formLogin.disable());

        return http.build();
    }
}
