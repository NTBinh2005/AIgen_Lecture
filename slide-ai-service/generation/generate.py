"""Sinh bộ slide JSON từ tài liệu bằng mô hình chạy trên Ollama, rồi dựng HTML.

Cách dùng:
    python -m generation.generate tai-lieu.pdf --slides 12
    python -m generation.generate bai.txt --slides 8 --audience THPT --model qwen3.5:2b
    python -m generation.generate tai-lieu.pdf --slides 12 --theme academic

Cần: Ollama đang chạy và đã `ollama pull qwen3.5:4b`.

Sinh theo 2 bước để mô hình nhỏ làm tốt và không tràn ngữ cảnh:
  1. Dàn ý: đúng N slide (tiêu đề, bố cục, ý chính).
  2. Viết chi tiết từng nhóm 3 slide theo dàn ý, kèm tài liệu gốc.
Mỗi lần gọi đều ép đúng số slide bằng JSON Schema, nên không còn chuyện thiếu slide.
"""
import argparse
import copy
import json
import re
import sys
import threading
import time
from pathlib import Path
from typing import Annotated, Literal

from pydantic import BaseModel, Field, TypeAdapter, ValidationError

from llm.providers import get_router
from generation.schema import Deck, Meta, Slide

HERE = Path(__file__).resolve().parents[1]  # gốc service: examples/, prompts/
MAX_CHARS = 12000      # ~4.000 token tiếng Việt
NUM_CTX = 12288        # đủ cho prompt + tài liệu + câu trả lời; giảm nếu thiếu VRAM
BATCH = 3              # số slide viết chi tiết mỗi lần gọi
LAYOUTS = Literal["TITLE", "BULLETS", "COMPARE", "CARD_GRID", "QUOTE_CARDS",
                  "TEXT_VISUAL", "TABLE", "STAT_SPLIT", "CLOSING"]


# Mỗi hình minh họa chỉ hợp khi tài liệu thật sự nói về chủ đề đó.
VISUAL_KEYWORDS = {
    "classification": ["phân loại", "classification", "phân lớp"],
    "regression": ["hồi quy", "regression"],
    "classification+regression": ["phân loại", "classification"],
    "clustering": ["phân cụm", "clustering", "cụm"],
    "clustering+anomaly": ["bất thường", "anomaly"],
    "fit-curves": ["quá khớp", "overfit", "underfit", "chưa khớp"],
}
FILLER = re.compile(r"cảm ơn|cám ơn|hỏi\s*(&|và)?\s*đáp|q\s*&\s*a|kết thúc|thảo luận|lắng nghe", re.I)
CHARS_PER_SLIDE = 600  # tài liệu ngắn hơn N×600 ký tự thì không đủ ý cho N slide


def allowed_visuals(text: str) -> list[str]:
    low = text.lower()
    allowed = [v for v, kws in VISUAL_KEYWORDS.items() if any(k in low for k in kws)]
    if "classification+regression" in allowed and "regression" not in allowed:
        allowed.remove("classification+regression")
    return allowed


class OutlineItem(BaseModel):
    title: Annotated[str, Field(max_length=70)]
    layout: LAYOUTS
    points: Annotated[list[Annotated[str, Field(max_length=120)]], Field(min_length=1, max_length=5)]


class Outline(BaseModel):
    meta: Meta
    slides: list[OutlineItem]


# ---------- Thanh tiến trình (của Duong) ----------

def format_time(seconds: float) -> str:
    seconds = max(0, int(seconds))
    minutes, seconds = divmod(seconds, 60)
    hours, minutes = divmod(minutes, 60)

    if hours:
        return f"{hours:02d}:{minutes:02d}:{seconds:02d}"
    return f"{minutes:02d}:{seconds:02d}"


def run_with_progress(func, *args, label="AI đang xử lý", estimated_seconds=60, **kwargs):
    """Hiển thị thanh tiến trình trong lúc chờ Ollama."""
    # estimated_seconds: thời gian ước tính cho một lần gọi AI; chỉnh lại sau khi chạy thử thực tế.
    started = time.time()
    if not sys.stdout.isatty():  # chạy trong service/Docker: không vẽ thanh tiến trình vào log
        result = func(*args, **kwargs)
        print(f"{label}: xong trong {format_time(time.time() - started)}", flush=True)
        return result
    stop_event = threading.Event()

    def show_progress():
        spinner = "|/-\\"
        index = 0

        while not stop_event.is_set():
            elapsed = time.time() - started

            # Tiến trình ước tính, tối đa 95% khi AI chưa trả kết quả.
            percent = min(95, elapsed / estimated_seconds * 95)
            filled = int(percent / 5)
            bar = "#" * filled + "-" * (20 - filled)

            remaining = max(0, estimated_seconds - elapsed)
            eta = format_time(remaining)

            sys.stdout.write(
                f"\r[{bar}] {percent:5.1f}% "
                f"| Đã chạy: {format_time(elapsed)} "
                f"| Còn khoảng: {eta} "
                f"| {label} {spinner[index % len(spinner)]}"
            )
            sys.stdout.flush()

            index += 1
            stop_event.wait(0.2)

    thread = threading.Thread(target=show_progress, daemon=True)
    thread.start()

    try:
        result = func(*args, **kwargs)
    finally:
        stop_event.set()
        thread.join()
        elapsed = time.time() - started
        sys.stdout.write("\r" + " " * 120 + "\r")
        sys.stdout.flush()

    print(f"{label}: xong trong {format_time(elapsed)}")
    return result


# ---------- Đọc tài liệu, prompt ----------

def read_document(path: Path) -> str:
    suffix = path.suffix.lower()
    if suffix == ".pdf":
        import pymupdf
        with pymupdf.open(path) as doc:
            return "\n".join(page.get_text() for page in doc)
    if suffix == ".docx":
        import docx  # python-docx
        return "\n".join(p.text for p in docx.Document(path).paragraphs)
    return path.read_text(encoding="utf-8")


def gold_slides() -> list[dict]:
    return json.loads((HERE / "examples" / "hoc-may.json").read_text(encoding="utf-8"))["slides"]


def examples_for(layouts: set[str]) -> str:
    """Lấy từ bài mẫu một slide cho mỗi bố cục sắp viết, làm ví dụ cho mô hình."""
    picked, seen = [], set()
    for s in gold_slides():
        if s["layout"] in layouts and s["layout"] not in seen:
            # Bỏ tên giảng viên, môn học của bài mẫu để mô hình không chép sang bài mới.
            s = {k: v for k, v in s.items() if k not in ("kicker", "author", "contact")}
            picked.append(s)
            seen.add(s["layout"])
    return json.dumps(picked, ensure_ascii=False)


def system_prompt() -> str:
    return (HERE / "prompts" / "system.txt").read_text(encoding="utf-8")


# ---------- Ép đúng số phần tử bằng JSON Schema ----------

def exact_schema(model_cls, n: int) -> dict:
    schema = copy.deepcopy(model_cls.model_json_schema())
    schema["properties"]["slides"]["minItems"] = n
    schema["properties"]["slides"]["maxItems"] = n
    return schema


LAYOUT_DEF = {"TITLE": "TitleSlide", "BULLETS": "BulletsSlide", "COMPARE": "CompareSlide",
              "CARD_GRID": "CardGridSlide", "QUOTE_CARDS": "QuoteCardsSlide", "TEXT_VISUAL": "TextVisualSlide",
              "TABLE": "TableSlide", "STAT_SPLIT": "StatSplitSlide", "CLOSING": "ClosingSlide"}


def batch_schema(layouts: list[str], visuals: list[str] | None = None) -> dict:
    """Mỗi vị trí bị ép đúng bố cục trong dàn ý (slide_1, slide_2...), nên mô hình
    không thể trả sai bố cục và không phải gọi lại."""
    deck = copy.deepcopy(Deck.model_json_schema())
    if visuals:  # chỉ cho chọn hình hợp với tài liệu
        deck["$defs"]["Visual"]["properties"]["type"]["enum"] = visuals
    keys = [f"slide_{i + 1}" for i in range(len(layouts))]
    return {"$defs": deck["$defs"], "type": "object", "required": keys,
            "properties": {k: {"$ref": f"#/$defs/{LAYOUT_DEF[l]}"} for k, l in zip(keys, layouts)}}


def call_model(model: str, messages: list[dict], schema: dict, log: list) -> str:
    # slide-ai-service: gọi qua providers.Router (Ollama, lỗi thì Gemini) thay cho ollama.chat.
    # `model` giữ lại cho CLI; model thật lấy từ OLLAMA_MODEL / GEMINI_API_URL.
    t0 = time.time()
    resp = get_router().chat(messages, schema, NUM_CTX)
    raw = resp["content"]
    log.append({"seconds": round(time.time() - t0, 1), "provider": resp["provider"], "prompt_tokens": resp.get("prompt_tokens"), "output_tokens": resp.get("output_tokens"), "raw": raw})
    if resp["provider"] == "ollama" and resp.get("prompt_tokens") and resp["prompt_tokens"] >= NUM_CTX - 64:
        print("Cảnh báo: prompt chạm giới hạn ngữ cảnh, một phần prompt có thể đã bị cắt.")
    return raw


def ask(model, messages, schema, parse, log, label, estimate, retries=2):
    """Gọi mô hình, kiểm tra bằng `parse`; lỗi thì gọi lại kèm thông báo lỗi (không kèm câu trả lời cũ cho đỡ tốn ngữ cảnh)."""
    error = ""
    for attempt in range(retries + 1):
        msgs = messages if not error else messages[:-1] + [
            {"role": "user", "content": messages[-1]["content"] + f"\n\nLần trước bị lỗi: {error}. Hãy tránh lỗi này."}]
        raw = run_with_progress(call_model, model, msgs, schema, log,
                                label=label, estimated_seconds=estimate)
        try:
            return parse(raw)
        except (ValidationError, ValueError) as e:
            error = str(e)[:600]
            print(f"  Lần {attempt + 1} chưa đạt: {error[:150]}")
    raise RuntimeError("Mô hình không tạo được JSON hợp lệ sau nhiều lần thử.")


# ---------- Hai bước sinh ----------

def make_outline(text, n, audience, model, log) -> Outline:
    user = (f"Bước 1/2: lập DÀN Ý đúng {n} slide (đối tượng: {audience}, ngôn ngữ: vi). "
            f"Slide đầu là TITLE, slide cuối là CLOSING. Mỗi slide: tiêu đề là câu chốt, chọn bố cục, "
            f"ghi 1–5 ý chính lấy từ tài liệu.\n\nTài liệu:\n{text[:MAX_CHARS]}")
    messages = [{"role": "system", "content": system_prompt()}, {"role": "user", "content": user}]

    def parse(raw):
        outline = Outline.model_validate_json(raw)
        problems = outline_problems(outline)
        if problems:
            raise ValueError("; ".join(problems))
        return outline

    try:
        outline = ask(model, messages, exact_schema(Outline, n), parse, log,
                      label="Bước 1: lập dàn ý", estimate=60)
    except RuntimeError:
        # Mô hình vẫn lặp sau nhiều lần thử: lấy bản cuối và để code tự bỏ slide thừa.
        outline = Outline.model_validate_json(log[-1]["raw"])
    return fix_structure(outline, text)


def is_filler(it: OutlineItem) -> bool:
    return bool(FILLER.search(it.title))


def outline_problems(outline: Outline) -> list[str]:
    problems, seen = [], set()
    middle = outline.slides[1:-1]
    for i, it in enumerate(middle, 2):
        key = it.title.strip().lower()
        if key in seen:
            problems.append(f"slide {i} trùng tiêu đề \"{it.title}\"")
        seen.add(key)
        if is_filler(it) or it.layout in ("TITLE", "CLOSING"):
            problems.append(f"slide {i} là slide cảm ơn/hỏi đáp; chỉ slide cuối được làm việc này")
    return problems


def fix_structure(outline: Outline, text: str) -> Outline:
    """Slide đầu là TITLE, cuối là CLOSING; bỏ slide trùng hoặc cảm ơn/hỏi đáp ở giữa;
    TEXT_VISUAL mà tài liệu không có hình phù hợp thì đổi sang CARD_GRID."""
    first, *middle, last = outline.slides
    first.layout, last.layout = "TITLE", "CLOSING"
    kept, seen = [], set()
    for it in middle:
        key = it.title.strip().lower()
        if key in seen or is_filler(it) or it.layout in ("TITLE", "CLOSING"):
            continue
        seen.add(key)
        if it.layout == "TEXT_VISUAL" and not allowed_visuals(text):
            it.layout = "CARD_GRID"
        kept.append(it)
    dropped = len(outline.slides) - len(kept) - 2
    if dropped:
        print(f"Bỏ {dropped} slide lặp hoặc slide cảm ơn thừa trong dàn ý.")
    outline.slides = [first, *kept, last]
    return outline


def write_slides(text, outline: Outline, start: int, model, log) -> list:
    items = outline.slides[start:start + BATCH]
    plan = "\n".join(f"- Slide {start + i + 1} [{it.layout}] {it.title}: " + " | ".join(it.points)
                     for i, it in enumerate(items))
    user = (f"Bước 2/2: viết chi tiết {len(items)} slide sau theo đúng thứ tự, đúng bố cục ghi trong ngoặc. "
            f"Bộ slide có tổng {len(outline.slides)} slide, chủ đề: {outline.meta.title}.\n{plan}\n\n"
            f"Trả về JSON có các khóa slide_1, slide_2... theo thứ tự trên.\n"
            f"Ví dụ JSON đúng phong cách cho các bố cục này:\n{examples_for({it.layout for it in items})}\n\n"
            f"Tài liệu gốc (chỉ dùng kiến thức trong đây):\n{text[:MAX_CHARS]}")
    messages = [{"role": "system", "content": system_prompt()}, {"role": "user", "content": user}]
    adapter = TypeAdapter(list[Slide])

    def parse(raw):
        data = json.loads(raw)
        slides = adapter.validate_python([data[f"slide_{i + 1}"] for i in range(len(items))])
        wrong = [i for i, (s, it) in enumerate(zip(slides, items)) if s.layout != it.layout]
        if wrong:
            raise ValueError(f"slide {start + wrong[0] + 1} phải dùng bố cục {items[wrong[0]].layout}")
        return slides

    label = f"Bước 2: viết slide {start + 1}–{start + len(items)}"
    schema = batch_schema([it.layout for it in items], allowed_visuals(text))
    return ask(model, messages, schema, parse, log, label=label, estimate=60)


def polish(deck: Deck, author: str = "") -> Deck:
    """Vá những thứ code tự làm được, không cần hỏi lại mô hình."""
    gold = json.loads((HERE / "examples" / "hoc-may.json").read_text(encoding="utf-8"))
    gold_values = {gold["meta"]["subject"], gold["meta"]["chapter"]} - {""}
    for s in gold["slides"]:
        gold_values |= {s.get("kicker", ""), s.get("author", ""), s.get("contact", "")} - {""}

    def leaked(v: str) -> bool:
        return any(g and g.lower() in v.lower() for g in gold_values) or "Lại Hiền Phương" in v

    if leaked(deck.meta.subject):
        deck.meta.subject = ""
    if leaked(deck.meta.chapter):
        deck.meta.chapter = ""
    n = 0
    for s in deck.slides:
        if s.layout == "TITLE":
            s.kicker = "" if leaked(s.kicker) else s.kicker
            s.author = author or ("" if leaked(s.author) else s.author)
            continue
        if s.layout == "CLOSING":
            s.kicker = "" if leaked(s.kicker) else s.kicker
            s.contact = author or ("" if leaked(s.contact) else s.contact)
            continue
        n += 1
        name = re.sub(r"^[\d\s•.\-–:]+", "", s.kicker or "").strip()  # bỏ số mô hình tự đánh
        s.kicker = f"{n:02d} • {name}" if name else f"{n:02d}"
    return deck


def generate(text, n, audience, model, log, author="") -> Deck:
    most = max(4, len(text) // CHARS_PER_SLIDE)
    if n > most:
        print(f"Tài liệu ngắn ({len(text)} ký tự) chỉ đủ ý cho khoảng {most} slide; giảm từ {n} xuống {most}.")
        n = most
    outline = make_outline(text, n, audience, model, log)
    n = len(outline.slides)
    print("Dàn ý: " + " / ".join(s.title for s in outline.slides))
    slides = []
    for start in range(0, n, BATCH):
        slides += write_slides(text, outline, start, model, log)
    deck = polish(Deck(meta=outline.meta, slides=slides), author)
    for problem in deck.check(requested=n):
        print("Cảnh báo:", problem)
    return deck


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("document")
    ap.add_argument("--slides", type=int, default=12)
    ap.add_argument("--audience", default="DAI_HOC", choices=["THCS", "THPT", "DAI_HOC", "DI_LAM"])
    ap.add_argument("--model", default="qwen3.5:4b")
    ap.add_argument("--theme", choices=["modern-blue", "academic", "fresh", "bright", "dark"],
                    default="modern-blue", help="bộ màu của slide")
    ap.add_argument("--author", default="", help='VD: "GV: Nguyễn Văn A • a@truong.edu.vn"')
    ap.add_argument("--no-render", action="store_true")
    args = ap.parse_args()

    src = Path(args.document)
    start, log = time.time(), []
    try:
        deck = generate(read_document(src), args.slides, args.audience, args.model, log, args.author)
        deck.meta.theme = args.theme
    finally:
        # Lưu mọi câu trả lời thô của mô hình để soát lỗi (gửi file này khi cần hỗ trợ).
        src.with_suffix(".debug.json").write_text(json.dumps(log, ensure_ascii=False, indent=1), encoding="utf-8")
    out = src.with_suffix(".slides.json")
    out.write_text(deck.model_dump_json(indent=2, exclude_defaults=True), encoding="utf-8")
    print(f"JSON: {out} ({len(deck.slides)} slide, tổng {format_time(time.time() - start)})")

    if not args.no_render:
        from rendering.render_html import render
        html_out = out.with_suffix(".html")
        html_out.write_text(render(deck, args.theme), encoding="utf-8")
        print("HTML:", html_out)


if __name__ == "__main__":
    main()
