package bookfronterab.config;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
public class CustomAuthenticationFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException, ServletException {

        // Mensaje por defecto
        String errorMessage = "Error de autenticación. Por favor, inténtalo de nuevo.";

        //  Intentar obtener el mensaje específico si es OAuth2
        if (exception instanceof OAuth2AuthenticationException) {
            String msg = exception.getMessage();
            // Validamos que no sea null y no esté vacío antes de asignarlo
            if (StringUtils.hasText(msg)) {
                errorMessage = msg;
            }
        } else if (StringUtils.hasText(exception.getMessage())) {
            // capturar mensajes de otras excepciones si no son null
            errorMessage = exception.getMessage();
        }

        //  Construir la URL (ahora errorMessage nunca será null)
        String redirectUrl = UriComponentsBuilder.fromUriString("http://localhost:5173/login")
                .queryParam("error", URLEncoder.encode(errorMessage, StandardCharsets.UTF_8))
                .build().toUriString();

        getRedirectStrategy().sendRedirect(request, response, redirectUrl);
    }
}