package com.universidad.espacios.springboot.service;

import com.universidad.espacios.model.Reserva;
import org.apache.poi.xwpf.usermodel.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Generador de Comprobantes Oficiales de Reserva en formato Microsoft Word (.docx)
 * utilizando la biblioteca Apache POI (poi-ooxml).
 * 
 * Diseña un documento formal con la identidad de Universidad Tecmilenio,
 * tabla de especificaciones, servicios requeridos y sellos de validación.
 */
public class WordComprobanteGenerator {

    /**
     * Genera en memoria el archivo Word (.docx) como un arreglo de bytes.
     *
     * @param reserva Datos de la reserva agendada
     * @return Arreglo de bytes del documento .docx
     * @throws IOException Si ocurre un error al escribir el documento
     */
    public static byte[] generarComprobanteDocx(Reserva reserva) throws IOException {
        try (XWPFDocument doc = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            // 1. TÍTULO INSTITUCIONAL
            XWPFParagraph headerPara = doc.createParagraph();
            headerPara.setAlignment(ParagraphAlignment.CENTER);
            headerPara.setSpacingAfter(100);

            XWPFRun runUni = headerPara.createRun();
            runUni.setText("UNIVERSIDAD TECMILENIO");
            runUni.setBold(true);
            runUni.setFontSize(18);
            runUni.setFontFamily("Segoe UI");
            runUni.setColor("005934"); // Verde Tecmilenio oscuro
            runUni.addBreak();

            XWPFRun runCampus = headerPara.createRun();
            runCampus.setText("CAMPUS CUAUTITLÁN IZCALLI • CONTROL DE ESPACIOS");
            runCampus.setBold(true);
            runCampus.setFontSize(11);
            runCampus.setFontFamily("Segoe UI");
            runCampus.setColor("68C143"); // Verde Tecmilenio vivo
            runCampus.addBreak();

            XWPFRun runSub = headerPara.createRun();
            runSub.setText("COMPROBANTE OFICIAL DE RESERVACIÓN DE ESPACIO");
            runSub.setBold(true);
            runSub.setFontSize(13);
            runSub.setFontFamily("Segoe UI");
            runSub.setColor("1E293B");
            runSub.addBreak();

            // 2. FOLIO Y FECHA DE EMISIÓN
            XWPFParagraph folioPara = doc.createParagraph();
            folioPara.setAlignment(ParagraphAlignment.RIGHT);
            folioPara.setSpacingAfter(200);

            XWPFRun runFolio = folioPara.createRun();
            String folio = String.format("TM-CUAU-%04d", reserva.getIdReserva());
            runFolio.setText("FOLIO DIGITAL: #" + folio);
            runFolio.setBold(true);
            runFolio.setFontSize(11);
            runFolio.setColor("005934");
            runFolio.addBreak();

            XWPFRun runFechaEmision = folioPara.createRun();
            runFechaEmision.setText("Fecha de Registro: " + reserva.getFechaReserva());
            runFechaEmision.setFontSize(9);
            runFechaEmision.setColor("64748B");

            // 3. TABLA DE DETALLES DEL EVENTO
            XWPFTable table = doc.createTable();
            table.setWidth("100%");

            agregarFilaTabla(table, "Nombre del Evento / Clase:", reserva.getMotivoEvento(), true);
            
            String espacioTxt = (reserva.getNombreEspacio() != null ? reserva.getNombreEspacio() : "N/A") +
                                (reserva.getEdificioEspacio() != null && !reserva.getEdificioEspacio().isEmpty() 
                                    ? " (" + reserva.getEdificioEspacio() + ")" : "");
            agregarFilaTabla(table, "Espacio Asignado:", espacioTxt, false);

            agregarFilaTabla(table, "Fecha de la Reserva:", reserva.getFechaReserva(), false);
            agregarFilaTabla(table, "Horario Programado:", reserva.getHoraInicio() + " a " + reserva.getHoraFin() + " hrs", false);

            String solicitante = (reserva.getNombreUsuario() != null ? reserva.getNombreUsuario() : "Responsable") +
                                 (reserva.getRolUsuario() != null ? " (" + reserva.getRolUsuario() + ")" : "");
            agregarFilaTabla(table, "Solicitante / Responsable:", solicitante, false);

            if (reserva.getCorreoUsuario() != null && !reserva.getCorreoUsuario().isEmpty()) {
                agregarFilaTabla(table, "Correo Institucional:", reserva.getCorreoUsuario(), false);
            }

            // SERVICIOS REQUERIDOS
            StringBuilder sbServ = new StringBuilder();
            if (reserva.isServicioMedico()) sbServ.append("✔ Tecmi Care (Asistencia Médica en sitio)\n");
            if (reserva.isServicioTI()) sbServ.append("✔ Soporte de TI (Cómputo, Audio, Proyector)\n");
            if (reserva.isServicioPlantaFisica()) sbServ.append("✔ Planta Física (Mobiliario, Acomodo, Montaje)\n");
            if (sbServ.length() == 0) sbServ.append("Ninguno solicitado.");
            agregarFilaTabla(table, "Servicios Requeridos:", sbServ.toString().trim(), false);

            if (reserva.getArchivoLayoutNombre() != null && !reserva.getArchivoLayoutNombre().isEmpty()) {
                agregarFilaTabla(table, "Layout / Croquis Adjunto:", reserva.getArchivoLayoutNombre(), false);
            }

            if (reserva.getObservaciones() != null && !reserva.getObservaciones().trim().isEmpty()) {
                agregarFilaTabla(table, "Observaciones / Acomodo:", reserva.getObservaciones(), false);
            }

            agregarFilaTabla(table, "Estado de la Reserva:", reserva.getEstadoReserva(), false);

            // 4. POLÍTICAS Y REGLAS INSTITUCIONALES
            XWPFParagraph terminosPara = doc.createParagraph();
            terminosPara.setSpacingBefore(300);
            terminosPara.setSpacingAfter(150);

            XWPFRun runTerminosTitle = terminosPara.createRun();
            runTerminosTitle.setText("LINEAMIENTOS INSTITUCIONALES DE USO DE ESPACIOS:");
            runTerminosTitle.setBold(true);
            runTerminosTitle.setFontSize(10);
            runTerminosTitle.setColor("005934");
            runTerminosTitle.addBreak();

            XWPFRun runTerminos = terminosPara.createRun();
            runTerminos.setFontSize(9);
            runTerminos.setColor("475569");
            runTerminos.setText("1. Presentar este comprobante impreso o digital ante Seguridad y Planta Física al momento de acceder.");
            runTerminos.addBreak();
            runTerminos.setText("2. El espacio debe entregarse limpio, con el mobiliario ordenado y equipos apagados.");
            runTerminos.addBreak();
            runTerminos.setText("3. Las áreas involucradas (TI, Tecmi Care y Planta Física) han sido notificadas automáticamente en copia.");
            runTerminos.addBreak();

            // 5. FIRMAS DE CONFORMIDAD
            XWPFParagraph firmasPara = doc.createParagraph();
            firmasPara.setSpacingBefore(400);
            firmasPara.setAlignment(ParagraphAlignment.CENTER);

            XWPFRun runFirmas = firmasPara.createRun();
            runFirmas.setFontSize(9);
            runFirmas.setColor("334155");
            runFirmas.setText("____________________________________            ____________________________________");
            runFirmas.addBreak();
            runFirmas.setText("Firma del Responsable / Solicitante                      Coordinación de Espacios y Logística");
            runFirmas.addBreak();

            doc.write(out);
            return out.toByteArray();
        }
    }

    private static void agregarFilaTabla(XWPFTable table, String campo, String valor, boolean resaltar) {
        XWPFTableRow row = table.createRow();
        
        // Celda Campo
        XWPFTableCell cellCampo = row.getCell(0);
        if (cellCampo == null) cellCampo = row.createCell();
        cellCampo.setColor("F8FAFC");
        XWPFParagraph pCampo = cellCampo.getParagraphArray(0);
        if (pCampo == null) pCampo = cellCampo.addParagraph();
        XWPFRun rCampo = pCampo.createRun();
        rCampo.setText(campo);
        rCampo.setBold(true);
        rCampo.setFontSize(10);
        rCampo.setFontFamily("Segoe UI");
        rCampo.setColor("334155");

        // Celda Valor
        XWPFTableCell cellValor = row.getCell(1);
        if (cellValor == null) cellValor = row.createCell();
        XWPFParagraph pValor = cellValor.getParagraphArray(0);
        if (pValor == null) pValor = cellValor.addParagraph();
        XWPFRun rValor = pValor.createRun();
        rValor.setText(valor != null ? valor : "-");
        rValor.setFontSize(10);
        rValor.setFontFamily("Segoe UI");
        if (resaltar) {
            rValor.setBold(true);
            rValor.setColor("005934");
        } else {
            rValor.setColor("0F172A");
        }
    }
}
