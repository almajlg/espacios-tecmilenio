package com.universidad.espacios.model;

public class Espacio {
    private int idEspacio;
    private String nombreEspacio;
    private String tipoEspacio; // "Salon", "Aldea", "Laboratorio", "Cancha", "Estacionamiento", "Otro"
    private int capacidad;
    private boolean activo = true;

    public Espacio() {}

    public Espacio(int idEspacio, String nombreEspacio, String tipoEspacio, int capacidad, boolean activo) {
        this.idEspacio = idEspacio;
        this.nombreEspacio = nombreEspacio;
        this.tipoEspacio = normalizarTipo(nombreEspacio, tipoEspacio);
        this.capacidad = capacidad;
        this.activo = activo;
    }

    /**
     * Aplica las reglas de negocio de la universidad:
     * 1. SOLO los salones que empiezan por 21 (ej. 2101 a 2113) pertenecen a "Aldea" (Edificio 2).
     * 2. Los salones restantes (ej. 1103-1310, 2201-2205, 2302-2305, 2404-2405) pertenecen al "Edificio 1".
     * 3. La Terraza es un tipo de espacio "Otro" (no un salón).
     */
    public static String normalizarTipo(String nombre, String tipoOriginal) {
        if (nombre == null) return tipoOriginal;
        String trimmed = nombre.trim();
        String lower = trimmed.toLowerCase();

        if (lower.contains("terraza")) {
            return "Otro";
        }

        if ("Laboratorio".equalsIgnoreCase(tipoOriginal) || "Cancha".equalsIgnoreCase(tipoOriginal) || 
            "Otro".equalsIgnoreCase(tipoOriginal) || "Estacionamiento".equalsIgnoreCase(tipoOriginal)) {
            return tipoOriginal;
        }

        // Regla: Salones 21xx y áreas de la Aldea pertenecen a Aldea
        if (trimmed.matches("^21\\d{2}.*") || lower.contains("aldea") || "aldea".equalsIgnoreCase(tipoOriginal)) {
            return "Aldea";
        }

        // Regla: Salones 22xx, 23xx, 24xx, 31xx pertenecen a Edificio 2 (Profesional)
        if (trimmed.matches("^(2[2-9]|3[0-9])\\d{2}.*") || lower.contains("profesional") || "edificio2".equalsIgnoreCase(tipoOriginal)) {
            return "Edificio2";
        }

        // Regla: Salones 1xxx pertenecen a Edificio 1 (Preparatoria)
        if (trimmed.matches("^1\\d{3}.*") || lower.contains("preparatoria") || "edificio1".equalsIgnoreCase(tipoOriginal) || "salon".equalsIgnoreCase(tipoOriginal)) {
            return "Edificio1";
        }

        if (tipoOriginal == null || tipoOriginal.isEmpty()) {
            return "Edificio1";
        }
        return tipoOriginal;
    }

    public String getEdificio() {
        if ("Aldea".equalsIgnoreCase(tipoEspacio) || (nombreEspacio != null && (nombreEspacio.trim().matches("^21\\d{2}.*") || nombreEspacio.toLowerCase().contains("aldea")))) {
            return "Aldea";
        }
        if ("Edificio2".equalsIgnoreCase(tipoEspacio) || (nombreEspacio != null && nombreEspacio.trim().matches("^(2[2-9]|3[0-9])\\d{2}.*"))) {
            return "Edificio 2 (Profesional)";
        }
        if ("Edificio1".equalsIgnoreCase(tipoEspacio) || "Salon".equalsIgnoreCase(tipoEspacio) || (nombreEspacio != null && nombreEspacio.trim().matches("^1\\d{3}.*"))) {
            return "Edificio 1 (Preparatoria)";
        }
        return "General";
    }

    public int getIdEspacio() { return idEspacio; }
    public void setIdEspacio(int idEspacio) { this.idEspacio = idEspacio; }

    public String getNombreEspacio() { return nombreEspacio; }
    public void setNombreEspacio(String nombreEspacio) {
        this.nombreEspacio = nombreEspacio;
        this.tipoEspacio = normalizarTipo(nombreEspacio, this.tipoEspacio);
    }

    public String getTipoEspacio() { return tipoEspacio; }
    public void setTipoEspacio(String tipoEspacio) {
        this.tipoEspacio = normalizarTipo(this.nombreEspacio, tipoEspacio);
    }

    public int getCapacidad() { return capacidad; }
    public void setCapacidad(int capacidad) { this.capacidad = capacidad; }

    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }

    public String toJson() {
        return String.format(
            "{\"idEspacio\":%d,\"nombreEspacio\":\"%s\",\"tipoEspacio\":\"%s\",\"capacidad\":%d,\"activo\":%b,\"edificio\":\"%s\"}",
            idEspacio,
            escapeJson(nombreEspacio),
            escapeJson(tipoEspacio),
            capacidad,
            activo,
            escapeJson(getEdificio())
        );
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}
