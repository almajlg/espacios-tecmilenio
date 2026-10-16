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
    rol ENUM('Profesor', 'Administrador', 'Responsable', 'Coordinador', 'Mentor') NOT NULL,
    oculto_directorio TINYINT(1) DEFAULT 0,
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
    servicio_medico TINYINT(1) DEFAULT 0,
    servicio_ti TINYINT(1) DEFAULT 0,
    servicio_planta_fisica TINYINT(1) DEFAULT 0,
    observaciones TEXT NULL,
    archivo_layout VARCHAR(500) NULL,
    archivo_layout_nombre VARCHAR(255) NULL,
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
INSERT INTO Usuarios (id_usuario, nombre_completo, correo, rol, oculto_directorio) VALUES
    (1, 'Diana Angélica Barrios Hernández', 'dabarrios@tecmilenio.mx', 'Administrador', 0),
    (2, 'Natalie Luna Casillas', 'natalie.luna@tecmilenio.mx', 'Profesor', 0),
    (3, 'Idania Irene López Soto', 'idania.lopez@tecmilenio.mx', 'Profesor', 0),
    (4, 'Lilia Tapia Bernal', 'lily.tapia@tecmilenio.mx', 'Coordinador', 0),
    (5, 'Jibsam Sarai Peñaloza Camino Real', 'jspenaloz@tecmilenio.mx', 'Coordinador', 0),
    (6, 'Raymundo Jacinto Hernández', 'raymundo.jacinto@tecmilenio.mx', 'Coordinador', 0),
    (7, 'Julio César Moreno Montoya', 'jcmoreno@tecmilenio.mx', 'Coordinador', 0),
    (8, 'Coordinación Académica Profesional', 'coordinacion.profesional@tecmilenio.mx', 'Coordinador', 0),
    (9, 'Coordinación Preparatoria', 'coordinacion.preparatoria@tecmilenio.mx', 'Coordinador', 0),
    (10, 'José Antonio Aguilar Montalvo', 'j.aguilar@tecmilenio.mx', 'Profesor', 0),
    (11, 'Daniel Alejandro Andrade Nava', 'andrade.alejandro@tecmilenio.mx', 'Profesor', 0),
    (12, 'Guillermo Anzaldo Cruz', 'guillermo.anzaldo@tecmilenio.mx', 'Profesor', 0),
    (13, 'Luis Manuel Arenas Merchant', 'luis.arenas@tecmilenio.mx', 'Coordinador', 0),
    (14, 'Avril Arteaga Puente', 'avrilartepuente@tecmilenio.mx', 'Profesor', 0),
    (15, 'Brenda Ivette Avalos Aquino', 'brenda.avalos@tecmilenio.mx', 'Profesor', 0),
    (16, 'Maria del Rosario Balderas Gonzaga', 'mrbalderas@tecmilenio.mx', 'Profesor', 0),
    (18, 'Ricardo Campos Vidales', 'campos.ricardo@tecmilenio.mx', 'Profesor', 0),
    (19, 'María Fernanda Cárdenas García', 'fer.cardenas@tecmilenio.mx', 'Profesor', 0),
    (20, 'Maricela Carvajal Aguilar', 'maricela.carvajal@tecmilenio.mx', 'Profesor', 0),
    (23, 'Paulina Alejandra Contreras Martínez', 'paucontrerasm@tecmilenio.mx', 'Profesor', 0),
    (25, 'Luis Ricardo Cozar Navarro', 'luis.cozar@tecmilenio.mx', 'Profesor', 0),
    (26, 'Juan Pablo Cuellar Cercle', 'pablo_633@tecmilenio.mx', 'Profesor', 0),
    (27, 'Cristian Daniel Damián Olvera', 'cristian.damian@tecmilenio.mx', 'Profesor', 0),
    (28, 'Paulo César De la Vega Bedolla', 'paulo-cesar@tecmilenio.mx', 'Profesor', 0),
    (30, 'Sayda Díaz Celaya', 'sayda.di@tecmilenio.mx', 'Profesor', 0),
    (31, 'Jesús Eduardo Domínguez Murrieta', 'murrieta.eduardo@tecmilenio.mx', 'Profesor', 0),
    (32, 'Brenda Escobar Domínguez', 'brenda.ed@tecmilenio.mx', 'Profesor', 0),
    (35, 'Mariana Lizbeth Esqueda Villalpando', 'mariana_esdesign@tecmilenio.mx', 'Profesor', 0),
    (36, 'Wendy María Flores Pérez', 'wenflores@tecmilenio.mx', 'Profesor', 0),
    (37, 'Jorge Alfredo Frias Estrada', 'jorge_frias@tecmilenio.mx', 'Profesor', 0),
    (38, 'Sonia García Hernández', 'sonia.garcia@tecmilenio.mx', 'Profesor', 0),
    (40, 'Evelin Itzel Gazcon Gutierrez', 'lic.gazcon.evelin@tecmilenio.mx', 'Profesor', 0),
    (41, 'Hugo Alan Gómez Alcaraz', 'hugo.gomez@tecmilenio.mx', 'Profesor', 0),
    (43, 'Lucía Gómez Sánchez', 'lucia.gomez.s@tecmilenio.mx', 'Profesor', 0),
    (44, 'Felix Gonzalez Gonzalez', 'fgonzale@tecmilenio.mx', 'Profesor', 0),
    (45, 'Debbie Amneris Granados Sevilla', 'debbie@tecmilenio.mx', 'Profesor', 0),
    (46, 'Guillermo Guerrero Aguilar', 'memoguerrero@tecmilenio.mx', 'Profesor', 0),
    (47, 'Liliana Gutiérrez Gutiérrez', 'lgutier@tecmilenio.mx', 'Profesor', 0),
    (48, 'Guillermo Hernández Carmona', 'hernndez.guillermo@tecmilenio.mx', 'Profesor', 0),
    (51, 'Blanca Hernández Vázquez', 'blanca.hernandez@tecmilenio.mx', 'Profesor', 0),
    (53, 'Bryan Antonio Islas Sánchez', 'antonio_i.sanchez@tecmilenio.mx', 'Profesor', 0),
    (56, 'Luis Miguel López de Uralde Martínez', 'migueluralde@tecmilenio.mx', 'Profesor', 0),
    (57, 'Laura Patricia López Martínez', 'lpez.patricia@tecmilenio.mx', 'Profesor', 0),
    (58, 'Alejandro Marrufo Márquez', 'alejandro.marrufo@tecmilenio.mx', 'Profesor', 0),
    (59, 'Smirna Guadalupe Martinez Lira', 'smirna.lira@tecmilenio.mx', 'Profesor', 0),
    (60, 'María Fernanda Martínez Nieto', 'mfmartinez@tecmilenio.mx', 'Profesor', 0),
    (61, 'Juvencio Martínez Peña', 'juvenciomartinezpena@tecmilenio.mx', 'Profesor', 0),
    (63, 'Cinthia Martínez Rojas', 'cinthia.martinezr@tecmilenio.mx', 'Profesor', 0),
    (64, 'Jessica Irais Mateos Ledezma', 'jess.i.mateos@tecmilenio.mx', 'Profesor', 0),
    (65, 'Nallely Mendez Mercado', 'nallely.mendez@tecmilenio.mx', 'Profesor', 0),
    (66, 'Misael Ricardo Mendoza Zúñiga', 'ricardo_mendoza@tecmilenio.mx', 'Profesor', 0),
    (67, 'Iván Meza Huerta', 'meza.ivan@tecmilenio.mx', 'Profesor', 0),
    (68, 'Marisol Molina Reyes', 'marisol.mr@tecmilenio.mx', 'Profesor', 0),
    (69, 'Javier David Molinar López', 'molinar.david@tecmilenio.mx', 'Profesor', 0),
    (70, 'Jorge Alberto Morales Hernández', 'jorge.morales@tecmilenio.mx', 'Profesor', 0),
    (72, 'Daniela Nava Flores', 'nava.daniela@tecmilenio.mx', 'Profesor', 0),
    (73, 'Adriana Ivonne Pacheco Garibay', 'ivonne_pacheco@tecmilenio.mx', 'Profesor', 0),
    (74, 'Víctor Daniel Palacios Chavarría', 'palacios.daniel@tecmilenio.mx', 'Profesor', 0),
    (75, 'Alexis Fabián Paz Álvarez', 'paz.fabin@tecmilenio.mx', 'Profesor', 0),
    (76, 'María Guadalupe Julieta Peña Iñiguez', 'maria.pena@tecmilenio.mx', 'Profesor', 0),
    (77, 'Luis Alberto Pineda Calvo', 'alberto.pineda@tecmilenio.mx', 'Profesor', 0),
    (78, 'Gabriel Eduardo Portillo Islas', 'gabriel.islas@tecmilenio.mx', 'Profesor', 0),
    (79, 'Esteban Puerta Elias', 'elias_puerta@tecmilenio.mx', 'Profesor', 0),
    (80, 'Luis Benjamin Quiñones González', 'benjamin.quinones@tecmilenio.mx', 'Profesor', 0),
    (81, 'Karina Irais Ramirez Flores', 'irais_ramirez@tecmilenio.mx', 'Profesor', 0),
    (83, 'Carlos Ravelo Lara', 'ravello.lara@tecmilenio.mx', 'Profesor', 0),
    (84, 'Alma Alin Reyes Hidalgo', 'alin.reyes@tecmilenio.mx', 'Mentor', 0),
    (85, 'Silvia Reyes León', 'silvia.reyes@tecmilenio.mx', 'Coordinador', 0),
    (86, 'Rooney Manuel Ríos Ruiz', 'rios.manuel@tecmilenio.mx', 'Profesor', 0),
    (88, 'María Eugenia Rivera Elizalde', 'maru_rivera@tecmilenio.mx', 'Profesor', 0),
    (89, 'Araceli Jiseth Rodríguez Pérez', 'jiseth.perez@tecmilenio.mx', 'Profesor', 0),
    (91, 'Jesús Fernando Sánchez Becerril', 'jesus.sanchez@tecmilenio.mx', 'Profesor', 0),
    (92, 'Gregorio Adrián Sánchez Díaz', 'greg_sd@tecmilenio.mx', 'Profesor', 0),
    (94, 'Carla Patricia Tirado Hernández', 'tirado.patricia@tecmilenio.mx', 'Profesor', 0),
    (95, 'Lily Daniella Torres Reyes', 'torres.daniella@tecmilenio.mx', 'Profesor', 0),
    (96, 'Hazael Tovar Beltrán', 'tovar.hazael@tecmilenio.mx', 'Profesor', 0),
    (98, 'Erika Van Scoit Arizmendi', 'eri.vanscoit@tecmilenio.mx', 'Profesor', 0),
    (99, 'Gustavo Vargas Cortes', 'gustavo.vargas@tecmilenio.mx', 'Profesor', 0),
    (100, 'Alejandra Vega Contreras', 'ale.vega@tecmilenio.mx', 'Profesor', 0),
    (102, 'David Villalobos Chávez', 'dvillalobos@tecmilenio.mx', 'Profesor', 0),
    (103, 'Diego Israel Villalpando Camberos', 'diego.villalpando@tecmilenio.mx', 'Profesor', 0),
    (104, 'Aleida Azeneth Zamorano Herrera', 'aleidaz@tecmilenio.mx', 'Profesor', 0),
    (105, 'Miguel Ángel Zúñiga Manríquez', 'mzuniga@tecmilenio.mx', 'Profesor', 0),
    (107, 'Jonathan Salazar Ortega', 'jonathansalazaro@tecmilenio.mx', 'Profesor', 0),
    (108, 'Profesor Demo', 'profesor.demo@tecmilenio.mx', 'Responsable', 0),
    (109, 'María del Consuelo Yanez Campos', 'mayanez@tecmilenio.mx', 'Coordinador', 0),
    (110, 'Elizabeth Guerrero Flores', 'eliguerrero@tecmilenio.mx', 'Profesor', 0),
    (111, 'Rosa Mirna Arzate Hernández', 'mirna.arzate@tecmilenio.mx', 'Profesor', 0),
    (112, 'Alejandra Ceron Maldonado', 'alceron@tecmilenio.mx', 'Mentor', 0),
    (113, 'Areli Mitshu Hernandez Moreno', 'armhernande@tecmilenio.mx', 'Profesor', 0),
    (114, 'Francelia Virgen Ochoa Sales', 'fran.ochoa@tecmilenio.mx', 'Profesor', 0),
    (115, 'Gibran Josué Cohen Ruiz', 'gcohen@tecmilenio.mx', 'Mentor', 0),
    (116, 'Josué Carreño Camacho', 'j.carreno@tecmilenio.mx', 'Profesor', 0),
    (117, 'Tabata Gamez Trejo', 'tgamez@tecmilenio.mx', 'Profesor', 0),
    (118, 'Yesenia Liliana Moran Santana', 'ymoran@tecmilenio.mx', 'Mentor', 0),
    (119, 'Alberto Moreno García', 'betomoreno1@tecmilenio.mx', 'Profesor', 0),
    (120, 'María Cristina Juárez Flores', 'juarez.cristina@tecmilenio.mx', 'Profesor', 0),
    (121, 'José Luis Valdes Flores', 'jlvaldes@tecmilenio.mx', 'Profesor', 0),
    (122, 'Gloria Alejandra Lopez Morell', 'gllopezm@tecmilenio.mx', 'Profesor', 0),
    (123, 'Maria Guadalupe Mondragon Martinez', 'guadalupe.mondragon@tecmilenio.mx', 'Profesor', 0),
    (124, 'Melisa Margaretha Juana Flameng Van Poppel', 'mmflameng@tecmilenio.mx', 'Profesor', 0),
    (125, 'Shady Shahin González', 'shady.shahin@tecmilenio.mx', 'Profesor', 0),
    (126, 'Virginia Buensuceso Solano', 'virginia.bs@tecmilenio.mx', 'Profesor', 0),
    (127, 'Wendolin López Arriaga', 'wendolin.arriaga@tecmilenio.mx', 'Profesor', 0),
    (128, 'Silvia Nayeli Díaz Gonzalez Ramírez', 'nayeli.diaz@tecmilenio.mx', 'Profesor', 0),
    (129, 'Valeria Montserrat Rodriguez Tahuilan', 'valeria.rdz@tecmilenio.mx', 'Coordinador', 0),
    (130, 'Elsa María Solano Guerrero', 'elsolano@tecmilenio.mx', 'Profesor', 0),
    (131, 'Israel Zepeda Peña', 'izepeda@tecmilenio.mx', 'Profesor', 0),
    (132, 'Martin de Jesús Diaz Ruiz', 'martinj.diaz@tecmilenio.mx', 'Profesor', 0),
    (133, 'Ruth Geraldine Tlapalamatl Lopez', 'ruth.tlapalamatl@tecmilenio.mx', 'Profesor', 0),
    (134, 'Mario Diaz Guerra', 'mario.diaz@tecmilenio.mx', 'Profesor', 0),
    (135, 'Ana Laura Martínez Anaya', 'analaura.martinez@tecmilenio.mx', 'Profesor', 0),
    (136, 'Maria de Lourdes Pérez Gudiño', 'marilourdes_perez@tecmilenio.mx', 'Profesor', 0),
    (137, 'Mónica Marlene Valdez Sánchez', 'monivaldez@tecmilenio.mx', 'Profesor', 0),
    (138, 'Daniel Montero Barrera', 'd.monterobarrera@tecmilenio.mx', 'Profesor', 0),
    (139, 'Miguel Ángel Delgadillo Arana', 'madelgadillo9700@tecmilenio.mx', 'Profesor', 0),
    (140, 'Claudia Abril López Morales', 'abril.lopez@tecmilenio.mx', 'Profesor', 0),
    (141, 'Gerardo Carrillo Alvarado', 'gerardocarrillo@tecmilenio.mx', 'Profesor', 0),
    (142, 'María Eugenia González Navarro', 'eugeniagonzalez.navarro@tecmilenio.mx', 'Profesor', 0),
    (143, 'Cyntia Jenifer Flores González', 'cjflores@tecmilenio.mx', 'Profesor', 0),
    (144, 'Valeria Itzel Sánchez Vázquez', 'vale.sanchez@tecmilenio.mx', 'Profesor', 0),
    (145, 'Areli Vargas Rojas', 'arelivargas@tecmilenio.mx', 'Profesor', 0),
    (146, 'Jacobo Rodríguez Monroy', 'jacobo.rodriguezm@tecmilenio.mx', 'Profesor', 0),
    (147, 'Rocío Marlene Reyes Madrigal', 'rocio.reyes@tecmilenio.mx', 'Profesor', 0),
    (148, 'Juan Carlos Miranda Flores', 'c_miranda@tecmilenio.mx', 'Profesor', 0),
    (149, 'Luis Miguel Cardenas Gomez', 'luis.miguel.cardenas@tecmilenio.mx', 'Profesor', 0),
    (150, 'Diosinantzin García Bucio', 'diosi.garcia.bucio@tecmilenio.mx', 'Profesor', 0),
    (151, 'Andrés Martínez Solano', 'andres_solano@tecmilenio.mx', 'Profesor', 0),
    (152, 'Andrés Colin Pineda', 'andres.colin@tecmilenio.mx', 'Profesor', 0),
    (153, 'Ivette Rodríguez Delgado', 'ivette.rodriguez@tecmilenio.mx', 'Profesor', 0),
    (154, 'Alfonso Sánchez Díaz', 'sanchez.alfonso@tecmilenio.mx', 'Profesor', 0),
    (155, 'Laura Ángelica Rivas Trejo', 'la.rivastrejo@tecmilenio.mx', 'Profesor', 0),
    (156, 'Ana Laura Iniestra Álvarez', 'ana.iniestra@tecmilenio.mx', 'Profesor', 0),
    (157, 'Claudia Elizabeth García del Bosque', 'claudia.garcia.bosque@tecmilenio.mx', 'Profesor', 0),
    (158, 'Nancy Chávez Meza', 'nancymeza@tecmilenio.mx', 'Profesor', 0),
    (159, 'José Porfirio Francisco Rosas Rivera', 'porfirio.rosas@tecmilenio.mx', 'Profesor', 0),
    (160, 'Aldo Gabriel Trejo Mendoza', 'aldo.trejo@tecmilenio.mx', 'Profesor', 0),
    (161, 'Maria Eugenia Gómez Tous', 'maru.gomez@tecmilenio.mx', 'Profesor', 0),
    (162, 'Bibiana Teresa Casco Ruiz', 'bibiana.casco@tecmilenio.mx', 'Profesor', 0),
    (163, 'Gabriela Guzmán García', 'gaby.guzman@tecmilenio.mx', 'Profesor', 0),
    (164, 'Mayra Sanchez Aguilar', 'msanchez2019@tecmilenio.mx', 'Profesor', 0),
    (165, 'Fátima García Sanchez', 'fatima_gs@tecmilenio.mx', 'Profesor', 0),
    (166, 'Oscar Rodríguez Espinosa', 'orodrigueze@tecmilenio.mx', 'Profesor', 0),
    (167, 'Abraham Antonio Velazquez Pineda', 'abraham8384@tecmilenio.mx', 'Profesor', 0),
    (168, 'Esther Carolina Limon Medrano', 'carolina_limon@tecmilenio.mx', 'Profesor', 0),
    (169, 'Arturo Flores Jaimes', 'darturo_fj@tecmilenio.mx', 'Profesor', 0),
    (170, 'Emmanuel Barrón Tapia', 'emmanuel.barron@tecmilenio.mx', 'Profesor', 0),
    (171, 'José Antonio Esqueda Villalpando', 'antonio.esqueda@tecmilenio.mx', 'Profesor', 0),
    (172, 'Jessica Nieto Álvarez', 'jessicanieto@tecmilenio.mx', 'Profesor', 0),
    (173, 'Yoyela Yanet Hernández Moreno', 'yoye.hdz@tecmilenio.mx', 'Profesor', 0),
    (174, 'Osvaldo Joaquín Loesa Velázquez', 'loesa@tecmilenio.mx', 'Profesor', 0),
    (175, 'Nayely Rojas Escamilla', 'n.rojas@tecmilenio.mx', 'Coordinador', 0),
    (176, 'Juan Alfredo Vazquez Hernández', 'alfredo.vazquez@tecmilenio.mx', 'Profesor', 0),
    (177, 'Alexasandra Pérez Sierra', 'alexa.perez@tecmilenio.mx', 'Profesor', 0),
    (178, 'Cynthia Areli Castillo Martínez', 'cynthia.castillo@tecmilenio.mx', 'Coordinador', 0),
    (179, 'Diana Carrillo González', 'diana.carrillo@tecmilenio.mx', 'Coordinador', 0),
    (180, 'Tania Lisuli Zarate Ramirez', 'lisuli_zarate@tecmilenio.mx', 'Profesor', 0),
    (181, 'Sandra Pérez Herrera', 'pherrera.sandra@tecmilenio.mx', 'Profesor', 0),
    (182, 'Ludwig Aarón López Ducoing', 'ludwig_ducoing@tecmilenio.mx', 'Profesor', 0),
    (183, 'Blanca Estela Cruz Espinoza', 'blantela.espinoza@tecmilenio.mx', 'Profesor', 0),
    (184, 'María Cristina Bassaure Anaya', 'bassaure.cristina@tecmilenio.mx', 'Profesor', 0),
    (185, 'Miguel Ángel Crisanto Godínez', 'miguel.crisanto@tecmilenio.mx', 'Profesor', 0),
    (186, 'Fernando Cruz Ibarra', 'fernando.cruzibarra@tecmilenio.mx', 'Profesor', 0),
    (187, 'José Luis Zurita Mora', 'luiszurita88@tecmilenio.mx', 'Profesor', 0),
    (188, 'Azucena Jocelyn Rojas Cristain', 'jocelynrojas@tecmilenio.mx', 'Profesor', 0),
    (189, 'Francisco Ratz Velasco', 'francisco.ratz@tecmilenio.mx', 'Profesor', 0),
    (190, 'Guadalupe Monserrat Jiménez Ayala', 'monserrat.jimenez@tecmilenio.mx', 'Profesor', 0),
    (191, 'Viridiana Ramos Arellano', 'viridianaramos@tecmilenio.mx', 'Profesor', 0),
    (192, 'Sandra Paola Gómez López', 'sandrap.gomezlo@tecmilenio.mx', 'Profesor', 0),
    (193, 'Thelma Martínez Correa', 'thelma.m.correa@tecmilenio.mx', 'Profesor', 0),
    (194, 'Alicia Mariana Fuentes Lechuga', 'am.fuentes@tecmilenio.mx', 'Coordinador', 0),
    (195, 'Verónica Olmos Martínez', 'veronica-om@tecmilenio.mx', 'Profesor', 0),
    (196, 'Frida Díaz Iniestra', 'frida.diaz@tecmilenio.mx', 'Profesor', 0),
    (197, 'Miriam Guadalupe Sánchez Aguilar', 'miriam.sanchez@tecmilenio.mx', 'Profesor', 0),
    (198, 'Luis Felipe Espinoza Uribe', 'luisfelipe.espinoza.uribe@tecmilenio.mx', 'Profesor', 0),
    (199, 'Sandra Colin Serafín', 'sandra_colin@tecmilenio.mx', 'Mentor', 0),
    (200, 'Maria Lizbeth Vargas González', 'vargas.l@tecmilenio.mx', 'Profesor', 0),
    (201, 'Amehed Sahid Flores Pérez', 'sahidf@tecmilenio.mx', 'Coordinador', 0),
    (202, 'Ana Erandi Jiménez González', 'jerandi@tecmilenio.mx', 'Profesor', 0),
    (203, 'Lucero Rocío Jiménez Ramos', 'lucero.jimenez@tecmilenio.mx', 'Profesor', 0),
    (204, 'Raúl Carrasco López', 'r.carrasco@tecmilenio.mx', 'Profesor', 0),
    (205, 'Cinthya Alejandra Mora Rodríguez', 'morar_cin@tecmilenio.mx', 'Profesor', 0),
    (206, 'Fernando Domínguez Chang', 'fernando.dominguez@tecmilenio.mx', 'Coordinador', 0),
    (207, 'Karen Selena Gutiérrez Martínez', 'karen_sgm@tecmilenio.mx', 'Profesor', 0),
    (208, 'Christian Carrasco Sánchez', 'c.carrasco@tecmilenio.mx', 'Profesor', 0),
    (209, 'Katia Paola Garduño González', 'pgarduno@tecmilenio.mx', 'Profesor', 0),
    (210, 'Abigail Peña Domínguez', 'abigail.p@tecmilenio.mx', 'Profesor', 0),
    (211, 'Yolotzin Doniz Vargas', 'yolotzin.doniz@tecmilenio.mx', 'Profesor', 0),
    (212, 'José Carlos Hernández Moya', 'jose_carlosh@tecmilenio.mx', 'Profesor', 0),
    (213, 'Karla Magali Valdez Rojas', 'karla.valdez@tecmilenio.mx', 'Profesor', 0),
    (214, 'Saúl López Torres', 'saul_lopez.t@tecmilenio.mx', 'Profesor', 0),
    (215, 'Diana Karen López Herrerías', 'dian.lopez@tecmilenio.mx', 'Profesor', 0),
    (216, 'Catharine Fernanda Allende Díaz', 'ferdiaz@tecmilenio.mx', 'Profesor', 0),
    (217, 'Karen Angélica Muñoz Torres', 'karen_munoz@tecmilenio.mx', 'Profesor', 0),
    (218, 'Nasheli López Bautista', 'nasheli.lopez@tecmilenio.mx', 'Profesor', 0),
    (219, 'Luis Daniel Hernández Licona', 'luis.hernandez@tecmilenio.mx', 'Profesor', 0),
    (220, 'Mary Judith Correa Jaimes', 'mary.correa@tecmilenio.mx', 'Profesor', 0),
    (221, 'Clauzzen Daniela Ávila Hernández', 'clauzzen_avila@tecmilenio.mx', 'Profesor', 0),
    (222, 'Jhonathan Robles Sánchez', 'jhonathan.robles@tecmilenio.mx', 'Profesor', 0),
    (223, 'Irma Alvarado Guzmán', 'irma.alvarado@tecmilenio.mx', 'Profesor', 0),
    (224, 'Juan Carlos Perez Vega', 'jcarlospvega@tecmilenio.mx', 'Profesor', 0),
    (225, 'Laura Elena Sánchez Castillo', 'laura.elena.san@tecmilenio.mx', 'Profesor', 0),
    (226, 'Laura Pamela Camacho Ortiz', 'pamela.camacho@tecmilenio.mx', 'Profesor', 0),
    (227, 'Montserrat Contreras Ortega', 'montserrat.ortega@tecmilenio.mx', 'Profesor', 0),
    (228, 'Daniel Missael Pérez Bárcenas', 'dmissaelpb@tecmilenio.mx', 'Profesor', 0),
    (229, 'Verónica Cisneros Carrillo', 'vero.cisneros@tecmilenio.mx', 'Profesor', 0),
    (230, 'Gerardo Montenegro Salgado', 'g.montengro@tecmilenio.mx', 'Profesor', 0),
    (231, 'Gilberto Pérez Arguelles', 'gilberto.p.arguelles@tecmilenio.mx', 'Profesor', 0),
    (232, 'Araceli Magdalena García Madrigal', 'aramadrigal@tecmilenio.mx', 'Profesor', 0),
    (233, 'Mónica Olmos Solis', 'olmos_monica@tecmilenio.mx', 'Profesor', 0),
    (234, 'Verónica Yael Escalante Hernández', 'vero.escalante@tecmilenio.mx', 'Profesor', 0),
    (235, 'Sarai Yuriko Aguilar Quevedo', 'yuriko.aguilar@tecmilenio.mx', 'Profesor', 0),
    (236, 'Enrique Osorio Andrade', 'enrique.osorio@tecmilenio.mx', 'Profesor', 0),
    (237, 'Rosa María Alejandra Romero Bañuelos', 'rosa.romero.b@tecmilenio.mx', 'Profesor', 0),
    (238, 'Marlen Montes Ruiz', 'marlen_montes@tecmilenio.mx', 'Profesor', 0),
    (239, 'Shalma Michelle Martinez Gutierrez', 'michellegutierrez@tecmilenio.mx', 'Profesor', 0),
    (240, 'Karla Yadira Vázquez Fernández', 'karlavzquez@tecmilenio.mx', 'Profesor', 0),
    (241, 'José Alfredo Suárez Hernández', 'alfredosuarez@tecmilenio.mx', 'Profesor', 0),
    (242, 'María del Rosario Aguilar Jiménez', 'rosario.aguilar@tecmilenio.mx', 'Profesor', 0),
    (243, 'Evelyn Andrea Mendoza Gonzalez', 'evelyn_mendoza@tecmilenio.mx', 'Coordinador', 0),
    (244, 'Luis Daniel Ramírez Segura', 'dany_ramirez@tecmilenio.mx', 'Profesor', 0),
    (245, 'Nayelli Sánchez Piña', 'naye.sanchez@tecmilenio.mx', 'Profesor', 0),
    (246, 'Matthew Álvarez Trejo', 'matt.alvarez@tecmilenio.mx', 'Profesor', 0),
    (247, 'Aylin Damaris Merino Reséndiz', 'aylinmerino@tecmilenio.mx', 'Profesor', 0),
    (248, 'María Fernanda Ling Flores', 'fernanda.ling@tecmilenio.mx', 'Profesor', 0),
    (249, 'Estefanía Magali Hernández Ramírez', 'ehernandezram@tecmilenio.mx', 'Profesor', 0),
    (250, 'Ricardo Gabriel Avilés Trujeque', 'ricardo.avilest@tecmilenio.mx', 'Profesor', 0),
    (251, 'Bernardo Noé Espinosa Moreno', 'noe.espinosa@tecmilenio.mx', 'Profesor', 0),
    (252, 'Karina Guadalupe Viquez Viquez', 'karina.gvv@tecmilenio.mx', 'Profesor', 0),
    (253, 'Carolina Avelar Rodríguez', 'carol_avelar@tecmilenio.mx', 'Profesor', 0),
    (254, 'Javier Armando Díaz Reyes', 'javi.dr@tecmilenio.mx', 'Profesor', 0),
    (255, 'Naizet Daniela Vazquez Montoya', 'naizetvazquez@tecmilenio.mx', 'Profesor', 0),
    (256, 'Karla Merari García Sánchez', 'karla.merari@tecmilenio.mx', 'Profesor', 0),
    (257, 'Daniela Melendez Hernández', 'dan_hernandez@tecmilenio.mx', 'Profesor', 0),
    (258, 'Roberto Javier Alvarado Guzmán', 'rob_alvarado@tecmilenio.mx', 'Profesor', 0),
    (259, 'Erika Enedina Martínez Montejo', 'erika.martinez@tecmilenio.mx', 'Profesor', 0),
    (260, 'Susana Alejandra Mendoza Contreras', 'susana_mendozac@tecmilenio.mx', 'Profesor', 0),
    (261, 'Monica Francisco Bautista', 'monica.fran@tecmilenio.mx', 'Profesor', 0),
    (262, 'Jesús Eduardo Espinosa Ibarra', 'jeduardo.espinosa@tecmilenio.mx', 'Profesor', 0),
    (263, 'Rubén Escobedo Cabello', 'rbnescobedoc@tecmilenio.mx', 'Profesor', 0),
    (264, 'José Luis Cerón Maldonado', 'luis.ceron@tecmilenio.mx', 'Profesor', 0),
    (265, 'Sergio Diaz García', 'sergio_dg@tecmilenio.mx', 'Profesor', 0),
    (266, 'Pamela Izaret Pérez Martínez', 'pamela.perez@tecmilenio.mx', 'Profesor', 0),
    (267, 'Marian Elizabeth Pontigo Suárez', 'maryann@tecmilenio.mx', 'Profesor', 0),
    (268, 'Tania Paola López Alcaraz', 'pao.alcaraz@tecmilenio.mx', 'Profesor', 0),
    (269, 'Roberto Luna Vázquez', 'roberto.luna@tecmilenio.mx', 'Profesor', 0),
    (270, 'Anahis Aparicio Izquierdo', 'anahis.aparicio@tecmilenio.mx', 'Profesor', 0),
    (271, 'Martha Patricia López Navarro', 'martha_lopezn@tecmilenio.mx', 'Profesor', 0),
    (272, 'Christian Jonathan Salinas Martínez', 'chris.salinas@tecmilenio.mx', 'Profesor', 0),
    (273, 'Dea Melina Pablo Francisco', 'melina.pablo@tecmilenio.mx', 'Profesor', 0),
    (274, 'Tamara Alvarado González', '', 'Profesor', 0),
    (275, 'Karla Cecilia Ledezma Chávez', 'karla.cecilia.ledezma@tecmilenio.mx', 'Profesor', 0),
    (276, 'Itzel Nuñez Mora', 'itzel_nunez@tecmilenio.mx', 'Profesor', 0),
    (277, 'Delia Paola Cárdenas Guzmán', 'delia.cardenas@tecmilenio.mx', 'Profesor', 0),
    (278, 'Abigail Gómez Briceño', 'abigailgbri@tecmilenio.mx', 'Profesor', 0),
    (279, 'Gustavo Sandate Díaz Leal', 'gustavo.sandate@tecmilenio.mx', 'Coordinador', 0),
    (280, 'Rodrigo Díaz Iniestra', 'roydi@tecmilenio.mx', 'Profesor', 0),
    (281, 'Fernando Alan García Ramírez', 'alan.garcia@tecmilenio.mx', 'Profesor', 0),
    (282, 'Pedro Javier Gálvez Santiago', 'pgalvez@tecmilenio.mx', 'Profesor', 0),
    (283, 'Daniela Gabriela Ruiz Olvera', 'daniela.olvera@tecmilenio.mx', 'Profesor', 0),
    (284, 'Edson Tonatiuh Santos Salazar', 'santos_92@tecmilenio.mx', 'Profesor', 0),
    (285, 'Jonathan Rodríguez Jiménez', 'rodriguez_jon@tecmilenio.mx', 'Profesor', 0),
    (286, 'Guillermo Ávila Martínez', 'guillermo.avila@tecmilenio.mx', 'Profesor', 0),
    (287, 'Jocelyn Caballero Ayala', 'j.caballero@tecmilenio.mx', 'Profesor', 0),
    (288, 'Alejandro López Lezama', 'lopezlezama@tecmilenio.mx', 'Profesor', 0),
    (289, 'Angélica Alcalá Hernández', 'angelica.alcala@tecmilenio.mx', 'Profesor', 0),
    (290, 'Leinali Alexsandybelle Arce López', 'leinali.arce@tecmilenio.mx', 'Profesor', 0),
    (291, 'Mariana Barros Escudero', 'marianabarrose@tecmilenio.mx', 'Profesor', 0),
    (292, 'José Arturo Martínez Pérez', 'josarturo_m_p@tecmilenio.mx', 'Profesor', 0),
    (293, 'Claudia Edith Raymundo Reyes', 'claudia.raymundo@tecmilenio.mx', 'Profesor', 0),
    (294, 'Rocío Corzo Salazar', 'rocio.corzo@tecmilenio.mx', 'Profesor', 0),
    (295, 'Adán Juárez Pereyra', 'adanjp@tecmilenio.mx', 'Profesor', 0),
    (296, 'Giovanni Román Rodríguez', 'gio_roman@tecmilenio.mx', 'Profesor', 0),
    (297, 'Jessica Mariana Pablo Alonso', 'jessica.pablo@tecmilenio.mx', 'Profesor', 0),
    (298, 'Yair de Jesús Hernandez Vazquez', 'yair.hernandez@tecmilenio.mx', 'Profesor', 0),
    (299, 'Ricardo Gutiérrez Prado', 'ricardo.gprado@tecmilenio.mx', 'Profesor', 0),
    (300, 'Miguel Ángel Hernández Gasca', 'miguel.gasca@tecmilenio.mx', 'Profesor', 0),
    (301, 'Huitzilin Fuentes Hernández', 'huitzi.fuentes@tecmilenio.mx', 'Profesor', 0),
    (302, 'Alejandro Velazquez Maza', 'alejandro.velazquez@tecmilenio.mx', 'Profesor', 0),
    (303, 'Ángel Ramses Jimenez Orozco', 'ramsesjimenez@tecmilenio.mx', 'Profesor', 0),
    (304, 'Enrique Arturo Campos Cid', 'enrique.campos@tecmilenio.mx', 'Profesor', 0),
    (305, 'Rubén Hernández Uvilla', 'rubenhuvilla@tecmilenio.mx', 'Profesor', 0),
    (306, 'Víctor Adriel Herrera Pérez', 'victoradriel@tecmilenio.mx', 'Profesor', 0),
    (307, 'Irving Maximiliano Rocha Guevara', 'irving.rocha@tecmilenio.mx', 'Profesor', 0),
    (308, 'Josué David Vergara Montañez', 'david.vergara@tecmilenio.mx', 'Profesor', 0),
    (309, 'Iztlli Yani Acosta Lozada', 'iztlli.aclo@tecmilenio.mx', 'Profesor', 0),
    (310, 'Juan Carlos Sánchez Hernández', 'juancsh@tecmilenio.mx', 'Profesor', 0),
    (311, 'Roberto Trejo Perez', '', 'Profesor', 0),
    (312, 'Marcela Sánchez Piña', 'marcela.sanchez.p@tecmilenio.mx', 'Profesor', 0),
    (313, 'Francisco Javier Toledo Escalona', 'francisco.toledo@tecmilenio.mx', 'Profesor', 0),
    (314, 'Frida Sharlin Rowine Juárez Hernández', 'sharlin.juarez@tecmilenio.mx', 'Profesor', 0),
    (315, 'Lizbeth Ariadna López Martínez', 'ariadnalm16@tecmilenio.mx', 'Profesor', 0),
    (316, 'Juan Pablo Cruz Carrillo', 'juan.carrillo@tecmilenio.mx', 'Profesor', 0),
    (317, 'Ángel Josué Mendoza Quintanar', 'angelmq945@tecmilenio.mx', 'Profesor', 0),
    (318, 'Alma Jaqueline Luis Galindo', 'al02995902@tecmilenio.mx', 'Administrador', 1);

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

    -- Salones Edificio 1 (Preparatoria: 1101 - 1310)
    ('1101', 'Edificio1', 35, 1),
    ('1102', 'Edificio1', 30, 1),
    ('1103', 'Edificio1', 30, 1),
    ('1104', 'Edificio1', 30, 1),
    ('1201', 'Edificio1', 30, 1),
    ('1202', 'Edificio1', 35, 1),
    ('1203', 'Edificio1', 30, 1),
    ('1204', 'Edificio1', 35, 1),
    ('1205', 'Edificio1', 35, 1),
    ('1206', 'Edificio1', 35, 1),
    ('1207', 'Edificio1', 35, 1),
    ('1208', 'Edificio1', 35, 1),
    ('1209', 'Edificio1', 35, 1),
    ('1210', 'Edificio1', 35, 1),
    ('1211', 'Edificio1', 35, 1),
    ('1212', 'Edificio1', 35, 1),
    ('1300', 'Edificio1', 35, 1),
    ('1301', 'Edificio1', 35, 1),
    ('1302', 'Edificio1', 35, 1),
    ('1303', 'Edificio1', 35, 1),
    ('1304', 'Edificio1', 35, 1),
    ('1305', 'Edificio1', 35, 1),
    ('1306', 'Edificio1', 35, 1),
    ('1307', 'Edificio1', 35, 1),
    ('1308', 'Edificio1', 30, 1),
    ('1309', 'Edificio1', 35, 1),
    ('1310', 'Edificio1', 30, 1),

    -- Salones Edificio 2 (Profesional: 2201 - 3109)
    ('2201', 'Edificio2', 30, 1),
    ('2202', 'Edificio2', 30, 1),
    ('2203', 'Edificio2', 30, 1),
    ('2204', 'Edificio2', 30, 1),
    ('2205', 'Edificio2', 30, 1),
    ('2301', 'Edificio2', 35, 1),
    ('2302', 'Edificio2', 30, 1),
    ('2304', 'Edificio2', 30, 1),
    ('2305', 'Edificio2', 30, 1),
    ('2306', 'Edificio2', 30, 1),
    ('2401', 'Edificio2', 35, 1),
    ('2402', 'Edificio2', 35, 1),
    ('2403', 'Edificio2', 35, 1),
    ('2404', 'Edificio2', 30, 1),
    ('2405', 'Edificio2', 30, 1),
    ('2406', 'Edificio2', 35, 1),
    ('3101', 'Edificio2', 35, 1),
    ('3102', 'Edificio2', 35, 1),
    ('3103', 'Edificio2', 35, 1),
    ('3104', 'Edificio2', 35, 1),
    ('3105', 'Edificio2', 35, 1),
    ('3106', 'Edificio2', 35, 1),
    ('3107', 'Edificio2', 35, 1),
    ('3108', 'Edificio2', 35, 1),
    ('3109', 'Edificio2', 35, 1),

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
