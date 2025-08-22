package bookfronterab.model;


import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.util.Set;

@Entity
@Table(name = "rooms")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Room {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false)
    private String nombre;

    @NotBlank
    @Column(nullable = false)
    private String ubicacion;

    @Min(1)
    @Column(nullable = false)
    private Integer capacidad;

    @ElementCollection
    @CollectionTable(name = "room_equipment", joinColumns = @JoinColumn(name = "room_id"))
    @Column(name = "equipo")
    private Set<String> equipos; // ej: "proyector", "pizarra"

    @Column(nullable = false)
    private Boolean activa;

    public boolean isActiva() {
        return activa;
    }
}
