package bookfronterab.service;

/*import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class TimeServiceTest {
    @Autowired
    private TimeService timeService;

    @Test
    void timezone_Santiago(){
        assertEquals(timeService.zone(),ZoneId.of("America/Santiago"));
    }
    @Test
    void localDate_IsNow(){
        assertEquals(timeService.today(), LocalDate.now(ZoneId.of("America/Santiago")));
    }
    @Test
    void OffDataSet_Now(){
        assertEquals(timeService.nowOffset(), OffsetDateTime.now(ZoneId.of("America/Santiago")));
    }
    @Test
    void atOffset_Now() {
        LocalDate date = timeService.today();
        LocalTime time = LocalTime.of(12, 0);
        OffsetDateTime timeServiceOffset = timeService.atOffset(date, time);
        OffsetDateTime offsetEsperado = date.atTime(time).atZone(ZoneId.of("America/Santiago")).toOffsetDateTime();
        assertEquals(offsetEsperado, timeServiceOffset);
    }

}
*/