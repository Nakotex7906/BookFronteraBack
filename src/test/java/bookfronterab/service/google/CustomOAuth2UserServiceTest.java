package bookfronterab.service.google;

import bookfronterab.repo.UserRepository;
import lombok.Setter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomOAuth2UserServiceTest {

    @Mock private UserRepository userRepository;
    private TestableCustomOAuth2UserService service;

    @BeforeEach
    void setUp() {
        service = new TestableCustomOAuth2UserService(userRepository);
        // Simulamos la inyección de la lista de admins
        ReflectionTestUtils.setField(service, "adminEmails", List.of("admin@externo.com"));
    }

    @Test
    @DisplayName("Debe cargar usuario institucional y asignar rol STUDENT por defecto")
    void loadUser_ShouldLoadInstitutionalUser() {
        OAuth2User mockUser = new DefaultOAuth2User(Collections.emptyList(), Map.of("email", "juan@ufromail.cl"), "email");
        service.setMockUser(mockUser);

        ClientRegistration clientReg = ClientRegistration.withRegistrationId("google")
                .authorizationGrantType(org.springframework.security.oauth2.core.AuthorizationGrantType.AUTHORIZATION_CODE)
                .clientId("id").redirectUri("uri").authorizationUri("auth").tokenUri("token")
                .userInfoUri("info").userNameAttributeName("email")
                .build();

        OAuth2UserRequest request = new OAuth2UserRequest(clientReg, mock(org.springframework.security.oauth2.core.OAuth2AccessToken.class));

        when(userRepository.findByEmail("juan@ufromail.cl")).thenReturn(Optional.empty());

        OAuth2User result = service.loadUser(request);

        assertNotNull(result);
        assertTrue(result.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_STUDENT")));
    }

    @Test
    @DisplayName("Debe lanzar excepción si el correo no es @ufromail.cl y no es admin")
    void loadUser_ShouldThrowException_WhenDomainInvalid() {
        OAuth2User mockUser = new DefaultOAuth2User(Collections.emptyList(), Map.of("email", "juan@gmail.com"), "email");
        service.setMockUser(mockUser);

        OAuth2UserRequest request = mock(OAuth2UserRequest.class);

        assertThrows(OAuth2AuthenticationException.class, () -> service.loadUser(request));
    }

    @Setter
    static class TestableCustomOAuth2UserService extends CustomOAuth2UserService {
        private OAuth2User mockUser;

        public TestableCustomOAuth2UserService(UserRepository repo) {
            super(repo);
        }


        @Override
        protected OAuth2User loadUserFromSuper(OAuth2UserRequest userRequest) {
            return mockUser;
        }
    }
}