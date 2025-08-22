package bookfronterab.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "blackouts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Blackout {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false,fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id")
    private Room room;

    @NotNull
    private OffsetDateTime startAt;
    @NotNull
    private OffsetDateTime endAt;
    @NotBlank
    private String motivo;
}
