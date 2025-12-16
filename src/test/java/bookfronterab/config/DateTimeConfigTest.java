package bookfronterab.config;

import org.junit.jupiter.api.Test;

import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

class DateTimeConfigTest {

    private final DateTimeConfig dateTimeConfig = new DateTimeConfig();

    @Test
    void appZoneId_shouldReturnSantiagoZoneId() {
        ZoneId result = dateTimeConfig.appZoneId();

        assertNotNull(result);
        assertEquals(ZoneId.of("America/Santiago"), result);
        assertEquals("America/Santiago", result.getId());
    }

    @Test
    void tzIdConstant_shouldBeAmericaSantiago() {
        assertEquals("America/Santiago", DateTimeConfig.TZ_ID);
    }
}