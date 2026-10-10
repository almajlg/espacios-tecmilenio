package com.universidad.espacios.model;

public class Usuario {
    private int idUsuario;
    private String nombreCompleto;
    private String correo;
    private String rol; // "Profesor", "Administrador", "Responsable"

    public Usuario() {}

    public Usuario(int idUsuario, String nombreCompleto, String correo, String rol) {
        this.idUsuario = idUsuario;
        this.nombreCompleto = nombreCompleto;
        this.correo = correo;
        this.rol = rol;
    }

    public int getIdUsuario() { return idUsuario; }
    public void setIdUsuario(int idUsuario) { this.idUsuario = idUsuario; }

    public String getNombreCompleto() { return nombreCompleto; }
    public void setNombreCompleto(String nombreCompleto) { this.nombreCompleto = nombreCompleto; }

    public String getCorreo() { return correo; }
    public void setCorreo(String correo) { this.correo = correo; }

    public String getRol() { return rol; }
    public void setRol(String rol) { this.rol = rol; }

    public String toJson() {
        return String.format(
            "{\"idUsuario\":%d,\"nombreCompleto\":\"%s\",\"correo\":\"%s\",\"rol\":\"%s\"}",
            idUsuario,
            escapeJson(nombreCompleto),
            escapeJson(correo),
            escapeJson(rol)
        );
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}
