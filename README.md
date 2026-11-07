# Backend Planificación Asistencial — Botathon Teletón 2026

Spring Boot + Java 21 + SQL Server. Este proyecto ya incluye recepción por staging, validación de datos, actualización de tablas definitivas y autenticación de Blue Prism por API Key.

> Esta versión documenta y prepara el flujo MVP Blue Prism → staging SQL Server → API de notificación → backend. No implica que el proyecto ya esté compilado o probado contra tu VM.

## 1. Flujo MVP recomendado

1. Blue Prism lee el Excel BUK o SIC.
2. Blue Prism inserta las filas en `ingesta.stg_buk` o `ingesta.stg_sic` de SQL Server. No escribe en las tablas definitivas.
3. Cuando termina de insertar todas las filas de **un archivo**, Blue Prism llama al endpoint de notificación:
   - BUK: `POST http://<PC_BACKEND>:8080/api/v1/ingesta/procesar-staging?tipo=BUK&reporteCompleto=false`
   - SIC: `POST http://<PC_BACKEND>:8080/api/v1/ingesta/procesar-staging?tipo=SIC`
4. En la llamada, Blue Prism envía el encabezado `X-API-Key: <BOT_API_KEY>`.
5. El backend lee las filas pendientes de staging, las valida y las procesa:
   - BUK actualiza `rrhh.persona` / `rrhh.dotacion_mes` (según el flujo implementado) y registra cambios.
   - SIC actualiza `sic.atencion`.
   - Los errores se guardan en `ingesta.error`; el resultado del lote queda en `ingesta.lote`.
6. El frontend consulta los resultados mediante las APIs del backend. Nunca se conecta directamente a SQL Server.

**Importante para este MVP:** `StagingService` procesa todas las filas pendientes (`procesado = 0`) del tipo solicitado. Por eso Blue Prism debe cargar y notificar un archivo a la vez para BUK, y lo mismo para SIC. No dejar dos archivos BUK pendientes simultáneamente, porque el backend podría procesarlos juntos. Una mejora posterior será asociar cada carga a un `lote_id` desde el comienzo y procesar por lote.

## 2. ¿Por qué staging y no las tablas definitivas?

Staging es una bandeja de entrada temporal. Permite conservar lo que Blue Prism leyó del Excel y que el backend aplique las reglas antes de modificar los datos oficiales. Así, un dato inválido no pasa directamente a la planificación.

Blue Prism tiene permiso de inserción solo en staging. El backend es responsable de validar, comparar, insertar/actualizar datos definitivos, recalcular y auditar.

## 3. Tablas de recepción existentes

- `ingesta.stg_buk`: `nombre_archivo`, `persona_id`, `instituto_id`, `periodo`, `cargo_buk`, `cod_esp`, `horas_semanales`, `tipo_contrato`, `tipo_trabajador`.
- `ingesta.stg_sic`: `nombre_archivo`, `persona_id`, `instituto_id`, `periodo`, `atenciones_realizadas`.
- `ingesta.lote`: historial de procesamiento creado por el backend.
- `ingesta.error`: errores de validación asociados al lote procesado.

Las tablas staging incluyen `procesado` y `lote_id`. En la implementación actual, el backend marca las filas como procesadas después de procesarlas y asigna el `lote_id` generado.

## 4. Preparar SQL Server

1. Habilitar TCP/IP en SQL Server Configuration Manager y definir un puerto fijo (por ejemplo 1433).
2. Autorizar en el firewall solo la IP del PC donde corre el backend para el puerto SQL.
3. Ejecutar `scripts/00_preparar_vm.sql` para crear la base y el usuario inicial del backend.
4. Ejecutar `scripts/01_usuario_blueprism.sql` con un administrador SQL para crear el login de Blue Prism con permiso `INSERT` solo en las dos tablas staging.
5. Cambiar todas las contraseñas de ejemplo antes de usar el entorno.

El backend y Blue Prism usan credenciales SQL distintas. El frontend no recibe credenciales SQL.

## 5. Configurar el backend en tu PC

Variables de entorno:
- `DB_HOST`: IP o nombre de la VM.
- `DB_PORT`: puerto de SQL Server.
- `DB_NAME`: `planificacion`.
- `DB_USER` / `DB_PASSWORD`: credenciales SQL del backend.
- `BOT_API_KEY`: clave compartida con Blue Prism.
- `JWT_SECRET`: secreto de al menos 32 caracteres.
- `ADMIN_PASSWORD`: contraseña inicial de administración.

La URL JDBC está en `src/main/resources/application.yml`. `trustServerCertificate=true` es una configuración de desarrollo; para un entorno productivo se debe configurar un certificado TLS confiable.

## 6. Probar la notificación por API

Ejemplo BUK:

```bash
curl -X POST "http://localhost:8080/api/v1/ingesta/procesar-staging?tipo=BUK&reporteCompleto=false" ^
  -H "X-API-Key: TU_BOT_API_KEY"
```

Ejemplo SIC:

```bash
curl -X POST "http://localhost:8080/api/v1/ingesta/procesar-staging?tipo=SIC" ^
  -H "X-API-Key: TU_BOT_API_KEY"
```

La respuesta incluye `loteId`, estado, total, nuevos, modificados, sin cambios, bajas y errores. Luego se puede revisar:
- `GET /api/v1/lotes`
- `GET /api/v1/lotes/{id}/errores`

También existe recepción directa por JSON (`POST /api/v1/ingesta/buk` y `/sic`), pero para la arquitectura acordada se recomienda staging SQL + notificación por API.

## 7. Endpoints principales del frontend

- `POST /api/v1/auth/login`
- `GET /api/v1/auth/me`
- `GET /api/v1/dashboard/resumen`
- `GET /api/v1/dashboard/plan-vs-real`
- `GET /api/v1/dotacion/profesionales`
- `GET /api/v1/planificacion/profesional/{personaId}`
- `POST /api/v1/planificacion/recalcular`
- `GET|PUT /api/v1/parametros/...`
- `GET /api/v1/catalogos/...`
- `GET /api/v1/lotes` y `GET /api/v1/lotes/{id}/errores`

## 8. Pendientes funcionales importantes

- Los porcentajes de distribución de ejemplo deben reemplazarse por los valores reales del Excel.
- Los catálogos de institutos, cargos y especialidades deben cargarse con los datos reales.
- Las fórmulas de `CalculoService` deben contrastarse con el Excel de referencia; no asumir que los porcentajes ilustrativos son definitivos.
- Falta incorporar y validar asignaciones especiales, áreas grupales, vacaciones/licencias y la vigencia de la jornada 44 → 42 horas.
- Los feriados y calendarios deben validarse con las fuentes oficiales y las reglas por instituto.
- El staging actual procesa todas las filas pendientes de un tipo en una sola ejecución. Para una siguiente iteración, implementar procesamiento por `lote_id`, control de cargas incompletas y reintentos idempotentes.

## 9. Compilación y validación

Este ZIP no acredita una prueba de compilación ni una conexión real a tu SQL Server. Antes de la demo:
1. Ejecutar `mvn clean test` o al menos `mvn clean compile`.
2. Arrancar con una base de prueba vacía y verificar Flyway.
3. Insertar unas pocas filas ficticias en staging.
4. Llamar el endpoint con `X-API-Key`.
5. Revisar el lote, los errores y las tablas definitivas.

## 10. Configuración de Blue Prism (integración)

1. **Antes de cada corrida** (opcional): `GET http://<PC_BACKEND>:8080/actuator/health` debe responder `{"status":"UP"}`.
2. **Inserción en staging**: el Excel se lee y las filas se insertan en `ingesta.stg_buk` / `ingesta.stg_sic` con el usuario `usr_blueprism`.
3. **Notificación** con la acción *Utility - HTTP → HTTP Request*:
   - Method: `POST`
   - URL: `http://<PC_BACKEND>:8080/api/v1/ingesta/procesar-staging?tipo=BUK&reporteCompleto=false` (o `tipo=SIC`)
   - Headers: `X-API-Key` = valor de `BOT_API_KEY`; `Accept` = `application/json`
   - Request Timeout: `300000` (5 min). Procesar un archivo grande puede tardar.
4. **Respuesta**: `200` con `loteId`, `estado` y contadores. Si no había filas pendientes responde `estado = SIN_PENDIENTES` con `loteId = 0` (no es error). `401` = API Key incorrecta. `500` = revisar logs del backend.
5. Blue Prism debe notificar **solo después** de insertar todas las filas de un archivo, y un archivo a la vez.

## 11. Desarrollo local con SQL Server en Docker

Para desarrollar y probar el backend en tu PC sin depender de la VM. Este entorno usa SQL Server, no PostgreSQL ni Neon.

1. Requisitos: Docker Desktop, Java 21 y Maven (o el wrapper `mvnw`).
2. Levantar SQL Server:
   ```
   docker compose up -d
   ```
   Esto crea un contenedor `sqlplan` en el puerto 1433, con la clave `Planif#Dev2026` (puedes cambiarla con la variable `MSSQL_SA_PASSWORD`).
3. Crear la base y el usuario de la aplicación (en PowerShell, desde la raíz del proyecto):
   ```
   .\scripts\dev_preparar_bd.ps1
   ```
4. Definir variables de entorno para el backend (ejemplo PowerShell):
   ```
   $env:DB_HOST="localhost"
   $env:DB_PORT="1433"
   $env:DB_NAME="planificacion"
   $env:DB_USER="planificacion_app"
   $env:DB_PASSWORD="CambiarEsto#2026"
   $env:BOT_API_KEY="dev-api-key-123456"
   $env:JWT_SECRET="dev-secreto-de-desarrollo-de-al-menos-32-caracteres"
   $env:ADMIN_PASSWORD="Admin#2026"
   ```
5. Arrancar el backend:
   ```
   .\mvnw spring-boot:run
   ```
   Flyway crea las tablas en la primera ejecución.
6. Verificar: `http://localhost:8080/actuator/health` debe responder `{"status":"UP"}`.

**Para pasar a la VM** solo cambia `DB_HOST` por la IP de la VM y usa la clave real de `planificacion_app`. Nada más del código cambia.

Nota: los valores de este ejemplo son solo para desarrollo. En producción deben cambiarse todas las claves.
