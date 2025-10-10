package bookfronterab.controller;

import bookfronterab.service.google.GoogleOAuthService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;

@Controller
@RequestMapping("/oauth2")
@RequiredArgsConstructor
public class GoogleOAuthController {

    private final GoogleOAuthService googleOAuthService;

    @GetMapping("/authorize/google")
    public void authorizeWithGoogle(HttpServletResponse response, @RequestParam("email") String email) throws IOException {
        // ESTO ES SOLO PARA PRUEBAS. En producción, obtén el usuario de la sesión.
        if (email == null || email.isEmpty()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "El parámetro 'email' es requerido para la prueba");
            return;
        }
        String authorizationUrl = googleOAuthService.getAuthorizationUrl(email);
        response.sendRedirect(authorizationUrl);
    }

    @GetMapping("/callback/google")
    public String handleGoogleCallback(@RequestParam("code") String code, @RequestParam("state") String userEmail) throws IOException {
        googleOAuthService.processAuthorizationCode(code, userEmail);
        // Redirigir a una página que indique éxito. Puedes cambiar esto.
        return "redirect:/?google_auth=success";
    }
}
