package bookfronterab.service;

import bookfronterab.dto.ReservationDto;
import bookfronterab.dto.RoomDto;
import bookfronterab.dto.UserDto;
import bookfronterab.model.Reservation;
import bookfronterab.model.Room;
import bookfronterab.model.User;
import bookfronterab.model.UserRole;
import bookfronterab.repo.ReservationRepository;
import bookfronterab.repo.RoomRepository;
import bookfronterab.repo.UserRepository;
import bookfronterab.service.google.GoogleCalendarService;
import bookfronterab.service.google.GoogleCredentialsService;
import com.google.api.client.auth.oauth2.Credential;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.time.DayOfWeek;
import java.time.temporal.TemporalAdjusters;


/**
 * Servicio para gestionar la lógica de negocio de las Reservas.
 * Se encarga de crear, consultar y cancelar reservas, validando la disponibilidad
 * y comunicándose con servicios externos como Google Calendar.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReservationService {

    /**
     * Repositorio para el acceso a datos de {@link Reservation}.
     */
    private final ReservationRepository reservationRepo;

    /**
     * Repositorio para el acceso a datos de {@link User}.
     */
    private final UserRepository userRepo;

    /**
     * Repositorio para el acceso a datos de {@link Room}.
     */
    private final RoomRepository roomRepo;

    /**
     * Servicio para interactuar con la API de Google Calendar.
     */
    private final GoogleCalendarService googleCalendarService;

    /**
     * Servicio para gestionar las credenciales de Google OAuth2.
     */
    private final GoogleCredentialsService googleCredentialsService;

    private final TimeService timeService;

    /**
     * Crea una nueva reserva, valida la disponibilidad y, opcionalmente,
     * la añade al Google Calendar del usuario.
     *
     * @param userEmail El email del usuario autenticado que realiza la reserva.
     * @param req       El DTO {@link ReservationDto.CreateRequest} con los datos de la reserva.
     * @throws IllegalArgumentException   Si las fechas son nulas, inválidas o la sala no existe.
     * @throws IllegalStateException      Si el usuario no se encuentra o la sala ya está reservada.
     */
    @Transactional
    public void create(String userEmail, ReservationDto.CreateRequest req) {

        //  Validación y búsqueda de User/Room
        validateReservationRequest(req);
        User user = userRepo.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalStateException("Usuario no encontrado: " + userEmail));
        Room room = roomRepo.findByIdWithLock(req.roomId()) // Usando el bloqueo pesimista
                .orElseThrow(() -> new IllegalArgumentException("Sala no encontrada: " + req.roomId()));

        // 3. Validar disponibilidad
        checkAvailability(req.roomId(), req.startAt(), req.endAt());
       //validar limite semanal (si no es admin verificamos si ya reservo esta semana)
        if (user.getRol() != UserRole.ADMIN) {
            validateUserWeeklyLimit(user, req.startAt());
        }

        // 4. Crear y guardar la reserva SIN EL ID DE GOOGLE
        Reservation reservation = Reservation.builder()
                .user(user)
                .room(room)
                .startAt(req.startAt())
                .endAt(req.endAt())
                .build();

        Reservation savedReservation = reservationRepo.save(reservation);
        log.info("Reserva {} creada (localmente) para usuario {}", savedReservation.getId(), userEmail);

        // 5. (Opcional) Sincronizar con Google Calendar
        if (req.addToGoogleCalendar()) {
            // ahora guardará el ID de Google en la reserva
            handleGoogleCalendarSync(user, savedReservation);
        } else {
            log.info("Usuario no solicitó añadir la reserva {} a Google Calendar. Omitiendo.", savedReservation.getId());
        }
    }
    @Transactional
    public void createOnBehalf(String userEmail, String othersEmail, ReservationDto.CreateRequest req){
        //  Validación y búsqueda de User/Room
        validateReservationRequest(req);
        Room room = roomRepo.findByIdWithLock(req.roomId()) // Usando el bloqueo pesimista
                .orElseThrow(() -> new IllegalArgumentException("Sala no encontrada: " + req.roomId()));
        User other = userRepo.findByEmail(othersEmail)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado: " + othersEmail));
        // 3. Validar disponibilidad
        checkAvailability(req.roomId(), req.startAt(), req.endAt());
        // 4. Crear y guardar la reserva SIN EL ID DE GOOGLE
        Reservation reservation = Reservation.builder()
                .user(other)
                .room(room)
                .startAt(req.startAt())
                .endAt(req.endAt())
                .build();

        Reservation savedReservation = reservationRepo.save(reservation);
        log.info("Reserva {} creada (localmente) por {} para usuario {}", savedReservation.getId(), userEmail,othersEmail);
        log.info("Las reserva {} es en nombre de otra persona y no se añade a google calendar", savedReservation.getId());
    }

    /**
     * ayuda para validar la lógica de negocio y disponibilidad.
     *
     * @param roomId  El ID de la sala.
     * @param startAt La fecha/hora de inicio.
     * @param endAt   La fecha/hora de fin.
     * @throws IllegalStateException Si se encuentran reservas conflictivas.
     */
    private void checkAvailability(Long roomId, ZonedDateTime startAt, ZonedDateTime endAt) {
        List<Reservation> conflictingReservations = reservationRepo.findConflictingReservations(
                roomId,
                startAt,
                endAt
        );

        if (!conflictingReservations.isEmpty()) {
            log.warn("Conflicto de reserva detectado para la sala {} en el horario {} a {}", roomId, startAt, endAt);
            throw new IllegalStateException("La sala ya está reservada en ese horario. Por favor, elige otro.");
        }
    }

    /**
     * Valida los datos de entrada de la petición de reserva.
     * Permite reservar "bloques actuales" siempre que la reserva no haya finalizado.
     *
     * @param req El DTO de creación con las fechas de inicio y fin.
     * @throws IllegalArgumentException Si las fechas son nulas, incoherentes o violan las reglas de negocio.
     */
    private void validateReservationRequest(ReservationDto.CreateRequest req) {
        if (req.startAt() == null || req.endAt() == null) {
            throw new IllegalArgumentException("Las fechas de inicio y fin no pueden ser nulas.");
        }

        if (!req.startAt().isBefore(req.endAt())) {
            throw new IllegalArgumentException("La fecha de inicio debe ser anterior a la fecha de fin.");
        }

        ZonedDateTime now = ZonedDateTime.now();

        if (req.endAt().isBefore(now)) {
            throw new IllegalArgumentException("No se pueden crear reservas para un horario que ya ha finalizado.");
        }

        // Validar duración mínima (Ej: 15 minutos)
        long durationMinutes = java.time.temporal.ChronoUnit.MINUTES.between(req.startAt(), req.endAt());
        if (durationMinutes < 15) {
            throw new IllegalArgumentException("La reserva es muy corta. La duración mínima es de 15 minutos.");
        }

        // Validar duración máxima (Ej: 1 hora)
        if (durationMinutes > 60) {
            throw new IllegalArgumentException("La reserva excede el tiempo permitido (máximo 1 hora).");
        }

        // 5. Validar antelación máxima 3 meses
        if (req.startAt().isAfter(now.plusMonths(3))) {
            throw new IllegalArgumentException("No se pueden realizar reservas con más de 3 meses de antelación.");
        }
    }

    /**
     * ayuda para sincronizar la reserva con Google Calendar.
     * AHORA también guarda el ID del evento de Google en la reserva.
     *
     * @param user             El usuario dueño de la reserva.
     * @param savedReservation La reserva que acaba de ser guardada.
     */
    private void handleGoogleCalendarSync(User user, Reservation savedReservation) {
        try {
            log.info("Intentando registrar reserva {} en Google Calendar para {}", savedReservation.getId(), user.getEmail());
            Credential credential = googleCredentialsService.getCredential(user);

            // 1. Creamos el evento y CAPTURAMOS el ID
            String googleEventId = googleCalendarService.createEventForReservation(savedReservation, credential.getAccessToken());

            // 2. Guardamos el ID en nuestra reserva local
            savedReservation.setGoogleEventId(googleEventId);
            reservationRepo.save(savedReservation); // Re-guardamos para persistir el ID

            log.info("Reserva {} registrada en Google Calendar con ID: {}", savedReservation.getId(), googleEventId);

        } catch (IOException e) {
            // No fallamos la reserva si Google Calendar falla.
            log.error("No se pudo crear el evento de Google Calendar para la reserva {}: {}", savedReservation.getId(), e.getMessage());
        }
    }
    /**
     * (NUEVO) Valida 1 reserva por semana laboral (Lunes-Viernes). (jose)
     */
    private void validateUserWeeklyLimit(User user, ZonedDateTime reservationDate) {
        // Inicio de semana: Lunes 00:00
        ZonedDateTime startOfWeek = reservationDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .withHour(0).withMinute(0).withSecond(0).withNano(0);

        // Fin de semana laboral: VIERNES 23:59
        ZonedDateTime endOfWeek = reservationDate.with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY))
                .withHour(23).withMinute(59).withSecond(59).withNano(999999999);

        long count = reservationRepo.countByUserEmailAndStartAtBetween(
                user.getEmail(), startOfWeek, endOfWeek
        );

        if (count >= 1) {
            log.warn("Bloqueo: Usuario {} ya tiene reserva entre el lunes {} y viernes {}.",
                    user.getEmail(), startOfWeek.toLocalDate(), endOfWeek.toLocalDate());
            throw new IllegalStateException("Límite alcanzado: Solo puedes realizar 1 reserva por semana laboral (Lun-Vie).");
        }
    }

    /**
     * Obtiene todas las reservas de un usuario y las clasifica en
     * actual, futuras y pasadas.
     *
     * @param userEmail El email del usuario autenticado.
     * @return Un DTO con las listas de reservas clasificadas.
     */

    @Transactional(readOnly = true)
    public List<ReservationDto.Detail> getReservationsByRoom(Long roomId, String userEmail) {
        User user = userRepo.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalStateException("Usuario no encontrado"));

        if (user.getRol() != UserRole.ADMIN) {
            throw new SecurityException("Acceso denegado.");
        }

        List<Reservation> reservations = reservationRepo.findByRoomIdOrderByStartAtAsc(roomId);

        return reservations.stream()
                .map(this::mapToDetailDto)
                .toList();
    }
    @Transactional(readOnly = true)
    public ReservationDto.MyReservationsResponse getMyReservations(String userEmail) {
        log.info("Buscando todas las reservas para el usuario: {}", userEmail);

        // Obtenemos la hora actual en la zona horaria de la app
        ZonedDateTime now = timeService.nowOffset().toZonedDateTime();

        // 1. Obtenemos todas las reservas del usuario desde la BD
        List<Reservation> allReservations = reservationRepo.findByUserEmailOrderByStartAtAsc(userEmail);

        ReservationDto.Detail currentReservation = null;
        List<ReservationDto.Detail> futureReservations = new ArrayList<>();
        List<ReservationDto.Detail> pastReservations = new ArrayList<>();

        // 3. Clasificamos cada reserva
        for (Reservation res : allReservations) {
            ReservationDto.Detail detailDto = mapToDetailDto(res);

            if (res.getStartAt().isAfter(now)) {
                // Si la reserva aún no empieza = FUTURA
                futureReservations.add(detailDto);
            } else if (res.getEndAt().isBefore(now)) {
                // Si la reserva ya terminó = PASADA
                pastReservations.add(detailDto);
            } else {
                // Si no es futura ni pasada, está ocurriendo AHORA
                currentReservation = detailDto;
            }
        }

        log.info("Usuario {} tiene {} reservas futuras, {} pasadas y {} actual.",
                userEmail, futureReservations.size(), pastReservations.size(), (currentReservation != null ? 1 : 0));

        // 4. Devolvemos el DTO de respuesta
        return new ReservationDto.MyReservationsResponse(
                currentReservation,
                futureReservations,
                pastReservations
        );
    }

    /**
     * Obtiene los detalles de una reserva específica por su ID.
     *
     * @param id El ID de la reserva a buscar.
     * @return Un DTO {@link ReservationDto.Detail} con la información completa.
     * @throws IllegalArgumentException Si no se encuentra una reserva con ese ID.
     */
    @Transactional(readOnly = true) // optimizar consultas de solo lectura
    public ReservationDto.Detail getById(Long id) {
        log.info("Buscando reserva con ID: {}", id);

        // 1. Buscar reserva por ID.
        // 2. Si no existe, lanzar NotFoundException (manejada por GlobalExceptionHandler).
        Reservation reservation = reservationRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Reserva no encontrada con ID: " + id));

        // 3. Mapear la entidad Reservation a un ReservationDto.Detail y devolverlo.
        return mapToDetailDto(reservation);
    }


    /**
     * Cancela una reserva existente, verificando los permisos del usuario.
     * <p>
     * La lógica de permisos es:
     * 1. El usuario que solicita la cancelación debe ser el dueño de la reserva.
     * 2. O, el usuario que solicita la cancelación es un Administrador (basado en el flag `isAdmin`).
     * <p>
     * Si no se cumple ninguna, se lanza una {@link SecurityException}.
     *
     * @param id        El ID de la reserva a cancelar.
     * @param userEmail El email del usuario autenticado que solicita la cancelación
     * @throws IllegalArgumentException Si la reserva no se encuentra.
     * @throws SecurityException        Si el usuario no tiene permisos para cancelar esta reserva.
     */
    @Transactional
    public void cancel(Long id, String userEmail) {
        log.info("Intento de cancelación para reserva ID: {} por usuario: {}", id, userEmail);

        // 1. Buscar la reserva por 'id'.
        Reservation reservation = reservationRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Reserva no encontrada con ID: " + id));
        //Para que el servicio ya no pregunte si es admin y lo averigue por el solo
        User requestor = userRepo.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalStateException("Usuario no encontrado"));
        // 2. Verificar permisos.
        boolean isOwner = reservation.getUser().getEmail().equals(userEmail);
        boolean isAdmin = requestor.getRol() == UserRole.ADMIN;
        if (!isOwner && !isAdmin) {
            log.warn("¡Acceso denegado! Usuario {} intentó cancelar la reserva {} (Dueño: {}) sin permisos.",
                    userEmail, id, reservation.getUser().getEmail());
            throw new SecurityException("No tienes permiso para cancelar esta reserva. Solo el dueño o un administrador pueden hacerlo.");
        }

        // 3. Sincronizar con Google Calendar para borrar el evento.
        // Verificamos si nuestra reserva tiene un ID de Google guardado.
        if (reservation.getGoogleEventId() != null) {
            log.info("La reserva {} tiene un evento de Google Calendar ({}). Intentando borrar...", id, reservation.getGoogleEventId());
            try {
                // Obtenemos las credenciales del usuario QUE HIZO la reserva
                User owner = reservation.getUser();
                Credential credential = googleCredentialsService.getCredential(owner);

                // Llamamos al servicio de borrado
                googleCalendarService.deleteEvent(reservation.getGoogleEventId(), credential.getAccessToken());

            } catch (IOException e) {
                // Si falla la API de Google, solo lo logueamos.
                // NO detenemos la cancelación local.
                log.error("No se pudo borrar el evento de Google Calendar ({}). La reserva local se borrará de todos modos. Error: {}",
                        reservation.getGoogleEventId(), e.getMessage());
            }
        }

        // 4. Borrar la reserva de la base de datos local.
        reservationRepo.delete(reservation);

        log.info("Reserva {} cancelada exitosamente por {}.", id, (isAdmin && !isOwner) ? "Admin " + userEmail : userEmail);
    }

    /**
     * Modifica una reserva existente (Sala, Fecha u Hora).
     * Maneja validaciones de propiedad, disponibilidad y sincronización con Google.
     */
    @Transactional
    public ReservationDto.Detail modify(Long reservationId, String userEmail, ReservationDto.CreateRequest request) {
        log.info("Modificando reserva ID: {} solicitada por {}", reservationId, userEmail);

        //  Buscar la reserva original
        Reservation reservation = reservationRepo.findById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("La reserva no existe."));

        //  Verificar Permisos (Dueño o Admin)
        User requestor = userRepo.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalStateException("Usuario solicitante no encontrado."));

        boolean isOwner = reservation.getUser().getEmail().equals(userEmail);
        boolean isAdmin = requestor.getRol() == UserRole.ADMIN;

        if (!isOwner && !isAdmin) {
            throw new SecurityException("No tienes permiso para modificar esta reserva.");
        }

        //  Validar las nuevas fechas (Reglas de Negocio: futuro, duración, etc.)
        validateReservationRequest(request);

        //  Validar Disponibilidad (Excluyendo la reserva actual para evitar auto-conflicto)
        checkAvailabilityForModification(request.roomId(), request.startAt(), request.endAt(), reservationId);

        // Validar Límite Semanal (Solo si es Estudiante)
        // Usamos la validación especial que excluye la reserva actual
        if (!isAdmin) {
            validateUserWeeklyLimitForModification(reservation.getUser(), request.startAt(), reservationId);
        }

        //  Actualizar Datos Locales
        Room newRoom = roomRepo.findById(request.roomId())
                .orElseThrow(() -> new IllegalArgumentException("La nueva sala seleccionada no existe."));

        reservation.setRoom(newRoom);
        reservation.setStartAt(request.startAt());
        reservation.setEndAt(request.endAt());
        // El estado o auditoría podría actualizarse aquí si tuvieras esa lógica

        Reservation updatedReservation = reservationRepo.save(reservation);

        //  Sincronizar con Google Calendar (Si aplica)
        if (reservation.getGoogleEventId() != null) {
            try {
                // Usamos las credenciales del DUEÑO de la reserva para actualizar SU calendario
                Credential credential = googleCredentialsService.getCredential(reservation.getUser());

                googleCalendarService.updateEvent(
                        reservation.getGoogleEventId(),
                        updatedReservation,
                        credential.getAccessToken()
                );
            } catch (Exception e) {
                log.error("Error actualizando Google Calendar para reserva {}: {}", reservationId, e.getMessage());
                // No lanzamos error fatal para no revertir el cambio local, pero queda registrado.
            }
        }

        log.info("Reserva {} modificada con éxito.", reservationId);
        return mapToDetailDto(updatedReservation);
    }

    /**
     *  Valida límite semanal excluyendo la reserva que se está modificando.
     */
    private void validateUserWeeklyLimitForModification(User user, ZonedDateTime reservationDate, Long excludedReservationId) {
        // Inicio de semana: Lunes 00:00
        ZonedDateTime startOfWeek = reservationDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .withHour(0).withMinute(0).withSecond(0).withNano(0);

        // Fin de semana laboral: VIERNES 23:59
        ZonedDateTime endOfWeek = reservationDate.with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY))
                .withHour(23).withMinute(59).withSecond(59).withNano(999999999);

        long count = reservationRepo.countByUserEmailAndStartAtBetweenAndIdNot(
                user.getEmail(), startOfWeek, endOfWeek, excludedReservationId
        );

        if (count >= 1) {
            log.warn("Bloqueo modificación: Usuario {} ya tiene otra reserva en la semana del {} (excluyendo la actual).",
                    user.getEmail(), startOfWeek.toLocalDate());
            throw new IllegalStateException("Límite alcanzado: Ya tienes otra reserva distinta en esa semana.");
        }
    }


    /**
     * auxiliar específico para modificaciones.
     * Verifica disponibilidad ignorando la reserva que se está editando.
     */
    private void checkAvailabilityForModification(Long roomId, ZonedDateTime start, ZonedDateTime end, Long excludeReservationId) {
        // Obtenemos TODAS las reservas conflictivas en ese horario y sala
        List<Reservation> conflicts = reservationRepo.findConflictingReservations(roomId, start, end);

        // Filtramos la lista para quitar la reserva actual (si aparece)
        boolean hasRealConflict = conflicts.stream()
                .anyMatch(res -> !res.getId().equals(excludeReservationId));

        if (hasRealConflict) {
            throw new IllegalStateException("La sala ya está ocupada en el nuevo horario seleccionado.");
        }
    }

    /**
     * Convierte una entidad {@link Reservation} a su DTO de detalle.
     *
     * @param reservation La entidad a convertir.
     * @return El DTO {@link ReservationDto.Detail}.
     */
    private ReservationDto.Detail mapToDetailDto(Reservation reservation) {
        return new ReservationDto.Detail(
                reservation.getId(),
                reservation.getStartAt(),
                reservation.getEndAt(),
                mapRoomToDto(reservation.getRoom()),
                mapUserToDto(reservation.getUser())
        );
    }

    /**
     * Convierte una entidad {@link Room} a su DTO.
     *
     * @param room La entidad a convertir.
     * @return El DTO {@link RoomDto}.
     */
    private RoomDto mapRoomToDto(Room room) {
        return RoomDto.builder()
                .id(room.getId())
                .name(room.getName())
                .capacity(room.getCapacity())
                .equipment(room.getEquipment())
                .floor(room.getFloor())
                .imageUrl(room.getImageUrl())
                .build();
    }

    /**
     * Convierte una entidad {@link User} a su DTO.
     *
     * @param user La entidad a convertir.
     * @return El DTO {@link UserDto}.
     */
    private UserDto mapUserToDto(User user) {
        return UserDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .nombre(user.getNombre())
                .rol(user.getRol())
                .build();
    }

}