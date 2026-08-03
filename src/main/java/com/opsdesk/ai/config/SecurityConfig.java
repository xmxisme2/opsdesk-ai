package com.opsdesk.ai.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsdesk.ai.common.exception.ErrorCode;
import com.opsdesk.ai.common.response.ApiResponse;
import com.opsdesk.ai.security.ServiceJwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.nio.charset.StandardCharsets;

/**
 * AI 服务安全配置。
 *
 * <p>仅 Actuator 探针和接口文档公开，全部业务及内部接口必须使用 Service JWT。</p>
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   ServiceJwtAuthenticationFilter serviceJwtFilter,
                                                   ObjectMapper objectMapper) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/actuator/health/**",
                                "/actuator/info",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html"
                        ).permitAll()
                        .anyRequest().hasRole("SERVICE")
                )
                .exceptionHandling(exception -> exception.authenticationEntryPoint((request, response, cause) -> {
                    response.setStatus(401);
                    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    objectMapper.writeValue(response.getWriter(), ApiResponse.error(ErrorCode.UNAUTHORIZED));
                }))
                .addFilterBefore(serviceJwtFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
