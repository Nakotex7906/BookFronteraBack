package bookfronterab.config;

import bookfronterab.service.google.CustomOidcUserService; // <-- IMPORTACIÓN EL NUEVO SERVICIO
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
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;

import java.util.List;
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
    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    @Bean
    @SuppressWarnings("java:S3330")
SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        CsrfTokenRequestAttributeHandler requestHandler = new CsrfTokenRequestAttributeHandler();
        requestHandler.setCsrfRequestAttributeName(null);
    http
            .cors(cors -> cors.configurationSource(corsConfig()))
            //Desactivamos CSRF para evitar problemas entre Vercel y Render
            .csrf(AbstractHttpConfigurer::disable)
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
                    .logoutSuccessHandler((request, response, authentication) ->
                            response.setStatus(HttpServletResponse.SC_OK)
                    )
                    .invalidateHttpSession(true)
                    .deleteCookies("JSESSIONID")
            );
        http.addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class);
    return http.build();
}
    @Bean
    CorsConfigurationSource corsConfig() {
        CorsConfiguration cfg = new CorsConfiguration();
        // El split es por si necesitas permitir varios (ej: el dominio oficial Y localhost para pruebas)
        cfg.setAllowedOrigins(List.of(allowedOrigins.split(",")));
        cfg.setAllowedMethods(List.of("GET","POST","PUT","DELETE","PATCH","OPTIONS"));
        cfg.setAllowedHeaders(List.of("*"));
        cfg.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }

    // Clase auxiliar para forzar la carga del token CSRF
    class CsrfCookieFilter extends OncePerRequestFilter {

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
                throws ServletException, IOException {
            // Obliga a Spring a cargar el token diferido
            CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
            if (csrfToken != null) {
                // Invocar .getToken() hace que se escriba la cookie
                csrfToken.getToken();
            }
            filterChain.doFilter(request, response);
        }
    }

}