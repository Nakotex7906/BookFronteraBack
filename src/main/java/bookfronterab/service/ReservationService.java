package bookfronterab.service;

import java.time.ZonedDateTime;

import org.hibernate.mapping.List;
import org.springframework.stereotype.Service;

import bookfronterab.model.Reservation;
import lombok.RequiredArgsConstructor;

@Service
public class ReservationService {
    private List<Reservation> reservations = new List<Reservation>();
    private List<Room> rooms = new List<Room>();

    private void CreateReservation(int room,ZonedDateTime date ,User user){
        Reservation reservation = new Reservation();
        reservation.setReservationCell(room);
        reservation.setFecha(date);
        reservation.setRoom(null);

    }
}
