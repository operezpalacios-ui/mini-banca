# Lección 4: lanza N transferencias A LA VEZ desde la misma cuenta.
# Uso (desde C:\dev\mini-banca, con el core arrancado):
#   powershell -ExecutionPolicy Bypass -File .\scripts\concurrencia.ps1 -Bloqueo PESIMISTA
#   powershell -ExecutionPolicy Bypass -File .\scripts\concurrencia.ps1 -Bloqueo OPTIMISTA
# Leccion 5 (idempotencia): las 20 con la MISMA Idempotency-Key -> solo debe hacerse UNA
#   powershell -ExecutionPolicy Bypass -File .\scripts\concurrencia.ps1 -MismaClave

param(
    [ValidateSet("PESIMISTA", "OPTIMISTA")]
    [string]$Bloqueo = "PESIMISTA",
    [int]$Hilos = 20,
    [long]$Origen = 6,
    [string]$IbanDestino = "ES0000000000000000000002",
    [decimal]$Importe = 10,
    [string]$UsuarioId = "1",
    [switch]$MismaClave
)

Add-Type -AssemblyName System.Net.Http
$base = "http://localhost:8080/internal/v1"
$client = New-Object System.Net.Http.HttpClient

$saldoAntes = (Invoke-RestMethod "$base/cuentas/$Origen" -Headers @{ "X-User-Id" = $UsuarioId }).saldo
Write-Host "Saldo inicial de la cuenta ${Origen}: $saldoAntes"
Write-Host "Lanzando $Hilos transferencias de $Importe a la vez (bloqueo $Bloqueo)..."

$claveComun = [guid]::NewGuid().ToString()
if ($MismaClave) { Write-Host "Todas con la MISMA Idempotency-Key: $claveComun" }

# Preparamos todas las peticiones y las disparamos SIN esperar a que acabe ninguna
$tareas = @()
for ($i = 1; $i -le $Hilos; $i++) {
    $json = @{ cuentaOrigenId = $Origen; ibanDestino = $IbanDestino; importe = $Importe; concepto = "Concurrencia $i" } | ConvertTo-Json
    $peticion = New-Object System.Net.Http.HttpRequestMessage([System.Net.Http.HttpMethod]::Post, "$base/transferencias?bloqueo=$Bloqueo")
    $peticion.Headers.Add("X-User-Id", $UsuarioId)
    if ($MismaClave) { $clave = $claveComun } else { $clave = [guid]::NewGuid().ToString() }
    $peticion.Headers.Add("Idempotency-Key", $clave)
    $peticion.Content = New-Object System.Net.Http.StringContent($json, [System.Text.Encoding]::UTF8, "application/json")
    $tareas += $client.SendAsync($peticion)
}
[System.Threading.Tasks.Task]::WaitAll($tareas)

Write-Host ""
Write-Host "Resultados (codigo HTTP x cantidad):"
$tareas | Group-Object { [int]$_.Result.StatusCode } | Sort-Object Name | ForEach-Object {
    $significado = switch ($_.Name) { "201" { "transferencia hecha" } "409" { "conflicto (bloqueo optimista)" } "422" { "saldo insuficiente" } default { "otro" } }
    Write-Host ("  {0} x {1}   -> {2}" -f $_.Name, $_.Count, $significado)
}

$saldoDespues = (Invoke-RestMethod "$base/cuentas/$Origen" -Headers @{ "X-User-Id" = $UsuarioId }).saldo
function EsRepetida($respuesta) {
    $valores = $null
    if ($respuesta.Headers.TryGetValues("Idempotent-Replayed", [ref]$valores)) { return (($valores -join "") -eq "true") }
    return $false
}
$repetidas = @($tareas | Where-Object { [int]$_.Result.StatusCode -eq 201 -and (EsRepetida $_.Result) }).Count
$ok = @($tareas | Where-Object { [int]$_.Result.StatusCode -eq 201 -and -not (EsRepetida $_.Result) }).Count
Write-Host "  (de los 201: $ok transferencias NUEVAS y $repetidas devueltas por idempotencia)"
Write-Host ""
Write-Host "Saldo final de la cuenta ${Origen}: $saldoDespues"
Write-Host ("Comprobacion: {0} - {1} x {2} = {3}" -f $saldoAntes, $ok, $Importe, ($saldoAntes - $ok * $Importe))
