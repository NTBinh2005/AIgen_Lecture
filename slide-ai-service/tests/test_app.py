import copy
import json
import sys
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

import app as app_module  # noqa: E402
from generation.generate import LAYOUT_DEF  # noqa: E402
from llm.providers import ProviderError, Router, set_router  # noqa: E402

GOLD = json.loads((ROOT / "examples" / "hoc-may.json").read_text(encoding="utf-8"))
BULLETS = {"layout": "BULLETS", "title": "Tóm tắt ý chính", "bullets": ["Ý thứ nhất", "Ý thứ hai"]}
DOC = ("Học máy là lĩnh vực giúp máy tính học từ dữ liệu. Học có giám sát dùng dữ liệu có nhãn "
       "để phân loại hoặc hồi quy. Học không giám sát tìm cấu trúc ẩn như phân cụm. ") * 40


def gold_slide(layout: str) -> dict:
    if layout == "BULLETS":
        return copy.deepcopy(BULLETS)
    return copy.deepcopy(next(s for s in GOLD["slides"] if s["layout"] == layout))


class FakeProvider:
    """Trả JSON hợp lệ dựng từ bộ slide mẫu, theo đúng schema được yêu cầu."""

    def __init__(self, name="fake", fail=False):
        self.name, self.fail, self.calls = name, fail, 0

    def available(self):
        return True

    def chat(self, messages, schema, num_ctx):
        self.calls += 1
        if self.fail:
            raise ProviderError(f"{self.name} tắt máy")
        props = schema["properties"]
        if "slide_1" in props:  # bước 2: mỗi slide_i bị ép một bố cục
            refs = {v: k for k, v in LAYOUT_DEF.items()}
            out = {k: gold_slide(refs[v["$ref"].rsplit("/", 1)[-1]]) for k, v in props.items()}
        else:  # bước 1: dàn ý đúng n slide
            n = props["slides"]["minItems"]
            layouts = ["TITLE"] + ["CARD_GRID", "COMPARE", "BULLETS", "TABLE"] * 8
            items = [{"title": f"Slide số {i + 1}", "layout": layouts[i], "points": ["ý chính"]}
                     for i in range(n - 1)]
            items.append({"title": "Kết thúc", "layout": "CLOSING", "points": ["tóm tắt"]})
            out = {"meta": {"title": "Nhập môn học máy"}, "slides": items}
        return {"content": json.dumps(out, ensure_ascii=False), "prompt_tokens": 10, "output_tokens": 10}


@pytest.fixture
def client():
    yield TestClient(app_module.app)
    set_router(None)


def test_generate_returns_valid_deck(client):
    set_router(Router([FakeProvider("ollama")]))
    resp = client.post("/generate", json={"text": DOC, "slides": 6, "theme": "academic"})
    assert resp.status_code == 200, resp.text
    body = resp.json()
    slides = body["deck"]["slides"]
    assert len(slides) == 6
    assert slides[0]["layout"] == "TITLE" and slides[-1]["layout"] == "CLOSING"
    assert body["deck"]["meta"]["theme"] == "academic"
    assert body["providers"] == ["ollama"]


def test_falls_back_to_gemini_when_ollama_down(client):
    ollama_p, gemini_p = FakeProvider("ollama", fail=True), FakeProvider("gemini")
    set_router(Router([ollama_p, gemini_p]))
    resp = client.post("/generate", json={"text": DOC, "slides": 4})
    assert resp.status_code == 200, resp.text
    assert resp.json()["providers"] == ["gemini"]
    assert ollama_p.calls >= 1 and gemini_p.calls >= 1


def test_all_providers_down_returns_503(client):
    set_router(Router([FakeProvider("ollama", fail=True), FakeProvider("gemini", fail=True)]))
    resp = client.post("/generate", json={"text": DOC, "slides": 4})
    assert resp.status_code == 503
    assert "Không có mô hình nào dùng được" in resp.json()["detail"]


def test_render_gold_deck_to_html(client):
    resp = client.post("/render", json={"deck": GOLD, "theme": "dark"})
    assert resp.status_code == 200
    assert resp.headers["content-type"].startswith("text/html")
    assert resp.text.count('class="slide') >= len(GOLD["slides"])


def test_render_rejects_invalid_deck(client):
    resp = client.post("/render", json={"deck": {"meta": {"title": "x"}, "slides": []}})
    assert resp.status_code == 422


def test_token_required_when_configured(client, monkeypatch):
    monkeypatch.setenv("SLIDE_AI_TOKEN", "secret")
    assert client.post("/render", json={"deck": GOLD}).status_code == 401
    ok = client.post("/render", json={"deck": GOLD}, headers={"X-Slide-AI-Token": "secret"})
    assert ok.status_code == 200
