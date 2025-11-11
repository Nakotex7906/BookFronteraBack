package bookfronterab.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas unitarias para {@link TimeService}.
 *
 * <p>Estas pruebas validan la lógica de {@link TimeService} de forma aislada,
 * sin cargar el contexto de Spring. El servicio se instancia manualmente
 * para verificar su comportamiento con una dependencia (ZoneId) controlada.
 */
class TimeServiceTest {

    /**
     * Verifica que el método {@code zone()} devuelve exactamente la
     * {@link ZoneId} que se le proveyó al servicio en el constructor.
     */
    @Test
    @DisplayName("El método zone() debe devolver la Zona configurada")
    void zone_ShouldReturnTheConfiguredZone() {
        // --- Arrange (Preparar) ---
        // Definimos una zona de prueba
        ZoneId expectedZone = ZoneId.of("Europe/Paris");
        
        // Creamos el servicio manualmente con esa zona
        TimeService timeService = new TimeService(expectedZone);

        // --- Act (Actuar) ---
        // Llamamos al método que queremos probar
        ZoneId actualZone = timeService.zone();

        // --- Assert (Afirmar) ---
        // Comprobamos que el resultado es el esperado
        assertEquals(expectedZone, actualZone);
    }

    /**
     * Verifica que el método {@code nowOffset()} devuelve la hora actual
     * utilizando la {@link ZoneId} configurada en el servicio.
     *
     * <p><b>Nota sobre la prueba:</b> Esta es una prueba no determinista porque
     * depende de {@link OffsetDateTime#now()}. Se valida de dos maneras:
     * <ol>
     * <li>Asegurando que el {@code Offset} (ej. "-05:00") del resultado
     * coincide con el offset esperado para esa zona.</li>
     * <li>Asegurando que el tiempo devuelto está dentro de un margen muy
     * pequeño (1 segundo) del tiempo capturado en el test.</li>
     * </ol>
     */
    @Test
    @DisplayName("El método nowOffset() debe devolver la hora actual en la Zona configurada")
    void nowOffset_ShouldReturnCurrentTimeInConfiguredZone() {
        // --- Arrange (Preparar) ---
        // Usamos una zona de prueba específica (ej. Nueva York)
        ZoneId expectedZone = ZoneId.of("America/New_York");
        TimeService timeService = new TimeService(expectedZone);

        // Capturamos el "ahora" real en esa zona como punto de referencia
        OffsetDateTime expectedTimeNow = OffsetDateTime.now(expectedZone);

        // --- Act (Actuar) ---
        // Llamamos al método que queremos probar
        OffsetDateTime actualResult = timeService.nowOffset();

        // --- Assert (Afirmar) ---
        
        // 1. Verificación del Offset:
        //    Asegura que el servicio aplicó la zona horaria correcta.
        assertEquals(expectedTimeNow.getOffset(), actualResult.getOffset(),
                "El Offset de la zona debe ser el mismo que el esperado.");
        
        // 2. Verificación del Tiempo:
        //    Asegura que el tiempo devuelto es "ahora".
        long timeDifference = Duration.between(expectedTimeNow, actualResult).toMillis();
        
        assertTrue(Math.abs(timeDifference) < 1000, 
                "El tiempo devuelto debe ser 'ahora' (diferencia < 1 segundo)");
    }
}