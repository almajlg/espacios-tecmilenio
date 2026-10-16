package com.universidad.espacios.service;

import com.universidad.espacios.model.Reserva;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Servicio generador de invitaciones de calendario estándar iCalendar (RFC 5545, formato .ics).
 * Compatible de manera nativa con Microsoft Outlook (Desktop, Web, Mobile), Gmail y Apple Calendar.
 * 
 * Configurado con METHOD:REQUEST para que los clientes de correo muestren
 * directamente los botones de interacción ("Aceptar", "Tentativa", "Rechazar").
 */
public class IcsCalendarService {

    private static final DateTimeFormatter ICS_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");
    private static final DateTimeFormatter ICS_UTC_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");

    /**
     * Genera el contenido del archivo .ics en memoria como un arreglo de bytes en UTF-8.
     *
     * @param reserva Datos de la reserva agendada
     * @param remitente Correo de quien organiza/envía la invitación (sistema)
     * @return Arreglo de bytes con el contenido del archivo .ics
     */
    public static byte[] generarIcsBytes(Reserva reserva, String remitente) {
        return generarIcsString(reserva, remitente).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Construye la cadena de texto con la estructura iCalendar (.ics) RFC 5545.
     *
     * @param reserva Datos de la reserva agendada
     * @param remitente Correo del organizador del sistema
     * @return Texto formateado de la invitación .ics
     */
    public static String generarIcsString(Reserva reserva, String remitente) {
        if (reserva == null) {
            throw new IllegalArgumentException("La reserva no puede ser nula");
        }

        String fecha = reserva.getFechaReserva(); // YYYY-MM-DD
        String horaInicio = normalizarHora(reserva.getHoraInicio()); // HH:mm:ss
        String horaFin = normalizarHora(reserva.getHoraFin()); // HH:mm:ss

        String dtStartStr = fecha.replace("-", "") + "T" + horaInicio.replace(":", "");
        String dtEndStr = fecha.replace("-", "") + "T" + horaFin.replace(":", "");

        String uid = "reserva-" + reserva.getIdReserva() + "-" + UUID.randomUUID() + "@tecmilenio.mx";
        String nowUtc = LocalDateTime.now().format(ICS_DATE_FORMAT);

        String titulo = escapeIcsText(reserva.getMotivoEvento());
        String ubicacion = escapeIcsText(
            (reserva.getNombreEspacio() != null ? reserva.getNombreEspacio() : "Espacio Universitario") +
            (reserva.getEdificioEspacio() != null && !reserva.getEdificioEspacio().isEmpty() ? " - " + reserva.getEdificioEspacio() : "")
        );

        // Construir descripción detallada
        StringBuilder desc = new StringBuilder();
        desc.append("CONFIRMACIÓN DE RESERVACIÓN DE ESPACIO\\n");
        desc.append("Universidad Tecmilenio - Campus Cuautitlán\\n\\n");
        desc.append("• Folio: TM-CUAU-").append(String.format("%04d", reserva.getIdReserva())).append("\\n");
        desc.append("• Evento: ").append(titulo).append("\\n");
        desc.append("• Espacio: ").append(ubicacion).append("\\n");
        desc.append("• Fecha: ").append(fecha).append("\\n");
        desc.append("• Horario: ").append(reserva.getHoraInicio()).append(" a ").append(reserva.getHoraFin()).append(" hrs\\n");
        
        if (reserva.getNombreUsuario() != null && !reserva.getNombreUsuario().isEmpty()) {
            desc.append("• Solicitante: ").append(escapeIcsText(reserva.getNombreUsuario()));
            if (reserva.getRolUsuario() != null) desc.append(" (").append(escapeIcsText(reserva.getRolUsuario())).append(")");
            desc.append("\\n");
        }

        // Servicios requeridos
        StringBuilder servs = new StringBuilder();
        if (reserva.isServicioMedico()) servs.append("Tecmi Care (Médico), ");
        if (reserva.isServicioTI()) servs.append("Soporte TI, ");
        if (reserva.isServicioPlantaFisica()) servs.append("Planta Física, ");
        if (servs.length() > 0) {
            String sStr = servs.substring(0, servs.length() - 2);
            desc.append("• Servicios Requeridos: ").append(escapeIcsText(sStr)).append("\\n");
        }

        if (reserva.getObservaciones() != null && !reserva.getObservaciones().trim().isEmpty()) {
            desc.append("• Observaciones: ").append(escapeIcsText(reserva.getObservaciones())).append("\\n");
        }

        desc.append("\\nEste evento fue generado automáticamente por la Plataforma de Control de Espacios Tecmilenio.");

        String organizadorCorreo = (remitente != null && !remitente.trim().isEmpty()) ? remitente.trim() : "notificaciones.espacios@tecmilenio.mx";
        String usuarioCorreo = reserva.getCorreoUsuario() != null ? reserva.getCorreoUsuario().trim() : "";
        String usuarioNombre = reserva.getNombreUsuario() != null ? escapeIcsText(reserva.getNombreUsuario()) : "Usuario Institucional";

        StringBuilder ics = new StringBuilder();
        ics.append("BEGIN:VCALENDAR\r\n");
        ics.append("PRODID:-//Tecmilenio//Control de Espacios 1.0//ES\r\n");
        ics.append("VERSION:2.0\r\n");
        ics.append("CALSCALE:GREGORIAN\r\n");
        ics.append("METHOD:REQUEST\r\n"); // Clave para que Outlook muestre los botones Aceptar/Rechazar

        // VTIMEZONE para America/Mexico_City
        ics.append("BEGIN:VTIMEZONE\r\n");
        ics.append("TZID:America/Mexico_City\r\n");
        ics.append("X-LIC-LOCATION:America/Mexico_City\r\n");
        ics.append("BEGIN:STANDARD\r\n");
        ics.append("TZOFFSETFROM:-0600\r\n");
        ics.append("TZOFFSETTO:-0600\r\n");
        ics.append("TZNAME:CST\r\n");
        ics.append("DTSTART:19700101T000000\r\n");
        ics.append("END:STANDARD\r\n");
        ics.append("END:VTIMEZONE\r\n");

        // VEVENT
        ics.append("BEGIN:VEVENT\r\n");
        ics.append("UID:").append(uid).append("\r\n");
        ics.append("DTSTAMP:").append(nowUtc).append("\r\n");
        ics.append("DTSTART;TZID=America/Mexico_City:").append(dtStartStr).append("\r\n");
        ics.append("DTEND;TZID=America/Mexico_City:").append(dtEndStr).append("\r\n");
        ics.append("SUMMARY:").append(titulo).append("\r\n");
        ics.append("DESCRIPTION:").append(desc).append("\r\n");
        ics.append("LOCATION:").append(ubicacion).append("\r\n");
        ics.append("ORGANIZER;CN=\"Control de Espacios Tecmilenio\":mailto:").append(organizadorCorreo).append("\r\n");

        if (!usuarioCorreo.isEmpty()) {
            ics.append("ATTENDEE;CUTYPE=INDIVIDUAL;ROLE=REQ-PARTICIPANT;PARTSTAT=NEEDS-ACTION;RSVP=TRUE;CN=\"")
               .append(usuarioNombre).append("\":mailto:").append(usuarioCorreo).append("\r\n");
        }

        ics.append("SEQUENCE:0\r\n");
        ics.append("STATUS:CONFIRMED\r\n");
        ics.append("TRANSP:OPAQUE\r\n");
        ics.append("PRIORITY:5\r\n");

        // Alarma / Recordatorio 15 minutos antes
        ics.append("BEGIN:VALARM\r\n");
        ics.append("TRIGGER:-PT15M\r\n");
        ics.append("ACTION:DISPLAY\r\n");
        ics.append("DESCRIPTION:Recordatorio: ").append(titulo).append("\r\n");
        ics.append("END:VALARM\r\n");

        ics.append("END:VEVENT\r\n");
        ics.append("END:VCALENDAR\r\n");

        return ics.toString();
    }

    private static String normalizarHora(String h) {
        if (h == null || h.trim().isEmpty()) return "00:00:00";
        h = h.trim();
        if (h.length() == 5) return h + ":00"; // 10:00 -> 10:00:00
        if (h.length() >= 8) return h.substring(0, 8);
        return h;
    }

    private static String escapeIcsText(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace(";", "\\;")
                .replace(",", "\\,")
                .replace("\r\n", "\\n")
                .replace("\n", "\\n")
                .replace("\r", "\\n");
    }
}
