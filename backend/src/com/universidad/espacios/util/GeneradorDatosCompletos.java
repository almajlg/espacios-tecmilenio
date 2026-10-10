package com.universidad.espacios.util;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class GeneradorDatosCompletos {
    public static void main(String[] args) {
        try {
            String path = "backend/campus_espacios_data.json";
            if (!new File(path).exists()) {
                path = "campus_espacios_data.json";
            }
            System.out.println("Leyendo archivo desde: " + path);

            byte[] bytes = Files.readAllBytes(Paths.get(path));
            String content = new String(bytes, StandardCharsets.UTF_8);

            // 1. Corrección exhaustiva de Mojibake / Caracteres corruptos
            Map<String, String> fixes = new LinkedHashMap<>();
            fixes.put("Ã¡", "á"); fixes.put("Ã©", "é"); fixes.put("Ã\u00ad", "í"); fixes.put("Ã³", "ó"); fixes.put("Ãº", "ú");
            fixes.put("Ã ", "Á"); fixes.put("Ã‰", "É"); fixes.put("Ã\u008d", "Í"); fixes.put("Ã“", "Ó"); fixes.put("Ãš", "Ú");
            fixes.put("Ã±", "ñ"); fixes.put("Ã‘", "Ñ");
            fixes.put("SalÃ³n", "Salón"); fixes.put("Saln", "Salón");
            fixes.put("Ilustracin", "Ilustración");
            fixes.put("Optativa prctica", "Optativa práctica");
            fixes.put("Ingls", "Inglés");
            fixes.put("Diseo", "Diseño");
            fixes.put("Evaluacin", "Evaluación");
            fixes.put("Negociacin", "Negociación");
            fixes.put("Ingeniera", "Ingeniería");
            fixes.put("electrnica", "electrónica");
            fixes.put("electrnicos", "electrónicos");
            fixes.put("elctricos", "eléctricos");
            fixes.put("elctrica", "eléctrica");
            fixes.put("Gestin", "Gestión");
            fixes.put("Animacin", "Animación");
            fixes.put("programacin", "programación");
            fixes.put("Teora", "Teoría");
            fixes.put("Estratgica", "Estratégica"); fixes.put("estratgica", "estratégica");
            fixes.put("estratgico", "estratégico");
            fixes.put("Administracin", "Administración");
            fixes.put("Geografa", "Geografía");
            fixes.put("poltica", "política");
            fixes.put("precisin", "precisión");
            fixes.put("Tecnologa", "Tecnología");
            fixes.put("produccin", "producción");
            fixes.put("Mecatrnica", "Mecatrónica");
            fixes.put("Robtica", "Robótica");
            fixes.put("planeacin", "planeación");
            fixes.put("Ftbol", "Fútbol");
            fixes.put("Fotografa", "Fotografía");
            fixes.put("investigacin", "investigación");
            fixes.put("Economa", "Economía");
            fixes.put("Inversin", "Inversión");
            fixes.put("Definicin", "Definición");
            fixes.put("medicin", "medición");
            fixes.put("Generacin", "Generación");
            fixes.put("Tcnicas", "Técnicas");
            fixes.put("edicin", "edición");
            fixes.put("tica", "Ética");
            fixes.put("estadstica", "estadística");
            fixes.put("Introduccin", "Introducción");
            fixes.put("mviles", "móviles");
            fixes.put("giles", "ágiles");
            fixes.put("Matemticas", "Matemáticas");
            fixes.put("Francs", "Francés");
            fixes.put("Hernndez", "Hernández");
            fixes.put("Rubn", "Rubén");
            fixes.put("Chvez", "Chávez");
            fixes.put("Lpez", "López");
            fixes.put("Ziga", "Zúñiga");
            fixes.put("Manrquez", "Manríquez");
            fixes.put("ngel", "Ángel");
            fixes.put("Snchez", "Sánchez");
            fixes.put("Jess", "Jesús");
            fixes.put("Jos", "José");
            fixes.put("Pea", "Peña");
            fixes.put("Iiguez", "Iñiguez");
            fixes.put("Mara", "María");
            fixes.put("Jurez", "Juárez");
            fixes.put("Adn", "Adán");
            fixes.put("Martnez", "Martínez");
            fixes.put("Garca", "García");
            fixes.put("Ramrez", "Ramírez");
            fixes.put("Len", "León");
            fixes.put("Josu", "Josué");
            fixes.put("Montaez", "Montañez");
            fixes.put("Prez", "Pérez");
            fixes.put("Vctor", "Víctor");
            fixes.put("Briceo", "Briceño");
            fixes.put("Daz", "Díaz");
            fixes.put("Gutirrez", "Gutiérrez");
            fixes.put("Roco", "Rocío");
            fixes.put("Csar", "César");
            fixes.put("Fabin", "Fabián");
            fixes.put("Damin", "Damián");
            fixes.put("Quiones", "Quiñones");
            fixes.put("Pealoza", "Peñaloza");
            fixes.put("Coordinacin", "Coordinación");
            fixes.put("Acadmica", "Académica");

            for (Map.Entry<String, String> entry : fixes.entrySet()) {
                content = content.replace(entry.getKey(), entry.getValue());
            }

            // 2. Guardar archivo con encoding UTF-8 puro
            Files.write(Paths.get(path), content.getBytes(StandardCharsets.UTF_8));
            System.out.println("Archivo limpiado de acentos corruptos y guardado con UTF-8.");
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
