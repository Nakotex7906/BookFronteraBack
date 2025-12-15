package bookfronterab.service;

import io.github.bucket4j.Bucket;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RateLimitingServiceTest {

    private final RateLimitingService rateLimitingService = new RateLimitingService();

    @Test
    @DisplayName("resolveBucket debe crear un nuevo bucket para una IP nueva")
    void resolveBucket_ShouldCreateNewBucket() {
        String ip = "192.168.1.10";
        Bucket bucket = rateLimitingService.resolveBucket(ip);

        assertNotNull(bucket);
        // Verificar que tiene tokens disponibles (configuración inicial)
        assertTrue(bucket.getAvailableTokens() > 0);
    }

    @Test
    @DisplayName("resolveBucket debe retornar el MISMO bucket para la misma IP (Cache)")
    void resolveBucket_ShouldReturnCachedBucket() {
        String ip = "10.0.0.5";
        Bucket bucket1 = rateLimitingService.resolveBucket(ip);
        Bucket bucket2 = rateLimitingService.resolveBucket(ip);

        assertSame(bucket1, bucket2, "Debe devolver la misma instancia de Bucket (cache funcionando)");
    }

    @Test
    @DisplayName("resolveBucket debe manejar diferentes IPs independientemente")
    void resolveBucket_ShouldHandleMultipleIps() {
        Bucket bucketA = rateLimitingService.resolveBucket("1.1.1.1");
        Bucket bucketB = rateLimitingService.resolveBucket("2.2.2.2");

        assertNotSame(bucketA, bucketB);
    }
}