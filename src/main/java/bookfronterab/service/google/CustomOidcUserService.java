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
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CustomOidcUserService extends OidcUserService {

    private final UserRepository userRepository;

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser oidcUser = super.loadUser(userRequest);

        Map<String, Object> attributes = oidcUser.getAttributes();
        String email = (String) attributes.get("email");

        // Permitir dominio institucional O tu correo personal específico de admin
        boolean esInstitucional = email != null && email.endsWith("@ufromail.cl");
        boolean esAdminPersonal = email != null && email.equals("guillermosalgado002@gmail.com"); // <--- TU CORREO

        if (!esInstitucional && !esAdminPersonal) {
            // Si no es ninguno de los dos, lanzamos la excepción que causa el "logout" forzado
            throw new OAuth2AuthenticationException("Acceso denegado. Solo correos @ufromail.cl o administradores autorizados.");
        }
        // -----------------------

        Optional<User> userOptional = userRepository.findByEmail(email);
        UserRole rol = userOptional.map(User::getRol).orElse(UserRole.STUDENT);

        Set<GrantedAuthority> authorities = Collections.singleton(
                new SimpleGrantedAuthority("ROLE_" + rol.name())
        );

        return new DefaultOidcUser(authorities, userRequest.getIdToken(), oidcUser.getUserInfo());
    }
}