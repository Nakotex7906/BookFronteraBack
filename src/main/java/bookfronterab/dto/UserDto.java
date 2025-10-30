package bookfronterab.dto;

import bookfronterab.model.UserRole;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserDto {
    private Long id;
    private String email;
    private String nombre;
    private UserRole rol;
}
