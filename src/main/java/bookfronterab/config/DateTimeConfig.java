package bookfronterab.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.ZoneId;

@Configuration
public class DateTimeConfig {

    public static final String TZ_ID = "America/Santiago";

    @Bean
    public ZoneId appZoneId() {
        return ZoneId.of(TZ_ID);
    }

}
