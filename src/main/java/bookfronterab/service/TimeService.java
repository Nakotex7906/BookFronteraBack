package bookfronterab.service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import org.springframework.stereotype.Component;

@Component
public class TimeService {
    private final ZoneId zoneId = ZoneId.of("America/Santiago");
    public ZoneId zone() { return zoneId; }
    public LocalDate today() { return LocalDate.now(zoneId); }
    public OffsetDateTime nowOffset() { return OffsetDateTime.now(zoneId); }
    public OffsetDateTime atOffset(LocalDate date, LocalTime time) {
        return date.atTime(time).atZone(zoneId).toOffsetDateTime();
    }

}
