package com.universidad.espacios.springboot.controller;

import com.universidad.espacios.model.Reserva;
import com.universidad.espacios.service.DataStore;
import com.universidad.espacios.springboot.service.ReservaEmailService;
import com.universidad.espacios.springboot.service.ReservaEmailService.EmailEnvioResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Controlador REST de Spring Boot para la gestión de reservas y disparo
 * automático de invitaciones por correo electrónico.
 */
@RestController
@RequestMapping("/api/reservas")
@CrossOrigin(origins = "*", allowedHeaders = "*", methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE})
public class ReservaSpringController {

    @Autowired
    private DataStore dataStore;

    @Autowired
    private ReservaEmailService emailService;

    /**
     * DTO de entrada para la creación de reservas.
     */
    public static class ReservaRequestDTO {
        public int idUsuario;
        public int idEspacio;
        public String fechaReserva;
        public String horaInicio;
        public String horaFin;
        public String motivoEvento;
        public String observaciones;
        public boolean servicioMedico;
        public boolean servicioTI;
        public boolean servicioPlantaFisica;
        public String archivoLayout;
        public String archivoLayoutNombre;
        public boolean enviarInvitacionCorreo = true; // Por defecto envía correo
    }

    /**
     * Endpoint principal: Guarda la cita en la base de datos y de manera inmediata
     * ejecuta el servicio de envío de correo con JavaMailSender (.ics + .docx + CC condicionales).
     */
    @PostMapping
    public ResponseEntity<?> crearReserva(@RequestBody ReservaRequestDTO req) {
        try {
            // 1. Validaciones de negocio
            if (req.idUsuario <= 0 || req.idEspacio <= 0) {
                return ResponseEntity.badRequest().body(Map.of("error", "Debe seleccionar un usuario y un espacio válidos."));
            }
            if (req.fechaReserva == null || req.horaInicio == null || req.horaFin == null ||
                req.fechaReserva.isEmpty() || req.horaInicio.isEmpty() || req.horaFin.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Fecha, hora de inicio y fin son obligatorias."));
            }
            if (req.motivoEvento == null || req.motivoEvento.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "El nombre del evento es obligatorio."));
            }

            // 2. Guardar en Base de Datos
            Reserva reservaGuardada = dataStore.addReserva(
                req.idUsuario,
                req.idEspacio,
                req.fechaReserva,
                req.horaInicio,
                req.horaFin,
                req.motivoEvento.trim(),
                req.observaciones,
                req.servicioMedico,
                req.servicioTI,
                req.servicioPlantaFisica,
                req.archivoLayout,
                req.archivoLayoutNombre
            );

            // 3. Ejecutar de inmediato el envío de correo si está solicitado
            EmailEnvioResult emailResult = null;
            if (req.enviarInvitacionCorreo) {
                emailResult = emailService.enviarConfirmacionCita(reservaGuardada);
            }

            // 4. Preparar respuesta integral
            Map<String, Object> response = new HashMap<>();
            response.put("reserva", reservaGuardada);
            response.put("idReserva", reservaGuardada.getIdReserva());
            response.put("success", true);

            if (emailResult != null) {
                response.put("emailNotificacion", Map.of(
                    "enviado", emailResult.isEnviado(),
                    "destinatario", emailResult.getDestinatarioPrincipal() != null ? emailResult.getDestinatarioPrincipal() : "",
                    "copiasCC", emailResult.getCopiasCC(),
                    "mensaje", emailResult.getMensaje(),
                    "error", emailResult.getError() != null ? emailResult.getError() : ""
                ));
            }

            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error interno al procesar la reserva: " + e.getMessage()));
        }
    }

    /**
     * Endpoint manual para reenviar o exportar la invitación (.ics + comprobante Word)
     * de una reserva existente en cualquier momento.
     */
    @PostMapping("/{id}/enviar-invitacion")
    public ResponseEntity<?> reenviarInvitacionManual(@PathVariable("id") int id) {
        try {
            Reserva reserva = dataStore.getReserva(id);
            if (reserva == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Reserva #" + id + " no encontrada."));
            }

            EmailEnvioResult result = emailService.enviarConfirmacionCita(reserva);

            Map<String, Object> resp = new HashMap<>();
            resp.put("idReserva", id);
            resp.put("enviado", result.isEnviado());
            resp.put("destinatario", result.getDestinatarioPrincipal());
            resp.put("copiasCC", result.getCopiasCC());
            resp.put("mensaje", result.getMensaje());
            if (result.getError() != null) resp.put("error", result.getError());

            return ResponseEntity.ok(resp);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error al enviar invitación: " + e.getMessage()));
        }
    }
}
