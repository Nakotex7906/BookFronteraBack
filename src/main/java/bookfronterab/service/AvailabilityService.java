package bookfronterab.service;

import bookfronterab.dto.AvailabilityDto;
import bookfronterab.dto.RoomDto;
import bookfronterab.model.Reservation;
import bookfronterab.model.Room;
import bookfronterab.repo.ReservationRepository;
import bookfronterab.repo.RoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AvailabilityService {

    private final RoomRepository roomRepo;
    private final ReservationRepository reservationRepo;
    private final TimeService timeService;

    public AvailabilityDto.DailyAvailabilityResponse getDailyAvailability(LocalDate date) {
        // Obtener todas las salas
        List<RoomDto> rooms = roomRepo.findAll().stream()
                .map(this::mapRoomToDto)
                .collect(Collectors.toList());

        // Generar la lista de bloques horarios (AHORA PERSONALIZADA)
        List<AvailabilityDto.TimeSlotDto> slots = generateTimeSlots();

        // Obtener rango del día
        ZonedDateTime startOfDay = date.atStartOfDay(timeService.zone());
        ZonedDateTime endOfDay = date.plusDays(1).atStartOfDay(timeService.zone());

        // Obtener reservas
        List<Reservation> allReservationsForDay = reservationRepo.findAllReservationsBetween(startOfDay, endOfDay);

        // Agrupar reservas
        Map<Long, List<Reservation>> reservationsByRoomId = allReservationsForDay.stream()
                .collect(Collectors.groupingBy(r -> r.getRoom().getId()));

        log.info("Calculando disponibilidad para {} salas y {} reservas en {}", rooms.size(), allReservationsForDay.size(), date);

        // Construir matriz
        List<AvailabilityDto.AvailabilityMatrixItemDto> availabilityMatrix = new ArrayList<>();

        for (RoomDto room : rooms) {
            List<Reservation> roomReservations = reservationsByRoomId.getOrDefault(room.getId(), List.of());

            for (AvailabilityDto.TimeSlotDto slot : slots) {
                ZonedDateTime slotStartAt = ZonedDateTime.of(date, LocalTime.parse(slot.getStart()), timeService.zone());
                ZonedDateTime slotEndAt = ZonedDateTime.of(date, LocalTime.parse(slot.getEnd()), timeService.zone());

                boolean isOccupied = roomReservations.stream().anyMatch(
                        res -> res.getStartAt().isBefore(slotEndAt) && res.getEndAt().isAfter(slotStartAt)
                );

                availabilityMatrix.add(new AvailabilityDto.AvailabilityMatrixItemDto(
                        String.valueOf(room.getId()),
                        slot.getId(),
                        !isOccupied
                ));
            }
        }

        return new AvailabilityDto.DailyAvailabilityResponse(rooms, slots, availabilityMatrix);
    }

    /**
     * Genera los bloques horarios específicos de la UFRO según intranet.
     */
    private List<AvailabilityDto.TimeSlotDto> generateTimeSlots() {
        List<AvailabilityDto.TimeSlotDto> slots = new ArrayList<>();

        // Formato: Hora Inicio, Hora Fin, Etiqueta (Periodo)
        addSlot(slots, "08:30", "09:30", "1°");
        addSlot(slots, "09:40", "10:40", "2°");
        addSlot(slots, "10:50", "11:50", "3°");
        addSlot(slots, "12:00", "13:00", "4°");
        addSlot(slots, "13:10", "14:10", "Alm.");
        addSlot(slots, "14:30", "15:30", "5°");
        addSlot(slots, "15:40", "16:40", "6°");
        addSlot(slots, "16:50", "17:50", "7°");
        addSlot(slots, "18:00", "19:00", "8°");
        addSlot(slots, "19:10", "20:10", "9°");
        addSlot(slots, "20:20", "21:20", "10°");

        return slots;
    }

    /**
     * auxiliar para agregar slots a la lista de forma limpia.
     */
    private void addSlot(List<AvailabilityDto.TimeSlotDto> list, String start, String end, String periodName) {
        // Manteniene el ID como "HH:mm-HH:mm" para que el frontend lo ordene correctamente
        String id = String.format("%s-%s", start, end);
        // El label combina el nombre del periodo y la hora para que el usuario lo vea claro
        String label = String.format("%s (%s-%s)", periodName, start, end);

        list.add(new AvailabilityDto.TimeSlotDto(id, label, start, end));
    }

    private RoomDto mapRoomToDto(Room room) {
        return RoomDto.builder()
                .id(room.getId())
                .name(room.getName())
                .capacity(room.getCapacity())
                .equipment(room.getEquipment())
                .floor(room.getFloor())
                .build();
    }
}