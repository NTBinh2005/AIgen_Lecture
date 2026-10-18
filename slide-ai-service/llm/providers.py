"""Chọn mô hình để sinh JSON: Ollama (máy có GPU, qua Tailscale) trước, lỗi thì Gemini.

Cấu hình bằng biến môi trường:
    SLIDE_AI_PROVIDER   auto (mặc định) | ollama | gemini
    OLLAMA_URL          VD http://100.x.y.z:11434 (Tailscale) hoặc http://host.docker.internal:11434
    OLLAMA_MODEL        mặc định qwen3.5:4b
    OLLAMA_TIMEOUT      giây chờ một lần gọi, mặc định 300
    OLLAMA_QUEUE_WAIT   giây chờ tới lượt dùng GPU trước khi chuyển sang Gemini, mặc định 30
    OLLAMA_COOLDOWN     giây bỏ qua Ollama sau khi không kết nối được, mặc định 60
    GEMINI_API_KEY      khóa Gemini (dùng chung với backend)
    GEMINI_API_URL      URL generateContent đầy đủ (dùng chung với backend)
    GEMINI_TIMEOUT      giây, mặc định 120
"""
from __future__ import annotations

import json
import logging
import os
import threading
import time

import httpx
import ollama

log = logging.getLogger("slide-ai.providers")

# Cùng mặc định với gemini.api.url của backend (application.properties).
DEFAULT_GEMINI_URL = ("https://generativelanguage.googleapis.com/v1beta/models/"
                      "gemini-3.8-flash:generateContent")


class ProviderError(Exception):
    """Mô hình không gọi được (mạng, hết hạn mức, máy tắt...). Khác với trả về JSON sai.
    Không kế thừa RuntimeError vì generate.make_outline bắt RuntimeError để dùng câu trả lời cuối."""


def _env_float(name: str, default: float) -> float:
    try:
        return float(os.environ.get(name, default))
    except ValueError:
        return default


class OllamaProvider:
    name = "ollama"

    def __init__(self):
        self.url = os.environ.get("OLLAMA_URL", "http://localhost:11434").rstrip("/")
        self.model = os.environ.get("OLLAMA_MODEL", "qwen3.5:4b")
        self.timeout = _env_float("OLLAMA_TIMEOUT", 300)
        self.queue_wait = _env_float("OLLAMA_QUEUE_WAIT", 30)
        self.cooldown = _env_float("OLLAMA_COOLDOWN", 60)
        self._gpu = threading.Semaphore(1)  # GPU 6GB chỉ chạy được một yêu cầu một lúc
        self._down_until = 0.0
        self._client = ollama.Client(host=self.url, timeout=self.timeout)

    def available(self) -> bool:
        return time.time() >= self._down_until

    def ping(self) -> bool:
        try:
            httpx.get(f"{self.url}/api/version", timeout=3).raise_for_status()
            return True
        except httpx.HTTPError:
            return False

    def chat(self, messages: list[dict], schema: dict, num_ctx: int) -> dict:
        if not self._gpu.acquire(timeout=self.queue_wait):
            raise ProviderError("Ollama đang bận xử lý yêu cầu khác")
        try:
            kwargs = dict(model=self.model, messages=messages, format=schema,
                          options={"temperature": 0.3, "num_ctx": num_ctx})
            try:
                resp = self._client.chat(think=False, **kwargs)  # tắt chế độ suy nghĩ cho nhanh
            except TypeError:  # thư viện ollama cũ chưa có tham số think
                resp = self._client.chat(**kwargs)
        except (httpx.HTTPError, ConnectionError, ollama.ResponseError) as e:
            self._down_until = time.time() + self.cooldown
            raise ProviderError(f"Ollama lỗi: {e}") from e
        finally:
            self._gpu.release()
        return {"content": resp["message"]["content"],
                "prompt_tokens": resp.get("prompt_eval_count"),
                "output_tokens": resp.get("eval_count")}


class GeminiProvider:
    name = "gemini"

    def __init__(self):
        self.key = os.environ.get("GEMINI_API_KEY", "").strip()
        self.url = os.environ.get("GEMINI_API_URL", "").strip() or DEFAULT_GEMINI_URL
        self.timeout = _env_float("GEMINI_TIMEOUT", 120)

    def available(self) -> bool:
        return bool(self.key)

    def _post(self, body: dict) -> httpx.Response:
        return httpx.post(self.url, json=body, timeout=self.timeout,
                          headers={"x-goog-api-key": self.key})

    def chat(self, messages: list[dict], schema: dict, num_ctx: int) -> dict:
        system = "\n\n".join(m["content"] for m in messages if m["role"] == "system")
        contents = [{"role": "model" if m["role"] == "assistant" else "user",
                     "parts": [{"text": m["content"]}]}
                    for m in messages if m["role"] != "system"]
        config = {"temperature": 0.3, "responseMimeType": "application/json",
                  "responseJsonSchema": schema}
        body = {"contents": contents, "generationConfig": config}
        if system:
            body["systemInstruction"] = {"parts": [{"text": system}]}
        try:
            resp = self._post(body)
            if resp.status_code == 400:
                # Model không nhận JSON Schema đầy đủ ($defs/$ref): gửi schema trong prompt,
                # kết quả vẫn được kiểm tra bằng pydantic và thử lại ở generate.ask().
                config.pop("responseJsonSchema")
                contents[-1]["parts"][0]["text"] += (
                    "\n\nTrả về đúng một JSON khớp JSON Schema sau:\n" + json.dumps(schema, ensure_ascii=False))
                resp = self._post(body)
            resp.raise_for_status()
        except httpx.HTTPError as e:
            status = getattr(getattr(e, "response", None), "status_code", None)
            raise ProviderError(f"Gemini lỗi{f' HTTP {status}' if status else ''}: {type(e).__name__}") from e
        data = resp.json()
        try:
            text = "".join(p.get("text", "") for p in data["candidates"][0]["content"]["parts"])
        except (KeyError, IndexError) as e:
            raise ProviderError(f"Gemini không trả nội dung (finishReason="
                                f"{(data.get('candidates') or [{}])[0].get('finishReason')})") from e
        usage = data.get("usageMetadata", {})
        return {"content": text, "prompt_tokens": usage.get("promptTokenCount"),
                "output_tokens": usage.get("candidatesTokenCount")}


class Router:
    """Thử lần lượt các provider theo SLIDE_AI_PROVIDER; provider lỗi thì chuyển sang cái sau."""

    def __init__(self, providers: list | None = None, mode: str | None = None):
        if providers is None:
            mode = (mode or os.environ.get("SLIDE_AI_PROVIDER", "auto")).lower()
            ollama_p, gemini_p = OllamaProvider(), GeminiProvider()
            providers = {"ollama": [ollama_p], "gemini": [gemini_p]}.get(mode, [ollama_p, gemini_p])
        self.providers = providers

    def chat(self, messages: list[dict], schema: dict, num_ctx: int) -> dict:
        errors = []
        for p in self.providers:
            if not p.available():
                errors.append(f"{p.name}: tạm bỏ qua")
                continue
            try:
                result = p.chat(messages, schema, num_ctx)
                result["provider"] = p.name
                return result
            except ProviderError as e:
                log.warning("%s", e)
                errors.append(f"{p.name}: {e}")
        raise ProviderError("Không có mô hình nào dùng được (" + "; ".join(errors) + ")")


_router: Router | None = None
_router_lock = threading.Lock()


def get_router() -> Router:
    global _router
    with _router_lock:
        if _router is None:
            _router = Router()
        return _router


def set_router(router: Router | None) -> None:
    """Dùng trong test để thay provider giả."""
    global _router
    with _router_lock:
        _router = router
