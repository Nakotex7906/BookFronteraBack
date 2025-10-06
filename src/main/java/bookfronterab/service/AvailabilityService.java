package bookfronterab.service;

import bookfronterab.dto.AvailabilityDto;
import bookfronterab.model.Blackout;
import bookfronterab.model.OpeningHour;
import bookfronterab.model.Reservation;
import bookfronterab.model.ReservationStatus;
import bookfronterab.repo.BlackoutRepository;
import bookfronterab.repo.OpeningHourRepository;
import bookfronterab.repo.ReservationRepository;
import bookfronterab.repo.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AvailabilityService {

    private final RoomRepository roomRepo;
    private final OpeningHourRepository openingRepo;
    private final ReservationRepository resRepo;
    private final BlackoutRepository blackoutRepo;
    private final TimeService time;

    public AvailabilityDto daily(Long roomId, LocalDate date, int slotMinutes) {
        var room = roomRepo.findById(roomId).orElseThrow();
        var weekday = date.getDayOfWeek();

        OpeningHour oh = openingRepo.findByRoomAndWeekday(room, weekday)
                .orElseThrow(() -> new IllegalStateException("La sala no tiene horario para " + weekday));

        // Ventana del día en zona local
        OffsetDateTime open = time.atOffset(date, oh.getOpenTime());
        OffsetDateTime close = time.atOffset(date, oh.getCloseTime());

        // Reservas y blackouts que tocan ese día
        List<Reservation> bookings = resRepo.overlaps(roomId, open, close, ReservationStatus.CONFIRMED);
        List<Blackout> outs = blackoutRepo.findByRoomIdAndEndAtAfterAndStartAtBefore(roomId, open, close);

        // Construye slots ocupados
        List<AvailabilityDto.Slot> booked = new ArrayList<>();
        for (Reservation r : bookings) {
            booked.add(new AvailabilityDto.Slot(r.getStartAt().toLocalTime().toString(), r.getEndAt().toLocalTime().toString()));
        }
        for (Blackout b : outs) {
            booked.add(new AvailabilityDto.Slot(b.getStartAt().toLocalTime().toString(), b.getEndAt().toLocalTime().toString()));
        }

        // Calcula libres por diferencia simple (a nivel de tramos grandes)
        var free = computeFree(open, close, bookings, outs, slotMinutes);

        return new AvailabilityDto(roomId, date.toString(), oh.getOpenTime().toString(), oh.getCloseTime().toString(),
                slotMinutes, booked, free);
    }

    private List<AvailabilityDto.Slot> computeFree(OffsetDateTime open, OffsetDateTime close,
                                                   List<Reservation> res, List<Blackout> outs,
                                                   int slotMinutes) {
        // Unimos ocupados (reservas + blackouts) y luego generamos gaps redondeados a slot
        record Range(OffsetDateTime s, OffsetDateTime e) {}
        List<Range> occ = new ArrayList<>();
        res.forEach(r -> occ.add(new Range(r.getStartAt(), r.getEndAt())));
        outs.forEach(b -> occ.add(new Range(b.getStartAt(), b.getEndAt())));
        occ.sort((a,b)->a.s.compareTo(b.s));

        // merge intervals
        List<Range> merged = new ArrayList<>();
        OffsetDateTime cs = open, ce = open; // current pointer

        for (Range r: occ) {
            if (r.e.isBefore(open) || r.s.isAfter(close)) continue; // fuera
            OffsetDateTime s = r.s.isBefore(open) ? open : r.s;
            OffsetDateTime e = r.e.isAfter(close) ? close : r.e;
            if (merged.isEmpty()) { merged.add(new Range(s,e)); }
            else {
                Range last = merged.get(merged.size()-1);
                if (!s.isAfter(last.e)) { // overlap
                    merged.set(merged.size()-1, new Range(last.s, e.isAfter(last.e)? e : last.e));
                } else {
                    merged.add(new Range(s,e));
                }
            }
        }

        List<AvailabilityDto.Slot> free = new ArrayList<>();
        OffsetDateTime cursor = open;
        for (Range r: merged) {
            if (cursor.isBefore(r.s)) {
                addFreeSlots(free, cursor, r.s, slotMinutes);
            }
            cursor = r.e.isAfter(cursor) ? r.e : cursor;
        }
        if (cursor.isBefore(close)) addFreeSlots(free, cursor, close, slotMinutes);
        return free;
    }

    private void addFreeSlots(List<AvailabilityDto.Slot> free, OffsetDateTime from, OffsetDateTime to, int slotMinutes) {
        var cur = from;
        while (cur.isBefore(to)) {
            var next = cur.plusMinutes(slotMinutes);
            if (next.isAfter(to)) break; // no agregamos slot incompleto
            free.add(new AvailabilityDto.Slot(cur.toLocalTime().toString(), next.toLocalTime().toString()));
            cur = next;
        }
    }

    public bookfronterab.dto.AvailabilityGridDto getAvailabilityGrid(LocalDate date) {
        List<bookfronterab.model.Room> rooms = roomRepo.findAll();
        List<bookfronterab.dto.RoomDto> roomDtos = rooms.stream()
                .map(r -> new bookfronterab.dto.RoomDto(r.getId(), r.getName(), r.getCapacity(), r.getEquipment()))
                .toList();

        List<bookfronterab.dto.TimeSlotDto> timeSlots = generateTimeSlots(9, 21, 60);

        List<bookfronterab.dto.AvailabilityStatusDto> availabilityStatus = new ArrayList<>();
        for (bookfronterab.model.Room room : rooms) {
            for (bookfronterab.dto.TimeSlotDto slot : timeSlots) {
                boolean isAvailable = isSlotAvailableForRoom(room, date, slot);
                availabilityStatus.add(new bookfronterab.dto.AvailabilityStatusDto(String.valueOf(room.getId()), slot.id(), isAvailable));
            }
        }

        return new bookfronterab.dto.AvailabilityGridDto(roomDtos, timeSlots, availabilityStatus);
    }

    public List<bookfronterab.dto.TimeSlotDto> generateTimeSlots(int startHour, int endHour, int slotDurationMinutes) {
        List<bookfronterab.dto.TimeSlotDto> slots = new ArrayList<>();
        java.time.LocalTime time = java.time.LocalTime.of(startHour, 0);
        while (time.getHour() < endHour) {
            java.time.LocalTime endTime = time.plusMinutes(slotDurationMinutes);
            String id = String.format("%02d-%02d", time.getHour(), endTime.getHour());
            String label = String.format("%02d:%02d - %02d:%02d", time.getHour(), time.getMinute(), endTime.getHour(), endTime.getMinute());
            slots.add(new bookfronterab.dto.TimeSlotDto(id, label));
            time = endTime;
        }
        return slots;
    }

    private boolean isSlotAvailableForRoom(bookfronterab.model.Room room, LocalDate date, bookfronterab.dto.TimeSlotDto slot) {
        String[] hours = slot.id().split("-");
        java.time.LocalTime startTime = java.time.LocalTime.of(Integer.parseInt(hours[0]), 0);
        java.time.LocalTime endTime = java.time.LocalTime.of(Integer.parseInt(hours[1]), 0);

        OffsetDateTime startDateTime = time.atOffset(date, startTime);
        OffsetDateTime endDateTime = time.atOffset(date, endTime);

        List<Reservation> bookings = resRepo.overlaps(room.getId(), startDateTime, endDateTime, ReservationStatus.CONFIRMED);
        List<Blackout> outs = blackoutRepo.findByRoomIdAndEndAtAfterAndStartAtBefore(room.getId(), startDateTime, endDateTime);

        return bookings.isEmpty() && outs.isEmpty();
    }
}
