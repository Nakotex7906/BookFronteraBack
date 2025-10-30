package bookfronterab.model;

import jakarta.persistence.Id;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Registry {
    @Id
    private int idRegistry;
    private Reservation reservation;
    private State state;
}
