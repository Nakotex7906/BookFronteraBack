package bookfronterab.config;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value; // IMPORTANTE
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

    // 1. INYECTAMOS LA URL DEL FRONTEND DESDE LAS VARIABLES DE ENTORNO
    @Value("${app.frontend.url}")
    private String frontendUrl;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException, ServletException {

        String errorMessage = "Error de autenticación. Por favor, inténtalo de nuevo.";

        if (exception instanceof OAuth2AuthenticationException) {
            String msg = exception.getMessage();
            if (StringUtils.hasText(msg)) {
                errorMessage = msg;
            }
        } else if (StringUtils.hasText(exception.getMessage())) {
            errorMessage = exception.getMessage();
        }

        // 2. USAMOS LA VARIABLE frontendUrl EN LUGAR DE "http://localhost:5173"
        // Asegúrate de concatenar "/login"
        String targetUrl = frontendUrl + "/login";

        String redirectUrl = UriComponentsBuilder.fromUriString(targetUrl)
                .queryParam("error", URLEncoder.encode(errorMessage, StandardCharsets.UTF_8))
                .build().toUriString();

        getRedirectStrategy().sendRedirect(request, response, redirectUrl);
    }
}