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
import org.springframework.transaction.annotation.Transactional; // <-- 1. IMPORTAR LA ANOTACIÓN

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Servicio para calcular la disponibilidad de salas para el endpoint público.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AvailabilityService {

    private static final LocalTime START_HOUR = LocalTime.of(8, 0);
    private static final LocalTime END_HOUR = LocalTime.of(18, 0);
    private static final int SLOT_DURATION_MINUTES = 60; // Bloques de 1 hora
    private static final DateTimeFormatter SLOT_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final RoomRepository roomRepo;
    private final ReservationRepository reservationRepo;
    private final TimeService timeService; // Para obtener la zona horaria correcta

    /**
     * Calcula la disponibilidad de todas las salas para una fecha específica.
     *
     * @param date La fecha para la cual calcular la disponibilidad.
     * @return Un DTO {@link AvailabilityDto.DailyAvailabilityResponse}
     * que contiene las listas 'rooms', 'slots', y 'availability' (matriz).
     */
    // <-- 2. AÑADIR LA ANOTACIÓN (SOLUCIÓN AL ERROR)
    @Transactional(readOnly = true) 
    public AvailabilityDto.DailyAvailabilityResponse getDailyAvailability(LocalDate date) {

        // 1. Obtener todas las salas y mapearlas a RoomDto
        //    (Gracias a @Transactional, el .getEquipment() en mapRoomToDto funcionará)
        List<RoomDto> rooms = roomRepo.findAll().stream()
                .map(this::mapRoomToDto)
                .collect(Collectors.toList());

        // 2. Generar la lista de bloques horarios (slots)
        List<AvailabilityDto.TimeSlotDto> slots = generateTimeSlots();

        // 3. Obtener el rango de tiempo completo para el día en la zona horaria de la app
        ZonedDateTime startOfDay = date.atStartOfDay(timeService.zone());
        ZonedDateTime endOfDay = date.plusDays(1).atStartOfDay(timeService.zone());

        // 4. Obtener TODAS las reservas del día en 1 consulta
        List<Reservation> allReservationsForDay = reservationRepo.findAllReservationsBetween(startOfDay, endOfDay);

        // 5. Agrupar reservas por ID de sala para búsqueda rápida (en memoria)
        Map<Long, List<Reservation>> reservationsByRoomId = allReservationsForDay.stream()
                .collect(Collectors.groupingBy(r -> r.getRoom().getId()));

        log.info("Calculando disponibilidad para {} salas y {} reservas en {}", rooms.size(), allReservationsForDay.size(), date);

        // 6. Construir la "matriz" de disponibilidad (List<AvailabilityMatrixItemDto>)
        List<AvailabilityDto.AvailabilityMatrixItemDto> availabilityMatrix = new ArrayList<>();

        for (RoomDto room : rooms) {
            List<Reservation> roomReservations = reservationsByRoomId.getOrDefault(Long.valueOf(room.getId()), List.of());

            for (AvailabilityDto.TimeSlotDto slot : slots) {
                // Convertir el slot a ZonedDateTime para comparar
                ZonedDateTime slotStartAt = ZonedDateTime.of(date, LocalTime.parse(slot.getStart()), timeService.zone());
                ZonedDateTime slotEndAt = ZonedDateTime.of(date, LocalTime.parse(slot.getEnd()), timeService.zone());

                // Lógica de conflicto: ¿Hay alguna reserva que se solape con este slot?
                boolean isOccupied = roomReservations.stream().anyMatch(
                        res -> res.getStartAt().isBefore(slotEndAt) && res.getEndAt().isAfter(slotStartAt)
                );

                availabilityMatrix.add(new AvailabilityDto.AvailabilityMatrixItemDto(
                        String.valueOf(room.getId()), // ID de sala como String (como espera el frontend)
                        slot.getId(),
                        !isOccupied // !isOccupied = isAvailable
                ));
            }
        }

        // 7. Devolver el DTO contenedor con las 3 listas
        return new AvailabilityDto.DailyAvailabilityResponse(rooms, slots, availabilityMatrix);
    } // <-- La transacción (y la sesión de BBDD) se cierran aquí

    /**
     * Genera la lista de bloques horarios (slots) para el día.
     */
    private List<AvailabilityDto.TimeSlotDto> generateTimeSlots() {
        List<AvailabilityDto.TimeSlotDto> slots = new ArrayList<>();
        LocalTime currentTime = START_HOUR;

        while (currentTime.isBefore(END_HOUR)) {
            LocalTime slotEnd = currentTime.plusMinutes(SLOT_DURATION_MINUTES);

            String startTimeStr = currentTime.format(SLOT_FORMATTER);
            String endTimeStr = slotEnd.format(SLOT_FORMATTER);
            String slotId = String.format("%s-%s", startTimeStr, endTimeStr);
            String slotLabel = String.format("%s - %s", startTimeStr, endTimeStr);

            slots.add(new AvailabilityDto.TimeSlotDto(slotId, slotLabel, startTimeStr, endTimeStr));
            currentTime = slotEnd;
        }
        return slots;
    }

    /**
     * Convierte una entidad {@link Room} a su DTO.
     */
    private RoomDto mapRoomToDto(Room room) {
        return RoomDto.builder()
                .id(room.getId())
                .name(room.getName())
                .capacity(room.getCapacity())
                .equipment(room.getEquipment()) // <-- Esto ya no fallará
                .floor(room.getFloor())
                .build();
    }
}