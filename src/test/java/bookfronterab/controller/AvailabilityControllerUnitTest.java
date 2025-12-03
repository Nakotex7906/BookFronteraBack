package bookfronterab.controller;

import bookfronterab.dto.AvailabilityDto;
import bookfronterab.service.AvailabilityService;
import bookfronterab.service.TimeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AvailabilityController.class)
@AutoConfigureMockMvc(addFilters = false) // Desactiva seguridad para facilitar el test
class AvailabilityControllerUnitTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AvailabilityService availabilityService;

    @MockBean
    private TimeService timeService;

    // CASO 1: El usuario envía una fecha específica (?date=2025-10-20)
    @Test
    void getDailyAvailability_DeberiaUsarFechaProporcionada() throws Exception {
        // 1. Datos de prueba
        String fechaInput = "2025-10-20";
        LocalDate fechaEsperada = LocalDate.parse(fechaInput);

        // 2. Simulamos la respuesta del servicio
        // FIX: Usamos List.of() en lugar de Collections.emptyList() para evitar errores de tipos genéricos
        AvailabilityDto.DailyAvailabilityResponse responseMock = new AvailabilityDto.DailyAvailabilityResponse(
                List.of(), // 1. Lista de Rooms vacía
                List.of(), // 2. Lista de TimeSlots vacía
                List.of()  // 3. Lista de Items vacía
        );

        when(availabilityService.getDailyAvailability(fechaEsperada)).thenReturn(responseMock);

        // 3. Ejecutar y Verificar
        mockMvc.perform(get("/api/v1/availability")
                        .param("date", fechaInput)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    // CASO 2: El usuario NO envía fecha (debe usar "hoy")
    @Test
    void getDailyAvailability_DeberiaUsarFechaActual_CuandoNoSeEnviaParametro() throws Exception {
        // 1. Simulamos el TimeService para evitar errores de NullPointerException
        when(timeService.zone()).thenReturn(ZoneId.systemDefault());

        // 2. Simulamos la respuesta
        // FIX: Usamos List.of() aquí también
        AvailabilityDto.DailyAvailabilityResponse responseMock = new AvailabilityDto.DailyAvailabilityResponse(
                List.of(),
                List.of(),
                List.of()
        );

        when(availabilityService.getDailyAvailability(any(LocalDate.class))).thenReturn(responseMock);

        // 3. Ejecutar SIN parámetro date
        mockMvc.perform(get("/api/v1/availability")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }
}