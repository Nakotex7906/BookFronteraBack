package bookfronterab.service.google;

import bookfronterab.model.User;
import bookfronterab.repo.UserRepository;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class GoogleCredentialsService {

    private final UserRepository userRepository;

    @Value("${spring.security.oauth2.client.registration.google.client-id}")
    private String clientId;

    @Value("${spring.security.oauth2.client.registration.google.client-secret}")
    private String clientSecret;

    // Define the scopes needed for Google Calendar
    private static final List<String> SCOPES = Collections.singletonList("https://www.googleapis.com/auth/calendar.events");


    public Credential getCredential(User user) throws IOException {
        if (user.getGoogleAccessToken() == null || user.getGoogleRefreshToken() == null) {
            throw new IOException("Access token or refresh token not found for user: " + user.getEmail());
        }

        GoogleCredential credential = new GoogleCredential.Builder()
                .setTransport(new NetHttpTransport())
                .setJsonFactory(GsonFactory.getDefaultInstance())
                .setClientSecrets(clientId, clientSecret)
                .build()
                .setAccessToken(user.getGoogleAccessToken())
                .setRefreshToken(user.getGoogleRefreshToken());

        // Check if the access token is expired or about to expire
        // We consider it expired if expiry date is null or it's in the past
        boolean isExpired = user.getGoogleTokenExpiryDate() == null || user.getGoogleTokenExpiryDate().isBefore(timeService.nowOffset());

        if (isExpired) {
            log.info("Access token for user {} is expired. Attempting to refresh.", user.getEmail());
            boolean refreshed = credential.refreshToken();
            if (refreshed) {
                log.info("Access token for user {} successfully refreshed.", user.getEmail());
                // Update user with new tokens and expiry
                user.setGoogleAccessToken(credential.getAccessToken());
                user.setGoogleRefreshToken(credential.getRefreshToken()); // Refresh token might also change
                user.setGoogleTokenExpiryDate(OffsetDateTime.now(timeService.zone()).plusSeconds(credential.getExpiresInSeconds()));
                userRepository.save(user);
            } else {
                log.error("Failed to refresh access token for user {}. Refresh token might be invalid or revoked.", user.getEmail());
                throw new IOException("Failed to refresh Google access token.");
            }
        }

        return credential;
    }
}
