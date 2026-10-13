package com.universidad.espacios.controller;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.universidad.espacios.model.Espacio;
import com.universidad.espacios.model.Reserva;
import com.universidad.espacios.model.Usuario;
import com.universidad.espacios.service.DataStore;

import java.io.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

public class ApiHandler implements HttpHandler {
    private final DataStore dataStore;

    public ApiHandler(DataStore dataStore) {
        this.dataStore = dataStore;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod().toUpperCase();
        String path = exchange.getRequestURI().getPath();
        String query = exchange.getRequestURI().getQuery();
        Map<String, String> params = parseQueryParams(query);

        // Cabeceras CORS
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");

        if ("OPTIONS".equalsIgnoreCase(method)) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        try {
            if (path.equals("/api/stats") && method.equals("GET")) {
                handleStats(exchange);
            } else if (path.equals("/api/espacios") && method.equals("GET")) {
                handleGetEspacios(exchange);
            } else if (path.equals("/api/espacios") && method.equals("POST")) {
                handlePostEspacio(exchange);
            } else if (path.startsWith("/api/espacios/") && method.equals("PUT")) {
                handlePutEspacio(exchange, path);
            } else if (path.startsWith("/api/espacios/") && method.equals("DELETE")) {
                handleDeleteEspacio(exchange, path);
            } else if (path.equals("/api/usuarios") && method.equals("GET")) {
                handleGetUsuarios(exchange);
            } else if (path.equals("/api/usuarios") && method.equals("POST")) {
                handlePostUsuario(exchange);
            } else if (path.equals("/api/reservas") && method.equals("GET")) {
                handleGetReservas(exchange, params);
            } else if (path.equals("/api/reservas") && method.equals("POST")) {
                handlePostReserva(exchange);
            } else if (path.matches("^/api/reservas/\\d+$") && method.equals("PUT")) {
                handlePutReserva(exchange, path);
            } else if (path.matches("^/api/reservas/\\d+/estado$") && method.equals("PUT")) {
                handlePutEstadoReserva(exchange, path);
            } else if (path.startsWith("/api/reservas/") && method.equals("DELETE")) {
                handleDeleteReserva(exchange, path);
            } else if (path.equals("/api/check-conflicto") && method.equals("GET")) {
                handleCheckConflicto(exchange, params);
            } else {
                sendJsonResponse(exchange, 404, "{\"error\":\"Ruta no encontrada: " + path + "\"}");
            }
        } catch (IllegalArgumentException e) {
            sendJsonResponse(exchange, 400, "{\"error\":\"" + escape(e.getMessage()) + "\"}");
        } catch (IllegalStateException e) {
            sendJsonResponse(exchange, 409, "{\"error\":\"" + escape(e.getMessage()) + "\"}");
        } catch (Exception e) {
            e.printStackTrace();
            sendJsonResponse(exchange, 500, "{\"error\":\"Error interno: " + escape(e.getMessage()) + "\"}");
        }
    }

    private void handleStats(HttpExchange exchange) throws IOException {
        List<Espacio> espacios = dataStore.getEspacios();
        List<Reserva> reservas = dataStore.getReservas();

        String hoy = LocalDate.now().toString();
        long totalEspacios = espacios.size();
        long espaciosActivos = espacios.stream().filter(Espacio::isActivo).count();
        long reservasActivas = reservas.stream().filter(r -> "Activa".equalsIgnoreCase(r.getEstadoReserva())).count();

        // Espacios ocupados hoy
        Set<Integer> ocupadosHoy = reservas.stream()
            .filter(r -> "Activa".equalsIgnoreCase(r.getEstadoReserva()) && hoy.equals(r.getFechaReserva()))
            .map(Reserva::getIdEspacio)
            .collect(Collectors.toSet());

        // Conteo por tipo
        Map<String, Long> porTipo = espacios.stream()
            .collect(Collectors.groupingBy(Espacio::getTipoEspacio, Collectors.counting()));

        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"totalEspacios\":").append(totalEspacios).append(",");
        sb.append("\"espaciosActivos\":").append(espaciosActivos).append(",");
        sb.append("\"reservasActivas\":").append(reservasActivas).append(",");
        sb.append("\"espaciosOcupadosHoy\":").append(ocupadosHoy.size()).append(",");
        sb.append("\"porTipo\":{");
        int idx = 0;
        for (Map.Entry<String, Long> entry : porTipo.entrySet()) {
            sb.append("\"").append(entry.getKey()).append("\":").append(entry.getValue());
            if (++idx < porTipo.size()) sb.append(",");
        }
        sb.append("}}");

        sendJsonResponse(exchange, 200, sb.toString());
    }

    private void handleGetEspacios(HttpExchange exchange) throws IOException {
        List<Espacio> list = dataStore.getEspacios();
        sendJsonList(exchange, list.stream().map(Espacio::toJson).collect(Collectors.toList()));
    }

    private void handlePostEspacio(HttpExchange exchange) throws Exception {
        String body = readBody(exchange);
        String nombre = getJsonString(body, "nombreEspacio");
        String tipo = getJsonString(body, "tipoEspacio");
        int capacidad = getJsonInt(body, "capacidad");
        boolean activo = !body.contains("\"activo\":false");

        if (nombre == null || nombre.trim().isEmpty()) {
            throw new IllegalArgumentException("El nombre del espacio es obligatorio.");
        }
        if (capacidad <= 0) {
            throw new IllegalArgumentException("La capacidad debe ser un número mayor a cero.");
        }

        Espacio e = dataStore.addEspacio(nombre.trim(), tipo, capacidad, activo);
        sendJsonResponse(exchange, 201, e.toJson());
    }

    private void handlePutEspacio(HttpExchange exchange, String path) throws Exception {
        int id = Integer.parseInt(path.substring("/api/espacios/".length()));
        String body = readBody(exchange);
        String nombre = getJsonString(body, "nombreEspacio");
        String tipo = getJsonString(body, "tipoEspacio");
        int capacidad = getJsonInt(body, "capacidad");
        boolean activo = !body.contains("\"activo\":false");

        Espacio e = dataStore.updateEspacio(id, nombre, tipo, capacidad, activo);
        if (e == null) {
            sendJsonResponse(exchange, 404, "{\"error\":\"Espacio no encontrado\"}");
        } else {
            sendJsonResponse(exchange, 200, e.toJson());
        }
    }

    private void handleDeleteEspacio(HttpExchange exchange, String path) throws IOException {
        int id = Integer.parseInt(path.substring("/api/espacios/".length()));
        boolean deleted = dataStore.deleteEspacio(id);
        if (deleted) {
            sendJsonResponse(exchange, 200, "{\"success\":true,\"message\":\"Espacio eliminado correctamente\"}");
        } else {
            sendJsonResponse(exchange, 404, "{\"error\":\"Espacio no encontrado\"}");
        }
    }

    private void handleGetUsuarios(HttpExchange exchange) throws IOException {
        List<Usuario> list = dataStore.getUsuarios();
        sendJsonList(exchange, list.stream().map(Usuario::toJson).collect(Collectors.toList()));
    }

    private void handlePostUsuario(HttpExchange exchange) throws Exception {
        String body = readBody(exchange);
        String nombre = getJsonString(body, "nombreCompleto");
        String correo = getJsonString(body, "correo");
        String rol = getJsonString(body, "rol");

        if (nombre.trim().isEmpty() || correo.trim().isEmpty()) {
            throw new IllegalArgumentException("Nombre y correo son obligatorios.");
        }

        Usuario u = dataStore.addUsuario(nombre.trim(), correo.trim(), rol);
        sendJsonResponse(exchange, 201, u.toJson());
    }

    private void handleGetReservas(HttpExchange exchange, Map<String, String> params) throws IOException {
        List<Reserva> list = dataStore.getReservas();
        
        // Filtros opcionales
        String fecha = params.get("fecha");
        String idEspacio = params.get("idEspacio");
        String estado = params.get("estado");

        if (fecha != null && !fecha.isEmpty()) {
            list = list.stream().filter(r -> r.getFechaReserva().equals(fecha)).collect(Collectors.toList());
        }
        if (idEspacio != null && !idEspacio.isEmpty()) {
            try {
                int idE = Integer.parseInt(idEspacio);
                list = list.stream().filter(r -> r.getIdEspacio() == idE).collect(Collectors.toList());
            } catch (Exception ignored) {}
        }
        if (estado != null && !estado.isEmpty()) {
            list = list.stream().filter(r -> r.getEstadoReserva().equalsIgnoreCase(estado)).collect(Collectors.toList());
        }

        sendJsonList(exchange, list.stream().map(Reserva::toJson).collect(Collectors.toList()));
    }

    private void handlePostReserva(HttpExchange exchange) throws Exception {
        String body = readBody(exchange);
        int idUsuario = getJsonInt(body, "idUsuario");
        int idEspacio = getJsonInt(body, "idEspacio");
        String fecha = getJsonString(body, "fechaReserva");
        String horaInicio = getJsonString(body, "horaInicio");
        String horaFin = getJsonString(body, "horaFin");
        String motivo = getJsonString(body, "motivoEvento");
        String obs = getJsonString(body, "observaciones");
        boolean servicioMedico = getJsonBool(body, "servicioMedico");
        boolean servicioTI = getJsonBool(body, "servicioTI");

        if (idUsuario <= 0 || idEspacio <= 0) {
            throw new IllegalArgumentException("Debe seleccionar un usuario y un espacio válidos.");
        }
        if (fecha.isEmpty() || horaInicio.isEmpty() || horaFin.isEmpty()) {
            throw new IllegalArgumentException("Fecha, hora de inicio y hora de fin son obligatorias.");
        }
        if (motivo.isEmpty()) {
            throw new IllegalArgumentException("El motivo o nombre del evento es obligatorio.");
        }

        Reserva r = dataStore.addReserva(idUsuario, idEspacio, fecha, horaInicio, horaFin, motivo, obs, servicioMedico, servicioTI);
        sendJsonResponse(exchange, 201, r.toJson());
    }

    private void handlePutReserva(HttpExchange exchange, String path) throws Exception {
        int id = Integer.parseInt(path.substring("/api/reservas/".length()));
        String body = readBody(exchange);
        int idUsuario = getJsonInt(body, "idUsuario");
        int idEspacio = getJsonInt(body, "idEspacio");
        String fecha = getJsonString(body, "fechaReserva");
        String horaInicio = getJsonString(body, "horaInicio");
        String horaFin = getJsonString(body, "horaFin");
        String motivo = getJsonString(body, "motivoEvento");
        String estado = getJsonString(body, "estadoReserva");
        String obs = getJsonString(body, "observaciones");
        boolean servicioMedico = getJsonBool(body, "servicioMedico");
        boolean servicioTI = getJsonBool(body, "servicioTI");

        if (idUsuario <= 0 || idEspacio <= 0) {
            throw new IllegalArgumentException("Debe seleccionar un usuario y un espacio válidos.");
        }
        if (fecha.isEmpty() || horaInicio.isEmpty() || horaFin.isEmpty()) {
            throw new IllegalArgumentException("Fecha, hora de inicio y hora de fin son obligatorias.");
        }
        if (motivo.isEmpty()) {
            throw new IllegalArgumentException("El motivo o nombre del evento es obligatorio.");
        }

        Reserva r = dataStore.updateReserva(id, idUsuario, idEspacio, fecha, horaInicio, horaFin, motivo, estado, obs, servicioMedico, servicioTI);
        sendJsonResponse(exchange, 200, r.toJson());
    }

    private void handlePutEstadoReserva(HttpExchange exchange, String path) throws Exception {
        // /api/reservas/{id}/estado
        String[] parts = path.split("/");
        int id = Integer.parseInt(parts[3]);
        String body = readBody(exchange);
        String estado = getJsonString(body, "estadoReserva");

        Reserva r = dataStore.updateEstadoReserva(id, estado);
        if (r == null) {
            sendJsonResponse(exchange, 404, "{\"error\":\"Reserva no encontrada\"}");
        } else {
            sendJsonResponse(exchange, 200, r.toJson());
        }
    }

    private void handleDeleteReserva(HttpExchange exchange, String path) throws IOException {
        int id = Integer.parseInt(path.substring("/api/reservas/".length()));
        boolean deleted = dataStore.deleteReserva(id);
        if (deleted) {
            sendJsonResponse(exchange, 200, "{\"success\":true,\"message\":\"Reserva eliminada correctamente\"}");
        } else {
            sendJsonResponse(exchange, 404, "{\"error\":\"Reserva no encontrada\"}");
        }
    }

    private void handleCheckConflicto(HttpExchange exchange, Map<String, String> params) throws IOException {
        String idEStr = params.get("idEspacio");
        String fecha = params.get("fecha");
        String horaInicio = params.get("horaInicio");
        String horaFin = params.get("horaFin");

        if (idEStr == null || fecha == null || horaInicio == null || horaFin == null) {
            sendJsonResponse(exchange, 400, "{\"error\":\"Faltan parámetros requeridos (idEspacio, fecha, horaInicio, horaFin)\"}");
            return;
        }

        int idEspacio = Integer.parseInt(idEStr);
        Reserva conflicto = dataStore.checkConflict(idEspacio, fecha, horaInicio, horaFin, -1);
        if (conflicto != null) {
            String json = String.format("{\"disponible\":false,\"conflicto\":%s}", conflicto.toJson());
            sendJsonResponse(exchange, 200, json);
        } else {
            sendJsonResponse(exchange, 200, "{\"disponible\":true}");
        }
    }

    // ================================================================
    // UTILIDADES HTTP Y JSON
    // ================================================================
    private void sendJsonList(HttpExchange exchange, List<String> jsonItems) throws IOException {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < jsonItems.size(); i++) {
            sb.append(jsonItems.get(i));
            if (i < jsonItems.size() - 1) sb.append(",");
        }
        sb.append("]");
        sendJsonResponse(exchange, 200, sb.toString());
    }

    private void sendJsonResponse(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private String readBody(HttpExchange exchange) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        }
    }

    private Map<String, String> parseQueryParams(String query) {
        Map<String, String> map = new HashMap<>();
        if (query == null || query.isEmpty()) return map;
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            if (idx > 0) {
                try {
                    String key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
                    String val = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
                    map.put(key, val);
                } catch (Exception ignored) {}
            }
        }
        return map;
    }

    private String getJsonString(String obj, String key) {
        int idx = obj.indexOf("\"" + key + "\"");
        int keyLen = key.length() + 2;
        if (idx == -1) {
            idx = obj.indexOf(key);
            keyLen = key.length();
        }
        if (idx == -1) return "";
        int colonIdx = obj.indexOf(":", idx + keyLen);
        if (colonIdx == -1) return "";
        int quoteStart = obj.indexOf("\"", colonIdx + 1);
        if (quoteStart == -1) return "";
        int quoteEnd = obj.indexOf("\"", quoteStart + 1);
        if (quoteEnd == -1) return "";
        return obj.substring(quoteStart + 1, quoteEnd).replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private int getJsonInt(String obj, String key) {
        int idx = obj.indexOf("\"" + key + "\"");
        int keyLen = key.length() + 2;
        if (idx == -1) {
            idx = obj.indexOf(key);
            keyLen = key.length();
        }
        if (idx == -1) return 0;
        int colonIdx = obj.indexOf(":", idx + keyLen);
        if (colonIdx == -1) return 0;
        int start = colonIdx + 1;
        while (start < obj.length() && (Character.isWhitespace(obj.charAt(start)) || obj.charAt(start) == '"')) {
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

    private boolean getJsonBool(String obj, String key) {
        int idx = obj.indexOf("\"" + key + "\"");
        int keyLen = key.length() + 2;
        if (idx == -1) {
            idx = obj.indexOf(key);
            keyLen = key.length();
        }
        if (idx == -1) return false;
        int colonIdx = obj.indexOf(":", idx + keyLen);
        if (colonIdx == -1) return false;
        String rest = obj.substring(colonIdx + 1).trim().toLowerCase();
        return rest.startsWith("true");
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ");
    }
}
