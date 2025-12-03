package bookfronterab.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import org.junit.Test;
import org.junit.jupiter.api.DisplayName;

public class AvailibilityDtoTest {
    
    @Test
    @DisplayName("El constructor privado lanza IllegalStateException (Clase de Utilidad)")
    public void IllegalStateExceptionAvailibilityDto() {
        
        // 1. Usamos assertThrowsExactly para atrapar la IllegalStateException
        IllegalStateException exception = assertThrowsExactly(IllegalStateException.class, () -> {
            
            // Usamos reflexión para obtener el constructor privado
            Constructor<AvailabilityDto> constructor = AvailabilityDto.class.getDeclaredConstructor();
            
            // Hacemos el constructor accesible (saltamos la seguridad para el test)
            constructor.setAccessible(true);

            try {
                // Intentar invocarlo lanzará InvocationTargetException, 
                // que envuelve la IllegalStateException.
                constructor.newInstance();
            } catch (InvocationTargetException e) {
                // Para que assertThrowsExactly funcione con la excepción interna,
                // relanzamos la causa raíz (e.getCause()), que es la IllegalStateException.
                throw e.getCause();
            }
        });

        // 2. Verificamos que el mensaje sea el correcto
        assertEquals("Utility class", exception.getMessage());
    }
}
