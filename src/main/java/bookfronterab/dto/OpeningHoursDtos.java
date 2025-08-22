package bookfronterab.dto;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

public class OpeningHoursDtos {

    public record DayRange(DayOfWeek weekday, LocalTime open, LocalTime close){}
    public record UpsertRequest(List<DayRange> week ){}

}
