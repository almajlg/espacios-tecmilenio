-- ====================================================================
-- SISTEMA DE GESTIÓN Y AGENDAMIENTO DE ESPACIOS UNIVERSITARIOS
-- Base de datos: campus_espacios
-- Compatible con: MySQL 8.0+ / MariaDB 10.4+
-- ====================================================================

CREATE DATABASE IF NOT EXISTS campus_espacios 
CHARACTER SET utf8mb4 
COLLATE utf8mb4_spanish_ci;

USE campus_espacios;

-- Desactivar llaves foráneas para reinicio limpio si existe
SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS Reservas;
DROP TABLE IF EXISTS Espacios;
DROP TABLE IF EXISTS Usuarios;
SET FOREIGN_KEY_CHECKS = 1;

-- ====================================================================
-- 1. TABLA: Usuarios
-- ====================================================================
CREATE TABLE Usuarios (
    id_usuario INT AUTO_INCREMENT PRIMARY KEY,
    nombre_completo VARCHAR(100) NOT NULL,
    correo VARCHAR(100) NOT NULL UNIQUE,
    rol ENUM('Profesor', 'Administrador', 'Responsable', 'Colaborador') NOT NULL,
    creado_en TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- ====================================================================
-- 2. TABLA: Espacios
-- Reglas de negocio:
--   - 'Edificio1': Salones Preparatoria (1102 a 1310)
--   - 'Edificio2': Salones Profesional (2201 a 2405)
--   - 'Aldea': Salones Aldea (2101 al 2113)
--   - 'Otro': Espacios abiertos/especiales (Terraza, Claustro, SUM, etc.)
--   - 'Laboratorio', 'Cancha', 'Estacionamiento'
-- ====================================================================
CREATE TABLE Espacios (
    id_espacio INT AUTO_INCREMENT PRIMARY KEY,
    nombre_espacio VARCHAR(50) NOT NULL,
    tipo_espacio ENUM('Edificio1', 'Edificio2', 'Aldea', 'Laboratorio', 'Cancha', 'Estacionamiento', 'Otro') NOT NULL,
    capacidad INT NOT NULL,
    activo TINYINT(1) DEFAULT 1,
    creado_en TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_tipo (tipo_espacio),
    INDEX idx_activo (activo)
) ENGINE=InnoDB;

-- ====================================================================
-- 3. TABLA: Reservas
-- Reglas de negocio:
--   - No se permite traslape de horario en el mismo espacio y fecha para reservas 'Activa'.
-- ====================================================================
CREATE TABLE Reservas (
    id_reserva INT AUTO_INCREMENT PRIMARY KEY,
    id_usuario INT NOT NULL,
    id_espacio INT NOT NULL,
    fecha_reserva DATE NOT NULL,
    hora_inicio TIME NOT NULL,
    hora_fin TIME NOT NULL,
    motivo_evento VARCHAR(255) NOT NULL,
    estado_reserva ENUM('Activa', 'Cancelada', 'Completada') DEFAULT 'Activa',
    observaciones TEXT NULL,
    creado_en TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    -- Restricciones de integridad referencial
    CONSTRAINT fk_usuario FOREIGN KEY (id_usuario) 
        REFERENCES Usuarios(id_usuario) ON DELETE CASCADE,
    CONSTRAINT fk_espacio FOREIGN KEY (id_espacio) 
        REFERENCES Espacios(id_espacio) ON DELETE CASCADE,
        
    -- Índices para búsqueda rápida de disponibilidad y traslapes
    INDEX idx_disponibilidad (id_espacio, fecha_reserva, estado_reserva),
    INDEX idx_fecha (fecha_reserva)
) ENGINE=InnoDB;

-- ====================================================================
-- 4. DISPARADOR (TRIGGER): Validar que el espacio pertenezca a Edificio 1
-- o a Edificio 2 (La Aldea) según su numeración
-- ====================================================================
DELIMITER $$
CREATE TRIGGER trg_validar_espacio_edificio
BEFORE INSERT ON Espacios
FOR EACH ROW
BEGIN
    -- Si el salón inicia con 21 o se menciona Aldea, asignarlo a Edificio 2 (La Aldea)
    IF NEW.nombre_espacio REGEXP '^[2][1][0-9]{2}' OR LOWER(NEW.nombre_espacio) LIKE '%aldea%' THEN
        SET NEW.tipo_espacio = 'Aldea';
    -- Los salones restantes (1xxx, 22xx, 23xx, 24xx, etc.) pasan al Edificio 1
    ELSEIF NEW.nombre_espacio REGEXP '^[0-9]{4}' OR LOWER(NEW.nombre_espacio) LIKE '%edificio 1%' THEN
        SET NEW.tipo_espacio = 'Edificio1';
    END IF;
    -- Si es Terraza y no se especificó 'Otro', cambiar a 'Otro'
    IF LOWER(NEW.nombre_espacio) LIKE '%terraza%' THEN
        SET NEW.tipo_espacio = 'Otro';
    END IF;
END$$

-- ====================================================================
-- DISPARADOR: Prevenir doble reserva (traslapes de horario)
-- ====================================================================
CREATE TRIGGER trg_prevenir_doble_reserva
BEFORE INSERT ON Reservas
FOR EACH ROW
BEGIN
    DECLARE traslapes INT;
    
    IF NEW.hora_inicio >= NEW.hora_fin THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'La hora de inicio debe ser anterior a la hora de fin.';
    END IF;

    IF NEW.estado_reserva = 'Activa' THEN
        SELECT COUNT(*) INTO traslapes
        FROM Reservas
        WHERE id_espacio = NEW.id_espacio
          AND fecha_reserva = NEW.fecha_reserva
          AND estado_reserva = 'Activa'
          AND (
              (NEW.hora_inicio >= hora_inicio AND NEW.hora_inicio < hora_fin)
              OR (NEW.hora_fin > hora_inicio AND NEW.hora_fin <= hora_fin)
              OR (NEW.hora_inicio <= hora_inicio AND NEW.hora_fin >= hora_fin)
          );
          
        IF traslapes > 0 THEN
            SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Conflicto de horario: Este espacio ya se encuentra reservado en el rango seleccionado.';
        END IF;
    END IF;
END$$
DELIMITER ;

-- ====================================================================
-- 5. DATOS INICIALES (SEEDS)
-- ====================================================================

-- Usuarios (Profesores, Administradores y Responsables)
INSERT INTO Usuarios (nombre_completo, correo, rol) VALUES
    ('Diana Angelica Barrios Hernández', 'diana.barrios@universidad.edu.mx', 'Administrador'),
    ('Natalie Luna', 'natalie.luna@universidad.edu.mx', 'Responsable'),
    ('Idania Lopez', 'idania.lopez@universidad.edu.mx', 'Responsable'),
    ('Jonathan Salazar', 'jonathan.salazar@universidad.edu.mx', 'Profesor'),
    ('Lilia Tapia', 'lilia.tapia@universidad.edu.mx', 'Responsable'),
    ('Jibsam Peñaloza', 'jibsam.penaloza@universidad.edu.mx', 'Responsable'),
    ('Luis Manuel Arenas Merchant', 'luis.arenas@universidad.edu.mx', 'Profesor'),
    ('Raymundo Jacinto', 'raymundo.jacinto@universidad.edu.mx', 'Responsable'),
    ('Silvia Reyes León', 'silvia.reyes@universidad.edu.mx', 'Profesor'),
    ('Julio Moreno', 'julio.moreno@universidad.edu.mx', 'Responsable'),
    ('Rubén Hernández Uvilla', 'ruben.hernandez@universidad.edu.mx', 'Profesor'),
    ('David Villalobos Chávez', 'david.villalobos@universidad.edu.mx', 'Profesor'),
    ('Lily Daniella Torres Reyes', 'lily.torres@universidad.edu.mx', 'Profesor'),
    ('María Fernanda Cárdenas García', 'maria.cardenas@universidad.edu.mx', 'Profesor'),
    ('Erika Van Scoit Arizmendi', 'erika.vanscoit@universidad.edu.mx', 'Profesor'),
    ('Jesús Fernando Sánchez Becerril', 'jesus.sanchez@universidad.edu.mx', 'Profesor');

-- Espacios (Edificio 1, Edificio 2 La Aldea, Labs, Canchas, Terraza 'Otro')
INSERT INTO Espacios (nombre_espacio, tipo_espacio, capacidad, activo) VALUES
    -- Terraza ('Otro' según requerimiento explícito)
    ('Terraza', 'Otro', 50, 1),

    -- Salones Edificio 2 (Complejo La Aldea): Únicamente los que inician en 21__
    ('2101', 'Aldea', 30, 1),
    ('2102', 'Aldea', 30, 1),
    ('2103', 'Aldea', 30, 1),
    ('2104', 'Aldea', 30, 1),
    ('2105', 'Aldea', 30, 1),
    ('2106', 'Aldea', 30, 1),
    ('2107', 'Aldea', 30, 1),
    ('2108', 'Aldea', 30, 1),
    ('2109', 'Aldea', 30, 1),
    ('2110', 'Aldea', 30, 1),
    ('2111', 'Aldea', 30, 1),
    ('2112', 'Aldea', 30, 1),
    ('2113', 'Aldea', 30, 1),

    -- Salones Edificio 1 (1103-1310 y salones restantes 2201-2405)
    ('1103', 'Edificio1', 30, 1),
    ('1104', 'Edificio1', 30, 1),
    ('1201', 'Edificio1', 30, 1),
    ('1203', 'Edificio1', 30, 1),
    ('1308', 'Edificio1', 30, 1),
    ('1310', 'Edificio1', 30, 1),
    ('2201', 'Edificio1', 30, 1),
    ('2202', 'Edificio1', 30, 1),
    ('2203', 'Edificio1', 30, 1),
    ('2204', 'Edificio1', 30, 1),
    ('2205', 'Edificio1', 30, 1),
    ('2302', 'Edificio1', 30, 1),
    ('2304', 'Edificio1', 30, 1),
    ('2305', 'Edificio1', 30, 1),
    ('2404', 'Edificio1', 30, 1),
    ('2405', 'Edificio1', 30, 1),

    -- Laboratorios Especializados
    ('CILSE1', 'Laboratorio', 30, 1),
    ('CILSE2', 'Laboratorio', 30, 1),
    ('CILMT1', 'Laboratorio', 30, 1),
    ('CILDG1', 'Laboratorio', 30, 1),
    ('CILAB3', 'Laboratorio', 30, 1),
    ('CILID1', 'Laboratorio', 30, 1),

    -- Canchas
    ('Cancha Polivalente', 'Cancha', 50, 1),
    ('Cancha Futbol', 'Cancha', 100, 1),

    -- Auditorios y Espacios Especiales ('Otro')
    ('Centro Estudiantil', 'Otro', 120, 1),
    ('SUM Prepa', 'Otro', 60, 1),
    ('Claustro', 'Otro', 40, 1),
    ('Juicios Orales', 'Otro', 35, 1),

    -- Cubículos Mezanine (Regla institucional: ocupados de Lun a Vie de 7:00 a 15:00; agendables > 3:00 PM)
    ('Mezanine (Cubículo 1)', 'Otro', 8, 1),
    ('Mezanine (Cubículo 2)', 'Otro', 8, 1),
    ('Mezanine (Cubículo 3)', 'Otro', 8, 1);

-- Reservas de muestra (Tomadas del calendario real del Campus Cuautitlán)
INSERT INTO Reservas (id_usuario, id_espacio, fecha_reserva, hora_inicio, hora_fin, motivo_evento, estado_reserva, observaciones) VALUES
    (2, 1, '2026-10-06', '10:00:00', '12:00:00', 'Conferencia de Violencia Digital', 'Completada', '50 personas en auditorio'),
    (3, 3, '2026-10-07', '11:00:00', '13:30:00', 'Explora Maps', 'Completada', 'Aldea 2102 y áreas conjuntas'),
    (4, 16, '2026-10-07', '16:00:00', '17:00:00', 'Clase muestra de Alemán', 'Completada', 'Salón limpio y acomodo habitual'),
    (5, 36, '2026-10-08', '13:30:00', '18:00:00', 'Tecmi Tour T.Alba-Gilwell', 'Activa', 'Acomodo para 100-120 personas'),
    (5, 14, '2026-10-08', '13:30:00', '18:00:00', 'Tecmi Tour T.Alba-Gilwell - Sesión Aldea', 'Activa', 'Salón Aldea 2113'),
    (5, 37, '2026-10-08', '13:30:00', '18:00:00', 'Tecmi Tour T.Alba-Gilwell - Prepa', 'Activa', 'Alineado hacia SUM1'),
    (6, 34, '2026-10-09', '16:00:00', '19:00:00', 'Partidos Amistosos Vive Prepa', 'Activa', 'Cancha Polivalente'),
    (5, 38, '2026-10-10', '09:00:00', '14:00:00', 'Open House Vida y Carrera', 'Activa', 'Claustro general'),
    (6, 35, '2026-10-12', '11:00:00', '15:00:00', 'Hora Vive Torneo', 'Activa', 'Cancha Futbol'),
    (7, 26, '2026-10-12', '17:00:00', '19:00:00', 'Traza tu ruta Profesional', 'Activa', 'Salón 2304'),
    (8, 38, '2026-10-15', '10:00:00', '18:00:00', 'Venta de Toga y Birrete', 'Activa', 'Servicios Escolares Claustro'),
    (9, 36, '2026-10-22', '10:00:00', '15:00:00', 'Feria del Empleo Profesional', 'Activa', 'Centro Estudiantil'),
    (10, 36, '2026-10-23', '13:00:00', '14:00:00', 'Badge Day Profesional', 'Activa', 'Acomodo tipo auditorio 100 personas, pantallas');

-- ====================================================================
-- 6. CONSULTAS DE EJEMPLO ÚTILES
-- ====================================================================

-- A) Consultar todos los espacios del edificio "Aldea"
-- SELECT * FROM Espacios WHERE tipo_espacio = 'Aldea';

-- B) Consultar espacios disponibles en una fecha y rango de horas
-- SELECT e.* 
-- FROM Espacios e
-- WHERE e.activo = 1
--   AND e.id_espacio NOT IN (
--       SELECT r.id_espacio 
--       FROM Reservas r 
--       WHERE r.fecha_reserva = '2026-10-08'
--         AND r.estado_reserva = 'Activa'
--         AND (('14:00:00' < r.hora_fin) AND ('16:00:00' > r.hora_inicio))
--   );

-- C) Agenda detallada con nombres de usuarios y espacios
-- SELECT 
--     r.id_reserva,
--     r.fecha_reserva,
--     r.hora_inicio,
--     r.hora_fin,
--     r.motivo_evento,
--     r.estado_reserva,
--     e.nombre_espacio,
--     e.tipo_espacio,
--     u.nombre_completo AS solicitante,
--     u.rol
-- FROM Reservas r
-- JOIN Espacios e ON r.id_espacio = e.id_espacio
-- JOIN Usuarios u ON r.id_usuario = u.id_usuario
-- ORDER BY r.fecha_reserva DESC, r.hora_inicio ASC;
