package bookfronterab.service.google;

import bookfronterab.model.User;
import bookfronterab.model.UserRole;
import bookfronterab.repo.UserRepository;
import lombok.Setter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomOidcUserServiceTest {

    @Mock private UserRepository userRepository;
    private TestableCustomOidcUserService service;

    @BeforeEach
    void setUp() {
        service = new TestableCustomOidcUserService(userRepository);
        ReflectionTestUtils.setField(service, "adminEmails", List.of("admin@externo.com"));
    }

    @Test
    @DisplayName("Debe permitir acceso a correo institucional @ufromail.cl")
    void loadUser_ShouldAllowInstitutionalEmail() {
        OidcUser mockUser = createMockOidcUser("estudiante@ufromail.cl");
        service.setMockUserToReturn(mockUser);

        when(userRepository.findByEmail("estudiante@ufromail.cl")).thenReturn(Optional.empty());

        OidcUserRequest mockRequest = mock(OidcUserRequest.class);
        when(mockRequest.getIdToken()).thenReturn(mockUser.getIdToken());

        OidcUser result = service.loadUser(mockRequest);

        assertNotNull(result);
        assertTrue(result.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_STUDENT")));
    }

    @Test
    @DisplayName("Debe permitir acceso a correo ADMIN externo si está en la lista blanca")
    void loadUser_ShouldAllowWhitelistedAdmin() {
        OidcUser mockUser = createMockOidcUser("admin@externo.com");
        service.setMockUserToReturn(mockUser);

        User adminUser = User.builder().email("admin@externo.com").rol(UserRole.ADMIN).build();
        when(userRepository.findByEmail("admin@externo.com")).thenReturn(Optional.of(adminUser));

        OidcUserRequest mockRequest = mock(OidcUserRequest.class);
        when(mockRequest.getIdToken()).thenReturn(mockUser.getIdToken());

        OidcUser result = service.loadUser(mockRequest);

        assertTrue(result.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    @Test
    @DisplayName("Debe lanzar excepción si el correo no es institucional ni admin")
    void loadUser_ShouldThrowException_WhenEmailInvalid() {
        OidcUser mockUser = createMockOidcUser("hacker@gmail.com");
        service.setMockUserToReturn(mockUser);


        OidcUserRequest mockRequest = mock(OidcUserRequest.class);

        assertThrows(OAuth2AuthenticationException.class, () ->
                service.loadUser(mockRequest)
        );
    }


    private OidcUser createMockOidcUser(String email) {
        OidcIdToken token = OidcIdToken.withTokenValue("token-falso-123")
                .claim("email", email)
                .claim("sub", "google-sub-123")
                .build();

        return new DefaultOidcUser(List.of(), token);
    }

    @Setter
    static class TestableCustomOidcUserService extends CustomOidcUserService {
        private OidcUser mockUserToReturn;

        public TestableCustomOidcUserService(UserRepository repo) {
            super(repo);
        }

        @Override
        protected OidcUser loadUserFromSuper(OidcUserRequest userRequest) {
            return mockUserToReturn;
        }
    }
}