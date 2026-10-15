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
INSERT INTO Usuarios (nombre_completo, correo, rol) VALUES
    ('Diana AÁngelica Barrios Hernandez', 'dabarrios@tecmilenio.mx', 'Administrador'),
    ('Natalie Luna Casillas', 'natalie.luna@tecmilenio.mx', 'Profesor'),
    ('Idania Irene López Soto', 'idania.lopez@tecmilenio.mx', 'Profesor'),
    ('Lilia Tapia Bernal', 'lily.tapia@tecmilenio.mx', 'Coordinador'),
    ('Jibsam Sarai Peñaloza Camino Real', 'jspenaloz@tecmilenio.mx', 'Coordinador'),
    ('Raymundo Jacinto Hernández', 'raymundo.jacinto@tecmilenio.mx', 'Coordinador'),
    ('Julio César Moreno Montoya', 'jcmoreno@tecmilenio.mx', 'Coordinador'),
    ('Coordinación Académica Profesional', 'coordinacion.profesional@tecmilenio.mx', 'Coordinador'),
    ('Coordinación Preparatoria', 'coordinacion.preparatoria@tecmilenio.mx', 'Coordinador'),
    ('José Antonio Aguilar Montalvo', 'j.aguilar@tecmilenio.mx', 'Profesor'),
    ('Andrade Nava  Daniel Alejandro', 'andrade.alejandro@tecmilenio.mx', 'Profesor'),
    ('Guillermo Anzaldo Cruz', 'guillermo.anzaldo@tecmilenio.mx', 'Profesor'),
    ('Luis Manuel Arenas Merchant', 'luis.arenas@tecmilenio.mx', 'Coordinador'),
    ('Avril Arteaga Puente', 'avrilartepuente@tecmilenio.mx', 'Profesor'),
    ('Brenda Ivette Avalos Aquino', 'brenda.avalos@tecmilenio.mx', 'Profesor'),
    ('Maria del Rosario Balderas Gonzaga', 'mrbalderas@tecmilenio.mx', 'Profesor'),
    ('Barrios Hernández  Diana AÁngelica', 'barrios.angelica@tecmilenio.mx', 'Profesor'),
    ('Campos Vidales  Ricardo', 'campos.ricardo@tecmilenio.mx', 'Profesor'),
    ('María Fernanda Cárdenas García', 'fer.cardenas@tecmilenio.mx', 'Profesor'),
    ('Maricela Carvajal Aguilar', 'maricela.carvajal@tecmilenio.mx', 'Profesor'),
    ('Ceron Maldonado  Josééée Luis', 'ceron.luis@tecmilenio.mx', 'Profesor'),
    ('Cohen Ruiz  Gibran Joséééué', 'cohen.josu@tecmilenio.mx', 'Profesor'),
    ('Paulina Alejandra Contreras Martínez', 'paucontrerasm@tecmilenio.mx', 'Profesor'),
    ('Corzo Saúlazar  Rocío', 'corzo.roco@tecmilenio.mx', 'Profesor'),
    ('Luis Ricardo Cozar Navarro', 'luis.cozar@tecmilenio.mx', 'Profesor'),
    ('Juan Pablo Cuellar Cercle', 'pablo_633@tecmilenio.mx', 'Profesor'),
    ('Cristian Daniel Damián Olvera', 'cristian.damian@tecmilenio.mx', 'Profesor'),
    ('Paulo César De la Vega Bedolla', 'paulo-cesar@tecmilenio.mx', 'Profesor'),
    ('Delgadillo Arana  Miguel AÁngel', 'delgadillo.angel@tecmilenio.mx', 'Profesor'),
    ('Sayda Díaz Celaya', 'sayda.di@tecmilenio.mx', 'Profesor'),
    ('Jesús Eduardo Domínguez Murrieta', 'murrieta.eduardo@tecmilenio.mx', 'Profesor'),
    ('Brenda Escobar Domínguez', 'brenda.ed@tecmilenio.mx', 'Profesor'),
    ('Escobedo Cabello  Rubén', 'escobedo.rubn@tecmilenio.mx', 'Profesor'),
    ('Espinosa Moreno  Bernardo Noé', 'espinosa.no@tecmilenio.mx', 'Profesor'),
    ('Mariana Lizbeth Esqueda Villalpando', 'mariana_esdesign@tecmilenio.mx', 'Profesor'),
    ('Wendy María Flores Pérez', 'wenflores@tecmilenio.mx', 'Profesor'),
    ('Jorge Alfredo Frias Estrada', 'jorge_frias@tecmilenio.mx', 'Profesor'),
    ('Sonia García Hernández', 'sonia.garcia@tecmilenio.mx', 'Profesor'),
    ('García Ramírez  Fernando Alan', 'garca.alan@tecmilenio.mx', 'Profesor'),
    ('Evelin Itzel Gazcon Gutierrez', 'lic.gazcon.evelin@tecmilenio.mx', 'Profesor'),
    ('Hugo Alan Gómez Alcaraz', 'hugo.gomez@tecmilenio.mx', 'Profesor'),
    ('Gómez Briceño  Abigail', 'gmez.abigail@tecmilenio.mx', 'Profesor'),
    ('Lucía Gómez Sánchez', 'lucia.gomez.s@tecmilenio.mx', 'Profesor'),
    ('Felix Gonzalez Gonzalez', 'fgonzale@tecmilenio.mx', 'Profesor'),
    ('Debbie Amneris Granados Sevilla', 'debbie@tecmilenio.mx', 'Profesor'),
    ('Guillermo Guerrero Aguilar', 'memoguerrero@tecmilenio.mx', 'Profesor'),
    ('Liliana Gutiérrez Gutiérrez', 'lgutier@tecmilenio.mx', 'Profesor'),
    ('Hernández Carmona  Guillermo', 'hernndez.guillermo@tecmilenio.mx', 'Profesor'),
    ('Hernandez Moya  Josééée Carlos', 'hernandez.carlos@tecmilenio.mx', 'Profesor'),
    ('Hernández Uvilla  Rubén', 'hernndez.rubn@tecmilenio.mx', 'Profesor'),
    ('Blanca Hernández Vázquez', 'blanca.hernandez@tecmilenio.mx', 'Profesor'),
    ('Herrera Pérez  Víctor Adriel', 'herrera.adriel@tecmilenio.mx', 'Profesor'),
    ('Bryan Antonio Islas Sánchez', 'antonio_i.sanchez@tecmilenio.mx', 'Profesor'),
    ('Juárez Pereyra  Adán', 'jurez.adn@tecmilenio.mx', 'Profesor'),
    ('Loesa Velázquez  Osvaldo Joaquín', 'loesa.joaqun@tecmilenio.mx', 'Profesor'),
    ('Luis Miguel López de Uralde Martínez', 'migueluralde@tecmilenio.mx', 'Profesor'),
    ('López Martinez  Laura Patricia', 'lpez.patricia@tecmilenio.mx', 'Profesor'),
    ('Alejandro Marrufo Márquez', 'alejandro.marrufo@tecmilenio.mx', 'Profesor'),
    ('Smirna Guadalupe Martinez Lira', 'smirna.lira@tecmilenio.mx', 'Profesor'),
    ('María Fernanda Martínez Nieto', 'mfmartinez@tecmilenio.mx', 'Profesor'),
    ('Juvencio Martínez Peña', 'juvenciomartinezpena@tecmilenio.mx', 'Profesor'),
    ('Martínez Pérez  Joséééé Arturo', 'martnez.arturo@tecmilenio.mx', 'Profesor'),
    ('Cinthia Martínez Rojas', 'cinthia.martinezr@tecmilenio.mx', 'Profesor'),
    ('Jesúsica Irais Mateos Ledezma', 'jess.i.mateos@tecmilenio.mx', 'Profesor'),
    ('Nallely Mendez Mercado', 'nallely.mendez@tecmilenio.mx', 'Profesor'),
    ('Misael Ricardo Mendoza Zúñiga', 'ricardo_mendoza@tecmilenio.mx', 'Profesor'),
    ('Meza Huerta  Ivan', 'meza.ivan@tecmilenio.mx', 'Profesor'),
    ('Marisol Molina Reyes', 'marisol.mr@tecmilenio.mx', 'Profesor'),
    ('Molinar Lopez  Javier David', 'molinar.david@tecmilenio.mx', 'Profesor'),
    ('Jorge Alberto Morales Hernández', 'jorge.morales@tecmilenio.mx', 'Profesor'),
    ('Julio César Moreno Montoya', 'jcmoreno@tecmilenio.mx', 'Coordinador'),
    ('Nava Flores  Daniela', 'nava.daniela@tecmilenio.mx', 'Profesor'),
    ('Adriana Ivonne Pacheco Garibay', 'ivonne_pacheco@tecmilenio.mx', 'Profesor'),
    ('Palacios Chavarria  Víctor Daniel', 'palacios.daniel@tecmilenio.mx', 'Profesor'),
    ('Paz AÁlvarez  Alexis Fabián', 'paz.fabin@tecmilenio.mx', 'Profesor'),
    ('María Guadalupe Julieta Peña Iñiguez', 'maria.pena@tecmilenio.mx', 'Profesor'),
    ('Luis Alberto Pineda Calvo', 'alberto.pineda@tecmilenio.mx', 'Profesor'),
    ('Gabriel Eduardo Portillo Islas', 'gabriel.islas@tecmilenio.mx', 'Profesor'),
    ('Esteban Puerta Elias', 'elias_puerta@tecmilenio.mx', 'Profesor'),
    ('Luis Benjamin Quiñones González', 'benjamin.quinones@tecmilenio.mx', 'Profesor'),
    ('Karina Irais Ramirez Flores', 'irais_ramirez@tecmilenio.mx', 'Profesor'),
    ('Ramírez Segura  Luis Daniel', 'ramrez.daniel@tecmilenio.mx', 'Profesor'),
    ('Carlos Ravelo Lara', 'ravello.lara@tecmilenio.mx', 'Profesor'),
    ('Alma Alin Reyes Hidalgo', 'alin.reyes@tecmilenio.mx', 'Mentor'),
    ('Silvia Reyes León', 'silvia.reyes@tecmilenio.mx', 'Coordinador'),
    ('Rios Ruiz  Rooney Manuel', 'rios.manuel@tecmilenio.mx', 'Profesor'),
    ('Rivas Trejo  Laura AÁngelica', 'rivas.angelica@tecmilenio.mx', 'Profesor'),
    ('María Eugenia Rivera Elizalde', 'maru_rivera@tecmilenio.mx', 'Profesor'),
    ('Araceli Jiseth Rodríguez Pérez', 'jiseth.perez@tecmilenio.mx', 'Profesor'),
    ('Rosas Rivera  Joséééé Porfirio Francisco', 'rosas.francisco@tecmilenio.mx', 'Profesor'),
    ('Jesús Fernando Sánchez Becerril', 'jesus.sanchez@tecmilenio.mx', 'Profesor'),
    ('Gregorio Adrián Sánchez Díaz', 'greg_sd@tecmilenio.mx', 'Profesor'),
    ('Suárez Hernández  Joséééé Alfredo', 'surez.alfredo@tecmilenio.mx', 'Profesor'),
    ('Tirado Hernandez  Carla Patricia', 'tirado.patricia@tecmilenio.mx', 'Profesor'),
    ('Torres Reyes  Lily Daniella', 'torres.daniella@tecmilenio.mx', 'Profesor'),
    ('Tovar Beltran  Hazael', 'tovar.hazael@tecmilenio.mx', 'Profesor'),
    ('Valdes Flores  Josééée Luis', 'valdes.luis@tecmilenio.mx', 'Profesor'),
    ('Erika Van Scoit Arizmendi', 'eri.vanscoit@tecmilenio.mx', 'Profesor'),
    ('Gustavo Vargas Cortes', 'gustavo.vargas@tecmilenio.mx', 'Profesor'),
    ('Alejandra Vega Contreras', 'ale.vega@tecmilenio.mx', 'Profesor'),
    ('Vergara Montañez  Joséééue David', 'vergara.david@tecmilenio.mx', 'Profesor'),
    ('David Villalobos Chávez', 'dvillalobos@tecmilenio.mx', 'Profesor'),
    ('Diego Israel Villalpando Camberos', 'diego.villalpando@tecmilenio.mx', 'Profesor'),
    ('Aleida Azeneth Zamorano Herrera', 'aleidaz@tecmilenio.mx', 'Profesor'),
    ('Miguel Ángel Zúñiga Manríquez', 'mzuniga@tecmilenio.mx', 'Profesor'),
    ('Zurita Mora  Joséééé Luis', 'zurita.luis@tecmilenio.mx', 'Profesor'),
    ('Jonathan Saúlazar Ortega', 'jonathansalazaro@tecmilenio.mx', 'Profesor'),
    ('Profesor Demo', 'profesor.demo@tecmilenio.mx', 'Responsable'),
    ('María del Consuelo Yanez Campos', 'mayanez@tecmilenio.mx', 'Coordinador'),
    ('Elizabeth Guerrero Flores', 'eliguerrero@tecmilenio.mx', 'Profesor'),
    ('Rosa Mirna Arzate Hernández', 'mirna.arzate@tecmilenio.mx', 'Profesor'),
    ('Alejandra Ceron Maldonado', 'alceron@tecmilenio.mx', 'Mentor'),
    ('Areli Mitshu Hernandez Moreno', 'armhernande@tecmilenio.mx', 'Profesor'),
    ('Francelia Virgen Ochoa Saules', 'fran.ochoa@tecmilenio.mx', 'Profesor'),
    ('Gibran Joséue Cohen Ruiz', 'gcohen@tecmilenio.mx', 'Profesor'),
    ('Joséue Carreño Camacho', 'j.carreno@tecmilenio.mx', 'Profesor'),
    ('Tabata Gamez Trejo', 'tgamez@tecmilenio.mx', 'Profesor'),
    ('Yesenia Liliana Moran Santana', 'ymoran@tecmilenio.mx', 'Mentor'),
    ('Alberto Moreno García', 'betomoreno1@tecmilenio.mx', 'Profesor'),
    ('María Cristina Juárez Flores', 'juarez.cristina@tecmilenio.mx', 'Profesor'),
    ('Josée Luis Valdes Flores', 'jlvaldes@tecmilenio.mx', 'Profesor'),
    ('Gloria Alejandra Lopez Morell', 'gllopezm@tecmilenio.mx', 'Profesor'),
    ('Maria Guadalupe Mondragon Martinez', 'guadalupe.mondragon@tecmilenio.mx', 'Profesor'),
    ('Melisa Margaretha Juana Flameng Van Poppel', 'mmflameng@tecmilenio.mx', 'Profesor'),
    ('Shady Shahin González', 'shady.shahin@tecmilenio.mx', 'Profesor'),
    ('Virginia Buensuceso Solano', 'virginia.bs@tecmilenio.mx', 'Profesor'),
    ('Wendolin López Arriaga', 'wendolin.arriaga@tecmilenio.mx', 'Profesor'),
    ('Silvia Nayeli Díaz Gonzalez Ramrez', 'nayeli.diaz@tecmilenio.mx', 'Profesor'),
    ('Valeria Montserrat Rodriguez Tahuilan', 'valeria.rdz@tecmilenio.mx', 'Coordinador'),
    ('Elsa María Solano Guerrero', 'elsolano@tecmilenio.mx', 'Profesor'),
    ('Israel Zepeda Peña', 'izepeda@tecmilenio.mx', 'Profesor'),
    ('Martin de Jesús Diaz Ruiz', 'martinj.diaz@tecmilenio.mx', 'Profesor'),
    ('Ruth Geraldine Tlapalamatl Lopez', 'ruth.tlapalamatl@tecmilenio.mx', 'Profesor'),
    ('Mario Diaz Guerra', 'mario.diaz@tecmilenio.mx', 'Profesor'),
    ('Ana Laura Martínez Anaya', 'analaura.martinez@tecmilenio.mx', 'Profesor'),
    ('Maria de Lourdes Pérez Gudio', 'marilourdes_perez@tecmilenio.mx', 'Profesor'),
    ('Mónica Marlene Valdez Sánchez', 'monivaldez@tecmilenio.mx', 'Profesor'),
    ('Daniel Montero Barrera', 'd.monterobarrera@tecmilenio.mx', 'Profesor'),
    ('Miguel AÁngel Delgadillo Arana', 'madelgadillo9700@tecmilenio.mx', 'Profesor'),
    ('Claudia Abril López Morales', 'abril.lopez@tecmilenio.mx', 'Profesor'),
    ('Gerardo Carrillo Alvarado', 'gerardocarrillo@tecmilenio.mx', 'Profesor'),
    ('María Eugenia González Navarro', 'eugeniagonzalez.navarro@tecmilenio.mx', 'Profesor'),
    ('Cyntia Jenifer Flores González', 'cjflores@tecmilenio.mx', 'Profesor'),
    ('Valeria Itzel Sánchez Vázquez', 'vale.sanchez@tecmilenio.mx', 'Profesor'),
    ('Areli Vargas Rojas', 'arelivargas@tecmilenio.mx', 'Profesor'),
    ('Jacobo Rodríguez Monroy', 'jacobo.rodriguezm@tecmilenio.mx', 'Profesor'),
    ('Roci Marlene Reyes Madrigal', 'rocio.reyes@tecmilenio.mx', 'Profesor'),
    ('Juan Carlos Miranda Flores', 'c_miranda@tecmilenio.mx', 'Profesor'),
    ('Luis Miguel Cardenas Gomez', 'luis.miguel.cardenas@tecmilenio.mx', 'Profesor'),
    ('Diosinantzin García Bucio', 'diosi.garcia.bucio@tecmilenio.mx', 'Profesor'),
    ('Andrs Martínez Solano', 'andres_solano@tecmilenio.mx', 'Profesor'),
    ('Andrs Colin Pineda', 'andres.colin@tecmilenio.mx', 'Profesor'),
    ('Ivette Rodríguez Delgado', 'ivette.rodriguez@tecmilenio.mx', 'Profesor'),
    ('Alfonso Sánchez Díaz', 'sanchez.alfonso@tecmilenio.mx', 'Profesor'),
    ('Laura AÁngelica Rivas Trejo', 'la.rivastrejo@tecmilenio.mx', 'Profesor'),
    ('Ana Laura Iniestra Álvarez', 'ana.iniestra@tecmilenio.mx', 'Profesor'),
    ('Claudia Elizabeth García del Bosque', 'claudia.garcia.bosque@tecmilenio.mx', 'Profesor'),
    ('Nancy Chávez Meza', 'nancymeza@tecmilenio.mx', 'Profesor'),
    ('José Porfirio Francisco Rosas Rivera', 'porfirio.rosas@tecmilenio.mx', 'Profesor'),
    ('Aldo Gabriel Trejo Mendoza', 'aldo.trejo@tecmilenio.mx', 'Profesor'),
    ('Maria Eugenia Gómez Tous', 'maru.gomez@tecmilenio.mx', 'Profesor'),
    ('Bibiana Teresa Casco Ruiz', 'bibiana.casco@tecmilenio.mx', 'Profesor'),
    ('Gabriela Guzmán García', 'gaby.guzman@tecmilenio.mx', 'Profesor'),
    ('Mayra Sanchez Aguilar', 'msanchez2019@tecmilenio.mx', 'Profesor'),
    ('Fátima García Sanchez', 'fatima_gs@tecmilenio.mx', 'Profesor'),
    ('Oscar Rodríguez Espinosa', 'orodrigueze@tecmilenio.mx', 'Profesor'),
    ('Abraham Antonio Velazquez Pineda', 'abraham8384@tecmilenio.mx', 'Profesor'),
    ('Esther Carolina Limon Medrano', 'carolina_limon@tecmilenio.mx', 'Profesor'),
    ('Arturo Flores Jaimes', 'darturo_fj@tecmilenio.mx', 'Profesor'),
    ('Emmanuel Barrn Tapia', 'emmanuel.barron@tecmilenio.mx', 'Profesor'),
    ('Josée Antonio Esqueda Villalpando', 'antonio.esqueda@tecmilenio.mx', 'Profesor'),
    ('Jesúsica Nieto Álvarez', 'jessicanieto@tecmilenio.mx', 'Profesor'),
    ('Yoyela Yanet Hernández Moreno', 'yoye.hdz@tecmilenio.mx', 'Profesor'),
    ('Osvaldo Joaqun Loesa Velázquez', 'loesa@tecmilenio.mx', 'Profesor'),
    ('Nayely Rojas Escamilla', 'n.rojas@tecmilenio.mx', 'Coordinador'),
    ('Juan Alfredo Vazquez Hernández', 'alfredo.vazquez@tecmilenio.mx', 'Profesor'),
    ('Alexasandra Pérez Sierra', 'alexa.perez@tecmilenio.mx', 'Profesor'),
    ('Cynthia Areli Castillo Martínez', 'cynthia.castillo@tecmilenio.mx', 'Coordinador'),
    ('Diana Carrillo González', 'diana.carrillo@tecmilenio.mx', 'Coordinador'),
    ('Tania Lisuli Zarate Ramirez', 'lisuli_zarate@tecmilenio.mx', 'Profesor'),
    ('Sandra Pérez Herrera', 'pherrera.sandra@tecmilenio.mx', 'Profesor'),
    ('Ludwig Aarón López Ducoing', 'ludwig_ducoing@tecmilenio.mx', 'Profesor'),
    ('Blanca Estela Cruz Espinoza', 'blantela.espinoza@tecmilenio.mx', 'Profesor'),
    ('María Cristina Bassaure Anaya', 'bassaure.cristina@tecmilenio.mx', 'Profesor'),
    ('Miguel AÁngel Crisanto Godínez', 'miguel.crisanto@tecmilenio.mx', 'Profesor'),
    ('Fernando Cruz Ibarra', 'fernando.cruzibarra@tecmilenio.mx', 'Profesor'),
    ('José Luis Zurita Mora', 'luiszurita88@tecmilenio.mx', 'Profesor'),
    ('Azucena Jocelyn Rojas Cristain', 'jocelynrojas@tecmilenio.mx', 'Profesor'),
    ('Francisco Ratz Velasco', 'francisco.ratz@tecmilenio.mx', 'Profesor'),
    ('Guadalupe Monserrat Jiménez Ayala', 'monserrat.jimenez@tecmilenio.mx', 'Profesor'),
    ('Viridiana Ramos Arellano', 'viridianaramos@tecmilenio.mx', 'Profesor'),
    ('Sandra Paola Gómez López', 'sandrap.gomezlo@tecmilenio.mx', 'Profesor'),
    ('Thelma Martínez Correa', 'thelma.m.correa@tecmilenio.mx', 'Profesor'),
    ('Alicia Mariana Fuentes Lechuga', 'am.fuentes@tecmilenio.mx', 'Coordinador'),
    ('Verónica Olmos Martínez', 'veronica-om@tecmilenio.mx', 'Profesor'),
    ('Frida Díaz Iniestra', 'frida.diaz@tecmilenio.mx', 'Profesor'),
    ('Miriam Guadalupe Sánchez Aguilar', 'miriam.sanchez@tecmilenio.mx', 'Profesor'),
    ('Luis Felipe Espinoza Uribe', 'luisfelipe.espinoza.uribe@tecmilenio.mx', 'Profesor'),
    ('Sandra Colin Serafín', 'sandra_colin@tecmilenio.mx', 'Mentor'),
    ('Maria Lizbeth Vargas González', 'vargas.l@tecmilenio.mx', 'Profesor'),
    ('Amehed Sahid Flores Pérez', 'sahidf@tecmilenio.mx', 'Coordinador'),
    ('Ana Erandi Jiménez González', 'jerandi@tecmilenio.mx', 'Profesor'),
    ('Lucero Rocío Jiménez Ramos', 'lucero.jimenez@tecmilenio.mx', 'Profesor'),
    ('Raúl Carrasco López', 'r.carrasco@tecmilenio.mx', 'Profesor'),
    ('Cinthya Alejandra Mora Rodríguez', 'morar_cin@tecmilenio.mx', 'Profesor'),
    ('Fernando Domínguez Chang', 'fernando.dominguez@tecmilenio.mx', 'Coordinador'),
    ('Karen Selena Gutiérrez Martínez', 'karen_sgm@tecmilenio.mx', 'Profesor'),
    ('Christian Carrasco Sánchez', 'c.carrasco@tecmilenio.mx', 'Profesor'),
    ('Katia Paola Garduño González', 'pgarduno@tecmilenio.mx', 'Profesor'),
    ('Abigail Peña Domínguez', 'abigail.p@tecmilenio.mx', 'Profesor'),
    ('Yolotzin Doniz Vargas', 'yolotzin.doniz@tecmilenio.mx', 'Profesor'),
    ('José Carlos Hernández Moya', 'jose_carlosh@tecmilenio.mx', 'Profesor'),
    ('Karla Magali Valdez Rojas', 'karla.valdez@tecmilenio.mx', 'Profesor'),
    ('Saúl López Torres', 'saul_lopez.t@tecmilenio.mx', 'Profesor'),
    ('Diana Karen López Herrerías', 'dian.lopez@tecmilenio.mx', 'Profesor'),
    ('Catharine Fernanda Allende Díaz', 'ferdiaz@tecmilenio.mx', 'Profesor'),
    ('Karen Anglica Muñoz Torres', 'karen_munoz@tecmilenio.mx', 'Profesor'),
    ('Nasheli López Bautista', 'nasheli.lopez@tecmilenio.mx', 'Profesor'),
    ('Luis Daniel Hernández Licona', 'luis.hernandez@tecmilenio.mx', 'Profesor'),
    ('Mary Judith Correa Jaimes', 'mary.correa@tecmilenio.mx', 'Profesor'),
    ('Clauzzen Daniela Ávila Hernández', 'clauzzen_avila@tecmilenio.mx', 'Profesor'),
    ('Jhonathan Robles Sánchez', 'jhonathan.robles@tecmilenio.mx', 'Profesor'),
    ('Irma Alvarado Guzmán', 'irma.alvarado@tecmilenio.mx', 'Profesor'),
    ('Juan Carlos Perez Vega', 'jcarlospvega@tecmilenio.mx', 'Profesor'),
    ('Laura Elena Sánchez Castillo', 'laura.elena.san@tecmilenio.mx', 'Profesor'),
    ('Laura Pamela Camacho Ortiz', 'pamela.camacho@tecmilenio.mx', 'Profesor'),
    ('Montserrat Contreras Ortega', 'montserrat.ortega@tecmilenio.mx', 'Profesor'),
    ('Daniel Missael Pérez Bárcenas', 'dmissaelpb@tecmilenio.mx', 'Profesor'),
    ('Verónica Cisneros Carrillo', 'vero.cisneros@tecmilenio.mx', 'Profesor'),
    ('Gerardo Montenegro Saúlgado', 'g.montengro@tecmilenio.mx', 'Profesor'),
    ('Gilberto Pérez Arguelles', 'gilberto.p.arguelles@tecmilenio.mx', 'Profesor'),
    ('Araceli Magdalena García Madrigal', 'aramadrigal@tecmilenio.mx', 'Profesor'),
    ('Mónica Olmos Solis', 'olmos_monica@tecmilenio.mx', 'Profesor'),
    ('Verónica Yael Escalante Hernández', 'vero.escalante@tecmilenio.mx', 'Profesor'),
    ('Sarai Yuriko Aguilar Quevedo', 'yuriko.aguilar@tecmilenio.mx', 'Profesor'),
    ('Enrique Osorio Andrade', 'enrique.osorio@tecmilenio.mx', 'Profesor'),
    ('Rosa María Alejandra Romero Bañuelos', 'rosa.romero.b@tecmilenio.mx', 'Profesor'),
    ('Marlen Montes Ruiz', 'marlen_montes@tecmilenio.mx', 'Profesor'),
    ('Shalma Michelle Martinez Gutierrez', 'michellegutierrez@tecmilenio.mx', 'Profesor'),
    ('Karla Yadira Vázquez Fernndez', 'karlavzquez@tecmilenio.mx', 'Profesor'),
    ('José Alfredo Suárez Hernández', 'alfredosuarez@tecmilenio.mx', 'Profesor'),
    ('María del Rosario Aguilar Jiménez', 'rosario.aguilar@tecmilenio.mx', 'Profesor'),
    ('Evelyn Andrea Mendoza Gonzalez', 'evelyn_mendoza@tecmilenio.mx', 'Coordinador'),
    ('Luis Daniel Ramrez Segura', 'dany_ramirez@tecmilenio.mx', 'Profesor'),
    ('Nayelli Sánchez Piña', 'naye.sanchez@tecmilenio.mx', 'Profesor'),
    ('Matthew AÁlvarez Trejo', 'matt.alvarez@tecmilenio.mx', 'Profesor'),
    ('Aylin Damaris Merino Resndiz', 'aylinmerino@tecmilenio.mx', 'Profesor'),
    ('María Fernanda Ling Flores', 'fernanda.ling@tecmilenio.mx', 'Profesor'),
    ('Estefana Magali Hernández Ramrez', 'ehernandezram@tecmilenio.mx', 'Profesor'),
    ('Ricardo Gabriel Avilés Trujeque', 'ricardo.avilest@tecmilenio.mx', 'Profesor'),
    ('Bernardo No Espinosa Moreno', 'noe.espinosa@tecmilenio.mx', 'Profesor'),
    ('Karina Guadalupe Viquez Viquez', 'karina.gvv@tecmilenio.mx', 'Profesor'),
    ('Carolina Avelar Rodríguez', 'carol_avelar@tecmilenio.mx', 'Profesor'),
    ('Javier Armando Díaz Reyes', 'javi.dr@tecmilenio.mx', 'Profesor'),
    ('Naizet Daniela Vazquez Montoya', 'naizetvazquez@tecmilenio.mx', 'Profesor'),
    ('Karla Merari García Sánchez', 'karla.merari@tecmilenio.mx', 'Profesor'),
    ('Daniela Melendez Hernández', 'dan_hernandez@tecmilenio.mx', 'Profesor'),
    ('Roberto Javier Alvarado Guzmán', 'rob_alvarado@tecmilenio.mx', 'Profesor'),
    ('Erika Enedina Martínez Montejo', 'erika.martinez@tecmilenio.mx', 'Profesor'),
    ('Susana Alejandra Mendoza Contreras', 'susana_mendozac@tecmilenio.mx', 'Profesor'),
    ('Monica Francisco Bautista', 'monica.fran@tecmilenio.mx', 'Profesor'),
    ('Jesús Eduardo Espinosa Ibarra', 'jeduardo.espinosa@tecmilenio.mx', 'Profesor'),
    ('Rubn Escobedo Cabello', 'rbnescobedoc@tecmilenio.mx', 'Profesor'),
    ('José Luis Cerón Maldonado', 'luis.ceron@tecmilenio.mx', 'Profesor'),
    ('Sergio Diaz García', 'sergio_dg@tecmilenio.mx', 'Profesor'),
    ('Pamela Izaret Pérez Martínez', 'pamela.perez@tecmilenio.mx', 'Profesor'),
    ('Marian Elizabeth Pontigo Suárez', 'maryann@tecmilenio.mx', 'Profesor'),
    ('Tania Paola López Alcaraz', 'pao.alcaraz@tecmilenio.mx', 'Profesor'),
    ('Roberto Luna Vázquez', 'roberto.luna@tecmilenio.mx', 'Profesor'),
    ('Anahis Aparicio Izquierdo', 'anahis.aparicio@tecmilenio.mx', 'Profesor'),
    ('Martha Patricia López Navarro', 'martha_lopezn@tecmilenio.mx', 'Profesor'),
    ('Christian Jonathan Saúlinas Martínez', 'chris.salinas@tecmilenio.mx', 'Profesor'),
    ('Dea Melina Pablo Francisco', 'melina.pablo@tecmilenio.mx', 'Profesor'),
    ('Tamara Alvarado González', '', 'Profesor'),
    ('Karla Cecilia Ledezma Chávez', 'karla.cecilia.ledezma@tecmilenio.mx', 'Profesor'),
    ('Itzel Nuñez Mora', 'itzel_nunez@tecmilenio.mx', 'Profesor'),
    ('Delia Paola Cárdenas Guzmán', 'delia.cardenas@tecmilenio.mx', 'Profesor'),
    ('Abigail Gómez Briceo', 'abigailgbri@tecmilenio.mx', 'Profesor'),
    ('Gustavo Sandate Díaz Leal', 'gustavo.sandate@tecmilenio.mx', 'Coordinador'),
    ('Rodrigo Díaz Iniestra', 'roydi@tecmilenio.mx', 'Profesor'),
    ('Fernando Alan García Ramrez', 'alan.garcia@tecmilenio.mx', 'Profesor'),
    ('Pedro Javier Gálvez Santiago', 'pgalvez@tecmilenio.mx', 'Profesor'),
    ('Daniela Gabriela Ruiz Olvera', 'daniela.olvera@tecmilenio.mx', 'Profesor'),
    ('Edson Tonatiuh Santos Saúlazar', 'santos_92@tecmilenio.mx', 'Profesor'),
    ('Jonathan Rodríguez Jiménez', 'rodriguez_jon@tecmilenio.mx', 'Profesor'),
    ('Guillermo Ávila Martínez', 'guillermo.avila@tecmilenio.mx', 'Profesor'),
    ('Jocelyn Caballero Ayala', 'j.caballero@tecmilenio.mx', 'Profesor'),
    ('Alejandro López Lezama', 'lopezlezama@tecmilenio.mx', 'Profesor'),
    ('Anglica Alcalá Hernández', 'angelica.alcala@tecmilenio.mx', 'Profesor'),
    ('Leinali Alexsandybelle Arce López', 'leinali.arce@tecmilenio.mx', 'Profesor'),
    ('Mariana Barros Escudero', 'marianabarrose@tecmilenio.mx', 'Profesor'),
    ('José Arturo Martínez Pérez', 'josarturo_m_p@tecmilenio.mx', 'Profesor'),
    ('Claudia Edith Raymundo Reyes', 'claudia.raymundo@tecmilenio.mx', 'Profesor'),
    ('Rocío Corzo Saúlazar', 'rocio.corzo@tecmilenio.mx', 'Profesor'),
    ('Adn Juárez Pereyra', 'adanjp@tecmilenio.mx', 'Profesor'),
    ('Giovanni Román Rodríguez', 'gio_roman@tecmilenio.mx', 'Profesor'),
    ('Jesúsica Mariana Pablo Alonso', 'jessica.pablo@tecmilenio.mx', 'Profesor'),
    ('Yair de Jesús Hernandez Vazquez', 'yair.hernandez@tecmilenio.mx', 'Profesor'),
    ('Ricardo Gutiérrez Prado', 'ricardo.gprado@tecmilenio.mx', 'Profesor'),
    ('Miguel Ángel Hernández Gasca', 'miguel.gasca@tecmilenio.mx', 'Profesor'),
    ('Huitzilin Fuentes Hernández', 'huitzi.fuentes@tecmilenio.mx', 'Profesor'),
    ('Alejandro Velazquez Maza', 'alejandro.velazquez@tecmilenio.mx', 'Profesor'),
    ('AÁngel Ramses Jimenez Orozco', 'ramsesjimenez@tecmilenio.mx', 'Profesor'),
    ('Enrique Arturo Campos Cid', 'enrique.campos@tecmilenio.mx', 'Profesor'),
    ('Rubn Hernández Uvilla', 'rubenhuvilla@tecmilenio.mx', 'Profesor'),
    ('Vctor Adriel Herrera Pérez', 'victoradriel@tecmilenio.mx', 'Profesor'),
    ('Irving Maximiliano Rocha Guevara', 'irving.rocha@tecmilenio.mx', 'Profesor'),
    ('Joséue David Vergara Montañez', 'david.vergara@tecmilenio.mx', 'Profesor'),
    ('Iztlli Yani Acosta Lozada', 'iztlli.aclo@tecmilenio.mx', 'Profesor'),
    ('Juan Carlos Sánchez Hernández', 'juancsh@tecmilenio.mx', 'Profesor'),
    ('Roberto Trejo Perez', '', 'Profesor'),
    ('Marcela Sánchez Piña', 'marcela.sanchez.p@tecmilenio.mx', 'Profesor'),
    ('Francisco Javier Toledo Escalona', 'francisco.toledo@tecmilenio.mx', 'Profesor'),
    ('Frida Sharlin Rowine Juárez Hernández', 'sharlin.juarez@tecmilenio.mx', 'Profesor'),
    ('Lizbeth Ariadna López Martínez', 'ariadnalm16@tecmilenio.mx', 'Profesor'),
    ('Juan Pablo Cruz Carrillo', 'juan.carrillo@tecmilenio.mx', 'Profesor'),
    ('Ángel Joséu Mendoza Quintanar', 'angelmq945@tecmilenio.mx', 'Profesor');

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
