package bookfronterab.controller;

import bookfronterab.dto.ReservationDto;
import bookfronterab.service.ReservationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ReservationController {
    public static final String EMAIL = "email";

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
        String userEmail = principal.getAttribute(EMAIL);
        reservationService.create(userEmail, req);
    }
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/reservations/on-behalf")
    @ResponseStatus(HttpStatus.CREATED)
    public void createOnBehalf(
            @RequestBody ReservationDto.CreateOnBehalfRequest req,
            @AuthenticationPrincipal OAuth2User principal) {
        if (principal == null) {
            throw new SecurityException("No estás autenticado.");
        }
        String userEmail = principal.getAttribute(EMAIL);

        ReservationDto.CreateRequest createRequest = new ReservationDto.CreateRequest(
                req.roomId(),
                req.startAt(),
                req.endAt(),
                req.addToGoogleCalendar()
        );

        reservationService.createOnBehalf(userEmail, req.othersEmail(), createRequest);
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
        String userEmail = principal.getAttribute(EMAIL);
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
                       @AuthenticationPrincipal OAuth2User principal)
                        {
        if (principal == null) {
            throw new SecurityException("No estás autenticado.");
        }
        String userEmail = principal.getAttribute(EMAIL);
        reservationService.cancel(id, userEmail);
    }
    /**
     * Endpoint para que el ADMIN vea las reservas de una sala específica.
     * Útil para gestionar conflictos o ver disponibilidad.
     */
    @GetMapping("/room/{roomId}")
    @ResponseStatus(HttpStatus.OK)
    public List<ReservationDto.Detail> getByRoom(
            @PathVariable Long roomId,
            @AuthenticationPrincipal OAuth2User principal) {

        if (principal == null) {
            throw new SecurityException("No estás autenticado.");
        }

        String userEmail = principal.getAttribute(EMAIL);

        // Llamamos al nuevo método del serv
        return reservationService.getReservationsByRoom(roomId, userEmail);
    }

    /**
     * Endpoint para modificar una reserva existente.
     * Permite cambiar fecha, hora o sala.
     *
     * @param id El ID de la reserva a modificar.
     * @param req Los nuevos datos solicitados (sala, fechas).
     * @param principal El usuario autenticado.
     * @return El detalle de la reserva actualizada.
     */
    @PutMapping("/reservations/{id}")
    @ResponseStatus(HttpStatus.OK)
    public ReservationDto.Detail update(
            @PathVariable Long id,
            @RequestBody ReservationDto.CreateRequest req,
            @AuthenticationPrincipal OAuth2User principal) {

        if (principal == null) {
            throw new SecurityException("No estás autenticado.");
        }
        String userEmail = principal.getAttribute(EMAIL);
        return reservationService.modify(id, userEmail, req);
    }

}