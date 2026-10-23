"""HTTP service bọc slide-ai cho backend gọi.

    GET  /health    trạng thái, Ollama có kết nối được không, Gemini đã cấu hình chưa
    POST /generate  văn bản tài liệu -> deck JSON (theo schema.py)
    POST /render    deck JSON -> HTML trình chiếu

Chạy: uvicorn app:app --host 0.0.0.0 --port 5100
Nếu đặt SLIDE_AI_TOKEN thì mọi request (trừ /health) phải gửi header X-Slide-AI-Token.
"""
from __future__ import annotations

import hmac
import logging
import os
import time
from typing import Literal, Optional

from fastapi import Depends, FastAPI, Header, HTTPException
from fastapi.responses import HTMLResponse
from pydantic import BaseModel, Field, ValidationError

import generation.generate as gen
from llm.providers import GeminiProvider, OllamaProvider, ProviderError, get_router
from rendering.render_html import THEMES, render
from generation.schema import Deck

logging.basicConfig(level=os.environ.get("LOG_LEVEL", "INFO"))
log = logging.getLogger("slide-ai")

MAX_INPUT_CHARS = 200_000  # generate.py chỉ dùng MAX_CHARS ký tự đầu; chặn request quá lớn

app = FastAPI(title="slide-ai-service")


def check_token(x_slide_ai_token: Optional[str] = Header(default=None)):
    expected = os.environ.get("SLIDE_AI_TOKEN", "")
    if expected and not hmac.compare_digest(x_slide_ai_token or "", expected):
        raise HTTPException(status_code=401, detail="Sai hoặc thiếu X-Slide-AI-Token")


class GenerateRequest(BaseModel):
    text: str = Field(min_length=1, max_length=MAX_INPUT_CHARS)
    slides: int = Field(default=12, ge=3, le=30)
    audience: Literal["THCS", "THPT", "DAI_HOC", "DI_LAM"] = "DAI_HOC"
    theme: Literal[tuple(THEMES)] = "modern-blue"  # type: ignore[valid-type]
    author: str = Field(default="", max_length=80)


class RenderRequest(BaseModel):
    deck: dict
    theme: Optional[Literal[tuple(THEMES)]] = None  # type: ignore[valid-type]


@app.get("/health")
def health():
    providers = {p.name: p for p in get_router().providers}
    ollama_p = providers.get("ollama")
    gemini_p = providers.get("gemini")
    return {
        "status": "ok",
        "mode": os.environ.get("SLIDE_AI_PROVIDER", "auto"),
        "ollama": None if not isinstance(ollama_p, OllamaProvider) else
        {"url": ollama_p.url, "model": ollama_p.model, "reachable": ollama_p.ping()},
        "gemini": None if not isinstance(gemini_p, GeminiProvider) else {"configured": gemini_p.available()},
    }


@app.post("/generate", dependencies=[Depends(check_token)])
def generate_deck(req: GenerateRequest):
    text = req.text.strip()
    if not text:
        raise HTTPException(status_code=400, detail="Tài liệu rỗng")
    started, calls = time.time(), []
    try:
        deck = gen.generate(text, req.slides, req.audience, None, calls, req.author)
    except ProviderError as e:
        log.warning("generate failed: %s", e)
        raise HTTPException(status_code=503, detail=str(e)) from e
    except (RuntimeError, ValueError, ValidationError) as e:
        log.warning("generate produced invalid deck: %s", str(e)[:300])
        raise HTTPException(status_code=502, detail="Mô hình không tạo được bộ slide hợp lệ, hãy thử lại") from e
    deck.meta.theme = req.theme
    used = sorted({c["provider"] for c in calls if c.get("provider")})
    return {
        "deck": deck.model_dump(mode="json"),
        "warnings": deck.check(),
        "providers": used,
        "calls": len(calls),
        "seconds": round(time.time() - started, 1),
    }


@app.post("/render", response_class=HTMLResponse, dependencies=[Depends(check_token)])
def render_deck(req: RenderRequest):
    try:
        deck = Deck.model_validate(req.deck)
    except ValidationError as e:
        raise HTTPException(status_code=422, detail=f"Deck JSON không hợp lệ: {e.error_count()} lỗi") from e
    return HTMLResponse(render(deck, req.theme))
