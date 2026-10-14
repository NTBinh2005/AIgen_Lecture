
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

# ─── 2. Avatar AI Service (Wav2Lip) ────────────────────────
Write-Host ""
Write-Host "[2/4] Khoi dong Avatar AI Service (Wav2Lip)..." -ForegroundColor Cyan
$avatarHealthUrl = "http://127.0.0.1:5000/health"
$avatarReady = $false
$avatarProcess = $null

try {
    $avatarStatus = Invoke-RestMethod -Uri $avatarHealthUrl -TimeoutSec 2
    $avatarReady = $avatarStatus.status -eq "ok" -and `
        $avatarStatus.lipSyncReady -eq $true -and `
        $avatarStatus.selectedEngine -eq "wav2lip"
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
    # This launcher is for the real local model. Fail clearly if Wav2Lip is
    # unavailable instead of silently producing a bobbing-image fallback.
    $env:AVATAR_ENGINE = "wav2lip"
    $env:SERVER_URL = "http://127.0.0.1:5000"
    $avatarProcess = Start-Process -FilePath $avatarPython `
        -ArgumentList "-m", "uvicorn", "app:app", "--host", "0.0.0.0", "--port", "5000" `
        -WorkingDirectory $avatarServiceDir `
        -WindowStyle Hidden `
        -RedirectStandardOutput (Join-Path $avatarLogDir "avatar-service.out.log") `
        -RedirectStandardError (Join-Path $avatarLogDir "avatar-service.err.log") `
        -PassThru
    $env:AVATAR_ENGINE = $previousAvatarEngine
    $env:SERVER_URL = $previousAvatarServerUrl

    for ($attempt = 1; $attempt -le 60; $attempt++) {
        Start-Sleep -Seconds 1
        if ($avatarProcess.HasExited) {
            Write-Host "      [ERROR] Avatar AI Service da thoat voi ma $($avatarProcess.ExitCode)." -ForegroundColor Red
            break
        }
        try {
            $avatarStatus = Invoke-RestMethod -Uri $avatarHealthUrl -TimeoutSec 2
            if ($avatarStatus.status -eq "ok" -and `
                $avatarStatus.lipSyncReady -eq $true -and `
                $avatarStatus.selectedEngine -eq "wav2lip") {
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
        $avatarLogPath = Join-Path $avatarLogDir "avatar-service.err.log"
        if (Test-Path $avatarLogPath) {
            Write-Host "             Dong log cuoi:" -ForegroundColor Yellow
            Get-Content $avatarLogPath -Tail 8 | ForEach-Object { Write-Host "             $_" -ForegroundColor DarkYellow }
        }
        Write-Host "             Da dung khoi dong vi video-service dang bat buoc lip-sync." -ForegroundColor Yellow
        exit 1
    }
}

# ─── 3. Video Service (Remotion) ─────────────────────────────
Write-Host ""
Write-Host "[3/4] Khoi dong Video Service (Remotion)..." -ForegroundColor Cyan
$videoHealthUrl = "http://127.0.0.1:3001/health"
$videoReady = $false

try {
    $videoStatus = Invoke-RestMethod -Uri $videoHealthUrl -TimeoutSec 3
    $videoReady = $videoStatus.status -eq "ok" -and $videoStatus.service -eq "video-service"
} catch {
    $videoReady = $false
}

if ($videoReady) {
    Write-Host "      [OK] Video Service da chay tren port 3001; khong khoi dong trung." -ForegroundColor Green
} else {
    $videoPort = Get-NetTCPConnection -LocalPort 3001 -State Listen -ErrorAction SilentlyContinue
    if ($videoPort) {
        $ownerPid = ($videoPort | Select-Object -First 1).OwningProcess
        Write-Host "      [ERROR] Port 3001 dang bi tien trinh PID $ownerPid chiem dung," -ForegroundColor Red
        Write-Host "              nhung endpoint /health khong phai Video Service hop le." -ForegroundColor Red
        Write-Host "              Hay dung tien trinh do hoac doi PORT cua Video Service." -ForegroundColor Yellow
        exit 1
    }

    Write-Host "      [INFO] Video service se chay trong cua so moi" -ForegroundColor Gray
    Start-Process powershell -ArgumentList "-NoExit", "-Command", @"
        Set-Location '$Root\video-service'
        Write-Host 'Video Service (Remotion)' -ForegroundColor Magenta
        Write-Host 'Health: http://127.0.0.1:3001/health' -ForegroundColor Cyan
        Write-Host ''
        npm.cmd run dev
"@

    for ($attempt = 1; $attempt -le 30; $attempt++) {
        Start-Sleep -Seconds 1
        try {
            $videoStatus = Invoke-RestMethod -Uri $videoHealthUrl -TimeoutSec 2
            if ($videoStatus.status -eq "ok" -and $videoStatus.service -eq "video-service") {
                $videoReady = $true
                break
            }
        } catch {
            # Node/tsx can need a few seconds to compile and bind the port.
        }
    }

    if ($videoReady) {
        Write-Host "      [OK] Video Service san sang tren port 3001" -ForegroundColor Green
    } else {
        Write-Host "      [ERROR] Video Service khong san sang sau 30 giay." -ForegroundColor Red
        Write-Host "              Kiem tra loi trong cua so Video Service." -ForegroundColor Yellow
        exit 1
    }
}

# ─── 4. Backend (Spring Boot) ────────────────────────────────
Write-Host ""
Write-Host "[4/4] Khoi dong Backend (Spring Boot)..." -ForegroundColor Cyan
$backendProbeUrl = "http://127.0.0.1:8080/swagger-ui/index.html"
$backendReady = $false

try {
    $backendResponse = Invoke-WebRequest -Uri $backendProbeUrl -TimeoutSec 3 -UseBasicParsing
    $backendReady = $backendResponse.StatusCode -eq 200
} catch {
    $backendReady = $false
}

if ($backendReady) {
    Write-Host "      [OK] Backend da chay tren port 8080; khong khoi dong trung." -ForegroundColor Green
} else {
    $backendPort = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue
    if ($backendPort) {
        $ownerPid = ($backendPort | Select-Object -First 1).OwningProcess
        Write-Host "      [ERROR] Port 8080 dang bi tien trinh PID $ownerPid chiem dung," -ForegroundColor Red
        Write-Host "              nhung khong phat hien Backend EduMind hop le." -ForegroundColor Red
        exit 1
    }

    Write-Host "      [INFO] Backend se chay trong cua so moi" -ForegroundColor Gray
    Start-Process powershell -ArgumentList "-NoExit", "-Command", @"
        Set-Location '$Root\backend'
        Write-Host 'Backend Spring Boot' -ForegroundColor Magenta
        Write-Host 'API: http://127.0.0.1:8080' -ForegroundColor Cyan
        Write-Host ''
        .\mvnw.cmd spring-boot:run
"@

    for ($attempt = 1; $attempt -le 90; $attempt++) {
        Start-Sleep -Seconds 1
        try {
            $backendResponse = Invoke-WebRequest -Uri $backendProbeUrl -TimeoutSec 2 -UseBasicParsing
            if ($backendResponse.StatusCode -eq 200) {
                $backendReady = $true
                break
            }
        } catch {
            # Spring Boot can take tens of seconds to initialize its database.
        }
    }

    if ($backendReady) {
        Write-Host "      [OK] Backend san sang tren port 8080" -ForegroundColor Green
    } else {
        Write-Host "      [ERROR] Backend khong san sang sau 90 giay." -ForegroundColor Red
        Write-Host "              Kiem tra loi trong cua so Backend Spring Boot." -ForegroundColor Yellow
        exit 1
    }
}

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
Write-Host "  - Avatar AI   : http://127.0.0.1:5000/health" -ForegroundColor Cyan
Write-Host ""
Write-Host "  Buoc cuoi - Chay Frontend (Terminal nay):" -ForegroundColor Yellow
Write-Host ""
Write-Host "    cd frontend" -ForegroundColor White
Write-Host "    npm run dev" -ForegroundColor White
Write-Host ""
Write-Host "  Roi mo trinh duyet: http://localhost:5173" -ForegroundColor Green
Write-Host ""
