package com.bookstore.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.bookstore.filter.JwtFilter;

import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.web.cors.CorsConfiguration;



import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.userdetails.UserDetailsService;

import org.springframework.http.HttpMethod;

// import com.bookstore.service.UserDetailsService;
import java.util.List;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableScheduling
@RequiredArgsConstructor
public class SecurityConfig {

    private final UserDetailsService userDetailsService;
    private final JwtFilter jwtFilter;

    @Value("${security.password-encoder.strength:12}")
    private int passwordEncoderStrength;

    @Value("${cors.allowed-origins:http://localhost:3000,http://localhost:5173}")
    private List<String> allowedOrigins;

    /**
     * When true (local/dev), Prometheus can scrape without a JWT.
     * Prod sets this false — scrape via private network with an ADMIN token, or keep
     * the metrics port off the public load balancer.
     */
    @Value("${security.actuator.prometheus-public:true}")
    private boolean prometheusPublic;

    /** Swagger/OpenAPI UI — disable in production. */
    @Value("${security.swagger-public:true}")
    private boolean swaggerPublic;

    @Bean
    public PasswordEncoder passwordEncoder(){
        return new BCryptPasswordEncoder(passwordEncoderStrength);
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider(){
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .authorizeHttpRequests(auth -> {
                var chain = auth
                    .requestMatchers("/api/auth/register", "/api/auth/login", "/api/auth/refresh").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/books/**").permitAll()
                    .requestMatchers("/actuator/health", "/actuator/info").permitAll();

                if (prometheusPublic) {
                    chain = chain.requestMatchers("/actuator/prometheus").permitAll();
                } else {
                    chain = chain.requestMatchers("/actuator/prometheus").hasRole("ADMIN");
                }

                if (swaggerPublic) {
                    chain = chain.requestMatchers(
                            "/swagger-ui.html", "/swagger-ui/**",
                            "/v3/api-docs", "/v3/api-docs/**").permitAll();
                }

                chain.requestMatchers("/api/admin/**").hasAnyRole("ADMIN")
                    .requestMatchers(HttpMethod.POST, "/api/payments/webhook/**").permitAll()
                    .anyRequest()
                    .authenticated();
            })
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .exceptionHandling(ex -> ex
            .authenticationEntryPoint((request, response, e) -> {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.setCharacterEncoding("UTF-8");
                response.getWriter().write(
                    "{\"status\":401,\"error\":\"UNAUTHORIZED\",\"message\":\"Authentication required\",\"path\":\"" + request.getRequestURI() + "\"}");
            })
            .accessDeniedHandler((request, response, e) -> {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json");
                response.setCharacterEncoding("UTF-8");
                response.getWriter().write(
                    "{\"status\":403,\"error\":\"FORBIDDEN\",\"message\":\"You do not have permission to perform this action\",\"path\":\"" + request.getRequestURI() + "\"}");
            })
        )
        .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setExposedHeaders(List.of("Authorization"));
        config.setAllowedHeaders(List.of(
                "Authorization", "Content-Type", "Accept",
                "Idempotency-key", "X-Paystack-Signature", "X-Webhook-Secret"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
