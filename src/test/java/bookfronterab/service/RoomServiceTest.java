package bookfronterab.service;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import bookfronterab.model.Room;
import bookfronterab.repo.OpeningHourRepository;
import bookfronterab.repo.RoomRepository;

@ExtendWith(MockitoExtension.class)
class RoomServiceTest {

    @Mock
    private RoomRepository roomRepo;
    @Mock
    private OpeningHourRepository openingRepo;
    @InjectMocks
    private RoomService roomService;

    @Test
    void list_devuelveTodasLasSalas() {
        List<Room> rooms = List.of(new Room(), new Room());
        when(roomRepo.findAll()).thenReturn(rooms);

        assertEquals(2, roomService.list().size());
        verify(roomRepo).findAll();
    }

    @Test
    void get_devuelveSalaExistente() {
        Room room = new Room();
        when(roomRepo.findById(1L)).thenReturn(Optional.of(room));

        Room result = roomService.get(1L);
        assertEquals(room, result);
    }

    @Test
    void get_lanzaExcepcionSiNoExiste() {
        when(roomRepo.findById(99L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> roomService.get(99L));
    }
}