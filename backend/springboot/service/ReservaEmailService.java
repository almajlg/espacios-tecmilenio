package com.universidad.espacios.springboot.service;

import com.universidad.espacios.model.Reserva;
import com.universidad.espacios.service.IcsCalendarService;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Servicio de Spring Boot para el envío automático de confirmaciones de citas por correo.
 * 
 * Cumple con los requerimientos de:
 * 1. Envío de invitación de calendario en memoria (.ics) compatible con Microsoft Outlook.
 * 2. Generación y adjunto del comprobante oficial en formato Word (.docx con Apache POI).
 * 3. Lógica condicional de copias (CC) basada en los checkboxes del formulario:
 *    - ¿Requiere Tecmi Care?      -> Copia a CORREO_TECMI_CARE
 *    - ¿Requiere Soporte de TI?    -> Copia a CORREO_SOPORTE_TI
 *    - ¿Requiere Planta Física?   -> Copia a CORREO_PLANTA_FISICA
 * 4. Placeholders claros y fáciles de modificar para cuentas de correo.
 */
@Service
public class ReservaEmailService {

    // ====================================================================
    // PLACEHOLDERS CONFIGURABLES DE CORREOS DEPARTAMENTALES
    // (Modifica estos valores con las direcciones institucionales finales)
    // ====================================================================
    public static final String CORREO_REMITENTE_DEFAULT = "CORREO_REMITENTE_SISTEMA_AQUI@tecmilenio.mx";
    public static final String CORREO_TECMI_CARE        = "CORREO_TECMI_CARE_AQUI@tecmilenio.mx";
    public static final String CORREO_SOPORTE_TI        = "CORREO_SOPORTE_TI_AQUI@tecmilenio.mx";
    public static final String CORREO_PLANTA_FISICA     = "CORREO_PLANTA_FISICA_AQUI@tecmilenio.mx";

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${spring.mail.username:" + CORREO_REMITENTE_DEFAULT + "}")
    private String remitenteConfigurado;

    /**
     * Resultado estructurado del envío de correo para auditoría y respuesta API.
     */
    public static class EmailEnvioResult {
        private final boolean enviado;
        private final String destinatarioPrincipal;
        private final List<String> copiasCC;
        private final String mensaje;
        private final String error;

        public EmailEnvioResult(boolean enviado, String destinatarioPrincipal, List<String> copiasCC, String mensaje, String error) {
            this.enviado = enviado;
            this.destinatarioPrincipal = destinatarioPrincipal;
            this.copiasCC = copiasCC;
            this.mensaje = mensaje;
            this.error = error;
        }

        public boolean isEnviado() { return enviado; }
        public String getDestinatarioPrincipal() { return destinatarioPrincipal; }
        public List<String> getCopiasCC() { return copiasCC; }
        public String getMensaje() { return mensaje; }
        public String getError() { return error; }
    }

    /**
     * Ejecuta el flujo integral de envío:
     * - Evalúa los checkboxes de la reserva.
     * - Genera el .ics en memoria.
     * - Genera el .docx en memoria con Apache POI.
     * - Construye y envía el MimeMessage mediante JavaMailSender.
     *
     * @param reserva Objeto de la reserva recién guardada en la base de datos
     * @return EmailEnvioResult con el detalle de destinatarios y estatus del envío
     */
    public EmailEnvioResult enviarConfirmacionCita(Reserva reserva) {
        if (reserva == null) {
            return new EmailEnvioResult(false, null, List.of(), "Reserva nula", "Datos de reserva inválidos");
        }

        String destinatarioTo = reserva.getCorreoUsuario();
        if (destinatarioTo == null || destinatarioTo.trim().isEmpty()) {
            return new EmailEnvioResult(false, null, List.of(), "Sin correo del usuario", "El solicitante no tiene correo institucional registrado");
        }
        destinatarioTo = destinatarioTo.trim();

        // 1. LÓGICA CONDICIONAL DE COPIAS (CC) BASADA EN LOS CHECKBOXES
        List<String> listaCC = new ArrayList<>();

        if (reserva.isServicioMedico()) {
            listaCC.add(CORREO_TECMI_CARE);
        }
        if (reserva.isServicioTI()) {
            listaCC.add(CORREO_SOPORTE_TI);
        }
        if (reserva.isServicioPlantaFisica()) {
            listaCC.add(CORREO_PLANTA_FISICA);
        }

        try {
            if (mailSender == null) {
                // Modo simulado / preventivo si JavaMailSender no ha sido inyectado en entorno local
                System.out.println("[ReservaEmailService] Aviso: JavaMailSender no configurado. Modo demostración activo.");
                System.out.println(" • Destinatario To: " + destinatarioTo);
                System.out.println(" • Destinatarios CC: " + listaCC);
                return new EmailEnvioResult(true, destinatarioTo, listaCC, 
                        "Correo preparado exitosamente (Modo simulación / JavaMailSender pendiente de credenciales SMTP)", null);
            }

            MimeMessage mimeMessage = mailSender.createMimeMessage();
            
            // MimeMessageHelper multipart = true para adjuntar archivos y cuerpo HTML
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            String remitente = (remitenteConfigurado != null && !remitenteConfigurado.isEmpty()) 
                    ? remitenteConfigurado : CORREO_REMITENTE_DEFAULT;
            helper.setFrom(remitente, "Control de Espacios • Universidad Tecmilenio");
            helper.setTo(destinatarioTo);

            if (!listaCC.isEmpty()) {
                helper.setCc(listaCC.toArray(new String[0]));
            }

            String folio = String.format("TM-CUAU-%04d", reserva.getIdReserva());
            String asunto = "Confirmación de Espacio Universitario - #" + folio + " - " + reserva.getMotivoEvento();
            helper.setSubject(asunto);

            // 2. CUERPO DEL CORREO EN HTML ESTILIZADO
            String cuerpoHtml = construirCuerpoCorreoHtml(reserva, folio, listaCC);
            helper.setText(cuerpoHtml, true);

            // 3. GENERAR Y ADJUNTAR ARCHIVO .ICS DE CALENDARIO EN MEMORIA (RFC 5545)
            byte[] icsBytes = IcsCalendarService.generarIcsBytes(reserva, remitente);
            ByteArrayResource icsResource = new ByteArrayResource(icsBytes);
            helper.addAttachment("invitacion_calendario.ics", icsResource, "text/calendar; charset=UTF-8; method=REQUEST");

            // 4. GENERAR Y ADJUNTAR COMPROBANTE OFICIAL WORD (.DOCX) CON APACHE POI
            byte[] docxBytes = WordComprobanteGenerator.generarComprobanteDocx(reserva);
            ByteArrayResource docxResource = new ByteArrayResource(docxBytes);
            String nombreDocx = "Comprobante_Reserva_" + folio + ".docx";
            helper.addAttachment(nombreDocx, docxResource, "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

            // 5. ENVÍO DEFINITIVO POR JAVAMAILSENDER
            mailSender.send(mimeMessage);

            return new EmailEnvioResult(true, destinatarioTo, listaCC, 
                    "Correo de confirmación enviado exitosamente con .ics y comprobante Word adjuntos.", null);

        } catch (Exception e) {
            e.printStackTrace();
            return new EmailEnvioResult(false, destinatarioTo, listaCC, 
                    "Error al enviar correo: " + e.getMessage(), e.getMessage());
        }
    }

    /**
     * Construye una plantilla HTML responsiva con la identidad institucional de Tecmilenio.
     */
    private String construirCuerpoCorreoHtml(Reserva r, String folio, List<String> copiasCC) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset='UTF-8'></head>");
        sb.append("<body style='font-family: Segoe UI, Arial, sans-serif; background-color: #f8faf8; margin: 0; padding: 20px; color: #1e293b;'>");
        sb.append("<div style='max-width: 620px; margin: 0 auto; background: #ffffff; border-radius: 12px; border: 1px solid #e2e8f0; overflow: hidden; box-shadow: 0 4px 12px rgba(0,0,0,0.05);'>");

        // Header verde
        sb.append("<div style='background: #005934; padding: 22px 28px; color: #ffffff;'>");
        sb.append("<div style='color: #68c143; font-weight: bold; font-size: 13px; text-transform: uppercase; letter-spacing: 1px;'>Universidad Tecmilenio • Campus Cuautitlán</div>");
        sb.append("<h1 style='margin: 6px 0 0; font-size: 20px; font-weight: 700;'>¡Tu reservación ha sido confirmada!</h1>");
        sb.append("</div>");

        // Contenido
        sb.append("<div style='padding: 24px 28px;'>");
        sb.append("<p style='font-size: 15px; margin-top: 0;'>Estimado(a) <strong>").append(r.getNombreUsuario() != null ? r.getNombreUsuario() : "Colaborador").append("</strong>,</p>");
        sb.append("<p style='font-size: 14px; color: #475569;'>Se ha registrado exitosamente la reserva de tu espacio en la plataforma. En este correo encontrarás adjuntos tu <strong>invitación de Outlook (.ics)</strong> para agregarla a tu calendario y tu <strong>comprobante oficial en Word (.docx)</strong>.</p>");

        // Tarjeta resumen
        sb.append("<div style='background: #f0f9ed; border-left: 4px solid #68c143; border-radius: 6px; padding: 14px 18px; margin: 20px 0;'>");
        sb.append("<table style='width: 100%; font-size: 14px; border-collapse: collapse;'>");
        sb.append("<tr><td style='padding: 4px 0; color: #64748b; width: 140px;'>Folio Digital:</td><td><strong style='color:#005934;'>#").append(folio).append("</strong></td></tr>");
        sb.append("<tr><td style='padding: 4px 0; color: #64748b;'>Evento / Clase:</td><td><strong>").append(r.getMotivoEvento()).append("</strong></td></tr>");
        sb.append("<tr><td style='padding: 4px 0; color: #64748b;'>Espacio:</td><td><strong>").append(r.getNombreEspacio()).append("</strong></td></tr>");
        sb.append("<tr><td style='padding: 4px 0; color: #64748b;'>Fecha:</td><td><strong>").append(r.getFechaReserva()).append("</strong></td></tr>");
        sb.append("<tr><td style='padding: 4px 0; color: #64748b;'>Horario:</td><td><strong>").append(r.getHoraInicio()).append(" a ").append(r.getHoraFin()).append(" hrs</strong></td></tr>");
        sb.append("</table>");
        sb.append("</div>");

        // Sección de servicios
        sb.append("<div style='margin-bottom: 20px;'>");
        sb.append("<strong style='font-size: 14px; color: #005934;'>Departamentos Notificados en Copia (CC):</strong>");
        if (copiasCC.isEmpty()) {
            sb.append("<p style='font-size: 13px; color: #64748b; margin: 4px 0 0;'>Ningún servicio especial requerido.</p>");
        } else {
            sb.append("<ul style='font-size: 13px; color: #334155; margin: 6px 0 0; padding-left: 20px;'>");
            if (r.isServicioMedico()) sb.append("<li><strong>Tecmi Care:</strong> Asistencia médica en sitio notificada en copia.</li>");
            if (r.isServicioTI()) sb.append("<li><strong>Soporte de TI:</strong> Apoyo multimedia/cómputo notificado en copia.</li>");
            if (r.isServicioPlantaFisica()) sb.append("<li><strong>Planta Física:</strong> Mobiliario y acomodo notificado en copia.</li>");
            sb.append("</ul>");
        }
        sb.append("</div>");

        // Indicación para aceptar el .ics
        sb.append("<div style='background: #eff6ff; border: 1px solid #bfdbfe; border-radius: 8px; padding: 12px 16px; margin: 20px 0; font-size: 13px; color: #1e40af;'>");
        sb.append("<strong>📅 ¿Cómo añadir a tu calendario de Outlook?</strong><br/>");
        sb.append("Haz clic en el archivo adjunto <strong>invitacion_calendario.ics</strong> o presiona el botón <em>'Aceptar'</em> en el encabezado de tu cliente Outlook para integrarlo directamente a tu agenda institucional.");
        sb.append("</div>");

        sb.append("<p style='font-size: 12px; color: #94a3b8; margin-top: 30px; border-top: 1px solid #e2e8f0; padding-top: 12px;'>");
        sb.append("Este es un correo automático emitido por la Plataforma de Control de Espacios de Universidad Tecmilenio Campus Cuautitlán.");
        sb.append("</p>");

        sb.append("</div></div></body></html>");
        return sb.toString();
    }
}
