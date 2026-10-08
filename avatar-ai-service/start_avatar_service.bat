@echo off
title SadTalker Avatar AI Service
color 0A
echo ================================================
echo   SadTalker Avatar AI Service - Startup
echo ================================================
echo.

REM Chuyen den thu muc avatar-ai-service
cd /d "%~dp0"

REM Kiem tra venv312
if not exist "venv312\Scripts\python.exe" (
    echo [ERROR] venv312 khong tim thay!
    echo Hay chay: python -m venv venv312
    echo Roi cai package: venv312\Scripts\pip install -r requirements312.txt
    pause
    exit /b 1
)

REM Use the real SadTalker model for facial motion and audio-driven lip sync.
set AVATAR_ENGINE=sadtalker
set SADTALKER_PREPROCESS=full
set SADTALKER_STILL=false
set SADTALKER_EXPRESSION_SCALE=1.1

REM Kiem tra GPU
echo.
venv312\Scripts\python.exe -c "import torch; print('[GPU] CUDA:', torch.cuda.is_available(), '| GPU:', torch.cuda.get_device_name(0) if torch.cuda.is_available() else 'None')"

echo.
echo [START] Khoi dong Avatar AI Service tren port 5000...
echo [INFO]  Health check: http://localhost:5000/health
echo [INFO]  API endpoint: http://localhost:5000/api/talk
echo.

REM Start FastAPI service with SadTalker enabled
venv312\Scripts\python.exe -m uvicorn app:app --host 0.0.0.0 --port 5000

pause
