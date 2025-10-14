package bookfronterab.config;

import bookfronterab.model.Role;
import bookfronterab.model.User;
import bookfronterab.repo.UserRepository;
import bookfronterab.service.TimeService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class CustomAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final OAuth2AuthorizedClientService authorizedClientService;
    private final UserRepository userRepository;
    private final TimeService timeService;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
        OAuth2User oauthUser = oauthToken.getPrincipal();

        Map<String, Object> attributes = oauthUser.getAttributes();
        String email = (String) attributes.get("email");
        String name = (String) attributes.get("name");

        // Find or create user
        Optional<User> userOptional = userRepository.findByEmail(email);
        User user;
        if (userOptional.isEmpty()) {
            user = new User();
            user.setEmail(email);
            user.setNombre(name);
            user.setRol(Role.STUDENT);
            user.setCreadoEn(timeService.nowOffset());
        } else {
            user = userOptional.get();
            user.setNombre(name);
        }

        // Get tokens
        OAuth2AuthorizedClient client = authorizedClientService.loadAuthorizedClient(
                oauthToken.getAuthorizedClientRegistrationId(),
                oauthToken.getName()
        );

        user.setGoogleAccessToken(client.getAccessToken().getTokenValue());
        if (client.getAccessToken().getExpiresAt() != null) {
            user.setGoogleTokenExpiryDate(client.getAccessToken().getExpiresAt().atZone(timeService.zone()).toOffsetDateTime());
        }

        if (client.getRefreshToken() != null) {
            user.setGoogleRefreshToken(client.getRefreshToken().getTokenValue());
        }

        userRepository.save(user);

        // Redirect to the frontend
        response.sendRedirect("http://localhost:5173");
    }
}
