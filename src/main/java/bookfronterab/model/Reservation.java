package bookfronterab.model;

import java.time.ZonedDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "\"reservations\"")
public class Reservation {
    @Id
    private int idReservation;
    private ZonedDateTime fecha;
    private int reservationCell;
}
