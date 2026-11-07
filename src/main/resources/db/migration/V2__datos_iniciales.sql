-- V2: datos iniciales.
-- IMPORTANTE: los porcentajes de distribucion son PLACEHOLDERS. Hay que reemplazarlos
-- con los valores reales del Excel de Teleton (se pueden editar por PUT /api/v1/parametros/distribucion).

-- Catalogos de ejemplo (cargar los reales de Teleton cuando los tengan)
INSERT INTO catalogo.instituto (id, nombre) VALUES (131, N'Instituto de ejemplo (131)');
INSERT INTO catalogo.especialidad (cod_esp, nombre) VALUES (N'KI', N'Kinesiologia');
INSERT INTO catalogo.cargo (cargo_buk, cod_esp) VALUES (N'KINESIOLOGO', N'KI');
GO

INSERT INTO parametros.parametro (clave, valor, descripcion) VALUES
 (N'META_GESTION_PCT',        90,  N'Meta de gestion asistencial: % sobre atenciones planificadas'),
 (N'MINUTOS_INDIRECTA',       30,  N'Minutos estandar por atencion indirecta'),
 (N'DIAS_SEMANA_HABILES',      5,  N'Dias habiles por semana (para proratear horas del mes)'),
 (N'TRAMO_ALTO_MIN',          40,  N'Horas semanales desde las cuales aplica tramo ALTO'),
 (N'TRAMO_MEDIO_MIN',         22,  N'Horas semanales desde las cuales aplica tramo MEDIO'),
 (N'JORNADA_HASTA_ABR_2026',  44,  N'Jornada legal hasta abril 2026 (informativo)'),
 (N'JORNADA_DESDE_MAY_2026',  42,  N'Jornada legal desde mayo 2026 (informativo)');
GO

INSERT INTO parametros.distribucion (cod_esp, tramo, pct_directo, pct_indirecto, pct_nas, minutos_directa) VALUES
 (N'*', N'ALTO',  70, 10, 20, 45),
 (N'*', N'MEDIO', 60, 10, 30, 45),
 (N'*', N'BAJO',  50, 10, 40, 45);
GO

-- Feriados Chile 2026 (verificar contra el calendario oficial)
INSERT INTO calendario.feriado (fecha, nombre) VALUES
 ('2026-01-01', N'Año Nuevo'),
 ('2026-04-03', N'Viernes Santo'),
 ('2026-04-04', N'Sabado Santo'),
 ('2026-05-01', N'Dia del Trabajo'),
 ('2026-05-21', N'Glorias Navales'),
 ('2026-06-21', N'Pueblos Indigenas'),
 ('2026-06-29', N'San Pedro y San Pablo'),
 ('2026-07-16', N'Virgen del Carmen'),
 ('2026-08-15', N'Asuncion de la Virgen'),
 ('2026-09-18', N'Independencia Nacional'),
 ('2026-09-19', N'Glorias del Ejercito'),
 ('2026-10-12', N'Encuentro de Dos Mundos'),
 ('2026-10-31', N'Iglesias Evangelicas'),
 ('2026-11-01', N'Todos los Santos'),
 ('2026-12-08', N'Inmaculada Concepcion'),
 ('2026-12-25', N'Navidad');
GO
