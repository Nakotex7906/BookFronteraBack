package bookfronterab.config;

import bookfronterab.service.google.CustomOidcUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(securedEnabled = true, jsr250Enabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomOidcUserService customOidcUserService;
    private final CustomAuthenticationSuccessHandler authenticationSuccessHandler;
    private final CustomAuthenticationFailureHandler authenticationFailureHandler;

    // Inyectamos la URL de producción desde las variables de entorno
    @Value("${app.frontend.url}")
    private String frontendUrl;

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // 1. Configuración de CORS
                .cors(cors -> cors.configurationSource(corsConfig()))

                // 2. DESACTIVAR CSRF (Soluciona el error 403 Forbidden en POST/PUT)
                // Al estar en dominios distintos (Vercel vs Render), las cookies de CSRF suelen fallar.
                // En APIs REST modernas con OAuth2, es seguro desactivarlo si se maneja bien la sesión.
                .csrf(AbstractHttpConfigurer::disable)

                .headers(headers -> headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin))
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
                .oauth2Login(oauth -> oauth
                        .userInfoEndpoint(userInfo -> userInfo.oidcUserService(customOidcUserService))
                        .successHandler(authenticationSuccessHandler)
                        .failureHandler(authenticationFailureHandler)
                )
                .logout(logout -> logout
                        .logoutUrl("/api/v1/logout")
                        .logoutSuccessUrl(frontendUrl) // Redirige a Vercel al salir
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                );

        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfig() {
        CorsConfiguration cfg = new CorsConfiguration();

        // Lista de orígenes permitidos: Localhost + Producción
        cfg.setAllowedOrigins(List.of("http://localhost:5173", frontendUrl));

        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));

        // Permitir todos los headers (Authorization, Content-Type, etc.)
        cfg.setAllowedHeaders(List.of("*"));

        // IMPORTANTE: Permitir credenciales (cookies)
        cfg.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }
}
