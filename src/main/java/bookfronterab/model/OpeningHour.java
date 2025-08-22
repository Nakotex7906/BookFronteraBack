package bookfronterab.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.DayOfWeek;
import java.time.LocalTime;

@Entity
@Table(name = "opening_hours",
        uniqueConstraints = @UniqueConstraint(columnNames = {"room_id", "weekday"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OpeningHour {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false,fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id")
    private Room room;

    @NotNull @Enumerated(EnumType.STRING)
    private DayOfWeek weekday; // martes, miercoles

    @NotNull
    private LocalTime openTime; // 08:00

    @NotNull
    private LocalTime closeTime; // 21:00

}
