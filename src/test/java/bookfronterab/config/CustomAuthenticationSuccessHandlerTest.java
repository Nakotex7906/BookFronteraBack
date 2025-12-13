package bookfronterab.config;

import bookfronterab.model.User;
import bookfronterab.model.UserRole;
import bookfronterab.repo.UserRepository;
import bookfronterab.service.TimeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.io.IOException;
import java.lang.reflect.Field;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomAuthenticationSuccessHandlerTest {

    @Mock private OAuth2AuthorizedClientService authorizedClientService;
    @Mock private UserRepository userRepository;
    @Mock private TimeService timeService;

    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private OAuth2AuthenticationToken authentication;
    @Mock private OAuth2User oauth2User;
    @Mock private OAuth2AuthorizedClient authorizedClient;

    @InjectMocks
    private CustomAuthenticationSuccessHandler successHandler;

    @Captor
    private ArgumentCaptor<User> userCaptor;

    private final String email = "test@ufromail.cl";
    private final String name = "Test User";
    private final ZoneId zoneId = ZoneId.of("America/Santiago");
    private final OffsetDateTime now = OffsetDateTime.now();
    private final String frontendURL = "http://localhost:5173";

    @BeforeEach
    void setUp() throws NoSuchFieldException, IllegalAccessException {
        when(authentication.getPrincipal()).thenReturn(oauth2User);
        when(oauth2User.getAttributes()).thenReturn(Map.of("email", email, "name", name));

        // Inyección manual del valor @Value para el test unitario
        Field frontendUrlField = CustomAuthenticationSuccessHandler.class.getDeclaredField("frontendUrl");
        frontendUrlField.setAccessible(true);
        frontendUrlField.set(successHandler, frontendURL);
    }

    @Test
    @DisplayName("Debe crear un NUEVO usuario (STUDENT) y guardar tokens si no existe en BD")
    void onAuthenticationSuccess_ShouldCreateNewUser() throws IOException {
        // Arrange
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());
        when(timeService.nowOffset()).thenReturn(now);
        when(timeService.zone()).thenReturn(zoneId);

        // Mockear el cliente OAuth2 y sus tokens
        setupOAuth2ClientMock("access-123", "refresh-123", Instant.now().plusSeconds(3600));

        // Act
        successHandler.onAuthenticationSuccess(request, response, authentication);

        // Assert
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();

        assertEquals(email, savedUser.getEmail());
        assertEquals(name, savedUser.getNombre());
        assertEquals(UserRole.STUDENT, savedUser.getRol()); // Verifica rol por defecto
        assertEquals("access-123", savedUser.getGoogleAccessToken());
        assertEquals("refresh-123", savedUser.getGoogleRefreshToken());
        assertNotNull(savedUser.getGoogleTokenExpiryDate());
        
        verify(response).sendRedirect(frontendURL);
    }

    @Test
    @DisplayName("Debe actualizar un usuario EXISTENTE y sus tokens")
    void onAuthenticationSuccess_ShouldUpdateExistingUser() throws IOException {
        // Arrange
        User existingUser = new User();
        existingUser.setId(1L);
        existingUser.setEmail(email);
        existingUser.setNombre("Old Name");
        existingUser.setRol(UserRole.ADMIN); // Un admin existente no debe cambiar a student

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(existingUser));
        when(timeService.zone()).thenReturn(zoneId);

        // Mockear nuevos tokens
        setupOAuth2ClientMock("new-access-token", "new-refresh-token", Instant.now().plusSeconds(3600));

        // Act
        successHandler.onAuthenticationSuccess(request, response, authentication);

        // Assert
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();

        assertEquals(1L, savedUser.getId()); // Mismo ID
        assertEquals(name, savedUser.getNombre()); // Nombre actualizado
        assertEquals(UserRole.ADMIN, savedUser.getRol()); // Rol mantenido
        assertEquals("new-access-token", savedUser.getGoogleAccessToken());
        
        verify(response).sendRedirect(frontendURL);
    }

    @Test
    @DisplayName("Debe manejar el caso donde el cliente autorizado es NULL (sin tokens)")
    void onAuthenticationSuccess_ShouldHandleNullClient() throws IOException {
        // Arrange
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());
        when(timeService.nowOffset()).thenReturn(now);

        // Simulamos que authorizedClientService devuelve null
        when(authentication.getAuthorizedClientRegistrationId()).thenReturn("google");
        when(authentication.getName()).thenReturn("sub-123");
        when(authorizedClientService.loadAuthorizedClient(anyString(), anyString())).thenReturn(null);

        // Act
        successHandler.onAuthenticationSuccess(request, response, authentication);

        // Assert
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        
        // El usuario se guarda, pero sin tokens
        assertEquals(email, savedUser.getEmail());
        assertNull(savedUser.getGoogleAccessToken());
        
        // La redirección ocurre igual
        verify(response).sendRedirect(frontendURL);
    }

    @Test
    @DisplayName("Debe manejar tokens con fecha de expiración nula")
    void onAuthenticationSuccess_ShouldHandleNullExpiry() throws IOException {
        // Arrange
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());
        when(timeService.nowOffset()).thenReturn(now);

        // Mockear token SIN fecha de expiración
        setupOAuth2ClientMock("access-token", null, null); // Expiry null

        // Act
        successHandler.onAuthenticationSuccess(request, response, authentication);

        // Assert
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();

        assertEquals("access-token", savedUser.getGoogleAccessToken());
        assertNull(savedUser.getGoogleTokenExpiryDate()); // No debe explotar
        
        verify(response).sendRedirect(frontendURL);
    }

    @Test
    @DisplayName("Debe manejar refresh token nulo (común en logins sucesivos)")
    void onAuthenticationSuccess_ShouldHandleNullRefreshToken() throws IOException {
        // Arrange
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());
        when(timeService.nowOffset()).thenReturn(now);
        when(timeService.zone()).thenReturn(zoneId);

        // Mockear token CON access pero SIN refresh
        setupOAuth2ClientMock("access-token", null, Instant.now()); 

        // Act
        successHandler.onAuthenticationSuccess(request, response, authentication);

        // Assert
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();

        assertEquals("access-token", savedUser.getGoogleAccessToken());
        assertNull(savedUser.getGoogleRefreshToken());
        
        verify(response).sendRedirect(frontendURL);
    }

    // --- Helper Method para configurar los mocks profundos de OAuth2 ---
    private void setupOAuth2ClientMock(String accessTokenVal, String refreshTokenVal, Instant expiresAt) {
        when(authentication.getAuthorizedClientRegistrationId()).thenReturn("google");
        when(authentication.getName()).thenReturn("user-sub-id");

        when(authorizedClientService.loadAuthorizedClient("google", "user-sub-id"))
                .thenReturn(authorizedClient);

        // Configurar Access Token 
        OAuth2AccessToken accessToken = mock(OAuth2AccessToken.class);
        when(authorizedClient.getAccessToken()).thenReturn(accessToken);
        when(accessToken.getTokenValue()).thenReturn(accessTokenVal);
        when(accessToken.getExpiresAt()).thenReturn(expiresAt);

        // Configurar Refresh Token (puede ser nulo)
        if (refreshTokenVal != null) {
            OAuth2RefreshToken refreshToken = mock(OAuth2RefreshToken.class);
            when(authorizedClient.getRefreshToken()).thenReturn(refreshToken);
            when(refreshToken.getTokenValue()).thenReturn(refreshTokenVal);
        } else {
            when(authorizedClient.getRefreshToken()).thenReturn(null);
        }
    }
}