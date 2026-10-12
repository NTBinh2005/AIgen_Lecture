
# ============================================================
# SadTalker: Download all missing checkpoints (Windows)
# Run từ thư mục avatar-ai-service:
#   powershell -ExecutionPolicy Bypass -File .\download_checkpoints.ps1
# ============================================================
$ErrorActionPreference = "Continue"

$CheckpointDir = "$PSScriptRoot\sadtalker_src\checkpoints"
$GfpganDir     = "$PSScriptRoot\sadtalker_src\gfpgan\weights"
$BfmDir        = "$PSScriptRoot\sadtalker_src\checkpoints\BFM_Fitting"

New-Item -ItemType Directory -Force -Path $CheckpointDir | Out-Null
New-Item -ItemType Directory -Force -Path $GfpganDir     | Out-Null
New-Item -ItemType Directory -Force -Path $BfmDir        | Out-Null

function Download-File {
    param([string]$Url, [string]$Dest)
    $filename = Split-Path $Dest -Leaf
    if (Test-Path $Dest) {
        $size = (Get-Item $Dest).Length
        if ($size -gt 1000) {
            Write-Host "[SKIP] $filename (da ton tai $('{0:N1}' -f ($size/1MB)) MB)" -ForegroundColor Cyan
            return
        }
    }
    Write-Host "[DL]  Dang tai $filename ..." -ForegroundColor Yellow
    try {
        $ProgressPreference = 'SilentlyContinue'
        Invoke-WebRequest -Uri $Url -OutFile $Dest -UseBasicParsing -TimeoutSec 300
        $finalSize = (Get-Item $Dest).Length
        Write-Host "[OK]  $filename xong ($('{0:N1}' -f ($finalSize/1MB)) MB)" -ForegroundColor Green
    } catch {
        Write-Host "[ERR] That bai: $filename" -ForegroundColor Red
        Write-Host "      URL: $Url" -ForegroundColor DarkRed
        Write-Host "      $_" -ForegroundColor DarkRed
    }
}

Write-Host ""
Write-Host "=============================================" -ForegroundColor Magenta
Write-Host "  SadTalker Checkpoint Downloader" -ForegroundColor Magenta
Write-Host "=============================================" -ForegroundColor Magenta
Write-Host ""

# ─── 1. Core SadTalker model weights ──────────────────────────
Write-Host ">>> [1/3] Core SadTalker checkpoints" -ForegroundColor White
$base = "https://github.com/OpenTalker/SadTalker/releases/download/v0.0.2-rc"
Download-File "$base/mapping_00109-model.pth.tar" "$CheckpointDir\mapping_00109-model.pth.tar"
Download-File "$base/mapping_00229-model.pth.tar" "$CheckpointDir\mapping_00229-model.pth.tar"
# SadTalker_V0.0.2_256.safetensors & 512 đã có rồi

# ─── 2. BFM_Fitting (3D face model) ───────────────────────────
Write-Host ""
Write-Host ">>> [2/3] BFM_Fitting (3D face model)" -ForegroundColor White
$bfmBase = "https://github.com/OpenTalker/SadTalker/releases/download/v0.0.2-rc"
Download-File "$bfmBase/BFM_Fitting.zip" "$CheckpointDir\BFM_Fitting.zip"

if (Test-Path "$CheckpointDir\BFM_Fitting.zip") {
    Write-Host "[UNZIP] Giai nen BFM_Fitting.zip ..." -ForegroundColor Yellow
    try {
        Expand-Archive -Path "$CheckpointDir\BFM_Fitting.zip" -DestinationPath "$CheckpointDir" -Force
        Write-Host "[OK]  BFM_Fitting giai nen xong." -ForegroundColor Green
    } catch {
        Write-Host "[ERR] Giai nen that bai: $_" -ForegroundColor Red
    }
}

# ─── 3. GFPGAN / facexlib weights (face enhancer) ─────────────
Write-Host ""
Write-Host ">>> [3/3] GFPGAN / facexlib weights" -ForegroundColor White
$facexlib = "https://github.com/xinntao/facexlib/releases/download"
$gfpgan   = "https://github.com/TencentARC/GFPGAN/releases/download"
Download-File "$facexlib/v0.1.0/alignment_WFLW_4HG.pth"      "$GfpganDir\alignment_WFLW_4HG.pth"
Download-File "$facexlib/v0.1.0/detection_Resnet50_Final.pth" "$GfpganDir\detection_Resnet50_Final.pth"
Download-File "$gfpgan/v1.3.0/GFPGANv1.4.pth"                "$GfpganDir\GFPGANv1.4.pth"
Download-File "$facexlib/v0.2.2/parsing_parsenet.pth"          "$GfpganDir\parsing_parsenet.pth"

# ─── Summary ──────────────────────────────────────────────────
Write-Host ""
Write-Host "=============================================" -ForegroundColor Magenta
Write-Host "  Download hoan tat! Kiem tra ket qua:" -ForegroundColor Magenta
Write-Host "=============================================" -ForegroundColor Magenta
Write-Host ""
Write-Host "Checkpoints:" -ForegroundColor White
Get-ChildItem "$CheckpointDir" -File | Format-Table Name, @{L='Size(MB)';E={'{0:N1}' -f ($_.Length/1MB)}} -AutoSize
Write-Host "GFPGAN weights:" -ForegroundColor White
Get-ChildItem "$GfpganDir" -File | Format-Table Name, @{L='Size(MB)';E={'{0:N1}' -f ($_.Length/1MB)}} -AutoSize
