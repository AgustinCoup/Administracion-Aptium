-- Datos iniciales de referencia.
-- INSERT IGNORE garantiza idempotencia: no falla si los registros ya existen.

INSERT IGNORE INTO autoclaves (nombre, capacidad) VALUES
('E01', 120), ('E02', 120), ('E03', 360), ('E05', 120),
('E07', 240), ('E08', 140), ('CH4', 430), ('CH6', 560);

INSERT IGNORE INTO instituciones (nombre) VALUES
('INSTITUTO MEDICO RIO CUARTO'), ('CLINICA REGIONAL DEL SUD'),
('POLICLINICO PRIVADO SAN LUCAS'), ('OFICINA'), ('SANATORIO PRIVADO'),
('NUEVO HOSPITAL RIO CUARTO'), ('NEOCLINICA');

INSERT IGNORE INTO profesionales (nombre) VALUES
('DR AGÜERO GIODA FERNANDO A.'), ('DR AZNAR IVÁN L.'), ('DR ARRIETA ALBERTO FABIÁN'),
('DR BARALE JAVIER EMILIO'), ('DR ASCORTI GUSTAVO A.'), ('DR BALMACEDA HERMES ARIEL'),
('DR BOCCARDO GERARDO ARIEL'), ('DR BALMACEDA RODOLFO HERMES'), ('DR PAULETTI ANTONIO EDUARDO D.'),
('DR PAULETTI HERNÁN ALEJANDRO'), ('DR PAULETTI GABRIEL'), ('DR BAGLIARDELLI JULIO CÉSAR'),
('DR LOVATO MAURO ATILIO'), ('DR LOVELL FERNANDO NICOLÁS'), ('DR GAGLIARDO NORBERTO JAVIER'),
('DR DA VALLE DANIEL EDUARDO'), ('DR TERZI ARIEL RODRIGO'), ('DR URBANI MARCOS RAÚL'),
('DR MACARRÓN MARCELO EDUARDO'), ('DR VIGLIONE GUILLERMO'), ('DR MARTINEZ JOSÉ ESTEBAN'),
('DR MADERNA RODRIGO MARTÍN'), ('DR COLOMBANO ROMÁN NICOLÁS'), ('DR LÓPEZ PÉREZ JUAN MANUEL'),
('DR ALCARAZ DIEGO ALEJANDRO'), ('DR SESSAREGO DANIEL EDUARDO'), ('DR SESSAREGO HORACIO'),
('DR NINI EZEQUIEL'), ('DR NUÑEZ RAFFIN FRANCO'), ('DR PAULUCCI JULIÁN');

INSERT IGNORE INTO clientes (nombre) VALUES
('CENTRO PRIVADO DE RADIOTERAPIA RIO CUARTO SA'), ('ORTOPEDIA Y CIRUGIA RIO CUARTO SRL'),
('CLINICA REGIONAL DEL SUD S A'), ('OFTALMOS SRL'), ('MUNICIPALIDAD DE LAS VERTIENTES'),
('COMUNA DE LAS ALBAHACAS'), ('CENTRO DE DIAGNOSTICO Y TRATAMIENTO MEDICO SRL'),
('SERPROMED S.A.S.'), ('GMRS S.A.'), ('GRUPO FIX S.R.L.'), ('CLINICA URQUIA PRIVADA S.A.'),
('CENTRO PRIVADO DERMI RIO CUARTO SA'), ('INSTITUTO ONCOHEMATOLOGICO PRIVADO SRL'),
('POLICLINICO PRIVADO SAN LUCAS S A'), ('EVOLUCION MEDICA S.R.L.'), ('CLINICA PRIVADA ITALIA SRL'),
('BERGAGNA MARCOS ADELFI'), ('ANGIOCOR SA'), ('IMPLANTES RB S.R.L.'),
('FUNDACION MATERNIDAD HORTENSIA GARDEY DE KOWALK'), ('PIASTRELLINI LAURA EMILIA'),
('CASASNOVAS FRANCISCO ANDRES'), ('INSTITUTO MEDICO RIO CUARTO S A'), ('DECOR MEDICA SRL'),
('AMMANN MARIA VIRGINIA'), ('MUNICIPALIDAD DE SAN BASILIO'), ('MINISTERIO DE SALUD'),
('UNIDAD RENAL RIO CUARTO SRL'), ('CARE MEDICAL SOLUTIONS S.A.'), ('OMICRON SRL'),
('KINERET SA'), ('PRIMA IMPLANTES S A'), ('HARAS RIO DOIS IRMAOS S. R. L.'),
('UNIVERSIDAD NACIONAL DE RIO CUARTO'), ('ALISER GASTRONOMIA S.A.'),
('POLIMANTI VIVIANA DEL CARMEN'), ('LOPEZ LALLANA PABLO ENRIQUE'), ('CARDIOMED S.A.'),
('SM SALUD SOCIEDAD DE RESPONSABILIDAD LIMITADA'), ('FEHU S.A.'),
('CAMPO BIOLOGICO SOCIEDAD POR ACCIONES SIMPLIFICADA'), ('KREMER MAURICIO'), ('ARMED SRL'),
('IMPLANT CIRUGIA ARGENTINA SRL'), ('MUNICIPALIDAD DE SAMPACHO'),
('CLINICA DR GREGORIO MARAÑON SA'), ('ETCHECHOURY JUAN MANUEL'), ('ODONTO GEO S.A.S.'),
('TRAUMACOR S.R.L.'), ('BIOA S.A.'), ('ZUVEL S.R.L.'), ('RIVAS PRANTTE RICARDO'),
('RAOMED S.A.'), ('AMMANN JAVIER ROBERTO'), ('FEIN MEC DE OSER Y CIA S.R.L.'),
('MUNICIPALIDAD DE RIO CUARTO'), ('BIOARTEC S.A.S.'), ('HARAS VACACION S.A.'),
('CONSULTORA DE RIESGO, SALUD Y AMBIENTE S.R.L.'), ('INDUSTRIAS MEDICAS SA'),
('DON ERCOLE SA COMERCIAL AGROPECUARIA INDUSTRIAL INMOBILIARIA Y FINANCIERA'),
('BALLESTE LUCIANA'), ('SARDOY MARIA CLARA'), ('GARAIS JOSEFINA ANGELES'),
('CLINICA PRIVADA DE PEDIATRIA Y NEONATO LOGIA SRL'), ('SANATORIO PRIVADO SAN ROQUE SRL'),
('SILMAG SOCIEDAD ANONIMA'), ('SANATORIO PRIVADO DEL SUDESTE SRL'),
('SOCIEDAD DE BENEFICENCIA HOSPITAL ITALIANO MONTE BUEY'),
('LA BARRANCOSA SOCIEDAD ANONIMA'), ('OCHOA FEDERICO GUILLERMO'), ('S & I GROUP S.R.L.'),
('INSTITUTO MULTIDISCIPLINARIO DE INVESTIGACION Y TRANSFERENCIA AGROALIMNETARIA Y BIOTECNOLOGICA IMITAB'),
('CLINICA PRIVADA DE ESPECIALIDADES DE VILLA MARIA S RL'), ('EQUIBIOTEC S.A.S.'),
('CLINICA UNION PRIVADA SRL'), ('ALQAZAR S.A.S.'), ('MERCADO DE ABASTO DE RIO CUARTO S A'),
('MUNICIPALIDAD DE BENGOLEA O. P.'), ('OSTEOLIFE SRL'), ('COCCO HECTOR CESAR'),
('FISSORE S A'), ('VERNA LUCIANO JOSE'), ('CENTRO MEDICO PAULUCCI S.A.S.'),
('INSTITUTO DE INVESTIGACION EN MICOLOGIA Y MICOTOXICOLOGIA IMICO'),
('DALVIT ALEJANDRO ENRIQUE'), ('GESTSAL S.R.L.'), ('SANATORIO CRUZ AZUL SRL'),
('MUNICIPALIDAD DE ALCIRA GIGENA'), ('SCIENTIFIC S.A.'), ('EMERG RC S.R.L.'),
('AVED S.A.'), ('TABARES MERCEDES LUISA DEL V'), ('FERNIGRINI PAULA'),
('SOUTH AMERICA IMPLANTS SA'), ('SOLESAL S.A.'), ('CREBIEQ S. A. S.'),
('AMBUMED S.A.S.'), ('ENDOVIA SA');

INSERT IGNORE INTO catalogo_descripciones (codigo, descripcion, volumen) VALUES
(400, 'Tornillera', 15), (401, 'Caja de Cirugía', 0), (402, 'Caja de Cirugía tamaño "M"', 25),
(403, 'Caja de Cirugía tamaño "L"', 30), (404, 'Caja de Cirugía tamaño "XL"', 40),
(405, 'Caja de Cirugía tamaño "XXL"', 50), (406, 'Makita - Perforador - Taladro', 10),
(407, 'Sierra BTR o simil, con hojas + accesorios', 15), (408, 'Micromotores', 10),
(409, 'Guias Metálicas - Instrumental pequeño', 10), (410, 'Instrumental grande', 20),
(411, 'Prótesis', 10), (412, 'Baterías extras', 10), (413, 'Implantes', 10),
(414, 'atornilladores/ dremmel', 10), (415, 'clavijas', 10), (416, 'alambre', 10),
(417, 'SUPER XXL', 60), (418, 'clavos - placas', 10), (419, 'Makita APTIUM', 10),
(420, 'Tornillo - elemento pequeño', 5);

INSERT IGNORE INTO catalogo_otros (descripcion) VALUES
('Electrobisturí'), ('Canula de aspiración'), ('Material de Neo'), ('Cortaclavija'),
('Corrugados'), ('Aspiración fina'), ('Aspiración doble vía'), ('Venda Smarch'),
('Pinza Diente Perro'), ('Pinza (Electricidad)'), ('Aguja'), ('Sutura'), ('Manguita'),
('Mango de Cialítica'), ('Sonda'), ('Equipo descartable'), ('Pinza de Trauma'),
('Pinza Coviden Liga Sure'), ('Aspiración de Silicona'), ('Gamusa de neuro'), ('Motor'),
('Impermeables'), ('Cajas de cateters'), ('Bandeja de cx'), ('I.A'), ('Mano de foco'),
('Pirex'), ('Bandeja blanca'), ('Precicup'), ('Tapones azules'), ('Caja chalazion'),
('Plastico chico'), ('Plastico grande'), ('Fibra'), ('Bandeja de cirugía'),
('Pinza de campo'), ('Inyector alcon'), ('Chop'), ('Tapón de Silicona'), ('Bata'),
('Toalla chica'), ('Campo de tela chico'), ('Campo de tela grande'), ('Campo chalazión'),
('Vitectromo'), ('Tijera'), ('Caja refractiva'), ('Caja cross'), ('Caja de Lucentis'),
('Tupper refractivo'), ('Filtro'), ('Sonda Foley'), ('Moria'), ('Diazemia'),
('Caja Puntum Piug'), ('Cepillo'), ('Fresita'), ('Moscones nebulizadores'),
('Moscones nebulizadores pediátricos'), ('K-33'), ('K-32'), ('K-31'), ('K-9');
