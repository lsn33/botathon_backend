-- V1: esquema base para SQL Server (T-SQL)
-- Flyway ejecuta este archivo solo una vez al arrancar la app.

IF NOT EXISTS (SELECT 1 FROM sys.schemas WHERE name = 'seguridad')     EXEC('CREATE SCHEMA seguridad');
IF NOT EXISTS (SELECT 1 FROM sys.schemas WHERE name = 'catalogo')      EXEC('CREATE SCHEMA catalogo');
IF NOT EXISTS (SELECT 1 FROM sys.schemas WHERE name = 'parametros')    EXEC('CREATE SCHEMA parametros');
IF NOT EXISTS (SELECT 1 FROM sys.schemas WHERE name = 'calendario')    EXEC('CREATE SCHEMA calendario');
IF NOT EXISTS (SELECT 1 FROM sys.schemas WHERE name = 'ingesta')       EXEC('CREATE SCHEMA ingesta');
IF NOT EXISTS (SELECT 1 FROM sys.schemas WHERE name = 'rrhh')          EXEC('CREATE SCHEMA rrhh');
IF NOT EXISTS (SELECT 1 FROM sys.schemas WHERE name = 'sic')           EXEC('CREATE SCHEMA sic');
IF NOT EXISTS (SELECT 1 FROM sys.schemas WHERE name = 'planificacion') EXEC('CREATE SCHEMA planificacion');
IF NOT EXISTS (SELECT 1 FROM sys.schemas WHERE name = 'auditoria')     EXEC('CREATE SCHEMA auditoria');
GO

-- ===== catalogo =====
CREATE TABLE catalogo.instituto (
    id     INT           NOT NULL PRIMARY KEY,
    nombre NVARCHAR(150) NOT NULL
);
GO

CREATE TABLE catalogo.especialidad (
    cod_esp NVARCHAR(10)  NOT NULL PRIMARY KEY,
    nombre  NVARCHAR(150) NOT NULL
);
GO

CREATE TABLE catalogo.cargo (
    cargo_buk NVARCHAR(100) NOT NULL PRIMARY KEY,
    cod_esp   NVARCHAR(10)  NULL REFERENCES catalogo.especialidad(cod_esp)
);
GO

-- ===== seguridad =====
CREATE TABLE seguridad.usuario (
    id            INT IDENTITY(1,1) PRIMARY KEY,
    email         NVARCHAR(150) NOT NULL UNIQUE,
    password_hash NVARCHAR(100) NOT NULL,
    nombre        NVARCHAR(150) NOT NULL,
    rol           NVARCHAR(40)  NOT NULL,
    instituto_id  INT           NULL REFERENCES catalogo.instituto(id),
    activo        BIT           NOT NULL DEFAULT 1,
    creado_en     DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME()
);
GO

-- ===== parametros =====
CREATE TABLE parametros.parametro (
    clave       NVARCHAR(60)   NOT NULL PRIMARY KEY,
    valor       DECIMAL(18,4)  NOT NULL,
    descripcion NVARCHAR(250)  NULL
);
GO

CREATE TABLE parametros.distribucion (
    id              INT IDENTITY(1,1) PRIMARY KEY,
    cod_esp         NVARCHAR(10)  NOT NULL,   -- '*' = valor por defecto para cualquier especialidad
    tramo           NVARCHAR(10)  NOT NULL,   -- ALTO | MEDIO | BAJO
    pct_directo     DECIMAL(5,2)  NOT NULL,
    pct_indirecto   DECIMAL(5,2)  NOT NULL,
    pct_nas         DECIMAL(5,2)  NOT NULL,
    minutos_directa INT           NOT NULL,
    CONSTRAINT uq_distribucion UNIQUE (cod_esp, tramo),
    CONSTRAINT ck_distribucion_suma CHECK (pct_directo + pct_indirecto + pct_nas = 100)
);
GO

-- ===== calendario =====
CREATE TABLE calendario.feriado (
    fecha  DATE          NOT NULL PRIMARY KEY,
    nombre NVARCHAR(100) NOT NULL
);
GO

-- ===== ingesta =====
CREATE TABLE ingesta.lote (
    id               BIGINT IDENTITY(1,1) PRIMARY KEY,
    tipo             NVARCHAR(10)  NOT NULL,          -- BUK | SIC
    nombre_archivo   NVARCHAR(250) NOT NULL,
    hash_sha256      NVARCHAR(64)  NULL,
    bp_proceso       NVARCHAR(100) NULL,
    bp_sesion_id     NVARCHAR(100) NULL,
    reporte_completo BIT           NOT NULL DEFAULT 0,
    estado           NVARCHAR(20)  NOT NULL DEFAULT 'PROCESANDO',
    total            INT           NOT NULL DEFAULT 0,
    nuevos           INT           NOT NULL DEFAULT 0,
    modificados      INT           NOT NULL DEFAULT 0,
    sin_cambios      INT           NOT NULL DEFAULT 0,
    bajas            INT           NOT NULL DEFAULT 0,
    errores          INT           NOT NULL DEFAULT 0,
    creado_en        DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME()
);
GO

CREATE TABLE ingesta.error (
    id      BIGINT IDENTITY(1,1) PRIMARY KEY,
    lote_id BIGINT         NOT NULL REFERENCES ingesta.lote(id),
    fila    INT            NOT NULL,
    mensaje NVARCHAR(500)  NOT NULL
);
GO

-- Tablas staging: Blue Prism puede insertar aqui directo (opcion sin llamar a la API).
-- Luego se procesan con POST /api/v1/ingesta/procesar-staging
CREATE TABLE ingesta.stg_buk (
    id              BIGINT IDENTITY(1,1) PRIMARY KEY,
    nombre_archivo  NVARCHAR(250) NOT NULL,
    persona_id      BIGINT        NOT NULL,
    instituto_id    INT           NOT NULL,
    periodo         DATE          NOT NULL,
    cargo_buk       NVARCHAR(100) NOT NULL,
    cod_esp         NVARCHAR(10)  NULL,
    horas_semanales DECIMAL(5,2)  NOT NULL,
    tipo_contrato   NVARCHAR(50)  NULL,
    tipo_trabajador NVARCHAR(50)  NULL,
    procesado       BIT           NOT NULL DEFAULT 0,
    lote_id         BIGINT        NULL,
    creado_en       DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME()
);
GO

CREATE TABLE ingesta.stg_sic (
    id                   BIGINT IDENTITY(1,1) PRIMARY KEY,
    nombre_archivo       NVARCHAR(250) NOT NULL,
    persona_id           BIGINT        NOT NULL,
    instituto_id         INT           NOT NULL,
    periodo              DATE          NOT NULL,
    atenciones_realizadas INT          NOT NULL,
    procesado            BIT           NOT NULL DEFAULT 0,
    lote_id              BIGINT        NULL,
    creado_en            DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME()
);
GO

-- ===== rrhh =====
CREATE TABLE rrhh.persona (
    id     BIGINT        NOT NULL PRIMARY KEY,
    nombre NVARCHAR(200) NULL
);
GO

-- Una fila por persona + instituto + cargo + mes
CREATE TABLE rrhh.dotacion_mes (
    id              BIGINT IDENTITY(1,1) PRIMARY KEY,
    persona_id      BIGINT        NOT NULL REFERENCES rrhh.persona(id),
    instituto_id    INT           NOT NULL REFERENCES catalogo.instituto(id),
    cargo_buk       NVARCHAR(100) NOT NULL REFERENCES catalogo.cargo(cargo_buk),
    cod_esp         NVARCHAR(10)  NULL,
    periodo         DATE          NOT NULL,           -- siempre dia 1 del mes
    horas_semanales DECIMAL(5,2)  NOT NULL,
    tipo_contrato   NVARCHAR(50)  NULL,
    tipo_trabajador NVARCHAR(50)  NULL,
    activo          BIT           NOT NULL DEFAULT 1,
    actualizado_en  DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT uq_dotacion UNIQUE (persona_id, instituto_id, cargo_buk, periodo)
);
GO
CREATE INDEX ix_dotacion_periodo ON rrhh.dotacion_mes (periodo, instituto_id) WHERE activo = 1;
GO

CREATE TABLE rrhh.dotacion_historial (
    id          BIGINT IDENTITY(1,1) PRIMARY KEY,
    dotacion_id BIGINT         NOT NULL REFERENCES rrhh.dotacion_mes(id),
    lote_id     BIGINT         NULL,
    tipo_cambio NVARCHAR(20)   NOT NULL,              -- ALTA | MODIFICACION | BAJA | REACTIVACION
    detalle     NVARCHAR(500)  NULL,
    creado_en   DATETIME2      NOT NULL DEFAULT SYSUTCDATETIME()
);
GO

-- ===== sic (atenciones realizadas, agregadas por persona/instituto/mes) =====
CREATE TABLE sic.atencion (
    id                    BIGINT IDENTITY(1,1) PRIMARY KEY,
    persona_id            BIGINT    NOT NULL,
    instituto_id          INT       NOT NULL REFERENCES catalogo.instituto(id),
    periodo               DATE      NOT NULL,
    atenciones_realizadas INT       NOT NULL,
    lote_id               BIGINT    NULL,
    actualizado_en        DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT uq_sic UNIQUE (persona_id, instituto_id, periodo)
);
GO

-- ===== planificacion =====
CREATE TABLE planificacion.resultado (
    id                      BIGINT IDENTITY(1,1) PRIMARY KEY,
    dotacion_id             BIGINT        NOT NULL UNIQUE REFERENCES rrhh.dotacion_mes(id),
    horas_contratadas       DECIMAL(8,2)  NOT NULL,
    horas_directas          DECIMAL(8,2)  NOT NULL,
    horas_indirectas        DECIMAL(8,2)  NOT NULL,
    horas_nas               DECIMAL(8,2)  NOT NULL,
    horas_disponibles       DECIMAL(8,2)  NOT NULL,
    atenciones_directas     INT           NOT NULL,
    atenciones_indirectas   INT           NOT NULL,
    atenciones_planificadas INT           NOT NULL,
    meta_gestion            DECIMAL(10,2) NOT NULL,
    pct_foja                DECIMAL(6,2)  NOT NULL,
    calculado_en            DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME()
);
GO

-- ===== auditoria =====
CREATE TABLE auditoria.registro (
    id        BIGINT IDENTITY(1,1) PRIMARY KEY,
    usuario   NVARCHAR(150) NOT NULL,
    accion    NVARCHAR(60)  NOT NULL,
    entidad   NVARCHAR(60)  NULL,
    detalle   NVARCHAR(1000) NULL,
    creado_en DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME()
);
GO
