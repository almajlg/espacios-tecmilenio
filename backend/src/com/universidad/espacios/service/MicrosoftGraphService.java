package com.universidad.espacios.service;

import com.universidad.espacios.model.Reserva;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Servicio de integración con Microsoft Graph API para la sincronización de citas
 * y eventos con el calendario de Outlook institucional (Microsoft 365).
 */
public class MicrosoftGraphService {

    private static final String GRAPH_EVENTS_ENDPOINT = "https://graph.microsoft.com/v1.0/me/events";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);

    /**
     * Objeto de resultado de la sincronización con Microsoft Graph.
     */
    public static class SyncResult {
        private final boolean success;
        private final String eventId;
        private final String webLink;
        private final String errorType; // "TOKEN_EXPIRED", "INSUFFICIENT_PERMISSIONS", "GRAPH_ERROR", "NETWORK_ERROR", etc.
        private final String message;
        private final int statusCode;

        public SyncResult(boolean success, String eventId, String webLink, String errorType, String message, int statusCode) {
            this.success = success;
            this.eventId = eventId;
            this.webLink = webLink;
            this.errorType = errorType;
            this.message = message;
            this.statusCode = statusCode;
        }

        public static SyncResult ok(String eventId, String webLink, String message) {
            return new SyncResult(true, eventId, webLink, null, message, 201);
        }

        public static SyncResult fail(String errorType, String message, int statusCode) {
            return new SyncResult(false, null, null, errorType, message, statusCode);
        }

        public boolean isSuccess() { return success; }
        public String getEventId() { return eventId; }
        public String getWebLink() { return webLink; }
        public String getErrorType() { return errorType; }
        public String getMessage() { return message; }
        public int getStatusCode() { return statusCode; }

        public String toJson() {
            StringBuilder sb = new StringBuilder("{");
            sb.append("\"success\":").append(success).append(",");
            if (eventId != null) sb.append("\"eventId\":\"").append(escapeJson(eventId)).append("\",");
            if (webLink != null) sb.append("\"webLink\":\"").append(escapeJson(webLink)).append("\",");
            if (errorType != null) sb.append("\"errorType\":\"").append(escapeJson(errorType)).append("\",");
            sb.append("\"message\":\"").append(escapeJson(message != null ? message : "")).append("\",");
            sb.append("\"statusCode\":").append(statusCode);
            sb.append("}");
            return sb.toString();
        }

        private static String escapeJson(String s) {
            if (s == null) return "";
            return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
        }
    }

    /**
     * Sincroniza una reserva con el calendario de Outlook del usuario mediante Microsoft Graph API.
     *
     * @param reserva Datos de la reserva agendada
     * @param accessToken Token de acceso de Microsoft OAuth 2.0 / MSAL
     * @return SyncResult con estado y detalles de la sincronización
     */
    public static SyncResult syncReservaToOutlook(Reserva reserva, String accessToken) {
        if (accessToken == null || accessToken.trim().isEmpty()) {
            return SyncResult.fail("TOKEN_MISSING", "No se proporcionó token de acceso de Microsoft. Inicia sesión con tu cuenta de Microsoft para sincronizar.", 400);
        }

        if (reserva == null) {
            return SyncResult.fail("INVALID_DATA", "Datos de reserva inválidos.", 400);
        }

        try {
            String jsonPayload = buildGraphEventPayload(reserva);

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(CONNECT_TIMEOUT)
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GRAPH_EVENTS_ENDPOINT))
                    .header("Authorization", "Bearer " + accessToken.trim())
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("Prefer", "outlook.timezone=\"America/Mexico_City\"")
                    .timeout(REQUEST_TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = response.statusCode();
            String responseBody = response.body();

            if (status == 201 || status == 200) {
                String eventId = extractJsonValue(responseBody, "id");
                String webLink = extractJsonValue(responseBody, "webLink");
                return SyncResult.ok(eventId, webLink, "Evento añadido exitosamente a tu calendario de Outlook.");
            } else if (status == 401) {
                // Token expirado o inválido
                return SyncResult.fail(
                        "TOKEN_EXPIRED",
                        "El token de Microsoft ha expirado o no es válido. La reserva se guardó en el sistema, pero debes volver a iniciar sesión con Microsoft para sincronizar tu calendario.",
                        401
                );
            } else if (status == 403) {
                // Faltan permisos de Calendars.ReadWrite
                return SyncResult.fail(
                        "INSUFFICIENT_PERMISSIONS",
                        "Tu cuenta de Microsoft no otorgó permisos para escribir en el calendario (Calendars.ReadWrite). Vuelve a iniciar sesión autorizando el permiso de calendario.",
                        403
                );
            } else {
                String errorMsg = extractGraphErrorMessage(responseBody);
                if (errorMsg == null || errorMsg.isEmpty()) {
                    errorMsg = "Error en Microsoft Graph API (HTTP " + status + ")";
                }
                return SyncResult.fail("GRAPH_ERROR", errorMsg, status);
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return SyncResult.fail("REQUEST_INTERRUPTED", "La solicitud a Microsoft Graph fue interrumpida: " + e.getMessage(), 500);
        } catch (IOException e) {
            return SyncResult.fail("NETWORK_ERROR", "Error de comunicación con Microsoft Graph API: " + e.getMessage(), 503);
        } catch (Exception e) {
            return SyncResult.fail("INTERNAL_ERROR", "Error inesperado al sincronizar con Outlook: " + e.getMessage(), 500);
        }
    }

    /**
     * Construye el JSON formateado que requiere Microsoft Graph API para /me/events.
     */
    private static String buildGraphEventPayload(Reserva r) {
        String fecha = r.getFechaReserva(); // YYYY-MM-DD
        String horaInicio = r.getHoraInicio(); // HH:mm
        String horaFin = r.getHoraFin(); // HH:mm

        // Formato ISO: YYYY-MM-DDTHH:mm:00
        String startDateTime = fecha + "T" + (horaInicio.length() == 5 ? horaInicio + ":00" : horaInicio);
        String endDateTime = fecha + "T" + (horaFin.length() == 5 ? horaFin + ":00" : horaFin);

        String nombreEspacio = r.getNombreEspacio() != null ? r.getNombreEspacio() : "Espacio Universitario";
        String edificio = r.getEdificioEspacio() != null && !r.getEdificioEspacio().isEmpty() ? " - " + r.getEdificioEspacio() : "";
        String ubicacion = nombreEspacio + edificio;

        // Construir contenido HTML enriquecido para el cuerpo del evento en Outlook
        StringBuilder htmlContent = new StringBuilder();
        htmlContent.append("<div style='font-family:Segoe UI,Helvetica,Arial,sans-serif;color:#1e293b;line-height:1.5;'>");
        htmlContent.append("<div style='background:#0d5c3a;color:#ffffff;padding:12px 16px;border-radius:6px;margin-bottom:14px;'>");
        htmlContent.append("<h2 style='margin:0;font-size:16px;'>").append(escapeHtml(r.getMotivoEvento())).append("</h2>");
        htmlContent.append("<p style='margin:4px 0 0;font-size:13px;opacity:0.9;'>Control de Espacios • Universidad Tecmilenio</p>");
        htmlContent.append("</div>");

        htmlContent.append("<table style='width:100%;border-collapse:collapse;font-size:14px;margin-bottom:14px;'>");
        htmlContent.append("<tr><td style='padding:6px 0;width:140px;color:#64748b;font-weight:600;'>Espacio:</td><td><strong>").append(escapeHtml(ubicacion)).append("</strong></td></tr>");
        htmlContent.append("<tr><td style='padding:6px 0;color:#64748b;font-weight:600;'>Fecha:</td><td>").append(escapeHtml(fecha)).append("</td></tr>");
        htmlContent.append("<tr><td style='padding:6px 0;color:#64748b;font-weight:600;'>Horario:</td><td>").append(escapeHtml(horaInicio)).append(" a ").append(escapeHtml(horaFin)).append(" hrs</td></tr>");
        if (r.getNombreUsuario() != null && !r.getNombreUsuario().isEmpty()) {
            htmlContent.append("<tr><td style='padding:6px 0;color:#64748b;font-weight:600;'>Responsable:</td><td>").append(escapeHtml(r.getNombreUsuario()));
            if (r.getRolUsuario() != null) htmlContent.append(" (").append(escapeHtml(r.getRolUsuario())).append(")");
            htmlContent.append("</td></tr>");
        }

        // Servicios solicitados
        StringBuilder servs = new StringBuilder();
        if (r.isServicioMedico()) servs.append("• Asistencia Tecmi Care en sitio<br/>");
        if (r.isServicioTI()) servs.append("• Soporte de TI y Equipo Multimedia<br/>");
        if (r.isServicioPlantaFisica()) servs.append("• Planta Física (Mobiliario / Acomodo)<br/>");
        if (servs.length() > 0) {
            htmlContent.append("<tr><td style='padding:6px 0;color:#64748b;font-weight:600;vertical-align:top;'>Servicios:</td><td>").append(servs).append("</td></tr>");
        }

        if (r.getObservaciones() != null && !r.getObservaciones().trim().isEmpty()) {
            htmlContent.append("<tr><td style='padding:6px 0;color:#64748b;font-weight:600;vertical-align:top;'>Observaciones:</td><td>").append(escapeHtml(r.getObservaciones())).append("</td></tr>");
        }
        htmlContent.append("</table>");

        htmlContent.append("<hr style='border:none;border-top:1px solid #e2e8f0;margin:16px 0;'/>");
        htmlContent.append("<p style='font-size:12px;color:#94a3b8;margin:0;'>Evento reservado y sincronizado automáticamente mediante el sistema de Control de Espacios de Campus Tecmilenio.</p>");
        htmlContent.append("</div>");

        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"subject\":\"").append(escapeJson(r.getMotivoEvento())).append("\",");
        sb.append("\"body\":{");
        sb.append("\"contentType\":\"HTML\",");
        sb.append("\"content\":\"").append(escapeJson(htmlContent.toString())).append("\"");
        sb.append("},");
        sb.append("\"start\":{");
        sb.append("\"dateTime\":\"").append(startDateTime).append("\",");
        sb.append("\"timeZone\":\"America/Mexico_City\"");
        sb.append("},");
        sb.append("\"end\":{");
        sb.append("\"dateTime\":\"").append(endDateTime).append("\",");
        sb.append("\"timeZone\":\"America/Mexico_City\"");
        sb.append("},");
        sb.append("\"location\":{");
        sb.append("\"displayName\":\"").append(escapeJson(ubicacion)).append("\"");
        sb.append("},");
        sb.append("\"reminderMinutesBeforeStart\":15,");
        sb.append("\"isReminderOn\":true");
        sb.append("}");

        return sb.toString();
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    /**
     * Extrae un valor string simple de un JSON devuelto por Microsoft Graph.
     */
    private static String extractJsonValue(String json, String key) {
        if (json == null) return null;
        int keyIdx = json.indexOf("\"" + key + "\"");
        if (keyIdx == -1) return null;
        int colonIdx = json.indexOf(":", keyIdx + key.length() + 2);
        if (colonIdx == -1) return null;
        int quoteStart = json.indexOf("\"", colonIdx + 1);
        if (quoteStart == -1) return null;
        int quoteEnd = json.indexOf("\"", quoteStart + 1);
        while (quoteEnd != -1 && json.charAt(quoteEnd - 1) == '\\') {
            quoteEnd = json.indexOf("\"", quoteEnd + 1);
        }
        if (quoteEnd == -1) return null;
        return json.substring(quoteStart + 1, quoteEnd).replace("\\\"", "\"").replace("\\\\", "\\");
    }

    /**
     * Extrae el mensaje de error de un payload JSON de error de Microsoft Graph.
     */
    private static String extractGraphErrorMessage(String json) {
        if (json == null) return null;
        String msg = extractJsonValue(json, "message");
        if (msg != null && !msg.isEmpty()) return msg;
        String code = extractJsonValue(json, "code");
        if (code != null && !code.isEmpty()) return "Código: " + code;
        return null;
    }
}
