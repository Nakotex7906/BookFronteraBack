package bookfronterab.service;

import bookfronterab.model.Reservation;
import bookfronterab.model.ReservationStatus;
import bookfronterab.model.User;
import bookfronterab.repo.BlackoutRepository;
import bookfronterab.repo.OpeningHourRepository;
import bookfronterab.repo.ReservationRepository;
import bookfronterab.repo.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class ReservationService {

    private final ReservationRepository repo;
    private final RoomRepository roomRepo;
    private final OpeningHourRepository openingRepo;
    private final BlackoutRepository blackoutRepo;
    private final TimeService time;

    private static final int MIN_MINUTES = 30;
    private static final int MAX_MINUTES = 120;
    private static final int SLOT = 30; // alineación
    private static final int USER_ACTIVE_LIMIT = 2;

    @Transactional
    public Reservation create(Long userId, Long roomId, OffsetDateTime startAt, OffsetDateTime endAt) {
        var room = roomRepo.findById(roomId).orElseThrow();
        if (!room.isActiva()) throw new IllegalStateException("La sala está inactiva");

        if (!startAt.isBefore(endAt)) throw new IllegalArgumentException("Rango horario inválido");
        long minutes = Duration.between(startAt, endAt).toMinutes();
        if (minutes < MIN_MINUTES || minutes > MAX_MINUTES) throw new IllegalArgumentException("Duración fuera de rango");
        if (startAt.getMinute() % SLOT != 0 || endAt.getMinute() % SLOT != 0) throw new IllegalArgumentException("Debe alinearse a slots de " + SLOT + " minutos");

        // Dentro del horario de apertura
        LocalDate date = startAt.atZoneSameInstant(time.zone()).toLocalDate();
        var oh = openingRepo.findByRoomAndWeekday(room, date.getDayOfWeek())
                .orElseThrow(() -> new IllegalStateException("Sala sin horario para el día"));

        OffsetDateTime open = time.atOffset(date, oh.getOpenTime());
        OffsetDateTime close = time.atOffset(date, oh.getCloseTime());
        if (startAt.isBefore(open) || endAt.isAfter(close)) throw new IllegalArgumentException("Fuera del horario de apertura");

        // No dentro de blackout
        if (!blackoutRepo.findByRoomIdAndEndAtAfterAndStartAtBefore(roomId, startAt, endAt).isEmpty())
            throw new IllegalStateException("Franja bloqueada (mantención/feriado)");

        // Sin solapes
        if (!repo.overlaps(roomId, startAt, endAt, ReservationStatus.CONFIRMED).isEmpty())
            throw new IllegalStateException("Existe una reserva superpuesta");

        // Límite por usuario (activas, ahora hacia adelante)
        if (repo.findByUserIdAndEndAtAfterOrderByStartAtAsc(userId, time.nowOffset()).stream()
                .filter(r -> r.getEstado() == ReservationStatus.CONFIRMED).count() >= USER_ACTIVE_LIMIT)
            throw new IllegalStateException("Excede el máximo de reservas activas");

        var res = Reservation.builder()
                .room(room)
                .user(User.builder().id(userId).build()) // attach por id
                .startAt(startAt)
                .endAt(endAt)
                .estado(ReservationStatus.CONFIRMED)
                .build();
        return repo.save(res);
    }

    public Reservation get(Long id) { return repo.findById(id).orElseThrow(); }

    @Transactional
    public void cancel(Long id, Long userId, boolean isAdmin) {
        var res = repo.findById(id).orElseThrow();
        if (!isAdmin && !res.getUser().getId().equals(userId))
            throw new SecurityException("No puedes cancelar reservas de otro usuario");
        res.setEstado(ReservationStatus.CANCELLED);
        repo.save(res);
    }

}
