package bookfronterab.service;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

class TimeServiceTest {

    @Test
    void zone_ShouldReturnTheConfiguredZone() {
        // --- Arrange (Preparar) ---
        // Definimos una zona de prueba
        ZoneId expectedZone = ZoneId.of("Europe/Paris");
        
        // Creamos el servicio manualmente con esa zona
        TimeService timeService = new TimeService(expectedZone);

        // --- Act (Actuar) ---
        ZoneId actualZone = timeService.zone();

        // --- Assert (Afirmar) ---
        assertEquals(expectedZone, actualZone);
    }

    @Test
    void nowOffset_ShouldReturnCurrentTimeInConfiguredZone() {
        // --- Arrange (Preparar) ---
        // Usamos una zona de prueba específica (ej. Nueva York)
        ZoneId expectedZone = ZoneId.of("America/New_York");
        TimeService timeService = new TimeService(expectedZone);

        // Capturamos el "ahora" real en esa zona
        OffsetDateTime expectedTimeNow = OffsetDateTime.now(expectedZone);

        // --- Act (Actuar) ---
        OffsetDateTime actualResult = timeService.nowOffset();

        // --- Assert (Afirmar) ---
        
        // Comparamos los "offsets"
        assertEquals(expectedTimeNow.getOffset(), actualResult.getOffset(),
                "El Offset de la zona debe ser el mismo que el esperado.");
        // Comprobamos que el tiempo devuelto esté dentro de un rango
        // muy pequeño (ej. 1 segundo) del tiempo que capturamos.
        long timeDifference = Duration.between(expectedTimeNow, actualResult).toMillis();
        
        assertTrue(Math.abs(timeDifference) < 1000, 
                "El tiempo devuelto debe ser 'ahora' (diferencia < 1 segundo)");
    }
}