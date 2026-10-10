package com.universidad.espacios.util;

import com.universidad.espacios.model.Espacio;
import com.universidad.espacios.model.Reserva;
import com.universidad.espacios.model.Usuario;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class ProcesadorEventosYEspacios {

    public static void main(String[] args) {
        try {
            String path = "backend/campus_espacios_data.json";
            if (!new File(path).exists()) path = "campus_espacios_data.json";

            byte[] bytes = Files.readAllBytes(Paths.get(path));
            String json = new String(bytes, StandardCharsets.UTF_8);

            // Parsear usuarios, espacios y reservas
            List<Usuario> usuarios = parseUsuarios(json);
            List<Espacio> espacios = parseEspacios(json);
            List<Reserva> reservas = parseReservas(json);

            System.out.println("Cargados: " + usuarios.size() + " usuarios, " + espacios.size() + " espacios, " + reservas.size() + " reservas.");

            // 1. REUBICAR "Salón de Espejos (Gimnasio)" a la categoría "Otro"
            for (Espacio e : espacios) {
                if (e.getNombreEspacio() != null && e.getNombreEspacio().toLowerCase().contains("espejos")) {
                    e.setNombreEspacio("Salón de Espejos (Gimnasio)");
                    e.setTipoEspacio("Otro");
                    System.out.println("-> Reubicado a categoría 'Otro': " + e.getNombreEspacio());
                }
            }

            // 2. CONFIGURAR ROLES DE RESPONSABLES (que también pueden ser maestros y agendar)
            Map<String, Usuario> userByName = new HashMap<>();
            int maxUserId = 0;
            for (Usuario u : usuarios) {
                if (u.getIdUsuario() > maxUserId) maxUserId = u.getIdUsuario();
                userByName.put(u.getNombreCompleto().trim().toLowerCase(), u);

                String norm = u.getNombreCompleto().trim().toLowerCase();
                if (norm.contains("arenas merchant") || norm.contains("luis manuel arenas")) {
                    u.setNombreCompleto("Luis Manuel Arenas");
                    u.setRol("Responsable");
                    System.out.println("-> Asignado rol Responsable: " + u.getNombreCompleto());
                } else if (norm.contains("reyes león  silvia") || norm.contains("silvia reyes")) {
                    u.setNombreCompleto("Silvia Reyes");
                    u.setRol("Responsable");
                    System.out.println("-> Asignado rol Responsable: " + u.getNombreCompleto());
                } else if (norm.contains("moreno montoya") || norm.contains("julio moreno")) {
                    u.setNombreCompleto("Julio Moreno");
                    u.setRol("Responsable");
                    System.out.println("-> Asignado rol Responsable: " + u.getNombreCompleto());
                } else if (norm.contains("natalie luna")) {
                    u.setRol("Responsable");
                } else if (norm.contains("idania lopez")) {
                    u.setRol("Responsable");
                } else if (norm.contains("jonathan salazar")) {
                    u.setRol("Responsable");
                } else if (norm.contains("lilia tapia")) {
                    u.setRol("Responsable");
                } else if (norm.contains("jibsam peñaloza")) {
                    u.setRol("Responsable");
                } else if (norm.contains("raymundo jacinto")) {
                    u.setRol("Responsable");
                }
            }

            // Asegurar que existan todos los responsables requeridos
            String[] responsablesRequeridos = {
                "Natalie Luna", "Idania Lopez", "Jonathan Salazar", "Lilia Tapia",
                "Jibsam Peñaloza", "Luis Manuel Arenas", "Raymundo Jacinto",
                "Silvia Reyes", "Julio Moreno"
            };

            for (String rNom : responsablesRequeridos) {
                Usuario found = findUser(usuarios, rNom);
                if (found == null) {
                    maxUserId++;
                    String mail = rNom.toLowerCase().replace(" ", ".").replace("ñ", "n") + "@tecmilenio.mx";
                    Usuario nuevo = new Usuario(maxUserId, rNom, mail, "Responsable");
                    usuarios.add(nuevo);
                    System.out.println("-> Creado Responsable: " + rNom);
                } else {
                    found.setRol("Responsable");
                }
            }

            // 3. AGREGAR EVENTOS DEL CALENDARIO DE ACTIVIDADES - CAMPUS CUAUTITLÁN (OCTUBRE 2026)
            // Filtramos eventos institucionales previos para insertar la versión completa y limpia
            List<Reserva> reservasFiltradas = new ArrayList<>();
            for (Reserva r : reservas) {
                String mot = r.getMotivoEvento() != null ? r.getMotivoEvento() : "";
                if (mot.startsWith("Conferencia de Violencia Digital") ||
                    mot.startsWith("Explora Maps") ||
                    mot.startsWith("Clase muestra de Alemán") ||
                    mot.startsWith("Clase muestra de Aleman") ||
                    mot.startsWith("Tecmi Tour") ||
                    mot.startsWith("Partidos Amistosos") ||
                    mot.startsWith("Open House") ||
                    mot.startsWith("Hora Vive") ||
                    mot.startsWith("Traza tu ruta") ||
                    mot.startsWith("Venta de Toga y Birrete") ||
                    mot.startsWith("Feria de Empleo") ||
                    mot.startsWith("Feria de empleo") ||
                    mot.startsWith("Badge Day") ||
                    mot.startsWith("Sesión Informativa")) {
                    // Omitir para reinsertar limpio
                } else {
                    reservasFiltradas.add(r);
                }
            }

            int maxResId = 0;
            for (Reserva r : reservasFiltradas) {
                if (r.getIdReserva() > maxResId) maxResId = r.getIdReserva();
            }

            // Definición de los eventos del PDF/imagen
            List<EventoDef> eventosDef = new ArrayList<>();
            eventosDef.add(new EventoDef("2026-10-06", "10:00", "12:00", "Conferencia de Violencia Digital", "Natalie Luna", "Terraza", "50 personas en auditorio. Área: Preparatoria. Solicitud: 05/10/26", false));

            // 07/10/2026 11:00-13:30 Explora Maps (Aldea 2102, 2103, 2104, SUM, 2305)
            eventosDef.add(new EventoDef("2026-10-07", "11:00", "13:30", "Explora Maps", "Idania Lopez", "2102", "Sesión Explora Maps. Área: Experiencia Académica", false));
            eventosDef.add(new EventoDef("2026-10-07", "11:00", "13:30", "Explora Maps", "Idania Lopez", "2103", "Sesión Explora Maps. Área: Experiencia Académica", false));
            eventosDef.add(new EventoDef("2026-10-07", "11:00", "13:30", "Explora Maps", "Idania Lopez", "2104", "Sesión Explora Maps. Área: Experiencia Académica", false));
            eventosDef.add(new EventoDef("2026-10-07", "11:00", "13:30", "Explora Maps", "Idania Lopez", "SUM 1 Edificio 2", "Sesión Explora Maps. Área: Experiencia Académica", false));
            eventosDef.add(new EventoDef("2026-10-07", "11:00", "13:30", "Explora Maps", "Idania Lopez", "2305", "Sesión Explora Maps. Área: Experiencia Académica", false));

            // 07/10/2026 16:00-17:00 Clase muestra de Aleman (Jonathan Salazar, 1201)
            eventosDef.add(new EventoDef("2026-10-07", "16:00", "17:00", "Clase muestra de Alemán", "Jonathan Salazar", "1201", "Salón limpio y acomodo habitual. Área: Preparatoria. Solicitud: 02/10/26", false));

            // 08/10/2026 13:30-18:00 Tecmi Tour T.Alba- Gilwell (Lilia Tapia, Centro Estudiantil, 2113, SUM Prepa, Cancha Polivalente)
            eventosDef.add(new EventoDef("2026-10-08", "13:30", "18:00", "Tecmi Tour T.Alba- Gilwell", "Lilia Tapia", "Centro Estudiantil", "Acomodo para 100-120 personas. Área: Vida y Carrera. Solicitud: 18/09/26", false));
            eventosDef.add(new EventoDef("2026-10-08", "13:30", "18:00", "Tecmi Tour T.Alba- Gilwell", "Lilia Tapia", "2113", "Área: Vida y Carrera. Solicitud: 18/09/26", false));
            eventosDef.add(new EventoDef("2026-10-08", "13:30", "18:00", "Tecmi Tour T.Alba- Gilwell", "Lilia Tapia", "SUM Edificio 1", "Alineado hacia SUM1. Área: Vida y Carrera. Solicitud: 18/09/26", false));
            eventosDef.add(new EventoDef("2026-10-08", "13:30", "18:00", "Tecmi Tour T.Alba- Gilwell", "Lilia Tapia", "Cancha Polivalente", "Bocina y micrófono. Área: Vida y Carrera. Solicitud: 18/09/26", false));

            // 09/10/2026 11:00-13:30 Explora Maps (Idania Lopez, Juicios Orales)
            eventosDef.add(new EventoDef("2026-10-09", "11:00", "13:30", "Explora Maps", "Idania Lopez", "Juicios Orales", "Área: Experiencia Académica", false));

            // 09/10/2026 16:00-19:00 Partidos Amistosos (Jibsam Peñaloza, Cancha Polivalente) [Requiere Servicio Médico]
            eventosDef.add(new EventoDef("2026-10-09", "16:00", "19:00", "Partidos Amistosos", "Jibsam Peñaloza", "Cancha Polivalente", "Área: Vive Prepa. Solicitud: 06/10/26", true));

            // 10/10/2026 09:00-14:00 Open House (Lilia Tapia, Claustro, 1103, 1104, 1310, 1308, SUM Prepa)
            eventosDef.add(new EventoDef("2026-10-10", "09:00", "14:00", "Open House", "Lilia Tapia", "Claustro", "Acomodo habitual. Área: Vida y Carrera. Solicitud: 18/09/26", false));
            eventosDef.add(new EventoDef("2026-10-10", "09:00", "14:00", "Open House", "Lilia Tapia", "1103", "Acomodo habitual. Área: Vida y Carrera. Solicitud: 18/09/26", false));
            eventosDef.add(new EventoDef("2026-10-10", "09:00", "14:00", "Open House", "Lilia Tapia", "1104", "Acomodo habitual. Área: Vida y Carrera. Solicitud: 18/09/26", false));
            eventosDef.add(new EventoDef("2026-10-10", "09:00", "14:00", "Open House", "Lilia Tapia", "1310", "Acomodo habitual. Área: Vida y Carrera. Solicitud: 18/09/26", false));
            eventosDef.add(new EventoDef("2026-10-10", "09:00", "14:00", "Open House", "Lilia Tapia", "1308", "Acomodo habitual. Área: Vida y Carrera. Solicitud: 18/09/26", false));
            eventosDef.add(new EventoDef("2026-10-10", "09:00", "14:00", "Open House", "Lilia Tapia", "SUM Edificio 1", "Área: Vida y Carrera. Solicitud: 18/09/26", false));

            // 12/10/2026 11:00-15:00 Hora Vive (Jibsam Peñaloza, Cancha Futbol) [Servicio Médico]
            eventosDef.add(new EventoDef("2026-10-12", "11:00", "15:00", "Hora Vive", "Jibsam Peñaloza", "Cancha de Fútbol", "Área: Vive Prepa. Solicitud: 05/10/26", true));

            // 12/10/2026 17:00-19:00 Traza tu ruta (Luis Manuel Arenas, 2304-2305)
            eventosDef.add(new EventoDef("2026-10-12", "17:00", "19:00", "Traza tu ruta", "Luis Manuel Arenas", "2304", "Área: Profesional. Solicitud: 29/09/26", false));
            eventosDef.add(new EventoDef("2026-10-12", "17:00", "19:00", "Traza tu ruta", "Luis Manuel Arenas", "2305", "Área: Profesional. Solicitud: 29/09/26", false));

            // 13/10/2026 06:30-12:00 Hora Vive (Jibsam Peñaloza, Cancha Futbol) [Servicio Médico]
            eventosDef.add(new EventoDef("2026-10-13", "06:30", "12:00", "Hora Vive", "Jibsam Peñaloza", "Cancha de Fútbol", "Área: Vive Prepa. Solicitud: 05/10/26", true));

            // 15/10/2026 10:00-18:00 Venta de Toga y Birrete (Raymundo Jacinto, Claustro)
            eventosDef.add(new EventoDef("2026-10-15", "10:00", "18:00", "Venta de Toga y Birrete", "Raymundo Jacinto", "Claustro", "Área: Servicios Escolares. Solicitud: 25/08/26", false));

            // 16/10/2026 10:00-18:00 Venta de Toga y Birrete (Raymundo Jacinto, Claustro)
            eventosDef.add(new EventoDef("2026-10-16", "10:00", "18:00", "Venta de Toga y Birrete", "Raymundo Jacinto", "Claustro", "Área: Servicios Escolares. Solicitud: 25/08/26", false));

            // 17/10/2026 09:00-14:00 Open House Profesional (Lilia Tapia, 2304-2305, Claustro, Centro Estudiantil, Labs, 2404-2405)
            eventosDef.add(new EventoDef("2026-10-17", "09:00", "14:00", "Open House Profesional", "Lilia Tapia", "2304", "Área: Vida y Carrera. Solicitud: 18/09/26", false));
            eventosDef.add(new EventoDef("2026-10-17", "09:00", "14:00", "Open House Profesional", "Lilia Tapia", "2305", "Área: Vida y Carrera. Solicitud: 18/09/26", false));
            eventosDef.add(new EventoDef("2026-10-17", "09:00", "14:00", "Open House Profesional", "Lilia Tapia", "Claustro", "Área: Vida y Carrera. Solicitud: 18/09/26", false));
            eventosDef.add(new EventoDef("2026-10-17", "09:00", "14:00", "Open House Profesional", "Lilia Tapia", "Centro Estudiantil", "Área: Vida y Carrera. Solicitud: 18/09/26", false));
            eventosDef.add(new EventoDef("2026-10-17", "09:00", "14:00", "Open House Profesional", "Lilia Tapia", "CILSE1", "Área: Vida y Carrera. Solicitud: 18/09/26", false));
            eventosDef.add(new EventoDef("2026-10-17", "09:00", "14:00", "Open House Profesional", "Lilia Tapia", "CILSE2", "Área: Vida y Carrera. Solicitud: 18/09/26", false));
            eventosDef.add(new EventoDef("2026-10-17", "09:00", "14:00", "Open House Profesional", "Lilia Tapia", "2404", "Área: Vida y Carrera. Solicitud: 18/09/26", false));
            eventosDef.add(new EventoDef("2026-10-17", "09:00", "14:00", "Open House Profesional", "Lilia Tapia", "2405", "Área: Vida y Carrera. Solicitud: 18/09/26", false));

            // 17/10/2026 09:00-13:00 Venta de Toga y Birrete (Raymundo Jacinto, Claustro)
            eventosDef.add(new EventoDef("2026-10-17", "09:00", "13:00", "Venta de Toga y Birrete", "Raymundo Jacinto", "Claustro", "Área: Servicios Escolares. Solicitud: 25/08/26", false));

            // 21/10/2026 09:00-13:00 Venta de Toga y Birrete (Raymundo Jacinto, Claustro)
            eventosDef.add(new EventoDef("2026-10-21", "09:00", "13:00", "Venta de Toga y Birrete", "Raymundo Jacinto", "Claustro", "Área: Servicios Escolares. Solicitud: 25/08/26", false));

            // 22/10/2026 10:00-15:00 Feria de empleo (Silvia Reyes, Centro Estudiantil)
            eventosDef.add(new EventoDef("2026-10-22", "10:00", "15:00", "Feria de Empleo Profesional", "Silvia Reyes", "Centro Estudiantil", "Área: Profesional", false));

            // 23/10/2026 13:00-14:00 Badge Day (Julio Moreno, Centro Estudiantil)
            eventosDef.add(new EventoDef("2026-10-23", "13:00", "14:00", "Badge Day Profesional", "Julio Moreno", "Centro Estudiantil", "Acomodo tipo auditorio para 100 personas, equipo de audio, micrófonos y pantallas, Back's, mesa de presidium y podium. Solicitud: 22/09/26", false));

            // 24/10/2026 09:30-12:30 Sesión Informativa Prepa (Lilia Tapia, 1203, 1103, 1104, SUM Prepa)
            eventosDef.add(new EventoDef("2026-10-24", "09:30", "12:30", "Sesión Informativa Prepa", "Lilia Tapia", "1203", "Mesa para servicio de café. Acomodo habitual. Área: Vida y Carrera. Solicitud: 18/09/26", false));
            eventosDef.add(new EventoDef("2026-10-24", "09:30", "12:30", "Sesión Informativa Prepa", "Lilia Tapia", "1103", "Mesa para servicio de café. Acomodo habitual. Área: Vida y Carrera. Solicitud: 18/09/26", false));
            eventosDef.add(new EventoDef("2026-10-24", "09:30", "12:30", "Sesión Informativa Prepa", "Lilia Tapia", "1104", "Mesa para servicio de café. Acomodo habitual. Área: Vida y Carrera. Solicitud: 18/09/26", false));
            eventosDef.add(new EventoDef("2026-10-24", "09:30", "12:30", "Sesión Informativa Prepa", "Lilia Tapia", "SUM Edificio 1", "Mesa para servicio de café. Acomodo habitual. Área: Vida y Carrera. Solicitud: 18/09/26", false));

            // 24/10/2026 09:30-12:30 Sesión Informativa Profesional (Lilia Tapia, 2404-2405)
            eventosDef.add(new EventoDef("2026-10-24", "09:30", "12:30", "Sesión Informativa Profesional", "Lilia Tapia", "2404", "Mesa para servicio de café. Acomodo habitual. Área: Vida y Carrera. Solicitud: 18/09/26", false));
            eventosDef.add(new EventoDef("2026-10-24", "09:30", "12:30", "Sesión Informativa Profesional", "Lilia Tapia", "2405", "Mesa para servicio de café. Acomodo habitual. Área: Vida y Carrera. Solicitud: 18/09/26", false));

            int insertados = 0;
            for (EventoDef ed : eventosDef) {
                Espacio esp = findEspacio(espacios, ed.espacioNom);
                if (esp == null) {
                    System.err.println("Advertencia: No se encontró espacio para '" + ed.espacioNom + "'");
                    continue;
                }
                Usuario usu = findUser(usuarios, ed.responsableNom);
                if (usu == null) usu = usuarios.get(0);

                maxResId++;
                String obsFinal = ed.servicioMedico ? ed.observaciones + " | [Servicio Médico Requerido]" : ed.observaciones;
                Reserva r = new Reserva(maxResId, usu.getIdUsuario(), esp.getIdEspacio(), ed.fecha, ed.horaIni, ed.horaFin, ed.motivo, "Activa", obsFinal);
                r.setNombreUsuario(usu.getNombreCompleto());
                r.setRolUsuario(usu.getRol());
                r.setCorreoUsuario(usu.getCorreo());
                r.setNombreEspacio(esp.getNombreEspacio());
                r.setTipoEspacio(esp.getTipoEspacio());
                r.setCapacidadEspacio(esp.getCapacidad());
                r.setEdificioEspacio(esp.getEdificio());
                reservasFiltradas.add(r);
                insertados++;
            }

            System.out.println("-> Insertados " + insertados + " eventos institucionales del calendario.");
            System.out.println("-> Total reservas: " + reservasFiltradas.size());

            // 4. GUARDAR JSON COMPLETO LIMPIO
            guardarJson(path, usuarios, espacios, reservasFiltradas);
            System.out.println("-> Base de datos guardada exitosamente en " + path);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static Usuario findUser(List<Usuario> list, String name) {
        String n = name.trim().toLowerCase();
        for (Usuario u : list) {
            if (u.getNombreCompleto().trim().toLowerCase().contains(n) || n.contains(u.getNombreCompleto().trim().toLowerCase())) {
                return u;
            }
        }
        return null;
    }

    private static Espacio findEspacio(List<Espacio> list, String name) {
        String n = name.trim().toLowerCase();
        for (Espacio e : list) {
            if (e.getNombreEspacio().trim().equalsIgnoreCase(n)) return e;
        }
        for (Espacio e : list) {
            if (e.getNombreEspacio().trim().toLowerCase().contains(n) || n.contains(e.getNombreEspacio().trim().toLowerCase())) {
                return e;
            }
        }
        return null;
    }

    private static void guardarJson(String path, List<Usuario> usuarios, List<Espacio> espacios, List<Reserva> reservas) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");

        sb.append("  \"usuarios\": [\n");
        for (int i = 0; i < usuarios.size(); i++) {
            sb.append("    ").append(usuarios.get(i).toJson());
            if (i < usuarios.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("  ],\n");

        sb.append("  \"espacios\": [\n");
        for (int i = 0; i < espacios.size(); i++) {
            sb.append("    ").append(espacios.get(i).toJson());
            if (i < espacios.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("  ],\n");

        sb.append("  \"reservas\": [\n");
        for (int i = 0; i < reservas.size(); i++) {
            Reserva r = reservas.get(i);
            boolean med = r.getObservaciones() != null && r.getObservaciones().toLowerCase().contains("servicio médico");
            String rJson = String.format(
                "{" +
                "\"idReserva\":%d," +
                "\"idUsuario\":%d," +
                "\"idEspacio\":%d," +
                "\"fechaReserva\":\"%s\"," +
                "\"horaInicio\":\"%s\"," +
                "\"horaFin\":\"%s\"," +
                "\"motivoEvento\":\"%s\"," +
                "\"estadoReserva\":\"%s\"," +
                "\"servicioMedico\":%b," +
                "\"observaciones\":\"%s\"," +
                "\"nombreUsuario\":\"%s\"," +
                "\"correoUsuario\":\"%s\"," +
                "\"rolUsuario\":\"%s\"," +
                "\"nombreEspacio\":\"%s\"," +
                "\"tipoEspacio\":\"%s\"," +
                "\"capacidadEspacio\":%d," +
                "\"edificioEspacio\":\"%s\"" +
                "}",
                r.getIdReserva(),
                r.getIdUsuario(),
                r.getIdEspacio(),
                escape(r.getFechaReserva()),
                escape(r.getHoraInicio()),
                escape(r.getHoraFin()),
                escape(r.getMotivoEvento()),
                escape(r.getEstadoReserva()),
                med,
                escape(r.getObservaciones()),
                escape(r.getNombreUsuario()),
                escape(r.getCorreoUsuario()),
                escape(r.getRolUsuario()),
                escape(r.getNombreEspacio()),
                escape(r.getTipoEspacio()),
                r.getCapacidadEspacio(),
                escape(r.getEdificioEspacio() != null ? r.getEdificioEspacio() : "")
            );
            sb.append("    ").append(rJson);
            if (i < reservas.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("  ]\n");
        sb.append("}");

        Files.write(Paths.get(path), sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    private static List<Usuario> parseUsuarios(String json) {
        List<Usuario> list = new ArrayList<>();
        int uStart = json.indexOf("\"usuarios\":");
        int eStart = json.indexOf("\"espacios\":");
        if (uStart == -1 || eStart == -1) return list;
        String block = json.substring(uStart, eStart);
        for (String item : splitObjects(block)) {
            int id = getInt(item, "idUsuario");
            String nom = getString(item, "nombreCompleto");
            String mail = getString(item, "correo");
            String rol = getString(item, "rol");
            if (id > 0) list.add(new Usuario(id, nom, mail, rol));
        }
        return list;
    }

    private static List<Espacio> parseEspacios(String json) {
        List<Espacio> list = new ArrayList<>();
        int eStart = json.indexOf("\"espacios\":");
        int rStart = json.indexOf("\"reservas\":");
        if (eStart == -1 || rStart == -1) return list;
        String block = json.substring(eStart, rStart);
        for (String item : splitObjects(block)) {
            int id = getInt(item, "idEspacio");
            String nom = getString(item, "nombreEspacio");
            String tipo = getString(item, "tipoEspacio");
            int cap = getInt(item, "capacidad");
            boolean act = getBool(item, "activo");
            if (id > 0) {
                Espacio e = new Espacio(id, nom, tipo, cap, act);
                list.add(e);
            }
        }
        return list;
    }

    private static List<Reserva> parseReservas(String json) {
        List<Reserva> list = new ArrayList<>();
        int rStart = json.indexOf("\"reservas\":");
        if (rStart == -1) return list;
        String block = json.substring(rStart);
        for (String item : splitObjects(block)) {
            int id = getInt(item, "idReserva");
            int idU = getInt(item, "idUsuario");
            int idE = getInt(item, "idEspacio");
            String fec = getString(item, "fechaReserva");
            String hi = getString(item, "horaInicio");
            String hf = getString(item, "horaFin");
            String mot = getString(item, "motivoEvento");
            String est = getString(item, "estadoReserva");
            String obs = getString(item, "observaciones");
            if (id > 0) {
                Reserva r = new Reserva(id, idU, idE, fec, hi, hf, mot, est, obs);
                r.setNombreUsuario(getString(item, "nombreUsuario"));
                r.setRolUsuario(getString(item, "rolUsuario"));
                r.setCorreoUsuario(getString(item, "correoUsuario"));
                r.setNombreEspacio(getString(item, "nombreEspacio"));
                r.setTipoEspacio(getString(item, "tipoEspacio"));
                r.setCapacidadEspacio(getInt(item, "capacidadEspacio"));
                r.setEdificioEspacio(getString(item, "edificioEspacio"));
                list.add(r);
            }
        }
        return list;
    }

    private static List<String> splitObjects(String block) {
        List<String> list = new ArrayList<>();
        int depth = 0;
        int start = -1;
        for (int i = 0; i < block.length(); i++) {
            char c = block.charAt(i);
            if (c == '{') {
                if (depth == 0) start = i;
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && start != -1) {
                    list.add(block.substring(start, i + 1));
                    start = -1;
                }
            }
        }
        return list;
    }

    private static String getString(String obj, String key) {
        String pattern = "\"" + key + "\":";
        int idx = obj.indexOf(pattern);
        if (idx == -1) return "";
        int start = obj.indexOf("\"", idx + pattern.length());
        if (start == -1) return "";
        int end = obj.indexOf("\"", start + 1);
        while (end != -1 && obj.charAt(end - 1) == '\\') {
            end = obj.indexOf("\"", end + 1);
        }
        if (end == -1) return "";
        return obj.substring(start + 1, end).replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private static int getInt(String obj, String key) {
        String pattern = "\"" + key + "\":";
        int idx = obj.indexOf(pattern);
        if (idx == -1) return 0;
        int start = idx + pattern.length();
        int end = start;
        while (end < obj.length() && (Character.isDigit(obj.charAt(end)) || obj.charAt(end) == '-')) end++;
        try {
            return Integer.parseInt(obj.substring(start, end).trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private static boolean getBool(String obj, String key) {
        String pattern = "\"" + key + "\":";
        int idx = obj.indexOf(pattern);
        if (idx == -1) return false;
        String sub = obj.substring(idx + pattern.length(), Math.min(idx + pattern.length() + 10, obj.length())).toLowerCase();
        return sub.contains("true");
    }

    static class EventoDef {
        String fecha, horaIni, horaFin, motivo, responsableNom, espacioNom, observaciones;
        boolean servicioMedico;

        EventoDef(String fecha, String horaIni, String horaFin, String motivo, String responsableNom, String espacioNom, String observaciones, boolean servicioMedico) {
            this.fecha = fecha;
            this.horaIni = horaIni;
            this.horaFin = horaFin;
            this.motivo = motivo;
            this.responsableNom = responsableNom;
            this.espacioNom = espacioNom;
            this.observaciones = observaciones;
            this.servicioMedico = servicioMedico;
        }
    }
}
