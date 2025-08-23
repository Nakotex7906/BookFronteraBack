package bookfronterab.controller;

import bookfronterab.dto.ReservationDtos;
import bookfronterab.service.ReservationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    @PostMapping("/reservations")
    @ResponseStatus(HttpStatus.CREATED)
    public void create(
            @RequestBody ReservationDtos.CreateRequest req,
            @RequestHeader(value = "X-User-Id") Long userId) {
        // Crea la reserva
        reservationService.create(userId, req.roomId(), req.startAt(), req.endAt());
    }

    @GetMapping("/reservations/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void get(@PathVariable Long id) {
        reservationService.get(id);
    }

    @DeleteMapping("/reservations/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable Long id,
                       @RequestHeader(value = "X-User-Id") Long userId,
                       @RequestHeader(value = "X-Is-Admin", defaultValue = "false") boolean isAdmin) {
        reservationService.cancel(id, userId, isAdmin);
    }

}
