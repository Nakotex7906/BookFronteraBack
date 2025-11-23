package bookfronterab.service.google;

import bookfronterab.model.Reservation;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.api.client.util.DateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;

/**
 * Servicio para interactuar con la API de Google Calendar.
 * Se encarga de la gestión de eventos (creación y eliminación).
 */
@Service
@Slf4j
public class GoogleCalendarService {

    private static final String APPLICATION_NAME = "BookFrontera Calendar";
    private static final String CALENDAR_ID = "primary";

    /**
     * Construye y devuelve un cliente de Google Calendar autenticado.
     * <p>
     * Se reemplaza el uso de la clase deprecada GoogleCredential por un
     * HttpRequestInitializer simple mediante una expresión lambda.
     * </p>
     *
     * @param accessToken El token de acceso OAuth2 del usuario.
     * @return Un cliente de Calendar configurado y listo para usar.
     */
    public Calendar getCalendarClient(String accessToken) {
        HttpTransport transport = new NetHttpTransport();
        GsonFactory jsonFactory = GsonFactory.getDefaultInstance();

        // En lugar de 'new GoogleCredential().setAccessToken(...)', definimos
        // manualmente cómo se inyecta el token en los headers.
        HttpRequestInitializer requestInitializer = request ->
                request.getHeaders().setAuthorization("Bearer " + accessToken);

        return new Calendar.Builder(transport, jsonFactory, requestInitializer)
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
                .setLocation(reservation.getRoom().getName());

        // Conversión segura de fechas
        DateTime startDateTime = new DateTime(reservation.getStartAt().toInstant().toEpochMilli());
        DateTime endDateTime = new DateTime(reservation.getEndAt().toInstant().toEpochMilli());

        event.setStart(new EventDateTime().setDateTime(startDateTime).setTimeZone(reservation.getStartAt().getZone().getId()));
        event.setEnd(new EventDateTime().setDateTime(endDateTime).setTimeZone(reservation.getEndAt().getZone().getId()));

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
            if (e.getMessage().contains("404") || e.getMessage().contains("410")) {
                log.warn("El evento {} ya no existe en Google Calendar (404/410).", googleEventId);
            } else {
                log.error("Error al eliminar evento {}: {}", googleEventId, e.getMessage());
                throw e;
            }
        }
    }
}