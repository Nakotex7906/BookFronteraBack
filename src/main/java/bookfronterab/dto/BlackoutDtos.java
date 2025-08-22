package bookfronterab.dto;

import java.time.OffsetDateTime;

public class BlackoutDtos {

    public record CreateRequest(OffsetDateTime startAt, OffsetDateTime endAt, String motivo){}
    public record Response(Long id, OffsetDateTime startAt, OffsetDateTime endAt, String motivo){}

}
