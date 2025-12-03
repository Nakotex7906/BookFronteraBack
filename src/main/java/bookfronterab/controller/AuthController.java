package bookfronterab.controller;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class AuthController {

    @GetMapping("/auth-debug")
    public Map<String, Object> debug(Authentication auth) {
        if (auth == null) return Map.of("status", "No autenticado");

        return Map.of(
                "usuario", auth.getName(),
                "roles_detectados", auth.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority).toList(),
                "principal_type", auth.getPrincipal().getClass().getName()
        );
    }
}