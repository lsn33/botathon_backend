-- Ejecutar como administrador SQL en la VM.
-- Crea un usuario separado para Blue Prism. Reemplaza la contraseña antes de ejecutar.

USE master;
GO
IF NOT EXISTS (SELECT 1 FROM sys.server_principals WHERE name = 'usr_blueprism')
    CREATE LOGIN usr_blueprism WITH PASSWORD = 'REEMPLAZAR_POR_CLAVE_SEGURA_2026', CHECK_POLICY = ON;
GO

USE planificacion;
GO
IF NOT EXISTS (SELECT 1 FROM sys.database_principals WHERE name = 'usr_blueprism')
    CREATE USER usr_blueprism FOR LOGIN usr_blueprism;
GO

-- Blue Prism solo puede insertar datos crudos en las tablas de recepción.
GRANT INSERT ON OBJECT::ingesta.stg_buk TO usr_blueprism;
GRANT INSERT ON OBJECT::ingesta.stg_sic TO usr_blueprism;
GO

-- No se conceden permisos sobre rrhh, sic, planificacion, parametros ni auditoria.
-- Blue Prism notifica al backend por HTTP; no necesita permisos SQL sobre ingesta.lote
-- ni ingesta.error en el flujo actual.
