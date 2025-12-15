package bookfronterab.service.google;

import bookfronterab.model.Reservation;
import bookfronterab.model.Room;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Event;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GoogleCalendarServiceTest {

    @InjectMocks
    private GoogleCalendarService service;

    private final String ACCESS_TOKEN = "dummy-token";

    @Test
    @DisplayName("getCalendarClient debe devolver un cliente configurado")
    void getCalendarClient_ShouldReturnClient() {
        Calendar client = service.getCalendarClient(ACCESS_TOKEN);
        assertNotNull(client);
        assertEquals("BookFrontera Calendar", client.getApplicationName());
    }

    @Test
    @DisplayName("createEventForReservation debe llamar a insert y devolver ID")
    void createEvent_ShouldCallInsert() throws IOException {
        Reservation res = mockReservation();

        // Mocks de la cadena de Google
        Calendar calendarMock = mock(Calendar.class);
        Calendar.Events eventsMock = mock(Calendar.Events.class);
        Calendar.Events.Insert insertMock = mock(Calendar.Events.Insert.class);
        Event googleEventResponse = new Event().setId("generated-google-id");

        when(calendarMock.events()).thenReturn(eventsMock);
        when(eventsMock.insert(anyString(), any(Event.class))).thenReturn(insertMock);
        when(insertMock.execute()).thenReturn(googleEventResponse);

        // Mock del constructor de Calendar.Builder
        try (MockedConstruction<Calendar.Builder> ignored = mockConstruction(Calendar.Builder.class,
                (mock, context) -> {
                    when(mock.setApplicationName(anyString())).thenReturn(mock);
                    when(mock.build()).thenReturn(calendarMock);
                })) {

            String eventId = service.createEventForReservation(res, ACCESS_TOKEN);

            assertEquals("generated-google-id", eventId);
            verify(insertMock).execute();
        }
    }

    @Test
    @DisplayName("deleteEvent debe hacer return inmediato si el ID es nulo")
    void deleteEvent_ShouldReturnEarly_WhenIdNull() throws IOException {
        service.deleteEvent(null, ACCESS_TOKEN);
        service.deleteEvent("", ACCESS_TOKEN);
        // Si no lanza excepción y corre, pasó el test.
    }

    @Test
    @DisplayName("deleteEvent debe llamar a delete.execute() en caso de éxito")
    void deleteEvent_Success() throws IOException {
        Calendar calendarMock = mock(Calendar.class);
        Calendar.Events eventsMock = mock(Calendar.Events.class);
        Calendar.Events.Delete deleteMock = mock(Calendar.Events.Delete.class);

        when(calendarMock.events()).thenReturn(eventsMock);
        when(eventsMock.delete(anyString(), anyString())).thenReturn(deleteMock);

        // El mock devuelve null por defecto, lo cual es suficiente para simular éxito.

        try (MockedConstruction<Calendar.Builder> ignored = mockConstruction(Calendar.Builder.class,
                (mock, context) -> {
                    when(mock.setApplicationName(anyString())).thenReturn(mock);
                    when(mock.build()).thenReturn(calendarMock);
                })) {

            service.deleteEvent("evt-123", ACCESS_TOKEN);

            // Verificamos que se llamó al método execute()
            verify(deleteMock).execute();
        }
    }

    @Test
    @DisplayName("deleteEvent debe capturar excepción 404/410 sin explotar")
    void deleteEvent_ShouldHandle404() throws IOException {
        Calendar calendarMock = mock(Calendar.class);
        Calendar.Events eventsMock = mock(Calendar.Events.class);
        Calendar.Events.Delete deleteMock = mock(Calendar.Events.Delete.class);

        when(calendarMock.events()).thenReturn(eventsMock);
        when(eventsMock.delete(anyString(), anyString())).thenReturn(deleteMock);
        // Aquí usamos stubbing porque queremos forzar una excepción
        when(deleteMock.execute()).thenThrow(new IOException("Google Error 404 Not Found"));

        try (MockedConstruction<Calendar.Builder> ignored = mockConstruction(Calendar.Builder.class,
                (mock, context) -> {
                    when(mock.setApplicationName(anyString())).thenReturn(mock);
                    when(mock.build()).thenReturn(calendarMock);
                })) {

            assertDoesNotThrow(() -> service.deleteEvent("evt-id", ACCESS_TOKEN));
        }
    }

    @Test
    @DisplayName("updateEvent debe buscar el evento, modificarlo y guardarlo")
    void updateEvent_Success() throws IOException {
        Reservation res = mockReservation();
        String eventId = "google-evt-id";

        Calendar calendarMock = mock(Calendar.class);
        Calendar.Events eventsMock = mock(Calendar.Events.class);
        Calendar.Events.Get getMock = mock(Calendar.Events.Get.class);
        Calendar.Events.Update updateMock = mock(Calendar.Events.Update.class);

        // Evento existente que devuelve Google
        Event existingEvent = new Event();
        existingEvent.setSummary("Viejo Título");

        when(calendarMock.events()).thenReturn(eventsMock);

        // 1. Mockear el GET (recuperar evento)
        when(eventsMock.get(anyString(), eq(eventId))).thenReturn(getMock);
        when(getMock.execute()).thenReturn(existingEvent);

        // 2. Mockear el UPDATE (guardar cambios)
        when(eventsMock.update(anyString(), eq(eventId), any(Event.class))).thenReturn(updateMock);
        when(updateMock.execute()).thenReturn(existingEvent); // Devuelve el evento actualizado

        try (MockedConstruction<Calendar.Builder> ignored = mockConstruction(Calendar.Builder.class,
                (mock, context) -> {
                    when(mock.setApplicationName(anyString())).thenReturn(mock);
                    when(mock.build()).thenReturn(calendarMock);
                })) {

            service.updateEvent(eventId, res, ACCESS_TOKEN);

            // Verificar que se llamó a update
            verify(updateMock).execute();
            // Verificar que el evento se modificó antes de enviarse
            assertEquals("Reserva de Sala: Sala Test", existingEvent.getSummary());
        }
    }

    @Test
    @DisplayName("updateEvent debe ignorar si el ID es nulo")
    void updateEvent_ShouldIgnore_WhenIdNull() throws IOException {
        service.updateEvent(null, mockReservation(), ACCESS_TOKEN);
        // No debe intentar construir el cliente ni llamar a google
    }

    // Helper
    private Reservation mockReservation() {
        Room room = Room.builder().name("Sala Test").build();
        return Reservation.builder()
                .room(room)
                .startAt(ZonedDateTime.now(ZoneId.of("UTC")))
                .endAt(ZonedDateTime.now(ZoneId.of("UTC")).plusHours(1))
                .build();
    }
}