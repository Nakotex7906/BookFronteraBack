package bookfronterab.controller;

import bookfronterab.dto.AvailabilityDto;
import bookfronterab.service.AvailabilityService;
import bookfronterab.service.TimeService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Controlador público para consultar la disponibilidad de salas.
 * No requiere autenticación, según lo definido en SecurityConfig.
 */
@RestController
@RequestMapping("/api/v1/availability")
@RequiredArgsConstructor
public class    AvailabilityController {

    private final AvailabilityService availabilityService;
    private final TimeService timeService; // Para obtener la fecha de "hoy"

    /**
     * Obtiene la grilla de disponibilidad diaria para todas las salas.
     *
     * @param dateString Opcional. La fecha a consultar en formato ISO (YYYY-MM-DD).
     * Si se omite, se usará la fecha actual ("hoy").
     * @return Un DTO {@link AvailabilityDto.DailyAvailabilityResponse} con la grilla de disponibilidad.
     */
    @GetMapping
    public AvailabilityDto.DailyAvailabilityResponse getDailyAvailability(
            // Agregamos (value = "date") para conectar con ?date=... del frontend
            @RequestParam(value = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) String dateString) {

        LocalDate date;
        if (dateString != null && !dateString.isEmpty()) {
            date = LocalDate.parse(dateString);
        } else {
            date = LocalDate.now(timeService.zone());
        }

        return availabilityService.getDailyAvailability(date);
    }
}