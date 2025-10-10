package bookfronterab.service.google;

import bookfronterab.config.GoogleCalendarProperties;
import bookfronterab.model.User;
import bookfronterab.repo.UserRepository;
import com.google.api.client.auth.oauth2.AuthorizationCodeRequestUrl;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.MemoryDataStoreFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class GoogleOAuthService {

    private final GoogleCalendarProperties googleCalendarProperties;
    private final UserRepository userRepository;

    private GoogleAuthorizationCodeFlow flow;

    @jakarta.annotation.PostConstruct
    public void init() {
        try {
            GoogleClientSecrets.Details web = new GoogleClientSecrets.Details();
            web.setClientId(googleCalendarProperties.getClientId());
            web.setClientSecret(googleCalendarProperties.getClientSecret());
            GoogleClientSecrets clientSecrets = new GoogleClientSecrets().setWeb(web);

            flow = new GoogleAuthorizationCodeFlow.Builder(
                    new NetHttpTransport(),
                    GsonFactory.getDefaultInstance(),
                    clientSecrets,
                    GoogleCalendarService.SCOPES)
                    .setDataStoreFactory(new MemoryDataStoreFactory()) // En una app real, usa un DataStoreFactory persistente
                    .setAccessType("offline")
                    .build();
        } catch (IOException e) {
            throw new RuntimeException("Fallo al inicializar con GoogleAuthorizationCodeFlow", e);
        }
    }

    public String getAuthorizationUrl(String userEmail) {
        AuthorizationCodeRequestUrl authorizationUrl = flow.newAuthorizationUrl();
        authorizationUrl.setRedirectUri(googleCalendarProperties.getRedirectUri());
        authorizationUrl.setState(userEmail); // Usar state para pasar el email del usuario
        return authorizationUrl.toString();
    }

    public void processAuthorizationCode(String code, String userEmail) throws IOException {
        GoogleTokenResponse response = flow.newTokenRequest(code)
                .setRedirectUri(googleCalendarProperties.getRedirectUri())
                .execute();

        // El "userId" aquí es para el DataStore, no es el ID de tu entidad User.
        Credential credential = flow.createAndStoreCredential(response, userEmail);

        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado con email  " + userEmail));

        user.setGoogleAccessToken(credential.getAccessToken());
        user.setGoogleRefreshToken(credential.getRefreshToken());
        if (credential.getExpiresInSeconds() != null) {
            user.setGoogleTokenExpiryDate(OffsetDateTime.now().plusSeconds(credential.getExpiresInSeconds()));
        }

        userRepository.save(user);
    }

    public Credential getCredentialForUser(String userEmail) throws IOException {
        // Carga la credencial del DataStore. El flow se encargará de refrescar el token si es necesario.
        return flow.loadCredential(userEmail);
    }
}
