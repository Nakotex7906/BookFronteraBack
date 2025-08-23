package bookfronterab.controller;

import bookfronterab.dto.AvailabilityDto;
import bookfronterab.dto.OpeningHoursDtos;
import bookfronterab.dto.RoomDto;
import bookfronterab.model.Room;
import bookfronterab.service.AvailabilityService;
import bookfronterab.service.RoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;
    private final AvailabilityService availabilityService;

    @GetMapping("/rooms")
    public List<RoomDto> listRooms() {
        return roomService.list().stream()
                .map(r -> new RoomDto(r.getId(), r.getNombre(), r.getUbicacion(), r.getCapacidad(), r.getEquipos(), r.isActiva()))
                .toList();
    }

    @GetMapping("/rooms/{id}")
    public RoomDto getRoom(@PathVariable Long id) {
        Room r = roomService.get(id);
        return new RoomDto(r.getId(), r.getNombre(), r.getUbicacion(), r.getCapacidad(), r.getEquipos(), r.isActiva());
    }

    @GetMapping("/rooms/{id}/availability")
    public AvailabilityDto availability(@PathVariable Long id,
                                        @RequestParam String date,
                                        @RequestParam(defaultValue = "30") int slot) {
        return availabilityService.daily(id, LocalDate.parse(date), slot);
    }

    // ADMIN: actualizar horarios de apertura
    @PutMapping("/rooms/{id}/opening-hours")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void upsertOpening(@PathVariable Long id, @RequestBody OpeningHoursDtos.UpsertRequest req) {
        roomService.upsertOpeningHours(id, req);
    }

}
