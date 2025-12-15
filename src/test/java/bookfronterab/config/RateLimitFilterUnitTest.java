package bookfronterab.config;

import bookfronterab.service.RateLimitingService;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.io.PrintWriter;

// Importamos estáticamente Mockito core (según la doc estándar)
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimitFilterUnitTest {

    @Mock
    private RateLimitingService rateLimitingService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @Mock
    private Bucket bucket;

    @Mock
    private PrintWriter printWriter;

    @InjectMocks
    private RateLimitFilter rateLimitFilter;

    @Test
    void shouldAllowRequest_whenBucketHasTokens() throws Exception {
        // 1. Stubbing (Configurar comportamiento) usando 'when' y 'thenReturn'
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        // Uso de Matcher explícito (anyString) como sugiere la sec. 0.3
        when(rateLimitingService.resolveBucket(anyString())).thenReturn(bucket);

        // Comportamiento del bucket
        when(bucket.tryConsume(1)).thenReturn(true);

        // 2. Ejecución
        rateLimitFilter.doFilter(request, response, filterChain);

        // 3. Verificación (verify)
        // Verificamos que se llamó a chain.doFilter exactamente 1 vez
        verify(filterChain, times(1)).doFilter(request, response);

        // Verificamos que NUNCA se estableció el status 429
        verify(response, never()).setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
    }

    @Test
    void shouldBlockRequest_whenBucketIsEmpty() throws Exception {
        // 1. Stubbing
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(rateLimitingService.resolveBucket(anyString())).thenReturn(bucket);
        when(bucket.tryConsume(1)).thenReturn(false); // No quedan tokens
        when(response.getWriter()).thenReturn(printWriter);

        // 2. Ejecución
        rateLimitFilter.doFilter(request, response, filterChain);

        // 3. Verificación
        // IMPORTANTE (Regla doc 0.3): Si usamos matchers, todos deben serlo.
        // Aquí no usamos matchers, pasamos los objetos directos, lo cual es válido y más estricto.
        verify(filterChain, never()).doFilter(request, response);

        verify(response).setStatus(HttpStatus.TOO_MANY_REQUESTS.value());

        // Verificamos que se escribió el mensaje de error.
        // Aquí usamos contains() que es un Matcher de Mockito para Strings.
        verify(printWriter).write(contains("Has excedido el límite"));
    }

    @Test
    void shouldExtractCorrectIp_fromXForwardedForHeader() throws Exception {
        // 1. Stubbing
        String clientIp = "203.0.113.195";
        String proxyIp = "192.168.1.1";
        // Simulamos un header con múltiples IPs
        when(request.getHeader("X-Forwarded-For")).thenReturn(clientIp + ", " + proxyIp);

        when(rateLimitingService.resolveBucket(anyString())).thenReturn(bucket);
        when(bucket.tryConsume(1)).thenReturn(true);

        // 2. Ejecución
        rateLimitFilter.doFilter(request, response, filterChain);

        // 3. Verificación
        // Aquí verificamos que el servicio fue llamado EXACTAMENTE con la IP del cliente (eq)
        // La documentación recomienda usar 'eq()' explícitamente si queremos estar seguros del valor.
        verify(rateLimitingService).resolveBucket(eq(clientIp));
    }
}