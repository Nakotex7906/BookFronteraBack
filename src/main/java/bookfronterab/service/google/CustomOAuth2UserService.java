package bookfronterab.service.google;

import bookfronterab.model.User;
import bookfronterab.model.UserRole;
import bookfronterab.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value; // Importante
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;

    @Value("#{'${app.admin.emails}'.split(',')}")
    private List<String> adminEmails;

    @Override
    @Transactional(readOnly = true)
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oauth2User = loadUserFromSuper(userRequest);

        Map<String, Object> attributes = oauth2User.getAttributes();
        String email = (String) attributes.get("email");

        boolean esInstitucional = email != null && email.endsWith("@ufromail.cl");
        boolean esAdmin = email != null && adminEmails != null && adminEmails.contains(email);

        if (!esInstitucional && !esAdmin) {
            OAuth2Error error = new OAuth2Error(
                    "access_denied",
                    "Acceso denegado. Solo correos @ufromail.cl o administradores autorizados.",
                    null
            );
            throw new OAuth2AuthenticationException(error, error.getDescription());
        }

        Optional<User> userOptional = userRepository.findByEmail(email);
        UserRole rol = userOptional.map(User::getRol).orElse(UserRole.STUDENT);

        Set<GrantedAuthority> authorities = Collections.singleton(
                new SimpleGrantedAuthority("ROLE_" + rol.name())
        );

        String userNameAttributeName = userRequest.getClientRegistration()
                .getProviderDetails()
                .getUserInfoEndpoint()
                .getUserNameAttributeName();

        return new DefaultOAuth2User(authorities, attributes, userNameAttributeName);
    }

    protected OAuth2User loadUserFromSuper(OAuth2UserRequest userRequest) {
        return super.loadUser(userRequest);
    }
}