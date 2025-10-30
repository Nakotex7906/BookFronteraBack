package bookfronterab.model;

import jakarta.persistence.ElementCollection;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Table(name = "\"rooms\"")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Room {
    @Id
    private int idRoom;
    private String name;
    private int capacity;
    @ElementCollection
    private String[] equipment;
    private int floor;
}
