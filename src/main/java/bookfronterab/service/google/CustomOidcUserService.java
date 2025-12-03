package bookfronterab.service.google;

import bookfronterab.model.User;
import bookfronterab.model.UserRole;
import bookfronterab.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error; // <--- Importante importar esto
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CustomOidcUserService extends OidcUserService {

    private final UserRepository userRepository;

    // Lista  de correos personales autorizados como Admin/Dev
    private static final List<String> ADMIN_EMAILS = List.of(
            "guillermosalgado002@gmail.com",
            "nachoessus@gmail.com"
    );

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser oidcUser = super.loadUser(userRequest);

        Map<String, Object> attributes = oidcUser.getAttributes();
        String email = (String) attributes.get("email");

        //  Validaciones de Acceso
        boolean esInstitucional = email != null && email.endsWith("@ufromail.cl");
        boolean esAdminPermitido = email != null && ADMIN_EMAILS.contains(email);

        if (!esInstitucional && !esAdminPermitido) {
            // Usamos OAuth2Error para que el mensaje llegue bien al frontend en caso de rechazo
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

        return new DefaultOidcUser(authorities, userRequest.getIdToken(), oidcUser.getUserInfo());
    }
}