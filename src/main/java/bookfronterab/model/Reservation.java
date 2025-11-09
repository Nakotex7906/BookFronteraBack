package bookfronterab.model;

import java.time.ZonedDateTime;

import jakarta.persistence.*;
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
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "reservation_seq")
    @SequenceGenerator(name = "reservation_seq", sequenceName = "reservation_id_seq", allocationSize = 1, initialValue = 100)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Column(nullable = false)
    private ZonedDateTime startAt;

    @Column(nullable = false)
    private ZonedDateTime endAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * Almacena el ID del evento correspondiente en Google Calendar.
     * Es 'null' si la reserva no fue sincronizada con Google Calendar.
     */
    @Column(nullable = true) // es opcional
    private String googleEventId;

}