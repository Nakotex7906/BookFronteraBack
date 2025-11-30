package bookfronterab.service.google;

import bookfronterab.model.User;
import bookfronterab.model.UserRole;
import bookfronterab.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        // 1. Cargar usuario desde Google
        OAuth2User oauth2User = super.loadUser(userRequest);
        Map<String, Object> attributes = oauth2User.getAttributes();
        String email = (String) attributes.get("email");

        // 2. Validar dominio institucional
        if (email == null || !email.endsWith("@ufromail.cl")) {
            throw new OAuth2AuthenticationException("Acceso denegado. Debes usar una cuenta de correo institucional @ufromail.cl");
        }

        // 3. Determinar el ROL del usuario basado en la BD
        // Si el usuario no existe aún, se asume STUDENT (se creará en el SuccessHandler)
        // Si existe, se toma su rol real (ADMIN o STUDENT)
        Optional<User> userOptional = userRepository.findByEmail(email);
        UserRole rol = userOptional.map(User::getRol).orElse(UserRole.STUDENT);

        // 4. Crear la autoridad para Spring Security (ROLE_ADMIN o ROLE_STUDENT)
        Set<GrantedAuthority> authorities = Collections.singleton(
                new SimpleGrantedAuthority("ROLE_" + rol.name())
        );

        // 5. Retornar el usuario con sus nuevas autoridades mapeadas
        String userNameAttributeName = userRequest.getClientRegistration()
                .getProviderDetails()
                .getUserInfoEndpoint()
                .getUserNameAttributeName();

        return new DefaultOAuth2User(authorities, attributes, userNameAttributeName);
    }
}