package bookfronterab.service;

import java.time.ZonedDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import bookfronterab.model.Reservation;
import bookfronterab.model.Room;
import bookfronterab.model.User;
import bookfronterab.repo.ReservationRepository;
import bookfronterab.repo.RoomRepository; // Necesitarás crear este repositorio
import bookfronterab.repo.UserRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor // Inyecta los 'final' fields en el constructor
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final RoomRepository roomRepository; 
    private final UserRepository userRepository;

    public Reservation createReservation(int roomId, ZonedDateTime date, User user, int reservationCell) {
        

        Room room = roomRepository.findById(roomId);

        if(reservationRepository.findByRoomIdDateReservationCell(roomId, date,reservationCell) != null) {
            throw new IllegalArgumentException("La celda de reserva ya está ocupada para esta habitación y fecha.");

        }else{
        Reservation reservation = new Reservation();
        reservation.setFecha(date);
        reservation.setUser(user);
        reservation.setRoom(room);
        
        return reservationRepository.save(reservation);
        }
    }
    
    public void deleteReservation(int roomId, ZonedDateTime date, User user, int reservationCell) {
        
        Room room = roomRepository.findById(roomId);
        Reservation reservation = reservationRepository.findByRoomIdDateReservationCell(roomId, date,reservationCell);
        reservationRepository.delete(reservation);
    }
    public List<Reservation> getAllReservations() {
        return reservationRepository.findAll();
    }
    
}