package bookfronterab.config;

import bookfronterab.service.google.CustomOidcUserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(securedEnabled = true, jsr250Enabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String XSRF_TOKEN_HEADER = "X-XSRF-TOKEN";

    private final CustomOidcUserService customOidcUserService;
    private final CustomAuthenticationSuccessHandler authenticationSuccessHandler;
    private final CustomAuthenticationFailureHandler authenticationFailureHandler;

    // INYECCIÓN DE LA URL DEL FRONTEND DESDE VARIABLES DE ENTORNO
    @Value("${app.frontend.url}")
    private String frontendUrl;

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        CsrfTokenRequestAttributeHandler requestHandler = new CsrfTokenRequestAttributeHandler();
        requestHandler.setCsrfRequestAttributeName(null);

        CookieCsrfTokenRepository tokenRepository = new CookieCsrfTokenRepository();
        tokenRepository.setCookiePath("/");

        http
                .cors(cors -> cors.configurationSource(corsConfig()))
                .csrf(csrf -> csrf
                        .csrfTokenRepository(tokenRepository)
                        .csrfTokenRequestHandler(requestHandler)
                )
                .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)

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
                        // USAR LA VARIABLE PARA QUE REDIRIJA AL SITIO CORRECTO EN PROD
                        .logoutSuccessUrl(frontendUrl)
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID", "XSRF-TOKEN")
                );

        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfig() {
        CorsConfiguration cfg = new CorsConfiguration();

        // AQUÍ ESTÁ LA SOLUCIÓN AL CORS:
        // Acepta localhost (desarrollo) Y la URL de producción (Vercel)
        cfg.setAllowedOrigins(List.of("http://localhost:5173", frontendUrl));

        cfg.setAllowedMethods(List.of("GET","POST","PUT","DELETE","PATCH","OPTIONS"));

        cfg.setAllowedHeaders(List.of(
                "Authorization",
                "Cache-Control",
                "Content-Type",
                XSRF_TOKEN_HEADER,
                "X-Requested-With"
        ));

        cfg.setAllowCredentials(true);
        cfg.setExposedHeaders(List.of(XSRF_TOKEN_HEADER));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }

    // Filtro interno
    static class CsrfCookieFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
                throws ServletException, IOException {
            CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
            if (csrfToken != null) {
                String token = csrfToken.getToken();
                if (token != null) {
                    response.setHeader(XSRF_TOKEN_HEADER, token);
                }
            }
            filterChain.doFilter(request, response);
        }
    }
}