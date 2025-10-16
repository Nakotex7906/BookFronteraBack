package bookfronterab.service;

import java.time.LocalDate;
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
        TimeService service = new TimeService();
        assertEquals(service.zone(),"America/Santiago");
    }
    @Test
    void localDate_IsNow(){
        TimeService service = new TimeService();
        assertEquals(service.today(), LocalDate.now(ZoneId.of("America/Santiago")));
    }
}
