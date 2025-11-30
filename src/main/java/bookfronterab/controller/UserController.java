package bookfronterab.controller;

import bookfronterab.dto.UserDto;
import bookfronterab.model.User;
import bookfronterab.model.UserRole;
import bookfronterab.repo.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;

    @GetMapping("/me")
    public ResponseEntity<UserDto> getMe(@AuthenticationPrincipal OAuth2User principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String email = principal.getAttribute("email");
        return userRepository.findByEmail(email)
                .map(user -> ResponseEntity.ok(mapToDto(user)))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }
    /**
     * Endpoint para alternar el rol del usuario actual (Dev/Demo feature).
     * Cambia de STUDENT <-> ADMIN y actualiza la sesión de seguridad inmediatamente.
     */
    @PatchMapping("/toggle-role")
    @Transactional
    public ResponseEntity<UserDto> toggleRole(@AuthenticationPrincipal OAuth2User principal, HttpServletRequest request) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String email = principal.getAttribute("email");
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Usuario no encontrado"));

        // 1. Cambiar Rol en BD
        UserRole newRole = (user.getRol() == UserRole.ADMIN) ? UserRole.STUDENT : UserRole.ADMIN;
        user.setRol(newRole);
        User savedUser = userRepository.save(user);

        // 2. Actualizar la sesión de Spring Security 'en caliente'
        // Esto evita tener que desloguearse para ver los efectos del nuevo rol
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        Set<GrantedAuthority> updatedAuthorities = new HashSet<>();
        updatedAuthorities.add(new SimpleGrantedAuthority("ROLE_" + newRole.name()));

        Authentication newAuth = new OAuth2AuthenticationToken(
                principal,
                updatedAuthorities,
                ((OAuth2AuthenticationToken) auth).getAuthorizedClientRegistrationId()
        );

        SecurityContextHolder.getContext().setAuthentication(newAuth);

        // Asegurar que la sesión HTTP persista el nuevo contexto
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.setAttribute("SPRING_SECURITY_CONTEXT", SecurityContextHolder.getContext());
        }

        return ResponseEntity.ok(mapToDto(savedUser));
    }

    private UserDto mapToDto(User user) {
        return UserDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .nombre(user.getNombre())
                .rol(user.getRol())
                .build();
    }
}
