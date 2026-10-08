import functools
import io
import logging
import math
import os
import shutil
import subprocess
import threading
import urllib.parse
import urllib.request
import wave
from pathlib import Path
from typing import Optional

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel, Field


logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger("avatar-ai-service")

BASE_DIR = Path(__file__).resolve().parent
SADTALKER_DIR = BASE_DIR / "sadtalker_src"
OUTPUT_DIR = BASE_DIR / "outputs"
TEMP_DIR = BASE_DIR / "temp"
ASSETS_DIR = BASE_DIR / "assets"

for directory in (OUTPUT_DIR, TEMP_DIR, ASSETS_DIR):
    directory.mkdir(parents=True, exist_ok=True)

PORT = int(os.environ.get("PORT", "5000"))
SERVER_URL = os.environ.get("SERVER_URL", f"http://localhost:{PORT}").rstrip("/")
AVATAR_ENGINE = os.environ.get("AVATAR_ENGINE", "auto").strip().lower()
DEFAULT_AVATAR_IMAGE = Path(
    os.environ.get("DEFAULT_AVATAR_IMAGE", str(ASSETS_DIR / "avatar_teacher_3d.png"))
)
TEACHER_POSE_IMAGES = {
    "EXPLAIN": ASSETS_DIR / "avatar_teacher_explain.png",
    "POINT": ASSETS_DIR / "avatar_teacher_point.png",
    "QUESTION": ASSETS_DIR / "avatar_teacher_question.png",
}
MAX_DOWNLOAD_BYTES = int(os.environ.get("MAX_DOWNLOAD_BYTES", str(50 * 1024 * 1024)))
DOWNLOAD_TIMEOUT_SECONDS = int(os.environ.get("DOWNLOAD_TIMEOUT_SECONDS", "60"))
SADTALKER_TIMEOUT_SECONDS = int(os.environ.get("SADTALKER_TIMEOUT_SECONDS", "900"))
SADTALKER_MAX_SOURCE_SIZE = max(
    256, int(os.environ.get("SADTALKER_MAX_SOURCE_SIZE", "512"))
)
MAX_CONCURRENT_AVATARS = max(1, int(os.environ.get("MAX_CONCURRENT_AVATARS", "1")))
_generation_slots = threading.BoundedSemaphore(MAX_CONCURRENT_AVATARS)


def _find_executable(name: str, env_name: str) -> str:
    configured = os.environ.get(env_name)
    if configured:
        return configured

    if name == "ffmpeg":
        imageio_bins = BASE_DIR / "venv312" / "Lib" / "site-packages" / "imageio_ffmpeg" / "binaries"
        imageio_ffmpeg = next(imageio_bins.glob("ffmpeg-*.exe"), None) if imageio_bins.exists() else None
        if imageio_ffmpeg:
            return str(imageio_ffmpeg)
        remotion = (
            BASE_DIR.parent
            / "video-service"
            / "node_modules"
            / "@remotion"
            / "compositor-win32-x64-msvc"
            / "ffmpeg.exe"
        )
        if remotion.exists():
            return str(remotion)
    if name == "ffprobe":
        remotion = (
            BASE_DIR.parent
            / "video-service"
            / "node_modules"
            / "@remotion"
            / "compositor-win32-x64-msvc"
            / "ffprobe.exe"
        )
        if remotion.exists():
            return str(remotion)
    return name


FFMPEG_BIN = _find_executable("ffmpeg", "FFMPEG_PATH")
FFPROBE_BIN = _find_executable("ffprobe", "FFPROBE_PATH")


def _find_sadtalker_python() -> Optional[Path]:
    configured = os.environ.get("SADTALKER_PYTHON")
    candidates = [
        Path(configured) if configured else None,
        BASE_DIR / "venv312" / "Scripts" / "python.exe",
        BASE_DIR / "venv312" / "bin" / "python",
    ]
    for candidate in candidates:
        if candidate and candidate.exists():
            return candidate
    return None


SADTALKER_PYTHON = _find_sadtalker_python()
SADTALKER_INFERENCE = SADTALKER_DIR / "inference.py"


class TalkRequest(BaseModel):
    audioUrl: Optional[str] = None
    text: Optional[str] = None
    slideIndex: int = Field(default=0, ge=0, le=1000)
    jobId: str = Field(min_length=1, max_length=100, pattern=r"^[A-Za-z0-9_-]+$")
    avatarImageUrl: Optional[str] = None
    durationMs: Optional[int] = Field(default=None, ge=500, le=600_000)
    lessonPhase: Optional[str] = Field(default=None, max_length=32)
    teachingGoal: Optional[str] = Field(default=None, max_length=1000)
    teacherAction: Optional[str] = Field(default="EXPLAIN", max_length=32)


app = FastAPI(
    title="Self-hosted 3D-style Talking Avatar Service",
    description="SadTalker when installed, with a deterministic animated 3D teacher fallback",
    version="2.0.0",
)
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["GET", "POST", "OPTIONS"],
    allow_headers=["Content-Type", "Authorization"],
)
app.mount("/outputs", StaticFiles(directory=str(OUTPUT_DIR)), name="outputs")


def _safe_remote_url(value: str) -> str:
    parsed = urllib.parse.urlparse(value)
    if parsed.scheme not in {"http", "https"} or not parsed.netloc:
        raise ValueError("only http(s) asset URLs are supported")
    return value


def download_file(url: str, destination: Path) -> None:
    """Download an internal media asset with a size limit and atomic rename."""
    _safe_remote_url(url)
    partial = destination.with_suffix(destination.suffix + ".part")
    request = urllib.request.Request(url, headers={"User-Agent": "AIgen-Lecture-Avatar/2.0"})
    try:
        with urllib.request.urlopen(request, timeout=DOWNLOAD_TIMEOUT_SECONDS) as response:
            declared_size = response.headers.get("Content-Length")
            if declared_size and int(declared_size) > MAX_DOWNLOAD_BYTES:
                raise ValueError("remote asset is larger than the configured limit")

            written = 0
            with partial.open("wb") as output:
                while True:
                    chunk = response.read(1024 * 1024)
                    if not chunk:
                        break
                    written += len(chunk)
                    if written > MAX_DOWNLOAD_BYTES:
                        raise ValueError("remote asset exceeded the configured limit")
                    output.write(chunk)
        partial.replace(destination)
    finally:
        partial.unlink(missing_ok=True)


@functools.lru_cache(maxsize=1)
def sadtalker_readiness() -> tuple[bool, list[str]]:
    issues: list[str] = []
    if not SADTALKER_INFERENCE.exists():
        issues.append("sadtalker_src/inference.py is missing")
    if SADTALKER_PYTHON is None:
        issues.append("SADTALKER_PYTHON/venv312 was not found")

    checkpoint_dir = SADTALKER_DIR / "checkpoints"
    if not (checkpoint_dir / "SadTalker_V0.0.2_256.safetensors").exists():
        issues.append("256px SadTalker checkpoint is missing")
    if not (checkpoint_dir / "mapping_00229-model.pth.tar").exists():
        issues.append("mapping_00229-model.pth.tar is missing")

    if not issues and SADTALKER_PYTHON:
        probe = "import cv2,torch,scipy,librosa,kornia,yacs,yaml,skimage,safetensors"
        try:
            result = subprocess.run(
                [str(SADTALKER_PYTHON), "-c", probe],
                capture_output=True,
                text=True,
                timeout=30,
            )
            if result.returncode != 0:
                last_line = (result.stderr or result.stdout).strip().splitlines()[-1]
                issues.append(f"SadTalker Python dependencies are incomplete: {last_line}")
        except Exception as exc:
            issues.append(f"could not probe SadTalker Python: {exc}")

    return not issues, issues


def _use_sadtalker() -> bool:
    ready, _ = sadtalker_readiness()
    if AVATAR_ENGINE == "fallback":
        return False
    if AVATAR_ENGINE == "sadtalker" and not ready:
        raise RuntimeError("SadTalker was requested but is not ready")
    return ready


def _teacher_pose_image(teacher_action: Optional[str], lesson_phase: Optional[str]) -> Path:
    """Choose a full upper-body teaching pose for the pedagogical intent."""
    action = (teacher_action or "").strip().upper()
    phase = (lesson_phase or "").strip().upper()
    if action in {"WELCOME", "QUESTION"} or phase in {"HOOK", "CHECK"}:
        pose = TEACHER_POSE_IMAGES["QUESTION"]
    elif action == "POINT" or phase in {"OBJECTIVE", "EXAMPLE"}:
        pose = TEACHER_POSE_IMAGES["POINT"]
    else:
        pose = TEACHER_POSE_IMAGES["EXPLAIN"]
    return pose if pose.exists() else DEFAULT_AVATAR_IMAGE


def _audio_envelope(audio_path: Optional[Path], fps: int) -> tuple[list[float], float]:
    if not audio_path or not audio_path.exists():
        return [], 0.0

    command = [
        FFMPEG_BIN,
        "-v",
        "error",
        "-i",
        str(audio_path),
        "-f",
        "wav",
        "-c:a",
        "pcm_s16le",
        "-ac",
        "1",
        "-ar",
        "16000",
        "pipe:1",
    ]
    try:
        result = subprocess.run(command, capture_output=True, check=True, timeout=120)
        import numpy as np

        with wave.open(io.BytesIO(result.stdout), "rb") as wav_file:
            samples = np.frombuffer(wav_file.readframes(wav_file.getnframes()), dtype=np.int16).astype(
                np.float32
            )
        duration = len(samples) / 16000.0
        samples_per_frame = max(1, round(16000 / fps))
        raw_levels: list[float] = []
        for start in range(0, len(samples), samples_per_frame):
            window = samples[start : start + samples_per_frame]
            raw_levels.append(float(np.sqrt(np.mean(np.square(window)))) if len(window) else 0.0)
        if not raw_levels:
            return [], duration

        scale = max(float(np.percentile(raw_levels, 92)), 1.0)
        envelope = [max(0.0, min(1.0, (level / scale - 0.06) / 0.94)) for level in raw_levels]
        return envelope, duration
    except Exception as exc:
        logger.warning("Could not analyze narration audio: %s", exc)
        return [], 0.0


def _prepare_square_image(image_path: Path, size: int):
    import cv2
    import numpy as np

    source = cv2.imread(str(image_path), cv2.IMREAD_COLOR)
    if source is None:
        source = np.zeros((size, size, 3), dtype=np.uint8)
        source[:] = (70, 40, 20)
        cv2.circle(source, (size // 2, size // 2), size // 3, (210, 205, 200), -1)

    height, width = source.shape[:2]
    side = min(height, width)
    x = (width - side) // 2
    y = (height - side) // 2
    cropped = source[y : y + side, x : x + side]
    return cv2.resize(cropped, (size, size), interpolation=cv2.INTER_AREA)


def _prepare_sadtalker_source(image_path: Path, output_path: Path) -> Path:
    """Bound inference resolution to the size actually used in the lecture.

    The presenter is rendered at roughly 330 CSS pixels. Feeding the original
    1254px artwork to full-frame SadTalker and GFPGAN makes every slide several
    times slower without a visible quality improvement in the final 720p video.
    """
    import cv2

    source = cv2.imread(str(image_path), cv2.IMREAD_COLOR)
    if source is None:
        raise ValueError(f"Could not read avatar source: {image_path}")

    height, width = source.shape[:2]
    longest_side = max(height, width)
    if longest_side <= SADTALKER_MAX_SOURCE_SIZE:
        return image_path

    scale = SADTALKER_MAX_SOURCE_SIZE / longest_side
    resized = cv2.resize(
        source,
        (max(1, round(width * scale)), max(1, round(height * scale))),
        interpolation=cv2.INTER_AREA,
    )
    if not cv2.imwrite(str(output_path), resized):
        raise RuntimeError("Could not write optimized SadTalker source image")
    return output_path


def _locate_face(image) -> tuple[int, int, int, int]:
    import cv2

    height, width = image.shape[:2]
    if hasattr(cv2, "CascadeClassifier"):
        cascade = cv2.CascadeClassifier(
            cv2.data.haarcascades + "haarcascade_frontalface_default.xml"
        )
        faces = cascade.detectMultiScale(
            cv2.cvtColor(image, cv2.COLOR_BGR2GRAY),
            scaleFactor=1.1,
            minNeighbors=5,
            minSize=(width // 4, height // 4),
        )
        if len(faces):
            return tuple(max(faces, key=lambda item: item[2] * item[3]))

    # Stable fallback for centered talking-head portraits, including stylized
    # faces that Haar cascades do not recognize reliably.
    return int(width * 0.25), int(height * 0.09), int(width * 0.50), int(height * 0.48)


def _encode_h264(raw_video: Path, output_path: Path) -> None:
    command = [
        FFMPEG_BIN,
        "-y",
        "-i",
        str(raw_video),
        "-an",
        "-c:v",
        "libx264",
        "-preset",
        "fast",
        "-crf",
        "20",
        "-pix_fmt",
        "yuv420p",
        "-movflags",
        "+faststart",
        str(output_path),
    ]
    subprocess.run(command, check=True, capture_output=True, timeout=180)


def generate_fallback_avatar_video(
    audio_path: Optional[Path],
    image_path: Path,
    output_path: Path,
    requested_duration_ms: Optional[int],
    teacher_action: Optional[str] = "EXPLAIN",
) -> None:
    """Animate the bundled 3D teacher and drive its mouth from narration energy."""
    import cv2

    size = 512
    fps = 30
    envelope, audio_duration = _audio_envelope(audio_path, fps)
    requested_duration = (requested_duration_ms or 0) / 1000.0
    duration = max(1.0, audio_duration, requested_duration)
    total_frames = max(fps, math.ceil(duration * fps))
    action = (teacher_action or "EXPLAIN").strip().upper()
    base = _prepare_square_image(image_path, size)

    raw_video = output_path.with_suffix(".raw.mp4")
    writer = cv2.VideoWriter(
        str(raw_video),
        cv2.VideoWriter_fourcc(*"mp4v"),
        fps,
        (size, size),
    )
    if not writer.isOpened():
        raise RuntimeError("OpenCV could not initialize the MP4 writer")

    try:
        for frame_index in range(total_frames):
            speech_energy = envelope[min(frame_index, len(envelope) - 1)] if envelope else 0.0
            gesture_pulse = math.sin(frame_index / 8.0) * min(1.0, speech_energy * 2.5)
            zoom = 1.0 + 0.009 * math.sin(frame_index / 24.0)
            shift_x = 1.8 * math.sin(frame_index / 31.0)
            shift_y = 2.2 * math.sin(frame_index / 20.0)
            rotation = 0.35 * math.sin(frame_index / 42.0)
            if action == "EMPHASIZE":
                zoom += 0.006 * gesture_pulse
                shift_y -= 2.4 * abs(gesture_pulse)
            elif action in {"WELCOME", "QUESTION"}:
                zoom += 0.004 * math.sin(frame_index / 17.0)
                shift_y -= 1.5 * math.sin(frame_index / 28.0)
            elif action == "POINT":
                shift_x -= 2.0 + 1.2 * gesture_pulse
                rotation -= 0.18
            matrix = cv2.getRotationMatrix2D(
                (size / 2, size / 2), rotation, zoom
            )
            matrix[0, 2] += shift_x
            matrix[1, 2] += shift_y
            frame = cv2.warpAffine(
                base, matrix, (size, size), borderMode=cv2.BORDER_REFLECT_101
            )

            writer.write(frame)
    finally:
        writer.release()

    try:
        _encode_h264(raw_video, output_path)
    except Exception as exc:
        logger.warning("H.264 conversion failed; keeping mp4v fallback: %s", exc)
        shutil.move(str(raw_video), str(output_path))
    finally:
        raw_video.unlink(missing_ok=True)


def _normalize_sadtalker_video(
    source: Path, output_path: Path, duration_ms: Optional[int]
) -> None:
    target_seconds = max(1.0, (duration_ms or 1000) / 1000.0)
    command = [
        FFMPEG_BIN,
        "-y",
        "-i",
        str(source),
        "-vf",
        f"tpad=stop_mode=clone:stop_duration={target_seconds:.3f},fps=30",
        "-t",
        f"{target_seconds:.3f}",
        "-an",
        "-c:v",
        "libx264",
        "-preset",
        "fast",
        "-crf",
        "20",
        "-pix_fmt",
        "yuv420p",
        "-movflags",
        "+faststart",
        str(output_path),
    ]
    subprocess.run(command, check=True, capture_output=True, timeout=180)


def run_sadtalker(
    audio_path: Path,
    image_path: Path,
    result_dir: Path,
    output_path: Path,
    duration_ms: Optional[int],
) -> bool:
    if not SADTALKER_PYTHON:
        return False

    preprocess_mode = os.environ.get("SADTALKER_PREPROCESS", "full").strip()
    still_mode = os.environ.get("SADTALKER_STILL", "false").strip().lower() in ("true", "1", "yes")

    command = [
        str(SADTALKER_PYTHON),
        str(SADTALKER_INFERENCE),
        "--driven_audio",
        str(audio_path),
        "--source_image",
        str(image_path),
        "--checkpoint_dir",
        str(SADTALKER_DIR / "checkpoints"),
        "--result_dir",
        str(result_dir),
        "--preprocess",
        preprocess_mode,
        "--size",
        "256",
        "--expression_scale",
        os.environ.get("SADTALKER_EXPRESSION_SCALE", "1.0"),
        "--batch_size",
        "8",
    ]
    if still_mode:
        command.append("--still")

    # Keep motion deterministic and restrained. Random pose styles can create
    # abrupt, cartoon-like head movement between otherwise continuous slides.
    pose_style = os.environ.get("SADTALKER_POSE_STYLE", "0")
    command.extend(["--pose_style", pose_style])

    # Enhance face quality so mouth matches the high-res image
    # The avatar occupies only ~330px in the final 720p composition. GFPGAN
    # doubles render time/resolution on a 4GB GPU with no visible benefit here.
    enhancer = os.environ.get("SADTALKER_ENHANCER", "").strip()
    if enhancer:
        command.extend(["--enhancer", enhancer])

    logger.info("Running SadTalker for %s", output_path.name)
    child_environment = os.environ.copy()
    media_tool_dirs = {
        str(Path(FFMPEG_BIN).resolve().parent),
        str(Path(FFPROBE_BIN).resolve().parent),
    }
    child_environment["PATH"] = os.pathsep.join(media_tool_dirs) + os.pathsep + child_environment.get("PATH", "")
    result = subprocess.run(
        command,
        cwd=str(SADTALKER_DIR),
        env=child_environment,
        timeout=SADTALKER_TIMEOUT_SECONDS,
        capture_output=True,
        text=True,
    )
    if result.returncode != 0:
        logger.error("SadTalker failed: %s", result.stderr[-3000:])
        return False

    videos = [v for v in sorted(result_dir.rglob("*.mp4"), key=lambda item: item.stat().st_mtime, reverse=True) if v.is_file()]
    if not videos:
        logger.error("SadTalker completed without producing an MP4")
        return False

    _normalize_sadtalker_video(videos[0], output_path, duration_ms)
    return True


@app.get("/")
def root():
    return {"message": "Self-hosted talking avatar service is running", "health": "/health"}


@app.get("/health")
def health():
    ready, issues = sadtalker_readiness()
    selected_engine = "sadtalker" if _use_sadtalker() else "animated-3d-fallback"
    return {
        "status": "ok",
        "service": "avatar-ai-service",
        "configuredEngine": AVATAR_ENGINE,
        "selectedEngine": selected_engine,
        "sadTalkerReady": ready,
        "sadTalkerIssues": issues,
        "defaultAvatarReady": DEFAULT_AVATAR_IMAGE.exists(),
        "outputsCount": len(list(OUTPUT_DIR.glob("*.mp4"))),
    }


@app.post("/api/talk")
def generate_talk(request: TalkRequest):
    if not _generation_slots.acquire(timeout=600):
        raise HTTPException(status_code=429, detail="Avatar generator is busy")

    job_filename = f"{request.jobId}_slide_{request.slideIndex}.mp4"
    output_path = OUTPUT_DIR / job_filename
    audio_path = TEMP_DIR / f"audio_{request.jobId}_{request.slideIndex}.mp3"
    custom_image = TEMP_DIR / f"avatar_{request.jobId}_{request.slideIndex}.img"
    prepared_image = TEMP_DIR / f"avatar_{request.jobId}_{request.slideIndex}_optimized.png"
    result_dir = TEMP_DIR / f"sadtalker_{request.jobId}_{request.slideIndex}"
    engine = "animated-3d-fallback"

    try:
        # A client can disconnect while the GPU is still finishing inference.
        # Reusing the completed clip makes retries idempotent and avoids paying
        # the multi-minute SadTalker cost twice for the same job/slide.
        if output_path.exists() and output_path.stat().st_size > 10_000:
            engine = "sadtalker" if _use_sadtalker() else engine
            logger.info("Reusing existing avatar output %s", output_path.name)
            return {
                "status": "success",
                "engine": engine,
                "jobId": request.jobId,
                "slideIndex": request.slideIndex,
                "lessonPhase": request.lessonPhase,
                "teacherAction": request.teacherAction,
                "avatarVideoUrl": f"{SERVER_URL}/outputs/{job_filename}",
            }

        if request.audioUrl:
            try:
                download_file(request.audioUrl, audio_path)
            except Exception as exc:
                logger.warning("Narration download failed; using silent animation: %s", exc)

        avatar_image = _teacher_pose_image(request.teacherAction, request.lessonPhase)
        if request.avatarImageUrl:
            try:
                download_file(request.avatarImageUrl, custom_image)
                avatar_image = custom_image
            except Exception as exc:
                logger.warning("Custom avatar download failed; using bundled teacher: %s", exc)

        if not avatar_image.exists():
            raise HTTPException(status_code=500, detail="Default avatar image is missing")

        generated = False
        if audio_path.exists() and _use_sadtalker():
            result_dir.mkdir(parents=True, exist_ok=True)
            try:
                inference_image = _prepare_sadtalker_source(avatar_image, prepared_image)
                generated = run_sadtalker(
                    audio_path,
                    inference_image,
                    result_dir,
                    output_path,
                    request.durationMs,
                )
                if generated:
                    engine = "sadtalker"
            except Exception as exc:
                if AVATAR_ENGINE == "sadtalker":
                    raise
                logger.exception("SadTalker failed; switching to animated fallback: %s", exc)

        if not generated:
            generate_fallback_avatar_video(
                audio_path if audio_path.exists() else None,
                avatar_image,
                output_path,
                request.durationMs,
                request.teacherAction,
            )

        return {
            "status": "success",
            "engine": engine,
            "jobId": request.jobId,
            "slideIndex": request.slideIndex,
            "lessonPhase": request.lessonPhase,
            "teacherAction": request.teacherAction,
            "avatarVideoUrl": f"{SERVER_URL}/outputs/{job_filename}",
        }
    except HTTPException:
        raise
    except Exception as exc:
        logger.exception("Avatar generation failed")
        raise HTTPException(status_code=500, detail=f"Avatar generation failed: {exc}") from exc
    finally:
        audio_path.unlink(missing_ok=True)
        custom_image.unlink(missing_ok=True)
        prepared_image.unlink(missing_ok=True)
        shutil.rmtree(result_dir, ignore_errors=True)
        _generation_slots.release()


if __name__ == "__main__":
    import uvicorn

    ready, issues = sadtalker_readiness()
    logger.info("SadTalker ready=%s issues=%s", ready, issues)
    uvicorn.run(app, host="0.0.0.0", port=PORT)
