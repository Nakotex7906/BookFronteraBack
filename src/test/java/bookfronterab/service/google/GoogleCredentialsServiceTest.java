package bookfronterab.service.google;

import bookfronterab.model.User;
import bookfronterab.repo.UserRepository;
import bookfronterab.service.TimeService;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleRefreshTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GoogleCredentialsServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private TimeService timeService;
    @InjectMocks private GoogleCredentialsService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "clientId", "fake-client-id");
        ReflectionTestUtils.setField(service, "clientSecret", "fake-client-secret");
    }

    @Test
    @DisplayName("getCredential debe devolver credenciales si el token NO ha expirado")
    void getCredential_ShouldReturnCredential_WhenNotExpired() throws IOException {
        User user = new User();
        user.setEmail("test@ufro.cl");
        user.setGoogleAccessToken("valid-access");
        user.setGoogleRefreshToken("valid-refresh");
        user.setGoogleTokenExpiryDate(OffsetDateTime.now().plusHours(1));

        when(timeService.nowOffset()).thenReturn(OffsetDateTime.now());

        Credential credential = service.getCredential(user);

        assertNotNull(credential);
        assertEquals("valid-access", credential.getAccessToken());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("getCredential debe refrescar token si ha expirado")
    void getCredential_ShouldRefresh_WhenExpired() throws IOException {
        User user = new User();
        user.setEmail("test@ufro.cl");
        user.setGoogleAccessToken("expired-access");
        user.setGoogleRefreshToken("refresh-token");
        user.setGoogleTokenExpiryDate(OffsetDateTime.now().minusDays(1));

        when(timeService.nowOffset()).thenReturn(OffsetDateTime.now());
        when(timeService.zone()).thenReturn(ZoneId.of("UTC"));

        try (MockedConstruction<GoogleRefreshTokenRequest> ignored = mockConstruction(GoogleRefreshTokenRequest.class,
                (mock, context) -> {
                    GoogleTokenResponse response = new GoogleTokenResponse();
                    response.setAccessToken("NEW-ACCESS-TOKEN");
                    response.setExpiresInSeconds(3600L);

                    doReturn(response).when(mock).execute();
                })) {

            Credential credential = service.getCredential(user);

            assertNotNull(credential);
            assertEquals("NEW-ACCESS-TOKEN", credential.getAccessToken());
            assertEquals("NEW-ACCESS-TOKEN", user.getGoogleAccessToken());
            verify(userRepository).save(user);
        }
    }

    @Test
    @DisplayName("Debe lanzar IOException si faltan tokens")
    void getCredential_ShouldThrow_WhenTokensMissing() {
        User user = new User();
        user.setEmail("test@ufro.cl");

        assertThrows(IOException.class, () -> service.getCredential(user));
    }
}