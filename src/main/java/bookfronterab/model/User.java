package bookfronterab.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "\"users\"")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id @GeneratedValue(strategy =  GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    @Email @NotBlank
    private String email;

    @Column(nullable = false)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role rol;

    @Column(nullable = false)
    private OffsetDateTime creadoEn;

    // Google Calendar Tokens
    @Column(length = 1024)
    private String googleAccessToken;

    @Column(length = 1024)
    private String googleRefreshToken;

    private OffsetDateTime googleTokenExpiryDate;
}
