package com.minhhai.amimate.config;

import com.minhhai.amimate.repository.DeviceRepository;
import com.minhhai.amimate.security.CustomAuthEntryPoint;
import com.minhhai.amimate.security.DeviceKeyAuthFilter;
import com.minhhai.amimate.security.JwtAuthFilter;
import com.minhhai.amimate.security.JwtService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, DeviceRepository devices,
                                    JwtService jwtService, CustomAuthEntryPoint customAuthEntryPoint) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/devices/factory/generate").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/logs").hasRole("DEVICE")
                        .requestMatchers("/api/v1/logs/**", "/api/v1/devices/**").hasRole("USER")
                        .anyRequest().denyAll())
                .exceptionHandling(e -> e.authenticationEntryPoint(customAuthEntryPoint))
                .addFilterBefore(new DeviceKeyAuthFilter(devices), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new JwtAuthFilter(jwtService), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}