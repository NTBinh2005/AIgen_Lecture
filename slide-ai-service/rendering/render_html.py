"""Dựng file HTML (và tùy chọn PDF, ảnh PNG từng slide) từ JSON slide.

Cách dùng:
    python -m rendering.render_html examples/hoc-may.json                # ra examples/hoc-may.html
    python -m rendering.render_html examples/hoc-may.json --pdf          # thêm file PDF
    python -m rendering.render_html examples/hoc-may.json --png out/     # ảnh PNG từng slide để so sánh
    python -m rendering.render_html examples/hoc-may.json --theme academic   # đổi bộ màu
"""
import argparse
import html
import os
import json
import re
from pathlib import Path

from jinja2 import Environment, FileSystemLoader
from markupsafe import Markup

from rendering.icons import icon
from generation.schema import Deck
from rendering.visuals import render as visual

HERE = Path(__file__).resolve().parents[1]  # gốc service: templates/


def md(text):
    """Chỉ hỗ trợ **đậm**; mọi thứ khác được escape."""
    escaped = html.escape(text or "")
    return Markup(re.sub(r"\*\*(.+?)\*\*", r"<strong>\1</strong>", escaped))


def plain(text):
    """Bỏ dấu ** (dùng cho tiêu đề, trích dẫn, con số: những chỗ không in đậm từng từ)."""
    return re.sub(r"\*\*(.+?)\*\*", r"\1", text or "").replace("**", "")


THEMES = ["modern-blue", "academic", "fresh", "bright", "dark"]


def grid_cols(n: int, wanted: int) -> int:
    """Chọn số cột để không có hàng lẻ loi (VD: 4 thẻ mà 3 cột thì thành 2 hoặc 4 cột)."""
    if n % wanted == 0 or n <= wanted:
        return min(wanted, n)
    for c in (4, 3, 2) if wanted >= 3 else (2, 3):
        if n % c == 0:
            return c
    return wanted


def render(deck: Deck, theme: str | None = None) -> str:
    env = Environment(loader=FileSystemLoader(HERE / "templates"), autoescape=True)
    env.filters["md"] = md
    env.filters["plain"] = plain
    env.globals["icon"] = lambda name, size=28: Markup(icon(name, size))
    env.globals["grid_cols"] = grid_cols
    env.globals["visual"] = lambda t: Markup(visual(t))
    return env.get_template("deck.html.j2").render(deck=deck, theme=theme or deck.meta.theme or "modern-blue")


def export(html_path: Path, pdf: bool, png_dir: Path | None):
    from playwright.sync_api import sync_playwright

    with sync_playwright() as p:
        # CHROMIUM_PATH: tùy chọn, dùng Chromium có sẵn thay vì bản Playwright tải về
        browser = p.chromium.launch(executable_path=os.environ.get("CHROMIUM_PATH") or None)
        page = browser.new_page(viewport={"width": 1700, "height": 1000})
        page.goto(html_path.resolve().as_uri())
        page.wait_for_load_state("networkidle")
        page.evaluate("document.fonts.ready")
        if pdf:
            out = html_path.with_suffix(".pdf")
            page.pdf(path=str(out), width="1600px", height="900px", print_background=True)
            print("PDF:", out)
        if png_dir:
            png_dir.mkdir(parents=True, exist_ok=True)
            for i, el in enumerate(page.query_selector_all(".slide"), 1):
                el.screenshot(path=str(png_dir / f"slide-{i:02d}.png"))
            print("PNG:", png_dir)
        browser.close()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("json_file")
    ap.add_argument("--out", help="đường dẫn file HTML (mặc định cạnh file JSON)")
    ap.add_argument("--theme", choices=THEMES, help="bộ màu (mặc định lấy từ JSON, hoặc modern-blue)")
    ap.add_argument("--pdf", action="store_true")
    ap.add_argument("--png", help="thư mục lưu ảnh PNG từng slide")
    args = ap.parse_args()

    deck = Deck.model_validate(json.loads(Path(args.json_file).read_text(encoding="utf-8")))
    for err in deck.check():
        print("Cảnh báo:", err)
    out = Path(args.out) if args.out else Path(args.json_file).with_suffix(".html")
    out.write_text(render(deck, args.theme), encoding="utf-8")
    print("HTML:", out)
    if args.pdf or args.png:
        export(out, args.pdf, Path(args.png) if args.png else None)


if __name__ == "__main__":
    main()
