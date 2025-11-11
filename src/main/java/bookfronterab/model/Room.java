package bookfronterab.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Entity
@Table(name = "\"rooms\"")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "room_seq")
    @SequenceGenerator(name = "room_seq", sequenceName = "room_id_seq", allocationSize = 1, initialValue = 1)
    private Long id;

    private String name;
    private int capacity;

    // ---
    // --- ESTA ES LA LÍNEA QUE ARREGLA EL TEST ---
    // ---
    // Le decimos a JPA que cargue esta lista "ansiosamente" (EAGER)
    // en lugar de "perezosamente" (LAZY).
    @ElementCollection(fetch = FetchType.EAGER)
    private List<String> equipment;

    private int floor;
}