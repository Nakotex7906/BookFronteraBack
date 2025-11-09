package bookfronterab.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.OffsetDateTime;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class TimeService {

    private final ZoneId appZoneId;

    public OffsetDateTime nowOffset() {
        return OffsetDateTime.now(appZoneId);
    }

    public ZoneId zone() {
        return appZoneId;
    }
}