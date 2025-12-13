package bookfronterab.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
public class CustomAuthenticationFailureHandler extends SimpleUrlAuthenticationFailureHandler {


    /**
     * Se ejecuta cuando la autenticación falla.
     * Construye una URL de redirección hacia la página de login del frontend,
     * adjuntando el motivo del error como parámetro de consulta.
     *
     * @param request   La solicitud HTTP.
     * @param response  La respuesta HTTP.
     * @param exception La excepción que causó el fallo de autenticación.
     * @throws IOException Si ocurre un error de entrada/salida.
     */
    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {

        // Obtener el mensaje de error. Priorizamos el mensaje de la excepción si existe.
        String errorMessage = "Error de autenticación. Por favor, inténtalo de nuevo.";

        if (exception != null && exception.getMessage() != null && !exception.getMessage().isBlank()) {
            errorMessage = exception.getMessage();
        }

        //  Construir la URL de redirección de forma segura
        // Se usa URLEncoder para evitar problemas con espacios o caracteres especiales en el mensaje.
        String redirectUrl = UriComponentsBuilder.fromUriString("http://localhost:5173/login")
                .queryParam("error", URLEncoder.encode(errorMessage, StandardCharsets.UTF_8))
                .build().toUriString();

        // Realizar la redirección
        getRedirectStrategy().sendRedirect(request, response, redirectUrl);
    }
}