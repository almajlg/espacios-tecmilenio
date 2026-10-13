package com.universidad.espacios.model;

public class Reserva {
    private int idReserva;
    private int idUsuario;
    private int idEspacio;
    private String fechaReserva; // YYYY-MM-DD
    private String horaInicio;   // HH:mm o HH:mm:ss
    private String horaFin;      // HH:mm o HH:mm:ss
    private String motivoEvento;
    private String estadoReserva = "Activa"; // "Activa", "Cancelada", "Completada"
    private String observaciones;
    private boolean servicioMedico;
    private boolean servicioTI;
    private String archivoLayout;
    private String archivoLayoutNombre;

    // Campos enriquecidos para la vista/API
    private String nombreUsuario;
    private String correoUsuario;
    private String rolUsuario;
    private String nombreEspacio;
    private String tipoEspacio;
    private int capacidadEspacio;
    private String edificioEspacio;

    public Reserva() {}

    public Reserva(int idReserva, int idUsuario, int idEspacio, String fechaReserva, 
                   String horaInicio, String horaFin, String motivoEvento, 
                   String estadoReserva, String observaciones) {
        this.idReserva = idReserva;
        this.idUsuario = idUsuario;
        this.idEspacio = idEspacio;
        this.fechaReserva = fechaReserva;
        this.horaInicio = normalizarHora(horaInicio);
        this.horaFin = normalizarHora(horaFin);
        this.motivoEvento = motivoEvento;
        this.estadoReserva = estadoReserva != null ? estadoReserva : "Activa";
        this.observaciones = observaciones != null ? observaciones : "";
        String obsLower = this.observaciones.toLowerCase();
        this.servicioMedico = obsLower.contains("servicio médico") || obsLower.contains("servicio medico");
        this.servicioTI = obsLower.contains("servicio ti") || obsLower.contains("soporte ti") || 
                          obsLower.contains("[ti]") || obsLower.contains("tecnologías de la información") || 
                          obsLower.contains("tecnologias de la informacion") ||
                          obsLower.contains("pantallas") || obsLower.contains("proyector") ||
                          obsLower.contains("micrófono") || obsLower.contains("microfono") ||
                          obsLower.contains("equipo de audio");
    }

    private static String normalizarHora(String h) {
        if (h == null) return "00:00";
        h = h.trim();
        if (h.length() == 5) return h; // 10:00
        if (h.length() >= 8) return h.substring(0, 5); // 10:00:00 -> 10:00
        return h;
    }

    public int getIdReserva() { return idReserva; }
    public void setIdReserva(int idReserva) { this.idReserva = idReserva; }

    public int getIdUsuario() { return idUsuario; }
    public void setIdUsuario(int idUsuario) { this.idUsuario = idUsuario; }

    public int getIdEspacio() { return idEspacio; }
    public void setIdEspacio(int idEspacio) { this.idEspacio = idEspacio; }

    public String getFechaReserva() { return fechaReserva; }
    public void setFechaReserva(String fechaReserva) { this.fechaReserva = fechaReserva; }

    public String getHoraInicio() { return horaInicio; }
    public void setHoraInicio(String horaInicio) { this.horaInicio = normalizarHora(horaInicio); }

    public String getHoraFin() { return horaFin; }
    public void setHoraFin(String horaFin) { this.horaFin = normalizarHora(horaFin); }

    public String getMotivoEvento() { return motivoEvento; }
    public void setMotivoEvento(String motivoEvento) { this.motivoEvento = motivoEvento; }

    public String getEstadoReserva() { return estadoReserva; }
    public void setEstadoReserva(String estadoReserva) { this.estadoReserva = estadoReserva; }

    public String getObservaciones() { return observaciones; }
    public void setObservaciones(String observaciones) { this.observaciones = observaciones; }

    public String getNombreUsuario() { return nombreUsuario; }
    public void setNombreUsuario(String nombreUsuario) { this.nombreUsuario = nombreUsuario; }

    public String getCorreoUsuario() { return correoUsuario; }
    public void setCorreoUsuario(String correoUsuario) { this.correoUsuario = correoUsuario; }

    public String getRolUsuario() { return rolUsuario; }
    public void setRolUsuario(String rolUsuario) { this.rolUsuario = rolUsuario; }

    public String getNombreEspacio() { return nombreEspacio; }
    public void setNombreEspacio(String nombreEspacio) { this.nombreEspacio = nombreEspacio; }

    public String getTipoEspacio() { return tipoEspacio; }
    public void setTipoEspacio(String tipoEspacio) { this.tipoEspacio = tipoEspacio; }

    public int getCapacidadEspacio() { return capacidadEspacio; }
    public void setCapacidadEspacio(int capacidadEspacio) { this.capacidadEspacio = capacidadEspacio; }

    public String getEdificioEspacio() { return edificioEspacio; }
    public void setEdificioEspacio(String edificioEspacio) { this.edificioEspacio = edificioEspacio; }

    public boolean isServicioMedico() { return servicioMedico; }
    public void setServicioMedico(boolean servicioMedico) { this.servicioMedico = servicioMedico; }

    public boolean isServicioTI() { return servicioTI; }
    public void setServicioTI(boolean servicioTI) { this.servicioTI = servicioTI; }

    public String getArchivoLayout() { return archivoLayout; }
    public void setArchivoLayout(String archivoLayout) { this.archivoLayout = archivoLayout; }

    public String getArchivoLayoutNombre() { return archivoLayoutNombre; }
    public void setArchivoLayoutNombre(String archivoLayoutNombre) { this.archivoLayoutNombre = archivoLayoutNombre; }

    public String toJson() {
        return String.format(
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
            "\"servicioTI\":%b," +
            "\"archivoLayout\":\"%s\"," +
            "\"archivoLayoutNombre\":\"%s\"," +
            "\"observaciones\":\"%s\"," +
            "\"nombreUsuario\":\"%s\"," +
            "\"correoUsuario\":\"%s\"," +
            "\"rolUsuario\":\"%s\"," +
            "\"nombreEspacio\":\"%s\"," +
            "\"tipoEspacio\":\"%s\"," +
            "\"capacidadEspacio\":%d," +
            "\"edificioEspacio\":\"%s\"" +
            "}",
            idReserva,
            idUsuario,
            idEspacio,
            escapeJson(fechaReserva),
            escapeJson(horaInicio),
            escapeJson(horaFin),
            escapeJson(motivoEvento),
            escapeJson(estadoReserva),
            servicioMedico,
            servicioTI,
            escapeJson(archivoLayout != null ? archivoLayout : ""),
            escapeJson(archivoLayoutNombre != null ? archivoLayoutNombre : ""),
            escapeJson(observaciones),
            escapeJson(nombreUsuario),
            escapeJson(correoUsuario),
            escapeJson(rolUsuario),
            escapeJson(nombreEspacio),
            escapeJson(tipoEspacio),
            capacidadEspacio,
            escapeJson(edificioEspacio != null ? edificioEspacio : "")
        );
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}
