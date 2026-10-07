
# ============================================================
#  EduMind AI Lecture - One-click Startup Script (Windows)
#  Chay bang: powershell -ExecutionPolicy Bypass -File .\start_all.ps1
# ============================================================

$Root = $PSScriptRoot

Write-Host ""
Write-Host "=========================================" -ForegroundColor Magenta
Write-Host "   EduMind AI Lecture - Khoi dong du an  " -ForegroundColor Magenta
Write-Host "=========================================" -ForegroundColor Magenta
Write-Host ""

# ─── 1. PostgreSQL Check & Startup ───────────────────────────
Write-Host "[1/4] Kiem tra PostgreSQL..." -ForegroundColor Cyan
$localPgPort = Get-NetTCPConnection -LocalPort 5432 -State Listen -ErrorAction SilentlyContinue
$dockerPgPort = Get-NetTCPConnection -LocalPort 5436 -State Listen -ErrorAction SilentlyContinue

if ($localPgPort) {
    Write-Host "      [OK] PostgreSQL local đang chay tren port 5432" -ForegroundColor Green
} elseif ($dockerPgPort) {
    Write-Host "      [OK] PostgreSQL Docker đang chay tren port 5436" -ForegroundColor Green
} else {
    Write-Host "      [INFO] Thu khoi dong PostgreSQL qua Docker..." -ForegroundColor Yellow
    Set-Location $Root
    docker-compose up -d postgres 2>&1 | Out-Null
    Write-Host "      [OK] Da gui lenh start PostgreSQL Docker" -ForegroundColor Green
}

# ─── 2. Avatar AI Service (SadTalker) ────────────────────────
Write-Host ""
Write-Host "[2/4] Khoi dong Avatar AI Service (SadTalker)..." -ForegroundColor Cyan
$avatarHealthUrl = "http://localhost:5000/health"
$avatarReady = $false

try {
    $avatarStatus = Invoke-RestMethod -Uri $avatarHealthUrl -TimeoutSec 2
    $avatarReady = $avatarStatus.status -eq "ok" -and `
        $avatarStatus.sadTalkerReady -eq $true -and `
        $avatarStatus.selectedEngine -eq "sadtalker"
    if (-not $avatarReady -and $avatarStatus.status -eq "ok") {
        Write-Host "      [WARN] Port 5000 dang chay engine '$($avatarStatus.selectedEngine)', khong co lip-sync that." -ForegroundColor Yellow
        Write-Host "             Hay dung avatar-ai-service fallback/Docker dang chiem port 5000 roi chay lai script." -ForegroundColor Yellow
        exit 1
    }
} catch {
    $avatarReady = $false
}

if ($avatarReady) {
    Write-Host "      [OK] Avatar AI Service da chay tren port 5000" -ForegroundColor Green
} else {
    $avatarServiceDir = Join-Path $Root "avatar-ai-service"
    $avatarVenvPython = Join-Path $avatarServiceDir "venv312\Scripts\python.exe"
    $avatarPython = if (Test-Path $avatarVenvPython) { $avatarVenvPython } else { "python" }
    $avatarLogDir = Join-Path $avatarServiceDir "logs"
    New-Item -ItemType Directory -Force $avatarLogDir | Out-Null

    Write-Host "      [INFO] Python: $avatarPython" -ForegroundColor Gray
    $previousAvatarEngine = $env:AVATAR_ENGINE
    $previousAvatarServerUrl = $env:SERVER_URL
    # This launcher is for the real local model. Fail clearly if SadTalker is
    # unavailable instead of silently producing a bobbing-image fallback.
    $env:AVATAR_ENGINE = "sadtalker"
    $env:SERVER_URL = "http://localhost:5000"
    Start-Process -FilePath $avatarPython `
        -ArgumentList "-m", "uvicorn", "app:app", "--host", "0.0.0.0", "--port", "5000" `
        -WorkingDirectory $avatarServiceDir `
        -WindowStyle Hidden `
        -RedirectStandardOutput (Join-Path $avatarLogDir "avatar-service.out.log") `
        -RedirectStandardError (Join-Path $avatarLogDir "avatar-service.err.log") | Out-Null
    $env:AVATAR_ENGINE = $previousAvatarEngine
    $env:SERVER_URL = $previousAvatarServerUrl

    for ($attempt = 1; $attempt -le 60; $attempt++) {
        Start-Sleep -Seconds 1
        try {
            $avatarStatus = Invoke-RestMethod -Uri $avatarHealthUrl -TimeoutSec 2
            if ($avatarStatus.status -eq "ok" -and `
                $avatarStatus.sadTalkerReady -eq $true -and `
                $avatarStatus.selectedEngine -eq "sadtalker") {
                $avatarReady = $true
                break
            }
        } catch {
            # Service can need several seconds to import its AI dependencies.
        }
    }

    if ($avatarReady) {
        Write-Host "      [OK] Avatar AI Service san sang tren port 5000" -ForegroundColor Green
    } else {
        Write-Host "      [ERROR] Avatar AI Service khong khoi dong duoc." -ForegroundColor Red
        Write-Host "             Xem log: avatar-ai-service\logs\avatar-service.err.log" -ForegroundColor Yellow
        Write-Host "             Da dung khoi dong vi video-service dang bat buoc lip-sync." -ForegroundColor Yellow
        exit 1
    }
}

# ─── 3. Video Service (Remotion) ─────────────────────────────
Write-Host ""
Write-Host "[3/4] Khoi dong Video Service (Remotion)..." -ForegroundColor Cyan
Write-Host "      [INFO] Video service se chay trong cua so moi" -ForegroundColor Gray
Start-Process powershell -ArgumentList "-NoExit", "-Command", @"
    Set-Location '$Root\video-service'
    Write-Host 'Video Service (Remotion)' -ForegroundColor Magenta
    Write-Host 'Health: http://localhost:3001/health' -ForegroundColor Cyan
    Write-Host ''
    npm run dev
"@
Write-Host "      [OK] Video Service dang khoi dong..." -ForegroundColor Green
Start-Sleep -Seconds 2

# ─── 4. Backend (Spring Boot) ────────────────────────────────
Write-Host ""
Write-Host "[4/4] Khoi dong Backend (Spring Boot)..." -ForegroundColor Cyan
Write-Host "      [INFO] Backend se chay trong cua so moi" -ForegroundColor Gray
Start-Process powershell -ArgumentList "-NoExit", "-Command", @"
    Set-Location '$Root\backend'
    Write-Host 'Backend Spring Boot' -ForegroundColor Magenta
    Write-Host 'API: http://localhost:8080' -ForegroundColor Cyan
    Write-Host ''
    .\mvnw spring-boot:run
"@
Write-Host "      [OK] Backend dang khoi dong..." -ForegroundColor Green

# ─── Summary ──────────────────────────────────────────────────
Write-Host ""
Write-Host "=========================================" -ForegroundColor Magenta
Write-Host "  Tat ca service da duoc khoi dong!" -ForegroundColor Magenta
Write-Host "=========================================" -ForegroundColor Magenta
Write-Host ""
Write-Host "  Service URLs:" -ForegroundColor White
Write-Host "  - Frontend    : http://localhost:5173" -ForegroundColor Cyan
Write-Host "  - Backend API : http://localhost:8080" -ForegroundColor Cyan
Write-Host "  - Video Svc   : http://localhost:3001/health" -ForegroundColor Cyan
Write-Host "  - Avatar AI   : http://localhost:5000/health" -ForegroundColor Cyan
Write-Host ""
Write-Host "  Buoc cuoi - Chay Frontend (Terminal nay):" -ForegroundColor Yellow
Write-Host ""
Write-Host "    cd frontend" -ForegroundColor White
Write-Host "    npm run dev" -ForegroundColor White
Write-Host ""
Write-Host "  Roi mo trinh duyet: http://localhost:5173" -ForegroundColor Green
Write-Host ""
