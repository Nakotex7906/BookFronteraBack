package bookfronterab.service.google;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.CalendarScopes;
import com.google.api.services.calendar.model.Event;
import bookfronterab.model.Reservation;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

@Service
public class GoogleCalendarService {

    private static final String APPLICATION_NAME = "BookFrontera Calendar";
    public static final List<String> SCOPES = Collections.singletonList(CalendarScopes.CALENDAR_EVENTS);

    public Calendar getCalendarClient(String accessToken) {
        Credential credential = new GoogleCredential().setAccessToken(accessToken);
        return new Calendar.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance(), credential)
                .setApplicationName(APPLICATION_NAME)
                .build();
    }

    public void createEventForReservation(Reservation reservation, String accessToken) throws IOException {
        Calendar service = getCalendarClient(accessToken);

        Event event = new Event()
                .setSummary("Reserva de Sala: " + reservation.getRoom().getName())
                .setDescription("Reserva realizada a través de BookFrontera.")
                .setStart(new com.google.api.services.calendar.model.EventDateTime().setDateTime(new com.google.api.client.util.DateTime(reservation.getStartAt().toInstant().toEpochMilli())))
                .setEnd(new com.google.api.services.calendar.model.EventDateTime().setDateTime(new com.google.api.client.util.DateTime(reservation.getEndAt().toInstant().toEpochMilli())));

        String calendarId = "primary";
        service.events().insert(calendarId, event).execute();
    }
}
