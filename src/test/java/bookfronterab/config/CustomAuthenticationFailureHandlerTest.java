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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
    @DisplayName("Debe redirigir con el mensaje de la excepción genérica cuando está presente")
    void onAuthenticationFailure_ShouldUseExceptionMessage_ForGenericException() throws IOException, ServletException {
        // Arrange
        final String EXPECTED_RAW_MESSAGE = "Credenciales malas. Por favor, verifica tus datos."; // Mensaje real de la excepción
        AuthenticationException genericException = new BadCredentialsException(EXPECTED_RAW_MESSAGE);
        
        // Act
        failureHandler.onAuthenticationFailure(request, response, genericException);

        // Assert
        verify(redirectStrategy).sendRedirect(eq(request), eq(response), urlCaptor.capture());
        String redirectUrl = urlCaptor.getValue();

        // 1. Verificar la URL base
        assertTrue(redirectUrl.startsWith("http://localhost:5173/login?"), "La URL debe apuntar a la página de login.");

        // 2. Extraer y Decodificar el parámetro 'error'
        Pattern pattern = Pattern.compile("error=(.*)");
        Matcher matcher = pattern.matcher(redirectUrl);

        assertTrue(matcher.find(), "La URL debe contener el parámetro 'error'.");
        
        String encodedErrorMessage = matcher.group(1); 
        
        // Usamos el nombre de clase correcto (URLDecoder) para el método estático
        String actualDecodedMessage = URLDecoder.decode(encodedErrorMessage, StandardCharsets.UTF_8);

        // 3. Usamos .trim() en ambos lados para ignorar cualquier whitespace
        assertTrue(actualDecodedMessage.trim().equals(EXPECTED_RAW_MESSAGE.trim()), 
                   "El mensaje decodificado debe ser el mensaje específico de la excepción.");
    }

    @Test
    @DisplayName("Debe redirigir con el mensaje POR DEFECTO si la excepción no tiene mensaje válido")
    void onAuthenticationFailure_ShouldUseDefaultMessage_WhenNoExceptionMessage() throws IOException, ServletException {
        // Arrange
        // Usamos una excepción que tenga un mensaje nulo o vacío
        AuthenticationException mockException = mock(AuthenticationException.class);
        when(mockException.getMessage()).thenReturn(null);
        
        final String EXPECTED_DEFAULT_MESSAGE = "Error de autenticación. Por favor, inténtalo de nuevo.";

        // Act
        failureHandler.onAuthenticationFailure(request, response, mockException);

        // Assert
        verify(redirectStrategy).sendRedirect(eq(request), eq(response), urlCaptor.capture());
        String redirectUrl = urlCaptor.getValue();

        // 1. Extraer y Decodificar
        Pattern pattern = Pattern.compile("error=(.*)");
        Matcher matcher = pattern.matcher(redirectUrl);

        assertTrue(matcher.find(), "La URL debe contener el parámetro 'error'.");
        
        String encodedErrorMessage = matcher.group(1); 
        String actualDecodedMessage = URLDecoder.decode(encodedErrorMessage, StandardCharsets.UTF_8);

        // 2. Verificar el mensaje por defecto
        assertTrue(actualDecodedMessage.trim().equals(EXPECTED_DEFAULT_MESSAGE.trim()), 
                   "Debe usar el mensaje por defecto cuando el mensaje de la excepción es nulo.");
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