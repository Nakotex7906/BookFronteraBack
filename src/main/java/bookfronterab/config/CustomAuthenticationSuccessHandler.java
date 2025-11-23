package bookfronterab.config;

import bookfronterab.model.User;
import bookfronterab.model.UserRole;
import bookfronterab.repo.UserRepository;
import bookfronterab.service.TimeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Manejador que se ejecuta cuando el usuario se autentica exitosamente con Google.
 * Se encarga de sincronizar la información del usuario y guardar los tokens de acceso.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CustomAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final OAuth2AuthorizedClientService authorizedClientService;
    private final UserRepository userRepository;
    private final TimeService timeService;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
        OAuth2User oauthUser = oauthToken.getPrincipal();

        //  Procesar datos básicos del usuario
        User user = processUser(oauthUser);

        //  Obtener cliente autorizado (contiene los tokens)
        OAuth2AuthorizedClient client = authorizedClientService.loadAuthorizedClient(
                oauthToken.getAuthorizedClientRegistrationId(),
                oauthToken.getName()
        );

        //  Actualizar tokens de forma segura (Solución a java:S2259)
        if (client != null) {
            updateUserTokens(user, client);
        } else {
            log.warn("No se pudo cargar el cliente autorizado para el usuario {}", user.getEmail());
        }

        userRepository.save(user);

        //  Redirigir al frontend
        response.sendRedirect("http://localhost:5173");
    }

    /**
     * Busca el usuario en la BD o crea uno nuevo si no existe.
     *
     * @param oauthUser El usuario proveniente de Google.
     * @return La entidad User gestionada.
     */
    private User processUser(OAuth2User oauthUser) {
        Map<String, Object> attributes = oauthUser.getAttributes();
        String email = (String) attributes.get("email");
        String name = (String) attributes.get("name");

        Optional<User> userOptional = userRepository.findByEmail(email);
        User user;

        if (userOptional.isEmpty()) {
            user = new User();
            user.setEmail(email);
            user.setNombre(name);
            user.setRol(UserRole.STUDENT);
            user.setCreadoEn(timeService.nowOffset());
        } else {
            user = userOptional.get();
            user.setNombre(name);
        }
        return user;
    }

    /**
     * Extrae y actualiza los tokens de acceso y refresco del cliente OAuth2.
     * Utiliza variables locales para evitar NullPointerExceptions y cumplir con reglas de SonarQube.
     *
     * @param user   El usuario a actualizar.
     * @param client El cliente autorizado que contiene los tokens.
     */
    private void updateUserTokens(User user, OAuth2AuthorizedClient client) {
        // Manejo seguro del Access Token
        OAuth2AccessToken accessToken = client.getAccessToken();
        if (accessToken != null) {
            user.setGoogleAccessToken(accessToken.getTokenValue());

            // Verificamos la fecha de expiración usando la variable local 'accessToken'
            Instant expiresAt = accessToken.getExpiresAt();
            if (expiresAt != null) {
                user.setGoogleTokenExpiryDate(expiresAt.atZone(timeService.zone()).toOffsetDateTime());
            }
        }

        // Manejo seguro del Refresh Token
        OAuth2RefreshToken refreshToken = client.getRefreshToken();
        if (refreshToken != null) {
            // Al usar la variable local 'refreshToken', aseguramos que no sea null aquí
            user.setGoogleRefreshToken(refreshToken.getTokenValue());
        }
    }
}