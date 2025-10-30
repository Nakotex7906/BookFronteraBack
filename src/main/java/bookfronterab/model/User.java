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

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "user_seq")
    @SequenceGenerator(name = "user_seq", sequenceName = "user_id_seq", allocationSize = 1, initialValue = 100)
    private Long id;

    @Column(nullable = false, unique = true)
    @Email @NotBlank
    private String email;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole rol;


    // Google Calendar Tokens
    @Column(length = 1024)
    private String googleAccessToken;

    @Column(length = 1024)
    private String googleRefreshToken;

    private OffsetDateTime googleTokenExpiryDate;
}
