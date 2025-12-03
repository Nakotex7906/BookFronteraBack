package bookfronterab.config;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.web.RedirectStrategy;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CustomAuthenticationFailureHandlerTest {

    // El sujeto de prueba
    private CustomAuthenticationFailureHandler failureHandler;

    // Mocks necesarios para simular el entorno web
    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private RedirectStrategy redirectStrategy;

    // Captor para interceptar la URL generada
    @Captor private ArgumentCaptor<String> urlCaptor;

    @BeforeEach
    void setUp() {
        failureHandler = new CustomAuthenticationFailureHandler();
        // IMPORTANTE: Inyectamos nuestra estrategia simulada para interceptar el redireccionamiento
        // y no depender de la implementación real de Spring.
        failureHandler.setRedirectStrategy(redirectStrategy);
    }

    @Test
    @DisplayName("Debe redirigir con mensaje por defecto si la excepción es genérica")
    void onAuthenticationFailure_ShouldUseDefaultMessage_ForGenericException() throws IOException, ServletException {
        // Arrange
        AuthenticationException genericException = new BadCredentialsException("Credenciales malas");

        // Act
        failureHandler.onAuthenticationFailure(request, response, genericException);

        // Assert
        // Verificamos que se llamó a sendRedirect en nuestra estrategia
        verify(redirectStrategy).sendRedirect(eq(request), eq(response), urlCaptor.capture());

        String redirectUrl = urlCaptor.getValue();
        
        // Verificamos la URL base
        assertTrue(redirectUrl.startsWith("http://localhost:5173/login"));
        
        // Verificamos el mensaje por defecto (codificado)
        // El mensaje esperado es: "Error de autenticación. Por favor, inténtalo de nuevo."
        // Al estar codificado, los espacios suelen ser '+' o '%20'
        assertTrue(redirectUrl.contains("error=Error+de+autenticaci%C3%B3n"), 
                   "La URL debe contener el mensaje por defecto codificado");
    }

    @Test
    @DisplayName("Debe redirigir con el mensaje de la excepción si es OAuth2AuthenticationException")
    void onAuthenticationFailure_ShouldUseExceptionMessage_ForOAuth2Exception() throws IOException, ServletException {
        // Arrange
        String specificError = "El token de Google ha expirado";
        OAuth2Error oauthError = new OAuth2Error("invalid_token");
        OAuth2AuthenticationException oauthException = new OAuth2AuthenticationException(oauthError, specificError);

        // Act
        failureHandler.onAuthenticationFailure(request, response, oauthException);

        // Assert
        verify(redirectStrategy).sendRedirect(any(), any(), urlCaptor.capture());
        String redirectUrl = urlCaptor.getValue();

        // Decodificamos la URL para verificar el texto fácilmente
        String decodedUrl = URLDecoder.decode(redirectUrl, StandardCharsets.UTF_8);

        assertTrue(decodedUrl.contains("error=" + specificError), 
                   "La URL debe contener el mensaje específico de OAuth2");
    }

    @Test
    @DisplayName("Debe codificar correctamente caracteres especiales en la URL")
    void onAuthenticationFailure_ShouldEncodeUrlParameters() throws IOException, ServletException {
        // Arrange
        // Mensaje con caracteres "peligrosos" para URL: espacios, ampersand, tildes
        String trickyMessage = "Error & Falla Crítica!"; 
        OAuth2Error oauthError = new OAuth2Error("error_code");
        OAuth2AuthenticationException exception = new OAuth2AuthenticationException(oauthError, trickyMessage);

        // Act
        failureHandler.onAuthenticationFailure(request, response, exception);

        // Assert
        verify(redirectStrategy).sendRedirect(any(), any(), urlCaptor.capture());
        String redirectUrl = urlCaptor.getValue();

        // Verificamos que NO contenga el espacio o el '&' crudos
        assertTrue(!redirectUrl.contains("& ")); 
        
        // Verificamos que contenga la versión codificada
        // & -> %26
        // espacio -> + (o %20 según el encoder)
        assertTrue(redirectUrl.contains("Error+%26+Falla+Cr%C3%ADtica%21") || redirectUrl.contains("Error+%26+Falla+Cr%C3%ADtica!"),
                "Los caracteres especiales deben estar URL-encoded");
    }
}