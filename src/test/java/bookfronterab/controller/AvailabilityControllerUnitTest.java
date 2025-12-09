package bookfronterab.controller;

import bookfronterab.dto.AvailabilityDto;
import bookfronterab.service.AvailabilityService;
import bookfronterab.service.RateLimitingService;
import bookfronterab.service.TimeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AvailabilityController.class)
@AutoConfigureMockMvc(addFilters = false)
class AvailabilityControllerUnitTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AvailabilityService availabilityService;

    @MockitoBean
    private TimeService timeService;

    // SOLUCIÓN: Agregamos el Mock del servicio que necesita el filtro
    @MockitoBean
    private RateLimitingService rateLimitingService;

    // CASO 1: El usuario envía una fecha específica
    @Test
    void getDailyAvailability_DeberiaUsarFechaProporcionada() throws Exception {
        // 1. Datos de prueba
        String fechaInput = "2025-10-20";
        LocalDate fechaEsperada = LocalDate.parse(fechaInput);

        // 2. Simulamos la respuesta del servicio
        AvailabilityDto.DailyAvailabilityResponse responseMock = new AvailabilityDto.DailyAvailabilityResponse(
                List.of(),
                List.of(),
                List.of()
        );

        when(availabilityService.getDailyAvailability(fechaEsperada)).thenReturn(responseMock);

        // 3. Ejecutar y Verificar
        mockMvc.perform(get("/api/v1/availability")
                        .param("date", fechaInput)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    // CASO 2: El usuario NO envía fecha
    @Test
    void getDailyAvailability_DeberiaUsarFechaActual_CuandoNoSeEnviaParametro() throws Exception {
        when(timeService.zone()).thenReturn(ZoneId.systemDefault());

        AvailabilityDto.DailyAvailabilityResponse responseMock = new AvailabilityDto.DailyAvailabilityResponse(
                List.of(),
                List.of(),
                List.of()
        );

        when(availabilityService.getDailyAvailability(any(LocalDate.class))).thenReturn(responseMock);

        mockMvc.perform(get("/api/v1/availability")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }
}