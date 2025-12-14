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
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GoogleCalendarServiceTest {

    @InjectMocks
    private GoogleCalendarService service;

    @Test
    @DisplayName("getCalendarClient debe devolver un cliente configurado")
    void getCalendarClient_ShouldReturnClient() {
        Calendar client = service.getCalendarClient("dummy-token");
        assertNotNull(client);
        assertEquals("BookFrontera Calendar", client.getApplicationName());
    }

    @Test
    @DisplayName("createEventForReservation debe llamar a la API de Google")
    void createEvent_ShouldCallInsert() throws IOException {
        Reservation res = new Reservation();
        Room room = Room.builder().name("Sala 1").build();
        res.setRoom(room);
        res.setStartAt(ZonedDateTime.now());
        res.setEndAt(ZonedDateTime.now().plusHours(1));

        Calendar calendarMock = mock(Calendar.class);
        Calendar.Events eventsMock = mock(Calendar.Events.class);
        Calendar.Events.Insert insertMock = mock(Calendar.Events.Insert.class);
        Event googleEventResponse = new Event().setId("generated-google-id");

        when(calendarMock.events()).thenReturn(eventsMock);
        when(eventsMock.insert(anyString(), any(Event.class))).thenReturn(insertMock);
        when(insertMock.execute()).thenReturn(googleEventResponse);

        try (MockedConstruction<Calendar.Builder> ignored = mockConstruction(Calendar.Builder.class,
                (mock, context) -> {
                    // Configuramos el Builder para que devuelva nuestro calendarMock preparado
                    when(mock.setApplicationName(anyString())).thenReturn(mock);
                    when(mock.build()).thenReturn(calendarMock);
                })) {

            String eventId = service.createEventForReservation(res, "token");

            assertEquals("generated-google-id", eventId);
            verify(insertMock).execute();
        }
    }

    @Test
    @DisplayName("deleteEvent debe llamar a delete y manejar excepciones 404")
    void deleteEvent_ShouldHandle404() throws IOException {
        Calendar calendarMock = mock(Calendar.class);
        Calendar.Events eventsMock = mock(Calendar.Events.class);
        Calendar.Events.Delete deleteMock = mock(Calendar.Events.Delete.class);

        when(calendarMock.events()).thenReturn(eventsMock);
        when(eventsMock.delete(anyString(), anyString())).thenReturn(deleteMock);
        when(deleteMock.execute()).thenThrow(new IOException("Google Error 404 Not Found"));

        try (MockedConstruction<Calendar.Builder> ignored = mockConstruction(Calendar.Builder.class,
                (mock, context) -> {
                    when(mock.setApplicationName(anyString())).thenReturn(mock);
                    when(mock.build()).thenReturn(calendarMock);
                })) {

            assertDoesNotThrow(() -> service.deleteEvent("evt-id", "token"));

            verify(deleteMock).execute();
        }
    }
}