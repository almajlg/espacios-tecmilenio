package com.universidad.espacios.model;

public class Usuario {
    private int idUsuario;
    private String nombreCompleto;
    private String correo;
    private String rol; // "Profesor", "Administrador", "Responsable", "Coordinador", "Mentor"
    private boolean ocultoDirectorio = false;

    public Usuario() {}

    public Usuario(int idUsuario, String nombreCompleto, String correo, String rol) {
        this.idUsuario = idUsuario;
        this.nombreCompleto = nombreCompleto;
        this.correo = correo;
        this.rol = rol;
    }

    public Usuario(int idUsuario, String nombreCompleto, String correo, String rol, boolean ocultoDirectorio) {
        this.idUsuario = idUsuario;
        this.nombreCompleto = nombreCompleto;
        this.correo = correo;
        this.rol = rol;
        this.ocultoDirectorio = ocultoDirectorio;
    }

    public int getIdUsuario() { return idUsuario; }
    public void setIdUsuario(int idUsuario) { this.idUsuario = idUsuario; }

    public String getNombreCompleto() { return nombreCompleto; }
    public void setNombreCompleto(String nombreCompleto) { this.nombreCompleto = nombreCompleto; }

    public String getCorreo() { return correo; }
    public void setCorreo(String correo) { this.correo = correo; }

    public String getRol() { return rol; }
    public void setRol(String rol) { this.rol = rol; }

    public boolean isOcultoDirectorio() { return ocultoDirectorio; }
    public void setOcultoDirectorio(boolean ocultoDirectorio) { this.ocultoDirectorio = ocultoDirectorio; }

    public String toJson() {
        return String.format(
            "{\"idUsuario\":%d,\"nombreCompleto\":\"%s\",\"correo\":\"%s\",\"rol\":\"%s\",\"ocultoDirectorio\":%b}",
            idUsuario,
            escapeJson(nombreCompleto),
            escapeJson(correo),
            escapeJson(rol),
            ocultoDirectorio
        );
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}
