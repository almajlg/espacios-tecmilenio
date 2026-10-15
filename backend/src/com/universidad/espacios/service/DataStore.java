package com.universidad.espacios.service;

import com.universidad.espacios.model.Espacio;
import com.universidad.espacios.model.Reserva;
import com.universidad.espacios.model.Usuario;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class DataStore {
    private static final String STORAGE_FILE = "campus_espacios_data.json";

    private final Map<Integer, Usuario> usuarios = new ConcurrentHashMap<>();
    private final Map<Integer, Espacio> espacios = new ConcurrentHashMap<>();
    private final Map<Integer, Reserva> reservas = new ConcurrentHashMap<>();

    private final AtomicInteger nextUsuarioId = new AtomicInteger(1);
    private final AtomicInteger nextEspacioId = new AtomicInteger(1);
    private final AtomicInteger nextReservaId = new AtomicInteger(1);

    public DataStore() {
        if (!loadFromFile()) {
            initSeedData();
            saveToFile();
        }
    }

    // ================================================================
    // GESTIÓN DE USUARIOS
    // ================================================================
    public List<Usuario> getUsuarios() {
        List<Usuario> list = new ArrayList<>(usuarios.values());
        list.sort(Comparator.comparing(Usuario::getNombreCompleto));
        return list;
    }

    public Usuario getUsuario(int id) {
        return usuarios.get(id);
    }

    public synchronized Usuario addUsuario(String nombreCompleto, String correo, String rol) {
        int id = nextUsuarioId.getAndIncrement();
        Usuario u = new Usuario(id, nombreCompleto, correo, rol != null ? rol : "Profesor");
        usuarios.put(id, u);
        saveToFile();
        return u;
    }

    public static boolean puedeAgendarOModificar(Usuario u) {
        if (u == null) return false;
        String rol = u.getRol() == null ? "" : u.getRol().toLowerCase().trim();
        return rol.contains("coordinador") || rol.contains("mentor") || rol.contains("admin");
    }

    // ================================================================
    // GESTIÓN DE ESPACIOS
    // ================================================================
    public List<Espacio> getEspacios() {
        List<Espacio> list = new ArrayList<>(espacios.values());
        list.sort(Comparator.comparing(Espacio::getNombreEspacio));
        return list;
    }

    public Espacio getEspacio(int id) {
        return espacios.get(id);
    }

    public synchronized Espacio addEspacio(String nombre, String tipo, int capacidad, boolean activo) {
        int id = nextEspacioId.getAndIncrement();
        Espacio e = new Espacio(id, nombre, tipo, capacidad, activo);
        espacios.put(id, e);
        saveToFile();
        return e;
    }

    public synchronized Espacio updateEspacio(int id, String nombre, String tipo, int capacidad, boolean activo) {
        Espacio e = espacios.get(id);
        if (e != null) {
            e.setNombreEspacio(nombre);
            e.setTipoEspacio(tipo);
            e.setCapacidad(capacidad);
            e.setActivo(activo);
            saveToFile();
        }
        return e;
    }

    public synchronized boolean deleteEspacio(int id) {
        Espacio removed = espacios.remove(id);
        if (removed != null) {
            // Eliminar reservas asociadas
            reservas.values().removeIf(r -> r.getIdEspacio() == id);
            saveToFile();
            return true;
        }
        return false;
    }

    // ================================================================
    // GESTIÓN DE RESERVAS Y DETECCIÓN DE CONFLICTOS
    // ================================================================
    public List<Reserva> getReservas() {
        List<Reserva> list = new ArrayList<>();
        for (Reserva r : reservas.values()) {
            enrichReserva(r);
            list.add(r);
        }
        // Ordenar por fecha y hora
        list.sort((a, b) -> {
            int cmp = b.getFechaReserva().compareTo(a.getFechaReserva());
            if (cmp != 0) return cmp;
            return a.getHoraInicio().compareTo(b.getHoraInicio());
        });
        return list;
    }

    public Reserva getReserva(int id) {
        Reserva r = reservas.get(id);
        if (r != null) enrichReserva(r);
        return r;
    }

    /**
     * Revisa si existe conflicto de traslape para el espacio y fecha indicada
     */
    public Reserva checkConflict(int idEspacio, String fecha, String horaInicio, String horaFin, int excludeId) {
        int startMin = timeToMinutes(horaInicio);
        int endMin = timeToMinutes(horaFin);

        if (startMin >= endMin) {
            return null; // El validador capturará hora de inicio >= hora fin
        }

        // Restricción institucional: Mezanine ocupado de Lunes a Viernes de 07:00 a 15:00 (3:00 PM)
        Espacio espacioObj = espacios.get(idEspacio);
        if (isMezanineCubiculo(espacioObj)) {
            try {
                LocalDate ld = LocalDate.parse(fecha);
                DayOfWeek dow = ld.getDayOfWeek();
                if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY) {
                    if (startMin < 15 * 60 && endMin > 7 * 60) {
                        Reserva mezanineLock = new Reserva(-1, 0, idEspacio, fecha, "07:00", "15:00",
                            "Ocupación fija de Mezanine (07:00 a 15:00)", "Activa",
                            "Todos los cubículos de Mezanine están ocupados de lunes a viernes de 7:00 a 15:00. Disponibles a partir de las 3:00 PM.");
                        enrichReserva(mezanineLock);
                        return mezanineLock;
                    }
                }
            } catch (Exception ignored) {}
        }

        for (Reserva r : reservas.values()) {
            if (r.getIdReserva() == excludeId) continue;
            if (r.getIdEspacio() != idEspacio) continue;
            if (!r.getFechaReserva().equals(fecha)) continue;
            if (!"Activa".equalsIgnoreCase(r.getEstadoReserva())) continue;

            int rStart = timeToMinutes(r.getHoraInicio());
            int rEnd = timeToMinutes(r.getHoraFin());

            // Regla matemática de traslape: (StartA < EndB) y (EndA > StartB)
            if (startMin < rEnd && endMin > rStart) {
                enrichReserva(r);
                return r; // Hay conflicto con esta reserva
            }
        }
        return null;
    }

    public synchronized Reserva addReserva(int idUsuario, int idEspacio, String fecha, 
                                          String horaInicio, String horaFin, 
                                          String motivo, String observaciones, 
                                          boolean servicioMedico, boolean servicioTI, boolean servicioPlantaFisica,
                                          String archivoLayout, String archivoLayoutNombre) throws Exception {
        Usuario u = usuarios.get(idUsuario);
        if (u == null) throw new IllegalArgumentException("Usuario no encontrado (ID: " + idUsuario + ")");
        if (!puedeAgendarOModificar(u)) {
            throw new IllegalStateException("El permiso de agendar un nuevo evento sólo lo tienen los coordinadores y mentores. Los demás pueden solo visualizar.");
        }

        Espacio e = espacios.get(idEspacio);
        if (e == null) throw new IllegalArgumentException("Espacio no encontrado (ID: " + idEspacio + ")");
        if (!e.isActivo()) throw new IllegalStateException("El espacio '" + e.getNombreEspacio() + "' se encuentra inactivo.");

        int startMin = timeToMinutes(horaInicio);
        int endMin = timeToMinutes(horaFin);
        if (startMin >= endMin) {
            throw new IllegalArgumentException("La hora de inicio (" + horaInicio + ") debe ser anterior a la hora de fin (" + horaFin + ").");
        }

        validateMezanineSchedule(e, fecha, startMin, endMin);

        Reserva conflicto = checkConflict(idEspacio, fecha, horaInicio, horaFin, -1);
        if (conflicto != null) {
            throw new IllegalStateException("Conflicto de horario: El espacio '" + e.getNombreEspacio() + 
                "' ya está reservado de " + conflicto.getHoraInicio() + " a " + conflicto.getHoraFin() + 
                " por " + conflicto.getNombreUsuario() + " para: '" + conflicto.getMotivoEvento() + "'.");
        }

        int id = nextReservaId.getAndIncrement();
        Reserva r = new Reserva(id, idUsuario, idEspacio, fecha, horaInicio, horaFin, motivo, "Activa", observaciones);
        r.setServicioMedico(servicioMedico);
        r.setServicioTI(servicioTI);
        r.setServicioPlantaFisica(servicioPlantaFisica);
        r.setArchivoLayout(archivoLayout);
        r.setArchivoLayoutNombre(archivoLayoutNombre);
        enrichReserva(r);
        reservas.put(id, r);
        saveToFile();
        return r;
    }

    public synchronized Reserva addReserva(int idUsuario, int idEspacio, String fecha, 
                                          String horaInicio, String horaFin, 
                                          String motivo, String observaciones, 
                                          boolean servicioMedico, boolean servicioTI) throws Exception {
        return addReserva(idUsuario, idEspacio, fecha, horaInicio, horaFin, motivo, observaciones, servicioMedico, servicioTI, false, null, null);
    }

    public synchronized Reserva addReserva(int idUsuario, int idEspacio, String fecha, 
                                          String horaInicio, String horaFin, 
                                          String motivo, String observaciones, boolean servicioMedico) throws Exception {
        boolean ti = observaciones != null && (observaciones.toLowerCase().contains("servicio ti") || 
                                               observaciones.toLowerCase().contains("soporte ti") || 
                                               observaciones.toLowerCase().contains("[ti]"));
        boolean pf = observaciones != null && (observaciones.toLowerCase().contains("planta física") || 
                                               observaciones.toLowerCase().contains("planta fisica") || 
                                               observaciones.toLowerCase().contains("mantenimiento") || 
                                               observaciones.toLowerCase().contains("mobiliario") ||
                                               observaciones.toLowerCase().contains("[pf]"));
        return addReserva(idUsuario, idEspacio, fecha, horaInicio, horaFin, motivo, observaciones, servicioMedico, ti, pf, null, null);
    }

    public synchronized Reserva addReserva(int idUsuario, int idEspacio, String fecha, 
                                          String horaInicio, String horaFin, 
                                          String motivo, String observaciones) throws Exception {
        boolean med = observaciones != null && (observaciones.toLowerCase().contains("servicio médico") || 
                                               observaciones.toLowerCase().contains("servicio medico") ||
                                               observaciones.toLowerCase().contains("tecmi care") ||
                                               observaciones.toLowerCase().contains("tecmicare"));
        boolean ti = observaciones != null && (observaciones.toLowerCase().contains("servicio ti") || 
                                               observaciones.toLowerCase().contains("soporte ti") || 
                                               observaciones.toLowerCase().contains("[ti]"));
        boolean pf = observaciones != null && (observaciones.toLowerCase().contains("planta física") || 
                                               observaciones.toLowerCase().contains("planta fisica") || 
                                               observaciones.toLowerCase().contains("mantenimiento") || 
                                               observaciones.toLowerCase().contains("mobiliario") ||
                                               observaciones.toLowerCase().contains("[pf]"));
        return addReserva(idUsuario, idEspacio, fecha, horaInicio, horaFin, motivo, observaciones, med, ti, pf, null, null);
    }

    public synchronized Reserva updateEstadoReserva(int id, String nuevoEstado) {
        Reserva r = reservas.get(id);
        if (r != null) {
            r.setEstadoReserva(nuevoEstado);
            enrichReserva(r);
            saveToFile();
        }
        return r;
    }

    public synchronized Reserva updateReserva(int id, int idUsuario, int idEspacio, String fecha, 
                                              String horaInicio, String horaFin, 
                                              String motivo, String estado, String observaciones) throws Exception {
        Reserva existing = reservas.get(id);
        boolean med = existing != null ? existing.isServicioMedico() : false;
        boolean ti = existing != null ? existing.isServicioTI() : false;
        boolean pf = existing != null ? existing.isServicioPlantaFisica() : false;
        return updateReserva(id, idUsuario, idEspacio, fecha, horaInicio, horaFin, motivo, estado, observaciones, med, ti, pf);
    }

    public synchronized Reserva updateReserva(int id, int idUsuario, int idEspacio, String fecha, 
                                              String horaInicio, String horaFin, 
                                              String motivo, String estado, String observaciones,
                                              boolean servicioMedico, boolean servicioTI) throws Exception {
        Reserva existing = reservas.get(id);
        boolean pf = existing != null ? existing.isServicioPlantaFisica() : false;
        String lay = existing != null ? existing.getArchivoLayout() : null;
        String layNom = existing != null ? existing.getArchivoLayoutNombre() : null;
        return updateReserva(id, idUsuario, idEspacio, fecha, horaInicio, horaFin, motivo, estado, observaciones, servicioMedico, servicioTI, pf, lay, layNom);
    }

    public synchronized Reserva updateReserva(int id, int idUsuario, int idEspacio, String fecha, 
                                              String horaInicio, String horaFin, 
                                              String motivo, String estado, String observaciones,
                                              boolean servicioMedico, boolean servicioTI, boolean servicioPlantaFisica) throws Exception {
        Reserva existing = reservas.get(id);
        String lay = existing != null ? existing.getArchivoLayout() : null;
        String layNom = existing != null ? existing.getArchivoLayoutNombre() : null;
        return updateReserva(id, idUsuario, idEspacio, fecha, horaInicio, horaFin, motivo, estado, observaciones, servicioMedico, servicioTI, servicioPlantaFisica, lay, layNom);
    }

    public synchronized Reserva updateReserva(int id, int idUsuario, int idEspacio, String fecha, 
                                              String horaInicio, String horaFin, 
                                              String motivo, String estado, String observaciones,
                                              boolean servicioMedico, boolean servicioTI, boolean servicioPlantaFisica,
                                              String archivoLayout, String archivoLayoutNombre) throws Exception {
        Reserva r = reservas.get(id);
        if (r == null) throw new IllegalArgumentException("Reserva no encontrada (ID: " + id + ")");

        Usuario u = usuarios.get(idUsuario);
        if (u == null) throw new IllegalArgumentException("Usuario no encontrado (ID: " + idUsuario + ")");
        if (!puedeAgendarOModificar(u)) {
            throw new IllegalStateException("El permiso de agendar y modificar eventos sólo lo tienen los coordinadores y mentores. Los demás pueden solo visualizar.");
        }

        Espacio e = espacios.get(idEspacio);
        if (e == null) throw new IllegalArgumentException("Espacio no encontrado (ID: " + idEspacio + ")");

        int startMin = timeToMinutes(horaInicio);
        int endMin = timeToMinutes(horaFin);
        if (startMin >= endMin) {
            throw new IllegalArgumentException("La hora de inicio (" + horaInicio + ") debe ser anterior a la hora de fin (" + horaFin + ").");
        }

        if (estado == null || estado.isEmpty()) {
            estado = r.getEstadoReserva();
        }

        if ("Activa".equalsIgnoreCase(estado)) {
            validateMezanineSchedule(e, fecha, startMin, endMin);
            Reserva conflicto = checkConflict(idEspacio, fecha, horaInicio, horaFin, id);
            if (conflicto != null) {
                throw new IllegalStateException("Conflicto de horario: El espacio '" + e.getNombreEspacio() + 
                    "' ya está reservado de " + conflicto.getHoraInicio() + " a " + conflicto.getHoraFin() + 
                    " por " + conflicto.getNombreUsuario() + " para: '" + conflicto.getMotivoEvento() + "'.");
            }
        }

        r.setIdUsuario(idUsuario);
        r.setIdEspacio(idEspacio);
        r.setFechaReserva(fecha);
        r.setHoraInicio(horaInicio);
        r.setHoraFin(horaFin);
        r.setMotivoEvento(motivo);
        r.setEstadoReserva(estado);
        r.setObservaciones(observaciones);
        r.setServicioMedico(servicioMedico);
        r.setServicioTI(servicioTI);
        r.setServicioPlantaFisica(servicioPlantaFisica);
        r.setArchivoLayout(archivoLayout);
        r.setArchivoLayoutNombre(archivoLayoutNombre);
        enrichReserva(r);
        saveToFile();
        return r;
    }

    public synchronized boolean deleteReserva(int id) {
        boolean removed = reservas.remove(id) != null;
        if (removed) saveToFile();
        return removed;
    }

    private void enrichReserva(Reserva r) {
        Usuario u = usuarios.get(r.getIdUsuario());
        if (u != null) {
            r.setNombreUsuario(u.getNombreCompleto());
            r.setCorreoUsuario(u.getCorreo());
            r.setRolUsuario(u.getRol());
        }
        Espacio e = espacios.get(r.getIdEspacio());
        if (e != null) {
            r.setNombreEspacio(e.getNombreEspacio());
            r.setTipoEspacio(e.getTipoEspacio());
            r.setCapacidadEspacio(e.getCapacidad());
            r.setEdificioEspacio(e.getEdificio());
        }
    }

    public static int timeToMinutes(String time) {
        if (time == null) return 0;
        String[] parts = time.trim().split(":");
        int h = Integer.parseInt(parts[0]);
        int m = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
        return h * 60 + m;
    }

    public static boolean isMezanineCubiculo(Espacio e) {
        if (e == null || e.getNombreEspacio() == null) return false;
        return e.getNombreEspacio().toLowerCase().contains("mezanine");
    }

    public static void validateMezanineSchedule(Espacio e, String fecha, int startMin, int endMin) {
        if (isMezanineCubiculo(e)) {
            try {
                LocalDate ld = LocalDate.parse(fecha);
                DayOfWeek dow = ld.getDayOfWeek();
                if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY) {
                    if (startMin < 15 * 60 && endMin > 7 * 60) {
                        throw new IllegalStateException("Restricción Mezanine: Todos los cubículos de Mezanine están ocupados de lunes a viernes de 07:00 a 15:00 (3:00 PM). Únicamente se puede agendar a partir de las 3:00 PM.");
                    }
                }
            } catch (IllegalStateException ise) {
                throw ise;
            } catch (Exception ignored) {}
        }
    }

    // ================================================================
    // SEMILLAS DE DATOS INICIALES (BASADAS EN EXCEL Y CALENDARIO)
    // ================================================================
    private void initSeedData() {
        // Usuarios iniciales
        addUsuario("Diana Angelica Barrios Hernández", "diana.barrios@universidad.edu.mx", "Administrador");
        addUsuario("Natalie Luna", "natalie.luna@universidad.edu.mx", "Responsable");
        addUsuario("Idania Lopez", "idania.lopez@universidad.edu.mx", "Responsable");
        addUsuario("Jonathan Salazar", "jonathan.salazar@universidad.edu.mx", "Profesor");
        addUsuario("Lilia Tapia", "lilia.tapia@universidad.edu.mx", "Responsable");
        addUsuario("Jibsam Peñaloza", "jibsam.penaloza@universidad.edu.mx", "Responsable");
        addUsuario("Luis Manuel Arenas Merchant", "luis.arenas@universidad.edu.mx", "Profesor");
        addUsuario("Raymundo Jacinto", "raymundo.jacinto@universidad.edu.mx", "Responsable");
        addUsuario("Silvia Reyes León", "silvia.reyes@universidad.edu.mx", "Profesor");
        addUsuario("Julio Moreno", "julio.moreno@universidad.edu.mx", "Responsable");
        addUsuario("Rubén Hernández Uvilla", "ruben.hernandez@universidad.edu.mx", "Profesor");
        addUsuario("David Villalobos Chávez", "david.villalobos@universidad.edu.mx", "Profesor");
        addUsuario("Lily Daniella Torres Reyes", "lily.torres@universidad.edu.mx", "Profesor");
        addUsuario("Erika Van Scoit Arizmendi", "erika.vanscoit@universidad.edu.mx", "Profesor");

        // Espacios iniciales con reglas aplicadas:
        // Terraza: "Otro"
        addEspacio("Terraza", "Otro", 50, true);

        // Aldea: SOLO los que inician en 21__
        for (int i = 2101; i <= 2113; i++) {
            addEspacio(String.valueOf(i), "Aldea", 30, true);
        }

        // Edificio 1 (Preparatoria): Salones que inician en 1 (1102 a 1310)
        int[] salonesEdif1 = {1102, 1103, 1104, 1201, 1203, 1308, 1310};
        for (int s : salonesEdif1) {
            addEspacio(String.valueOf(s), "Edificio1", 30, true);
        }

        // Edificio 2 (Profesional): Salones 2201-2205, 2302-2306, 2404-2405
        int[] salonesEdif2 = {2201, 2202, 2203, 2204, 2205, 2302, 2304, 2305, 2306, 2404, 2405};
        for (int s : salonesEdif2) {
            addEspacio(String.valueOf(s), "Edificio2", 30, true);
        }

        // Laboratorios
        addEspacio("CILSE1", "Laboratorio", 30, true);
        addEspacio("CILSE2", "Laboratorio", 30, true);
        addEspacio("CILMT1", "Laboratorio", 30, true);
        addEspacio("CILDG1", "Laboratorio", 30, true);
        addEspacio("CILAB3", "Laboratorio", 30, true);
        addEspacio("CILID1", "Laboratorio", 30, true);

        // Canchas
        addEspacio("Cancha Polivalente", "Cancha", 50, true);
        addEspacio("Cancha Futbol", "Cancha", 100, true);

        // Auditorios y espacios de eventos especiales ("Otro")
        addEspacio("Centro Estudiantil", "Otro", 120, true);
        addEspacio("SUM Prepa", "Otro", 60, true);
        addEspacio("Claustro", "Otro", 40, true);
        addEspacio("Juicios Orales", "Otro", 35, true);

        // Cubículos Mezanine
        addEspacio("Mezanine (Cubículo 1)", "Otro", 8, true);
        addEspacio("Mezanine (Cubículo 2)", "Otro", 8, true);
        addEspacio("Mezanine (Cubículo 3)", "Otro", 8, true);

        // Reservas iniciales tomadas del calendario real del Campus Cuautitlán
        try {
            addReserva(2, 1, "2026-10-06", "10:00", "12:00", "Conferencia de Violencia Digital", "50 personas en auditorio");
            addReserva(3, 3, "2026-10-07", "11:00", "13:30", "Explora Maps", "Aldea 2102 y áreas de experiencia");
            addReserva(4, 17, "2026-10-07", "16:00", "17:00", "Clase muestra de Alemán", "Salón limpio y acomodo habitual");
            addReserva(5, 41, "2026-10-08", "13:30", "18:00", "Tecmi Tour T.Alba-Gilwell", "Acomodo para 100-120 personas");
            addReserva(5, 14, "2026-10-08", "13:30", "18:00", "Tecmi Tour - Sesión Aldea", "Salón Aldea 2113");
            addReserva(5, 42, "2026-10-08", "13:30", "18:00", "Tecmi Tour - Prepa", "Alineado hacia SUM1");
            addReserva(6, 39, "2026-10-09", "16:00", "19:00", "Partidos Amistosos Vive Prepa", "Cancha Polivalente, bocina");
            addReserva(5, 43, "2026-10-10", "09:00", "14:00", "Open House Vida y Carrera", "Claustro general");
            addReserva(6, 40, "2026-10-12", "11:00", "15:00", "Hora Vive Torneo", "Cancha Futbol");
            addReserva(7, 29, "2026-10-12", "17:00", "19:00", "Traza tu ruta Profesional", "Salón 2304");
            addReserva(8, 43, "2026-10-15", "10:00", "18:00", "Venta de Toga y Birrete", "Servicios Escolares");
            addReserva(9, 41, "2026-10-22", "10:00", "15:00", "Feria del Empleo Profesional", "Centro Estudiantil");
            addReserva(10, 41, "2026-10-23", "13:00", "14:00", "Badge Day Profesional", "Acomodo tipo auditorio 100 personas");
        } catch (Exception ignored) {}
    }

    // ================================================================
    // PERSISTENCIA SIMPLE A DISCO (SIN DEPENDENCIAS EXTERNAS)
    // ================================================================
    private File resolveStorageFile() {
        File f1 = new File(STORAGE_FILE);
        if (f1.exists()) return f1;
        File f2 = new File("backend/" + STORAGE_FILE);
        if (f2.exists()) return f2;
        File f3 = new File("../backend/" + STORAGE_FILE);
        if (f3.exists()) return f3;
        File backendDir = new File("backend");
        if (backendDir.isDirectory()) {
            return new File(backendDir, STORAGE_FILE);
        }
        return f1;
    }

    private synchronized void saveToFile() {
        File file = resolveStorageFile();
        try (PrintWriter out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            sb.append("{\n");
            
            // Usuarios
            sb.append("  \"usuarios\": [\n");
            List<Usuario> uList = new ArrayList<>(usuarios.values());
            for (int i = 0; i < uList.size(); i++) {
                sb.append("    ").append(uList.get(i).toJson());
                if (i < uList.size() - 1) sb.append(",");
                sb.append("\n");
            }
            sb.append("  ],\n");

            // Espacios
            sb.append("  \"espacios\": [\n");
            List<Espacio> eList = new ArrayList<>(espacios.values());
            for (int i = 0; i < eList.size(); i++) {
                sb.append("    ").append(eList.get(i).toJson());
                if (i < eList.size() - 1) sb.append(",");
                sb.append("\n");
            }
            sb.append("  ],\n");

            // Reservas
            sb.append("  \"reservas\": [\n");
            List<Reserva> rList = new ArrayList<>(reservas.values());
            for (int i = 0; i < rList.size(); i++) {
                sb.append("    ").append(rList.get(i).toJson());
                if (i < rList.size() - 1) sb.append(",");
                sb.append("\n");
            }
            sb.append("  ]\n");
            sb.append("}");

            out.write(sb.toString());
        } catch (Exception e) {
            System.err.println("Error al guardar archivo local: " + e.getMessage());
        }
    }

    private synchronized boolean loadFromFile() {
        File file = resolveStorageFile();
        if (!file.exists()) return false;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            String json = sb.toString();
            parseStoredJson(json);
            return true;
        } catch (Exception e) {
            System.err.println("Error al cargar datos previos: " + e.getMessage());
            return false;
        }
    }

    private void parseStoredJson(String json) {
        // Parse simple robusto de objetos
        // Usuarios
        int uStart = json.indexOf("\"usuarios\":");
        int eStart = json.indexOf("\"espacios\":");
        int rStart = json.indexOf("\"reservas\":");

        if (uStart != -1 && eStart != -1) {
            String uArray = json.substring(uStart, eStart);
            parseArrayObjects(uArray, obj -> {
                int id = getInt(obj, "idUsuario");
                String nom = getString(obj, "nombreCompleto");
                String mail = getString(obj, "correo");
                String rol = getString(obj, "rol");
                if (id > 0) {
                    usuarios.put(id, new Usuario(id, nom, mail, rol));
                    if (id >= nextUsuarioId.get()) nextUsuarioId.set(id + 1);
                }
            });
        }

        if (eStart != -1 && rStart != -1) {
            String eArray = json.substring(eStart, rStart);
            parseArrayObjects(eArray, obj -> {
                int id = getInt(obj, "idEspacio");
                String nom = getString(obj, "nombreEspacio");
                String tipo = getString(obj, "tipoEspacio");
                int cap = getInt(obj, "capacidad");
                boolean act = getBool(obj, "activo", true);
                if (id > 0) {
                    espacios.put(id, new Espacio(id, nom, tipo, cap, act));
                    if (id >= nextEspacioId.get()) nextEspacioId.set(id + 1);
                }
            });
        }

        if (rStart != -1) {
            String rArray = json.substring(rStart);
            parseArrayObjects(rArray, obj -> {
                int id = getInt(obj, "idReserva");
                int idU = getInt(obj, "idUsuario");
                int idE = getInt(obj, "idEspacio");
                String fec = getString(obj, "fechaReserva");
                String hi = getString(obj, "horaInicio");
                String hf = getString(obj, "horaFin");
                String mot = getString(obj, "motivoEvento");
                String est = getString(obj, "estadoReserva");
                String obs = getString(obj, "observaciones");
                boolean med = getBool(obj, "servicioMedico") || (obs != null && (obs.toLowerCase().contains("servicio médico") || 
                              obs.toLowerCase().contains("servicio medico") || obs.toLowerCase().contains("tecmi care") || obs.toLowerCase().contains("tecmicare")));
                boolean ti = getBool(obj, "servicioTI") || (obs != null && (obs.toLowerCase().contains("servicio ti") || 
                             obs.toLowerCase().contains("soporte ti") || obs.toLowerCase().contains("[ti]") ||
                             obs.toLowerCase().contains("pantallas") || obs.toLowerCase().contains("proyector") ||
                             obs.toLowerCase().contains("micrófono") || obs.toLowerCase().contains("microfono") ||
                             obs.toLowerCase().contains("equipo de audio")));
                boolean pf = getBool(obj, "servicioPlantaFisica") || (obs != null && (obs.toLowerCase().contains("planta física") ||
                             obs.toLowerCase().contains("planta fisica") || obs.toLowerCase().contains("mantenimiento") ||
                             obs.toLowerCase().contains("mobiliario") || obs.toLowerCase().contains("[pf]")));
                String lay = getString(obj, "archivoLayout");
                String layNom = getString(obj, "archivoLayoutNombre");
                if (id > 0) {
                    Reserva r = new Reserva(id, idU, idE, fec, hi, hf, mot, est, obs);
                    r.setServicioMedico(med);
                    r.setServicioTI(ti);
                    r.setServicioPlantaFisica(pf);
                    if (lay != null && !lay.trim().isEmpty()) r.setArchivoLayout(lay);
                    if (layNom != null && !layNom.trim().isEmpty()) r.setArchivoLayoutNombre(layNom);
                    enrichReserva(r);
                    reservas.put(id, r);
                    if (id >= nextReservaId.get()) nextReservaId.set(id + 1);
                }
            });
        }
    }

    private void parseArrayObjects(String text, java.util.function.Consumer<String> consumer) {
        int depth = 0;
        int objStart = -1;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{') {
                if (depth == 0) objStart = i;
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && objStart != -1) {
                    String obj = text.substring(objStart, i + 1);
                    consumer.accept(obj);
                    objStart = -1;
                }
            }
        }
    }

    private String getString(String obj, String key) {
        String pattern = "\"" + key + "\"";
        int idx = obj.indexOf(pattern);
        if (idx == -1) return "";
        int colonIdx = obj.indexOf(":", idx + pattern.length());
        if (colonIdx == -1) return "";
        int quoteStart = obj.indexOf("\"", colonIdx + 1);
        if (quoteStart == -1) return "";
        int quoteEnd = obj.indexOf("\"", quoteStart + 1);
        if (quoteEnd == -1) return "";
        return obj.substring(quoteStart + 1, quoteEnd).replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private int getInt(String obj, String key) {
        String pattern = "\"" + key + "\"";
        int idx = obj.indexOf(pattern);
        if (idx == -1) return 0;
        int colonIdx = obj.indexOf(":", idx + pattern.length());
        if (colonIdx == -1) return 0;
        int start = colonIdx + 1;
        while (start < obj.length() && Character.isWhitespace(obj.charAt(start))) {
            start++;
        }
        int end = start;
        while (end < obj.length() && (Character.isDigit(obj.charAt(end)) || obj.charAt(end) == '-')) {
            end++;
        }
        try {
            return Integer.parseInt(obj.substring(start, end).trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private boolean getBool(String obj, String key) {
        return getBool(obj, key, false);
    }

    private boolean getBool(String obj, String key, boolean defaultValue) {
        String pattern = "\"" + key + "\":";
        int idx = obj.indexOf(pattern);
        if (idx == -1) return defaultValue;
        idx += pattern.length();
        return obj.substring(idx).trim().startsWith("true");
    }
}
