-- Ejecutar UNA vez en la VM (SSMS / Azure Data Studio / sqlcmd) con un usuario administrador.
-- Flyway necesita que la base ya exista; las tablas las crea la aplicacion al arrancar.

IF DB_ID('planificacion') IS NULL
    CREATE DATABASE planificacion;
GO

USE master;
GO
IF NOT EXISTS (SELECT 1 FROM sys.server_principals WHERE name = 'planificacion_app')
    CREATE LOGIN planificacion_app WITH PASSWORD = 'CambiarEsto#2026', CHECK_POLICY = OFF;
GO

USE planificacion;
GO
IF NOT EXISTS (SELECT 1 FROM sys.database_principals WHERE name = 'planificacion_app')
    CREATE USER planificacion_app FOR LOGIN planificacion_app;
GO
-- db_owner para que Flyway pueda crear esquemas y tablas. Se puede restringir despues.
ALTER ROLE db_owner ADD MEMBER planificacion_app;
GO
