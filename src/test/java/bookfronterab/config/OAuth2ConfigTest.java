package bookfronterab.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class OAuth2ConfigTest {

    @Mock
    private ClientRegistrationRepository clientRegistrationRepository;

    private final OAuth2Config oauth2Config = new OAuth2Config();

    @Test
    void authorizedClientService_shouldReturnInMemoryImplementation() {
        OAuth2AuthorizedClientService result =
                oauth2Config.authorizedClientService(clientRegistrationRepository);

        assertNotNull(result);
        assertInstanceOf(InMemoryOAuth2AuthorizedClientService.class, result);
    }
}