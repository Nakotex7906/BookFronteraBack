package bookfronterab.service.google;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.CalendarScopes;
import com.google.api.services.calendar.model.Event;
import bookfronterab.model.Reservation;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.model.EventDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * Servicio para interactuar con la API de Google Calendar.
 * Se encarga de crear y eliminar eventos.
 */
@Service
@Slf4j // Añadido para logs
public class GoogleCalendarService {

    private static final String APPLICATION_NAME = "BookFrontera Calendar";
    public static final List<String> SCOPES = Collections.singletonList(CalendarScopes.CALENDAR_EVENTS);
    private static final String CALENDAR_ID = "primary";

    /**
     * Construye y devuelve un cliente de Google Calendar autenticado.
     *
     * @param accessToken El token de acceso OAuth2 del usuario.
     * @return Un cliente de Calendar listo para usar.
     */
    public Calendar getCalendarClient(String accessToken) {
        Credential credential = new GoogleCredential().setAccessToken(accessToken);
        HttpTransport transport = new NetHttpTransport();
        GsonFactory jsonFactory = GsonFactory.getDefaultInstance();

        return new Calendar.Builder(transport, jsonFactory, credential)
                .setApplicationName(APPLICATION_NAME)
                .build();
    }

    /**
     * Crea un nuevo evento en Google Calendar basado en una reserva.
     *
     * @param reservation La entidad de reserva con los detalles.
     * @param accessToken El token de acceso del usuario.
     * @return El ID del evento de Google Calendar que se ha creado.
     * @throws IOException Si hay un error de comunicación con la API.
     */
    public String createEventForReservation(Reservation reservation, String accessToken) throws IOException {
        Calendar service = getCalendarClient(accessToken);

        Event event = new Event()
                .setSummary("Reserva de Sala: " + reservation.getRoom().getName())
                .setDescription("Reserva realizada a través de BookFrontera.")
                .setLocation(reservation.getRoom().getName()); // Añadimos la ubicación

        // Convertimos ZonedDateTime a DateTime de Google
        DateTime startDateTime = new DateTime(reservation.getStartAt().toInstant().toEpochMilli());
        DateTime endDateTime = new DateTime(reservation.getEndAt().toInstant().toEpochMilli());

        event.setStart(new EventDateTime().setDateTime(startDateTime).setTimeZone(reservation.getStartAt().getZone().getId()));
        event.setEnd(new EventDateTime().setDateTime(endDateTime).setTimeZone(reservation.getEndAt().getZone().getId()));

        // Insertamos el evento
        Event createdEvent = service.events().insert(CALENDAR_ID, event).execute();
        log.info("Evento de Google Calendar creado con ID: {}", createdEvent.getId());

        return createdEvent.getId();
    }

    /**
     * Elimina un evento del Google Calendar del usuario.
     *
     * @param googleEventId El ID del evento a eliminar.
     * @param accessToken   El token de acceso del usuario.
     * @throws IOException Si hay un error de comunicación con la API.
     */
    public void deleteEvent(String googleEventId, String accessToken) throws IOException {
        if (googleEventId == null || googleEventId.isEmpty()) {
            log.warn("Se intentó borrar un evento de Google Calendar pero el ID era nulo o vacío.");
            return;
        }

        try {
            Calendar service = getCalendarClient(accessToken);
            service.events().delete(CALENDAR_ID, googleEventId).execute();
            log.info("Evento de Google Calendar eliminado con ID: {}", googleEventId);
        } catch (IOException e) {
            // Manejamos el caso de "ya no existe" (404 o 410) para no lanzar un error
            if (e.getMessage().contains("404") || e.getMessage().contains("410")) {
                log.warn("Se intentó borrar el evento de Google Calendar (ID: {}), pero ya no se encontró (404/410).", googleEventId);
            } else {
                // Si es otro error (ej. 403 Sin Permiso), lo relanzamos
                log.error("Error al intentar eliminar el evento de Google Calendar (ID: {}): {}", googleEventId, e.getMessage());
                throw e;
            }
        }
    }
}