package bookfronterab.config;

import bookfronterab.service.RateLimitingService;
import io.github.bucket4j.Bucket;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Order(1) // Se ejecuta antes que otros filtros (excepto los de seguridad crítica de Spring)
@RequiredArgsConstructor
public class RateLimitFilter implements Filter {

    private final RateLimitingService rateLimitingService;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        //  Obtener la IP real del cliente
        String clientIp = getClientIp(httpRequest);

        //  Obtener el Bucket asignado a esa IP
        Bucket bucket = rateLimitingService.resolveBucket(clientIp);

        //  Intentar consumir 1 token
        if (bucket.tryConsume(1)) {
            // Si tiene tokens, deja pasar la petición
            chain.doFilter(request, response);
        } else {
            // Si no tiene tokens, devuelve error 429 (Too Many Requests)
            httpResponse.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            httpResponse.getWriter().write("Has excedido el límite de peticiones. Intenta más tarde.");
        }
    }

    /**
     * Extrae la IP real, fundamental si usas Docker, Nginx o Cloudflare.
     * Si solo usas getRemoteAddr() en Docker, verás la IP interna del gateway (ej: 172.18.0.1)
     * para todos los usuarios, y bloquearás a todos al mismo tiempo.
     */
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            // X-Forwarded-For puede ser una lista: "client, proxy1, proxy2"
            // Nos interesa la primera, que es la del cliente original.
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}