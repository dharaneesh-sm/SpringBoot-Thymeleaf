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
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**", "/ws/**"))

                // Configure authorization - allow most endpoints but let controllers handle auth
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/signin", "/signup", "/join-meeting",
                                "/meeting/**", "/api/**", "/ws/**",
                                "/css/**", "/js/**", "/actuator/**").permitAll()
                        .requestMatchers("/create-meeting", "/profile/**", "/signout").permitAll()
                        .anyRequest().authenticated())

                // Disable HTTP Basic authentication
                .httpBasic(httpBasic -> httpBasic.disable())

                // Disable form login
                .formLogin(formLogin -> formLogin.disable())
                
                // Configure session management
                .sessionManagement(session -> session
                        .maximumSessions(1)
                        .maxSessionsPreventsLogin(false));

        return http.build();
    }
}
