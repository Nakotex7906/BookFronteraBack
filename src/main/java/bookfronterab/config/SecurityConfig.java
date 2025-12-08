package bookfronterab.config;

import bookfronterab.service.google.CustomOidcUserService; // <-- IMPORTACIÓN EL NUEVO SERVICIO
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(securedEnabled = true, jsr250Enabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    // Inyección el nuevo servicio OIDC
    private final CustomOidcUserService customOidcUserService;

    private final CustomAuthenticationSuccessHandler authenticationSuccessHandler;
    private final CustomAuthenticationFailureHandler authenticationFailureHandler;

    @Bean
SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        CsrfTokenRequestAttributeHandler requestHandler = new CsrfTokenRequestAttributeHandler();
        requestHandler.setCsrfRequestAttributeName(null);
    http
            .cors(cors -> cors.configurationSource(corsConfig()))
            .csrf(csrf -> csrf
                    .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                    .csrfTokenRequestHandler(requestHandler)
            )
            .headers(headers -> headers
                    .frameOptions(HeadersConfigurer.FrameOptionsConfig::deny)
                    .xssProtection(HeadersConfigurer.XXssConfig::disable)
                    .contentSecurityPolicy(csp -> csp.policyDirectives("script-src 'self'"))
            )

            .authorizeHttpRequests(auth -> auth
                    .requestMatchers(
                            "/", "/.well-known/**", "/favicon.ico", "/swagger-ui/**", "/v3/api-docs/**",
                            "/api/v1", "/api/v1/", "/api/v1/availability/**", "/h2-console/**", "/api/v1/auth-debug"
                    ).permitAll()

                    .requestMatchers("/api/v1/rooms/**").hasRole("ADMIN")

                    .anyRequest().authenticated()
            )
            .exceptionHandling(exception -> exception
                .authenticationEntryPoint((request, response, authException) ->
                    response.sendError(jakarta.servlet.http.HttpServletResponse.SC_UNAUTHORIZED, "Error: No autenticado"))
            )
            // --------------------------------------------------------
            .oauth2Login(oauth -> oauth
                    .userInfoEndpoint(userInfo -> userInfo
                            .oidcUserService(customOidcUserService)
                    )
                    .successHandler(authenticationSuccessHandler)
                    .failureHandler(authenticationFailureHandler)
            )
            .logout(logout -> logout
                    .logoutUrl("/api/v1/logout")
                    .logoutSuccessUrl("http://localhost:5173")
                    .invalidateHttpSession(true)
                    .deleteCookies("JSESSIONID")
            );

    return http.build();
}
    @Bean
    CorsConfigurationSource corsConfig() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(List.of("http://localhost:5173"));
        cfg.setAllowedMethods(List.of("GET","POST","PUT","DELETE","PATCH","OPTIONS"));
        cfg.setAllowedHeaders(List.of("*"));
        cfg.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }
}