package bookfronterab.controller;

import bookfronterab.dto.ReservationDto;
import bookfronterab.service.ReservationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    /**
     * Endpoint para crear un reserva.
     */
    @PostMapping("/reservations")
    @ResponseStatus(HttpStatus.CREATED)
    public void create(
            @RequestBody ReservationDto.CreateRequest req,
            @AuthenticationPrincipal OAuth2User principal) {
        if (principal == null) {
            throw new SecurityException("No estás autenticado.");
        }
        String userEmail = principal.getAttribute("email");
        reservationService.create(userEmail, req);
    }

    /**
     * Endpoint para obtener todas las reservas del usuario autenticado,
     * clasificadas en actual, futuras y pasadas.
     *
     * @param principal El usuario autenticado (OAuth2User).
     * @return Un DTO {@link ReservationDto.MyReservationsResponse} con las listas.
     */
    @GetMapping("/reservations/my-reservations")
    @ResponseStatus(HttpStatus.OK)
    public ReservationDto.MyReservationsResponse getMyReservations(
            @AuthenticationPrincipal OAuth2User principal) {
        if (principal == null) {
            throw new SecurityException("No estás autenticado.");
        }
        String userEmail = principal.getAttribute("email");
        return reservationService.getMyReservations(userEmail);
    }

    /**
     * Endpoint para obtener los detalles de una reserva específica por su ID.
     *
     * @param id El ID de la reserva.
     * @return Un DTO {@link ReservationDto.Detail} con la información completa de la reserva.
     */
    @GetMapping("/reservations/{id}")
    @ResponseStatus(HttpStatus.OK)
    public ReservationDto.Detail get(@PathVariable Long id) {
        return reservationService.getById(id);
    }

    @DeleteMapping("/reservations/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable Long id,
                       @AuthenticationPrincipal OAuth2User principal,
                       @RequestHeader(value = "X-Is-Admin", defaultValue = "false") boolean isAdmin) {
        if (principal == null) {
            throw new SecurityException("No estás autenticado.");
        }
        String userEmail = principal.getAttribute("email");
        reservationService.cancel(id, userEmail, isAdmin);
    }
}