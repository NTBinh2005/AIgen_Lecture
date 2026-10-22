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
WAV2LIP_DIR = BASE_DIR / "wav2lip_src"
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
SADTALKER_TIMEOUT_PER_AUDIO_SECOND = float(
    os.environ.get("SADTALKER_TIMEOUT_PER_AUDIO_SECOND", "90")
)
SADTALKER_MAX_TIMEOUT_SECONDS = int(
    os.environ.get("SADTALKER_MAX_TIMEOUT_SECONDS", "3600")
)
SADTALKER_MAX_SOURCE_SIZE = max(
    256, int(os.environ.get("SADTALKER_MAX_SOURCE_SIZE", "512"))
)
MAX_CONCURRENT_AVATARS = max(1, int(os.environ.get("MAX_CONCURRENT_AVATARS", "1")))
AVATAR_QUEUE_TIMEOUT_SECONDS = max(
    600,
    int(os.environ.get("AVATAR_QUEUE_TIMEOUT_SECONDS", str(SADTALKER_MAX_TIMEOUT_SECONDS + 600))),
)
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
WAV2LIP_INFERENCE = WAV2LIP_DIR / "inference.py"
WAV2LIP_CHECKPOINT = Path(os.environ.get(
    "WAV2LIP_CHECKPOINT", str(WAV2LIP_DIR / "checkpoints" / "wav2lip_gan.pth")
))
WAV2LIP_FACE_DETECTOR = WAV2LIP_DIR / "face_detection" / "detection" / "sfd" / "s3fd.pth"


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


@functools.lru_cache(maxsize=1)
def wav2lip_readiness() -> tuple[bool, list[str]]:
    issues: list[str] = []
    if not WAV2LIP_INFERENCE.exists():
        issues.append("wav2lip_src/inference.py is missing")
    if SADTALKER_PYTHON is None:
        issues.append("venv312 Python was not found")
    if not WAV2LIP_CHECKPOINT.exists():
        issues.append(f"Wav2Lip checkpoint is missing: {WAV2LIP_CHECKPOINT}")
    if not WAV2LIP_FACE_DETECTOR.exists():
        issues.append("Wav2Lip S3FD face detector is missing")
    return not issues, issues


def selected_engine() -> str:
    wav_ready, _ = wav2lip_readiness()
    sad_ready, _ = sadtalker_readiness()
    if AVATAR_ENGINE == "wav2lip":
        if not wav_ready:
            raise RuntimeError("Wav2Lip was requested but is not ready")
        return "wav2lip"
    if AVATAR_ENGINE == "sadtalker":
        if not sad_ready:
            raise RuntimeError("SadTalker was requested but is not ready")
        return "sadtalker"
    if AVATAR_ENGINE == "fallback":
        return "animated-3d-fallback"
    if wav_ready:
        return "wav2lip"
    if sad_ready:
        return "sadtalker"
    return "animated-3d-fallback"


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


def _pose_key_for_image(image_path: Path, teacher_action: Optional[str]) -> Optional[str]:
    stem = image_path.stem.lower()
    if "point" in stem:
        return "point"
    if "question" in stem:
        return "question"
    if "explain" in stem or "avatar_teacher_3d" in stem:
        return "explain"
    action = (teacher_action or "").strip().upper()
    if image_path == DEFAULT_AVATAR_IMAGE:
        if action in {"POINT", "EMPHASIZE"}:
            return "point"
        if action in {"QUESTION", "WELCOME"}:
            return "question"
        return "explain"
    return None


# Exact anatomical calibration on 1254x1254 bundled 3D teacher artworks
POSE_MOUTH_1254 = {
    "explain": {"lx": 564.0, "ly": 420.0, "rx": 656.0, "ry": 415.0, "cy": 420.5},
    "point": {"lx": 795.0, "ly": 398.0, "rx": 885.0, "ry": 408.0, "cy": 404.0},
    "question": {"lx": 588.0, "ly": 442.0, "rx": 682.0, "ry": 431.0, "cy": 438.0},
}

POSE_EYES_1254 = {
    "explain": [
        {"cx": 546.0, "top_y": 286.0, "bot_y": 324.0, "rx": 38.0, "angle": 3.0},
        {"cx": 676.0, "top_y": 274.0, "bot_y": 316.0, "rx": 36.0, "angle": -5.0},
    ],
    "point": [
        {"cx": 802.0, "top_y": 276.0, "bot_y": 315.0, "rx": 33.0, "angle": 6.0},
        {"cx": 910.0, "top_y": 290.0, "bot_y": 326.0, "rx": 35.0, "angle": 4.0},
    ],
    "question": [
        {"cx": 556.0, "top_y": 312.0, "bot_y": 352.0, "rx": 37.0, "angle": -4.0},
        {"cx": 690.0, "top_y": 280.0, "bot_y": 325.0, "rx": 35.0, "angle": -8.0},
    ],
}

POSE_RIG_512 = {
    "explain": {
        "head": {"cx": 248.0, "cy": 130.0, "rx": 95.0, "ry": 105.0, "pivot_x": 248.0, "pivot_y": 210.0},
        "wav2lip_box": (65, 205, 198, 300),  # (y1, y2, x1, x2) calibrated so mouth lands at (249.3, 172.5)
        "hands": [
            {"cx": 190.0, "cy": 365.0, "rx": 68.0, "ry": 78.0, "dx": 5.5, "dy": -8.5, "phase": 0.0},
            {"cx": 415.0, "cy": 345.0, "rx": 88.0, "ry": 92.0, "dx": 7.5, "dy": -10.0, "phase": 1.1},
        ],
    },
    "point": {
        "head": {"cx": 345.0, "cy": 125.0, "rx": 95.0, "ry": 105.0, "pivot_x": 340.0, "pivot_y": 205.0},
        "wav2lip_box": (69, 196, 295, 391),  # calibrated so mouth lands at (343.0, 166.2)
        "hands": [
            {"cx": 115.0, "cy": 265.0, "rx": 108.0, "ry": 88.0, "dx": -9.0, "dy": -8.0, "phase": 0.0},
            {"cx": 330.0, "cy": 395.0, "rx": 85.0, "ry": 68.0, "dx": 4.0, "dy": -5.0, "phase": 1.6},
        ],
    },
    "question": {
        "head": {"cx": 255.0, "cy": 135.0, "rx": 95.0, "ry": 105.0, "pivot_x": 252.0, "pivot_y": 215.0},
        "wav2lip_box": (68, 215, 205, 313),  # calibrated so mouth lands at (259.3, 180.5)
        "hands": [
            {"cx": 85.0, "cy": 365.0, "rx": 82.0, "ry": 78.0, "dx": -6.5, "dy": -9.0, "phase": 0.0},
            {"cx": 430.0, "cy": 385.0, "rx": 82.0, "ry": 78.0, "dx": 6.5, "dy": -9.0, "phase": 0.4},
        ],
    },
}


def _audio_speech_features(
    audio_path: Optional[Path], fps: int
) -> tuple[list[float], list[float], float]:
    if not audio_path or not audio_path.exists():
        return [], [], 0.0

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
        zcr_list: list[float] = []
        for start in range(0, len(samples), samples_per_frame):
            window = samples[start : start + samples_per_frame]
            if len(window):
                raw_levels.append(float(np.sqrt(np.mean(np.square(window)))))
                signs = np.sign(window)
                zcr = float(np.mean(np.abs(np.diff(signs)) > 0)) if len(window) > 1 else 0.0
                zcr_list.append(zcr)
            else:
                raw_levels.append(0.0)
                zcr_list.append(0.0)
        if not raw_levels:
            return [], [], duration

        scale = max(float(np.percentile(raw_levels, 92)), 1.0)
        raw_env = [max(0.0, min(1.0, (level / scale - 0.05) / 0.95)) for level in raw_levels]

        # Smooth temporally while preserving syllable articulation
        smoothed_env: list[float] = []
        prev = 0.0
        for idx, val in enumerate(raw_env):
            # Add natural syllable cadence modulation when speaking
            cadence = 0.85 + 0.22 * math.sin(idx * 0.72) if val > 0.08 else 1.0
            target = min(1.0, (val ** 0.82) * cadence)
            alpha = 0.58 if target > prev else 0.42
            prev = prev * (1.0 - alpha) + target * alpha
            smoothed_env.append(prev if prev >= 0.025 else 0.0)

        # Width scale: high ZCR (E/I/S) widens mouth slightly; low ZCR (O/U) rounds mouth slightly
        width_scales: list[float] = []
        prev_w = 1.0
        for env_val, zcr in zip(smoothed_env, zcr_list):
            if env_val < 0.04:
                target_w = 1.0
            elif zcr > 0.16:
                target_w = 1.0 + 0.055 * min(1.0, env_val * 1.4)
            elif zcr < 0.075:
                target_w = 1.0 - 0.065 * min(1.0, env_val * 1.3)
            else:
                target_w = 1.0 + 0.02 * math.sin(zcr * 25.0) * env_val
            prev_w = prev_w * 0.6 + target_w * 0.4
            width_scales.append(prev_w)

        return smoothed_env, width_scales, duration
    except Exception as exc:
        logger.warning("Could not analyze narration audio: %s", exc)
        return [], [], 0.0


def _audio_envelope(audio_path: Optional[Path], fps: int) -> tuple[list[float], float]:
    envelope, _, duration = _audio_speech_features(audio_path, fps)
    return envelope, duration


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


def _blink_amount_at_frame(frame_index: int, fps: int) -> float:
    """Deterministic natural blink cycle (~3.5s) with occasional double-blink."""
    cycle = int(round(fps * 3.5))
    pos = (frame_index + 19) % max(1, cycle)
    blink_len = max(4, int(round(fps * 0.16)))
    if pos < blink_len:
        phase = pos / float(blink_len)
        return math.sin(phase * math.pi) ** 1.2
    # Occasional secondary blink every 3rd cycle
    if ((frame_index + 19) // max(1, cycle)) % 3 == 1:
        pos2 = pos - int(round(fps * 0.34))
        if 0 <= pos2 < blink_len:
            phase = pos2 / float(blink_len)
            return 0.88 * (math.sin(phase * math.pi) ** 1.2)
    return 0.0


def _render_mouth_articulation(
    full_img,
    pose_key: str,
    openness: float,
    width_scale: float = 1.0,
):
    """Part the character's actual high-res lips and render a 3D oral cavity."""
    import cv2
    import numpy as np

    if pose_key not in POSE_MOUTH_1254:
        return full_img
    if openness <= 0.015 and abs(width_scale - 1.0) < 0.015:
        return full_img

    h_img, w_img = full_img.shape[:2]
    s = w_img / 1254.0
    m = POSE_MOUTH_1254[pose_key]
    lx, ly = m["lx"] * s, m["ly"] * s
    rx, ry = m["rx"] * s, m["ry"] * s
    cx = 0.5 * (lx + rx)
    cy = m["cy"] * s
    mouth_w = math.hypot(rx - lx, ry - ly)
    angle_rad = math.atan2(ry - ly, rx - lx)

    pad_x = int(mouth_w * 0.85)
    pad_y_top = int(mouth_w * 0.45)
    pad_y_bot = int(mouth_w * 0.95)
    x1 = max(0, int(cx - pad_x))
    x2 = min(w_img, int(cx + pad_x))
    y1 = max(0, int(cy - pad_y_top))
    y2 = min(h_img, int(cy + pad_y_bot))

    roi = full_img[y1:y2, x1:x2].copy()
    rh, rw = roi.shape[:2]
    if rh < 4 or rw < 4:
        return full_img

    yy, xx = np.mgrid[0:rh, 0:rw].astype(np.float32)
    gx = xx + x1
    gy = yy + y1

    dx = gx - cx
    dy = gy - cy
    cos_a = math.cos(angle_rad)
    sin_a = math.sin(angle_rad)
    u = dx * cos_a + dy * sin_a
    v = -dx * sin_a + dy * cos_a

    half_w = 0.5 * mouth_w * width_scale
    u_norm = u / max(1.0, half_w)
    horiz = np.clip(1.0 - u_norm * u_norm, 0.0, 1.0) ** 1.15

    max_gap = mouth_w * 0.31 * min(1.0, openness)
    upper_lift = max_gap * 0.28 * horiz
    lower_drop = max_gap * 0.72 * horiz

    v_src = v.copy()
    above = v < -upper_lift
    below = v > lower_drop

    top_falloff = np.exp(-np.maximum(0.0, (-upper_lift - v)) / max(1.0, mouth_w * 0.18))
    bot_falloff = np.exp(-np.maximum(0.0, (v - lower_drop)) / max(1.0, mouth_w * 0.42))
    edge_guard = np.clip((rh - 2 - yy) / max(1.0, rh * 0.22), 0.0, 1.0)
    bot_falloff = bot_falloff * edge_guard

    v_src[above] = v[above] + upper_lift[above] * top_falloff[above]
    v_src[below] = v[below] - lower_drop[below] * bot_falloff[below]

    u_src = u / (
        1.0 + (width_scale - 1.0) * horiz * np.exp(-((v / max(1.0, mouth_w * 0.25)) ** 2))
    )

    src_x = cx + u_src * cos_a - v_src * sin_a - x1
    src_y = cy + u_src * sin_a + v_src * cos_a - y1
    warped = cv2.remap(
        roi,
        src_x.astype(np.float32),
        src_y.astype(np.float32),
        interpolation=cv2.INTER_CUBIC,
        borderMode=cv2.BORDER_REFLECT_101,
    )

    cavity_half = 0.5 * (upper_lift + lower_drop)
    cavity_mid = 0.5 * (lower_drop - upper_lift)
    cavity_dist = np.abs(v - cavity_mid) / np.maximum(0.45, cavity_half)
    cavity_alpha = (
        np.clip((1.0 - cavity_dist) * 3.0, 0.0, 1.0)
        * (cavity_half > 0.35)
        * (np.abs(u_norm) < 0.92)
    )
    cavity_alpha = cv2.GaussianBlur(cavity_alpha.astype(np.float32), (5, 5), 1.1)[..., None]

    rel_v = (v + upper_lift) / np.maximum(0.8, upper_lift + lower_drop)
    cavity_img = np.zeros_like(warped, dtype=np.float32)
    cavity_img[:, :] = (28, 16, 62)

    teeth_mask = np.clip(1.0 - rel_v / 0.35, 0.0, 1.0) * np.clip(
        1.0 - (u_norm / 0.78) ** 4, 0.0, 1.0
    )
    teeth_shade = 0.88 + 0.12 * np.clip(1.0 - u_norm * u_norm, 0.0, 1.0)
    for c_idx, val in enumerate((236, 244, 252)):
        cavity_img[:, :, c_idx] = (
            cavity_img[:, :, c_idx] * (1.0 - teeth_mask) + (val * teeth_shade) * teeth_mask
        )

    tongue_mask = np.clip((rel_v - 0.50) / 0.50, 0.0, 1.0) * np.clip(
        1.0 - (u_norm / 0.66) ** 2, 0.0, 1.0
    )
    for c_idx, val in enumerate((108, 92, 212)):
        cavity_img[:, :, c_idx] = (
            cavity_img[:, :, c_idx] * (1.0 - tongue_mask * 0.85) + val * (tongue_mask * 0.85)
        )

    out_roi = warped.astype(np.float32) * (1.0 - cavity_alpha) + cavity_img * cavity_alpha
    result = full_img.copy()
    result[y1:y2, x1:x2] = np.clip(out_roi, 0, 255).astype(np.uint8)
    return result


def _apply_eye_blink(img, pose_key: str, blink_amount: float):
    """Smoothly pull the upper eyelid and lash line down over the eyeball."""
    import cv2
    import numpy as np

    if pose_key not in POSE_EYES_1254 or blink_amount <= 0.02:
        return img
    h_img, w_img = img.shape[:2]
    s = w_img / 1254.0
    out = img.copy()
    for eye in POSE_EYES_1254[pose_key]:
        cx = eye["cx"] * s
        top_y = eye["top_y"] * s
        bot_y = eye["bot_y"] * s
        rx = eye["rx"] * s
        eye_h = bot_y - top_y
        cy = 0.5 * (top_y + bot_y)
        angle_rad = math.radians(eye["angle"])
        cos_a, sin_a = math.cos(angle_rad), math.sin(angle_rad)

        pad_x = int(rx * 1.4)
        pad_y = int(eye_h * 1.6)
        x1, x2 = max(0, int(cx - pad_x)), min(w_img, int(cx + pad_x))
        y1, y2 = max(0, int(cy - pad_y)), min(h_img, int(cy + pad_y))
        roi = out[y1:y2, x1:x2]
        rh, rw = roi.shape[:2]
        if rh < 4 or rw < 4:
            continue
        yy, xx = np.mgrid[0:rh, 0:rw].astype(np.float32)
        dx = (xx + x1) - cx
        dy = (yy + y1) - top_y

        u = dx * cos_a + dy * sin_a
        v = -dx * sin_a + dy * cos_a

        u_norm = u / max(1.0, rx)
        horiz = np.clip(1.0 - u_norm * u_norm, 0.0, 1.0) ** 0.85
        drop = eye_h * 0.92 * blink_amount * horiz
        v_src = v.copy()
        mask_zone = (v > -eye_h * 0.6) & (v < eye_h * 1.15) & (horiz > 0.01)

        above_lid = mask_zone & (v <= drop)
        span = np.maximum(0.5, drop + 0.5 * eye_h)
        v_src[above_lid] = -0.5 * eye_h + (
            (v[above_lid] + 0.5 * eye_h) / span[above_lid]
        ) * (0.5 * eye_h)

        below_lid = mask_zone & (v > drop)
        rem_dst = np.maximum(0.5, eye_h * 1.15 - drop)
        rem_src = eye_h * 1.15
        v_src[below_lid] = ((v[below_lid] - drop[below_lid]) / rem_dst[below_lid]) * rem_src

        src_x = cx + u * cos_a - v_src * sin_a - x1
        src_y = top_y + u * sin_a + v_src * cos_a - y1
        out[y1:y2, x1:x2] = cv2.remap(
            roi,
            src_x.astype(np.float32),
            src_y.astype(np.float32),
            interpolation=cv2.INTER_CUBIC,
            borderMode=cv2.BORDER_REFLECT_101,
        )
    return out


def _apply_articulated_rig(
    img,
    pose_key: Optional[str],
    frame_index: int,
    fps: int,
    speech_energy: float,
    action: str,
):
    """Apply articulated head nod/tilt, chest breathing, and hand/arm teaching gestures."""
    import cv2
    import numpy as np

    h, w = img.shape[:2]
    if not pose_key or pose_key not in POSE_RIG_512:
        gesture_pulse = math.sin(frame_index / 8.0) * min(1.0, speech_energy * 2.5)
        zoom = 1.0 + 0.008 * math.sin(frame_index / 24.0) + 0.004 * gesture_pulse
        shift_x = 1.6 * math.sin(frame_index / 31.0)
        shift_y = 2.0 * math.sin(frame_index / 20.0) - 1.5 * abs(gesture_pulse)
        rotation = 0.35 * math.sin(frame_index / 42.0)
        matrix = cv2.getRotationMatrix2D((w / 2, h / 2), rotation, zoom)
        matrix[0, 2] += shift_x
        matrix[1, 2] += shift_y
        return cv2.warpAffine(img, matrix, (w, h), borderMode=cv2.BORDER_REFLECT_101)

    s = w / 512.0
    rig = POSE_RIG_512[pose_key]
    t = frame_index / float(max(1, fps))

    gh, gw = 64, 64
    gy, gx = np.mgrid[0:gh, 0:gw].astype(np.float32)
    px = gx * (w / float(gw - 1))
    py = gy * (h / float(gh - 1))

    disp_x = np.zeros((gh, gw), dtype=np.float32)
    disp_y = np.zeros((gh, gw), dtype=np.float32)

    # 1. Subtle chest & shoulder breathing
    breath = math.sin(t * 2.2) * 1.8 * s
    chest_w = np.exp(
        -((px - 256.0 * s) / (145.0 * s)) ** 2 - ((py - 310.0 * s) / (95.0 * s)) ** 2
    )
    disp_y -= breath * chest_w

    # 2. Head nod & tilt around neck pivot synced to narration
    head = rig["head"]
    hcx, hcy = head["cx"] * s, head["cy"] * s
    hrx, hry = head["rx"] * s, head["ry"] * s
    pvx, pvy = head["pivot_x"] * s, head["pivot_y"] * s
    head_w = np.exp(-((((px - hcx) / hrx) ** 2 + ((py - hcy) / hry) ** 2) ** 1.3))

    action_boost = 1.25 if action in {"EMPHASIZE", "POINT", "QUESTION"} else 1.0
    tilt_deg = (1.3 * math.sin(t * 1.65) + 1.6 * math.sin(t * 4.1) * speech_energy) * action_boost
    tilt_rad = math.radians(tilt_deg)
    nod_y = (1.3 * math.sin(t * 2.7) + 2.8 * abs(math.sin(t * 6.2)) * speech_energy) * s
    nod_x = (1.2 * math.cos(t * 1.85)) * s

    rx_off = px - pvx
    ry_off = py - pvy
    rot_dx = (rx_off * math.cos(tilt_rad) - ry_off * math.sin(tilt_rad) - rx_off) + nod_x
    rot_dy = (rx_off * math.sin(tilt_rad) + ry_off * math.cos(tilt_rad) - ry_off) + nod_y
    disp_x += rot_dx * head_w
    disp_y += rot_dy * head_w

    # 3. Articulated teaching hand & forearm gestures
    gesture_amp = (0.38 + 0.85 * min(1.0, speech_energy * 1.65)) * action_boost
    for hand in rig["hands"]:
        cx, cy = hand["cx"] * s, hand["cy"] * s
        rx, ry = hand["rx"] * s, hand["ry"] * s
        hw = np.exp(-((((px - cx) / rx) ** 2 + ((py - cy) / ry) ** 2) ** 1.2))
        wave1 = math.sin(t * 3.6 + hand["phase"])
        wave2 = 0.55 * math.sin(t * 7.0 + hand["phase"]) * speech_energy
        g = (wave1 + wave2) * gesture_amp
        disp_x += (hand["dx"] * s * g) * hw
        disp_y += (hand["dy"] * s * abs(g)) * hw

    border_x = np.clip(np.minimum(px, w - 1 - px) / (18.0 * s), 0.0, 1.0)
    border_y = np.clip(np.minimum(py, h - 1 - py) / (18.0 * s), 0.0, 1.0)
    border = border_x * border_y
    disp_x *= border
    disp_y *= border

    full_dx = cv2.resize(disp_x, (w, h), interpolation=cv2.INTER_CUBIC)
    full_dy = cv2.resize(disp_y, (w, h), interpolation=cv2.INTER_CUBIC)
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    return cv2.remap(
        img,
        xx - full_dx,
        yy - full_dy,
        interpolation=cv2.INTER_LINEAR,
        borderMode=cv2.BORDER_REFLECT_101,
    )


def generate_fallback_avatar_video(
    audio_path: Optional[Path],
    image_path: Path,
    output_path: Path,
    requested_duration_ms: Optional[int],
    teacher_action: Optional[str] = "EXPLAIN",
    external_mouth_envelope: Optional[list[float]] = None,
) -> None:
    """Animate the 3D teacher with aligned lip articulation, eye blinks, and teaching gestures."""
    import cv2

    size = 512
    fps = 30
    envelope, width_scales, audio_duration = _audio_speech_features(audio_path, fps)
    if external_mouth_envelope:
        # Fuse neural lip-sync openness with acoustic speech envelope
        fused: list[float] = []
        for idx in range(max(len(envelope), len(external_mouth_envelope))):
            e_aud = envelope[min(idx, len(envelope) - 1)] if envelope else 0.0
            e_net = external_mouth_envelope[min(idx, len(external_mouth_envelope) - 1)]
            fused.append(0.55 * e_aud + 0.45 * e_net if e_aud > 0.02 else 0.0)
        envelope = fused

    requested_duration = (requested_duration_ms or 0) / 1000.0
    duration = max(1.0, audio_duration, requested_duration)
    total_frames = max(fps, math.ceil(duration * fps))
    action = (teacher_action or "EXPLAIN").strip().upper()
    pose_key = _pose_key_for_image(image_path, action)
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
            speech_energy = (
                envelope[frame_index] if frame_index < len(envelope) else 0.0
            )
            width_scale = (
                width_scales[frame_index] if frame_index < len(width_scales) else 1.0
            )
            blink = _blink_amount_at_frame(frame_index, fps)

            frame = base
            if pose_key:
                frame = _render_mouth_articulation(frame, pose_key, speech_energy, width_scale)
                frame = _apply_eye_blink(frame, pose_key, blink)
            frame = _apply_articulated_rig(frame, pose_key, frame_index, fps, speech_energy, action)

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
    audio_seconds = max(1.0, (duration_ms or 1000) / 1000.0)
    timeout_seconds = min(
        SADTALKER_MAX_TIMEOUT_SECONDS,
        max(
            SADTALKER_TIMEOUT_SECONDS,
            math.ceil(300 + audio_seconds * SADTALKER_TIMEOUT_PER_AUDIO_SECOND),
        ),
    )
    logger.info(
        "SadTalker audio %.1fs; inference timeout %ss",
        audio_seconds,
        timeout_seconds,
    )
    try:
        result = subprocess.run(
            command,
            cwd=str(SADTALKER_DIR),
            env=child_environment,
            timeout=timeout_seconds,
            capture_output=True,
            text=True,
        )
    except subprocess.TimeoutExpired as exc:
        logger.error(
            "SadTalker timed out for %.1fs of audio after %ss",
            audio_seconds,
            timeout_seconds,
        )
        raise RuntimeError(
            f"SadTalker timed out after {timeout_seconds // 60} minutes "
            f"while processing {audio_seconds:.1f}s of narration"
        ) from exc
    if result.returncode != 0:
        logger.error("SadTalker failed: %s", result.stderr[-3000:])
        return False

    videos = [v for v in sorted(result_dir.rglob("*.mp4"), key=lambda item: item.stat().st_mtime, reverse=True) if v.is_file()]
    if not videos:
        logger.error("SadTalker completed without producing an MP4")
        return False

    _normalize_sadtalker_video(videos[0], output_path, duration_ms)
    return True


def run_wav2lip(
    audio_path: Path,
    image_path: Path,
    output_path: Path,
    duration_ms: Optional[int],
    teacher_action: Optional[str],
) -> bool:
    if not SADTALKER_PYTHON:
        return False
    audio_path = audio_path.resolve()
    image_path = image_path.resolve()
    output_path = output_path.resolve()
    synced_video = output_path.with_name(output_path.stem + "_wav2lip_raw.mp4")

    action = (teacher_action or "EXPLAIN").strip().upper()
    pose_key = _pose_key_for_image(image_path, action)

    # For bundled teacher poses, use calibrated bounding box so mouth lands dead-center
    # without S3FD detection jitter or bottom padding offsets.
    command = [
        str(SADTALKER_PYTHON),
        str(WAV2LIP_INFERENCE),
        "--checkpoint_path",
        str(WAV2LIP_CHECKPOINT),
        "--audio",
        str(audio_path),
        "--outfile",
        str(synced_video),
        "--pads",
        "0",
        os.environ.get("WAV2LIP_BOTTOM_PAD", "0"),
        "0",
        "0",
        "--face_det_batch_size",
        os.environ.get("WAV2LIP_FACE_BATCH_SIZE", "4"),
        "--wav2lip_batch_size",
        os.environ.get("WAV2LIP_BATCH_SIZE", "8"),
    ]
    if os.environ.get("WAV2LIP_NOSMOOTH", "true").lower() in {"1", "true", "yes"}:
        command.append("--nosmooth")

    temp_base_face: Optional[Path] = None
    if pose_key and pose_key in POSE_RIG_512:
        # Prepare 512x512 canonical face image for static inference
        temp_base_face = output_path.with_name(output_path.stem + "_face512.png")
        base_512 = _prepare_square_image(image_path, 512)
        import cv2

        cv2.imwrite(str(temp_base_face), base_512)
        box = POSE_RIG_512[pose_key]["wav2lip_box"]
        command.extend(
            [
                "--face",
                str(temp_base_face),
                "--box",
                str(box[0]),
                str(box[1]),
                str(box[2]),
                str(box[3]),
            ]
        )
    else:
        command.extend(["--face", str(image_path)])

    logger.info("Running Wav2Lip for %s (pose=%s)", output_path.name, pose_key or "custom")
    child_environment = os.environ.copy()
    child_environment["PATH"] = (
        str(Path(FFMPEG_BIN).resolve().parent)
        + os.pathsep
        + child_environment.get("PATH", "")
    )
    child_environment["WAV2LIP_FFMPEG"] = str(Path(FFMPEG_BIN).resolve())

    try:
        result = subprocess.run(
            command,
            cwd=str(WAV2LIP_DIR),
            env=child_environment,
            capture_output=True,
            text=True,
            timeout=int(os.environ.get("WAV2LIP_TIMEOUT_SECONDS", "1800")),
        )
        if result.returncode != 0 or not synced_video.exists():
            logger.error("Wav2Lip failed: %s", (result.stderr or result.stdout)[-4000:])
            return False

        # Post-process Wav2Lip synced frames with natural eye blinking and articulated gestures
        import cv2

        cap = cv2.VideoCapture(str(synced_video))
        fps = int(cap.get(cv2.CAP_PROP_FPS) or 30)
        total_frames = int(cap.get(cv2.CAP_PROP_FRAME_COUNT) or 30)
        envelope, _, _ = _audio_speech_features(audio_path, fps)

        enhanced_raw = output_path.with_suffix(".enhanced.raw.mp4")
        writer = cv2.VideoWriter(
            str(enhanced_raw),
            cv2.VideoWriter_fourcc(*"mp4v"),
            fps,
            (512, 512),
        )

        frame_idx = 0
        while True:
            ret, frame = cap.read()
            if not ret or frame is None:
                break
            if frame.shape[:2] != (512, 512):
                frame = cv2.resize(frame, (512, 512), interpolation=cv2.INTER_AREA)

            speech_energy = (
                envelope[frame_idx] if frame_idx < len(envelope) else 0.0
            )
            blink = _blink_amount_at_frame(frame_idx, fps)
            if pose_key:
                frame = _apply_eye_blink(frame, pose_key, blink)
            frame = _apply_articulated_rig(
                frame, pose_key, frame_idx, fps, speech_energy, action
            )
            writer.write(frame)
            frame_idx += 1

        cap.release()
        writer.release()

        _encode_h264(enhanced_raw, output_path)
        enhanced_raw.unlink(missing_ok=True)
        return True
    finally:
        if temp_base_face:
            temp_base_face.unlink(missing_ok=True)
        synced_video.unlink(missing_ok=True)


@app.get("/")
def root():
    return {"message": "Self-hosted talking avatar service is running", "health": "/health"}


@app.get("/health")
def health():
    ready, issues = sadtalker_readiness()
    wav_ready, wav_issues = wav2lip_readiness()
    try:
        active_engine = selected_engine()
    except RuntimeError:
        active_engine = AVATAR_ENGINE
    lip_sync_ready = (
        (active_engine == "wav2lip" and wav_ready)
        or (active_engine == "sadtalker" and ready)
    )
    return {
        "status": "ok",
        "service": "avatar-ai-service",
        "configuredEngine": AVATAR_ENGINE,
        "selectedEngine": active_engine,
        # Readiness must belong to the engine that will actually serve /api/talk.
        # For example, an unavailable strict Wav2Lip configuration must not be
        # reported ready merely because SadTalker happens to be installed.
        "lipSyncReady": lip_sync_ready,
        "wav2LipReady": wav_ready,
        "wav2LipIssues": wav_issues,
        "sadTalkerReady": ready,
        "sadTalkerIssues": issues,
        "defaultAvatarReady": DEFAULT_AVATAR_IMAGE.exists(),
        "outputsCount": len(list(OUTPUT_DIR.glob("*.mp4"))),
    }


@app.post("/api/talk")
def generate_talk(request: TalkRequest):
    if not _generation_slots.acquire(timeout=AVATAR_QUEUE_TIMEOUT_SECONDS):
        raise HTTPException(
            status_code=429,
            detail=(
                "Avatar generator remained busy for "
                f"{AVATAR_QUEUE_TIMEOUT_SECONDS} seconds"
            ),
        )

    # Include the engine so switching from the old fallback/SadTalker pipeline
    # cannot accidentally reuse a clip with different mouth motion.
    # New cache key prevents reuse of clips produced by the reverted pose-warp pipeline.
    job_filename = f"{request.jobId}_slide_{request.slideIndex}_{selected_engine()}_stable-v2.mp4"
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
            engine = selected_engine()
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
                if AVATAR_ENGINE in {"sadtalker", "wav2lip"}:
                    raise RuntimeError(
                        f"Could not download narration audio for SadTalker: {exc}"
                    ) from exc
                logger.warning("Narration download failed; using silent animation: %s", exc)

        if AVATAR_ENGINE in {"sadtalker", "wav2lip"} and not audio_path.exists():
            raise RuntimeError(
                f"{AVATAR_ENGINE} requires narration audio, but audioUrl was empty or unavailable"
            )

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
        active_engine = selected_engine()
        if audio_path.exists() and active_engine == "wav2lip":
            generated = run_wav2lip(
                audio_path, avatar_image, output_path, request.durationMs, request.teacherAction
            )
            if generated:
                engine = "wav2lip"
            elif AVATAR_ENGINE == "wav2lip":
                raise RuntimeError("Wav2Lip inference failed; check avatar-service logs")
        if audio_path.exists() and not generated and active_engine == "sadtalker":
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
                elif AVATAR_ENGINE == "sadtalker":
                    raise RuntimeError(
                        "SadTalker inference failed without producing a video; "
                        "check avatar-service logs for the model error"
                    )
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
    wav_ready, wav_issues = wav2lip_readiness()
    logger.info("Wav2Lip ready=%s issues=%s", wav_ready, wav_issues)
    uvicorn.run(app, host="0.0.0.0", port=PORT)
