<#
  v9 - Túnel público para el código QR de las etiquetas.

  Crea una dirección https://xxxx.trycloudflare.com que llega a este PC, para que cualquier celular
  (en cualquier red, también con datos móviles) abra la página de la muestra al escanear el QR.
  Por esa dirección solo se puede abrir la página del QR: la API y el login quedan bloqueados desde
  internet (AccesoExternoFilter).

  Uso (con el backend ya iniciado), desde la carpeta lims-backend:
      powershell -ExecutionPolicy Bypass -File .\iniciar-tunel-qr.ps1

  Deja esta ventana abierta mientras se use el QR. Para cerrar el túnel: Ctrl+C.
  La dirección cambia cada vez que se inicia el túnel: después de iniciarlo, vuelve a generar
  (imprimir) las etiquetas para que el QR tenga la dirección nueva.

  Requiere cloudflared:  winget install --id Cloudflare.cloudflared -e
#>
param([int]$Puerto = 8080)

$ErrorActionPreference = 'Stop'
$archivoUrl = Join-Path $PSScriptRoot 'firma\url-publica.txt'
$log = Join-Path $env:TEMP 'lims-tunel-qr.log'

$cf = (Get-Command cloudflared -ErrorAction SilentlyContinue).Source
if (-not $cf) {
    $cf = @("${env:ProgramFiles(x86)}\cloudflared\cloudflared.exe", "$env:ProgramFiles\cloudflared\cloudflared.exe") |
        Where-Object { Test-Path $_ } | Select-Object -First 1
}
if (-not $cf) {
    Write-Host 'No se encontró cloudflared. Instálalo con:  winget install --id Cloudflare.cloudflared -e' -ForegroundColor Red
    exit 1
}

try {
    Invoke-WebRequest -UseBasicParsing "http://localhost:$Puerto/m/x" -TimeoutSec 5 | Out-Null
} catch {
    if (-not $_.Exception.Response) {
        Write-Host "El backend no responde en el puerto $Puerto. Inícialo primero." -ForegroundColor Red
        exit 1
    }
}

Write-Host 'Iniciando el túnel público (puede tardar unos segundos)...'
Remove-Item $log -ErrorAction SilentlyContinue
# http2: funciona también en redes que bloquean QUIC (UDP), como las redes institucionales
$proceso = Start-Process -FilePath $cf -PassThru -WindowStyle Hidden -RedirectStandardError $log `
    -ArgumentList @('tunnel', '--no-autoupdate', '--protocol', 'http2', '--url', "http://localhost:$Puerto")

try {
    $url = $null
    for ($i = 0; $i -lt 60 -and -not $proceso.HasExited; $i++) {
        Start-Sleep -Seconds 1
        $texto = Get-Content $log -Raw -ErrorAction SilentlyContinue
        if (-not $url -and $texto -match 'https://[a-z0-9-]+\.trycloudflare\.com') { $url = $Matches[0] }
        if ($url -and $texto -match 'Registered tunnel connection') { break }
    }
    if (-not $url -or $proceso.HasExited) {
        Write-Host "No se pudo crear el túnel. Revisa la conexión a internet. Detalle en $log" -ForegroundColor Red
        exit 1
    }

    New-Item -ItemType Directory -Force (Split-Path $archivoUrl) | Out-Null
    [System.IO.File]::WriteAllText($archivoUrl, $url)

    Write-Host ''
    Write-Host "Túnel activo: $url" -ForegroundColor Green
    Write-Host ''
    Write-Host 'Ahora:'
    Write-Host '  1. Espera ~30 segundos (el backend toma la dirección nueva).'
    Write-Host '  2. En el LIMS abre una muestra -> "Imprimir etiqueta" (genera el QR con esta dirección).'
    Write-Host '  3. Escanea el QR con cualquier celular, en cualquier red.'
    Write-Host ''
    Write-Host 'Deja esta ventana abierta. Ctrl+C para cerrar el túnel.' -ForegroundColor Yellow
    Wait-Process -Id $proceso.Id
} finally {
    if (-not $proceso.HasExited) { Stop-Process -Id $proceso.Id -Force -ErrorAction SilentlyContinue }
    Remove-Item $archivoUrl -ErrorAction SilentlyContinue
    Write-Host 'Túnel cerrado. El QR vuelve a usar la IP de la red local.'
}
