package bookfronterab.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Service
public class RateLimitingService {

    private final Cache<String, Bucket> bucketCache = Caffeine.newBuilder()
            .expireAfterAccess(1, TimeUnit.HOURS)
            .build();

    public Bucket resolveBucket(String ip) {
        return bucketCache.get(ip, this::createNewBucket);
    }

    private Bucket createNewBucket(String ip) {
        Bandwidth limit = Bandwidth.builder()
                // Ajusta estos valores según tus necesidades reales.
                .capacity(20) // Capacidad del balde
                .refillGreedy(20, Duration.ofMinutes(1)) // Recarga 20 tokens cada minuto
                .build();

        return Bucket.builder()
                .addLimit(limit)
                .build();
    }
}