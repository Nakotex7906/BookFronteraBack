package bookfronterab.service;

import bookfronterab.service.google.GoogleCalendarService;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock ReservationRepository repo;
    @Mock RoomRepository roomRepo;
    @Mock OpeningHourRepository openingRepo;
    @Mock BlackoutRepository blackoutRepo;
    @Mock UserRepository userRepo;
    @Mock GoogleCalendarService googleCalendarService;
    @Mock GoogleCredentialsService googleCredentialsService;
    @Mock TimeService time;

    @InjectMocks ReservationService service;

    User user;
    Room room;
    OffsetDateTime start;
    OffsetDateTime end;
    OpeningHour openingHour;

    @BeforeEach
    void setup() {
        user = new User(); user.setId(1L); user.setEmail("test@mail.com");
        room = new Room(); room.setId(1L); room.setActive(true);
        start = OffsetDateTime.parse("2025-10-21T10:00:00Z");
        end = OffsetDateTime.parse("2025-10-21T11:00:00Z");
        openingHour = new OpeningHour();
        openingHour.setOpenTime(LocalTime.of(9, 0));
        openingHour.setCloseTime(LocalTime.of(18, 0));

        when(time.zone()).thenReturn(ZoneId.of("UTC"));
        when(time.atOffset(any(), any())).thenAnswer(i -> OffsetDateTime.of(i.getArgument(0), i.getArgument(1), ZoneOffset.UTC));
        when(time.nowOffset()).thenReturn(OffsetDateTime.now());
    }

    @Test
    void createReservation_success() {
        when(userRepo.findById(1L)).thenReturn(Optional.of(user));
        when(roomRepo.findById(1L)).thenReturn(Optional.of(room));
        when(openingRepo.findByRoomAndWeekday(any(), any())).thenReturn(Optional.of(openingHour));
        when(blackoutRepo.findByRoomIdAndEndAtAfterAndStartAtBefore(any(), any(), any())).thenReturn(List.of());
        when(repo.overlaps(any(), any(), any(), any())).thenReturn(List.of());
        when(repo.findByUserIdAndEndAtAfterOrderByStartAtAsc(any(), any())).thenReturn(List.of());
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));

        Reservation res = service.create(1L, 1L, start, end);

        assertEquals(ReservationStatus.CONFIRMED, res.getEstado());
        verify(repo).save(any(Reservation.class));
    }
}
