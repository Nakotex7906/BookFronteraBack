package bookfronterab.service.google;

import bookfronterab.model.User;
import bookfronterab.repo.UserRepository;
import bookfronterab.service.TimeService;
import com.google.api.client.auth.oauth2.BearerToken;
import com.google.api.client.auth.oauth2.ClientParametersAuthentication;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.googleapis.auth.oauth2.GoogleRefreshTokenRequest;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.OffsetDateTime;

/**
 * Servicio encargado de gestionar y validar las credenciales OAuth2 de Google.
 * Realiza el refresco de tokens automáticamente si estos han expirado.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GoogleCredentialsService {

    private final UserRepository userRepository;
    private final TimeService timeService;

    // URL estándar de Google para obtener tokens
    private static final String TOKEN_SERVER_URL = "https://oauth2.googleapis.com/token";

    @Value("${spring.security.oauth2.client.registration.google.client-id}")
    private String clientId;

    @Value("${spring.security.oauth2.client.registration.google.client-secret}")
    private String clientSecret;

    /**
     * Obtiene una Credencial válida para el usuario.
     * Si el token de acceso ha expirado, intenta refrescarlo utilizando el refresh token.
     *
     * @param user El usuario del cual se requieren las credenciales.
     * @return Un objeto {@link Credential} válido.
     * @throws IOException Si faltan tokens o falla el proceso de refresco.
     */
    public Credential getCredential(User user) throws IOException {
        if (user.getGoogleAccessToken() == null || user.getGoogleRefreshToken() == null) {
            throw new IOException("Tokens OAuth2 no encontrados para el usuario: " + user.getEmail());
        }

        //  Verificamos si el token está expirado o cerca de expirar
        boolean isExpired = user.getGoogleTokenExpiryDate() == null ||
                user.getGoogleTokenExpiryDate().isBefore(timeService.nowOffset());

        if (isExpired) {
            log.info("El token de acceso para {} ha expirado. Iniciando refresco...", user.getEmail());
            refreshAccessToken(user);
        }

        //  Construimos y devolvemos la credencial usando clases estándar no deprecadas
        return createCredentialObject(user);
    }

    /**
     * Realiza la petición a Google para refrescar el token de acceso.
     * Actualiza la entidad User con los nuevos valores.
     *
     * @param user El usuario a actualizar.
     * @throws IOException Si la petición de refresco falla.
     */
    private void refreshAccessToken(User user) throws IOException {
        try {
            // Usamos GoogleRefreshTokenRequest en lugar de GoogleCredential.refreshToken()
            TokenResponse response = new GoogleRefreshTokenRequest(
                    new NetHttpTransport(),
                    GsonFactory.getDefaultInstance(),
                    user.getGoogleRefreshToken(),
                    clientId,
                    clientSecret
            ).execute();

            log.info("Token refrescado exitosamente para {}", user.getEmail());

            // Actualizamos el usuario con el nuevo Access Token
            user.setGoogleAccessToken(response.getAccessToken());

            // A veces Google rota el Refresh Token también, si viene uno nuevo, lo guardamos
            if (response.getRefreshToken() != null) {
                user.setGoogleRefreshToken(response.getRefreshToken());
            }

            // Calculamos la nueva fecha de expiración
            // getExpiresInSeconds() devuelve Long, si es null asumimos 3600 (1 hora)
            long expiresInSeconds = response.getExpiresInSeconds() != null ? response.getExpiresInSeconds() : 3600;
            user.setGoogleTokenExpiryDate(
                    OffsetDateTime.now(timeService.zone()).plusSeconds(expiresInSeconds)
            );

            userRepository.save(user);

        } catch (IOException e) {
            log.error("Fallo al refrescar token para {}. El refresh token podría haber sido revocado.", user.getEmail());
            throw new IOException("Error al refrescar el token de Google: " + e.getMessage(), e);
        }
    }

    /**
     * Crea el objeto Credential necesario para las librerías de Google API.
     * Reemplaza al builder deprecado de GoogleCredential.
     */
    private Credential createCredentialObject(User user) {
        return new Credential.Builder(BearerToken.authorizationHeaderAccessMethod())
                .setTransport(new NetHttpTransport())
                .setJsonFactory(GsonFactory.getDefaultInstance())
                .setTokenServerUrl(new GenericUrl(TOKEN_SERVER_URL))
                .setClientAuthentication(new ClientParametersAuthentication(clientId, clientSecret))
                .build()
                .setAccessToken(user.getGoogleAccessToken())
                .setRefreshToken(user.getGoogleRefreshToken());
    }
}