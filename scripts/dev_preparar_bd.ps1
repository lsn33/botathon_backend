# Prepara la base local de desarrollo (Docker) ejecutando los scripts SQL dentro del contenedor.
# Uso (PowerShell, desde la raiz del proyecto):  .\scripts\dev_preparar_bd.ps1
$ErrorActionPreference = "Stop"
$sa = if ($env:MSSQL_SA_PASSWORD) { $env:MSSQL_SA_PASSWORD } else { "Planif#Dev2026" }
$sqlcmd = "/opt/mssql-tools18/bin/sqlcmd"

docker cp scripts/00_preparar_vm.sql sqlplan:/tmp/00.sql
docker exec sqlplan $sqlcmd -S localhost -U sa -P $sa -C -i /tmp/00.sql
if ($LASTEXITCODE -ne 0) { throw "Fallo 00_preparar_vm.sql" }

Write-Host "Base 'planificacion' y usuario 'planificacion_app' listos."
Write-Host "Si necesitas probar Blue Prism en local, edita scripts/01_usuario_blueprism.sql y ejecutalo igual con 01.sql."
