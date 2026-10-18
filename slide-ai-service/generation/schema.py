"""Schema JSON cho bộ slide (phiên bản 2, theo slide mẫu "Nhập Môn Học Máy").

Mô hình chỉ sinh JSON theo schema này; mọi thứ về màu, font, vị trí do bộ dựng lo.
Dùng `Deck.model_json_schema()` để đưa vào tham số `format` của Ollama.
"""
from __future__ import annotations

from typing import Annotated, Literal, Optional, Union

from pydantic import BaseModel, Field

# Danh sách icon cố định (tên icon Lucide). Mô hình chỉ được chọn trong danh sách này.
ICONS = Literal[
    "eye", "brain", "code", "database", "shield", "chart-line", "chart-bar",
    "messages-square", "bot", "images", "scale", "filter-x", "wand-sparkles",
    "flame", "sliders-horizontal", "lightbulb", "target", "book-open",
    "graduation-cap", "users", "clock", "globe", "cpu", "layers", "rocket",
    "search", "settings", "circle-check", "triangle-alert", "trending-up",
    "leaf", "flask-conical", "calculator", "map", "heart-pulse", "zap",
]

# Thư viện hình minh họa do code vẽ sẵn (visuals.py). Không có thì bỏ trống.
VISUALS = Literal[
    "classification", "regression", "classification+regression",
    "clustering", "clustering+anomaly", "fit-curves",
]

TONES = Literal["default", "primary", "info", "warning", "danger", "dark"]

Short = Annotated[str, Field(max_length=120)]
Title = Annotated[str, Field(max_length=70)]


class Card(BaseModel):
    heading: Annotated[str, Field(max_length=60)]
    body: Short
    icon: Optional[ICONS] = None
    badge: Optional[Annotated[str, Field(max_length=3)]] = None


class LabelText(BaseModel):
    label: Annotated[str, Field(max_length=40)]
    text: Short


class Step(BaseModel):
    label: Annotated[str, Field(max_length=50)]
    icon: Optional[ICONS] = None
    tone: TONES = "default"


class Example(BaseModel):
    heading: Annotated[str, Field(max_length=60)]
    body: Annotated[str, Field(max_length=160)]


class Side(BaseModel):
    heading: Annotated[str, Field(max_length=60)]
    icon: Optional[ICONS] = None
    items: Annotated[list[LabelText], Field(max_length=5)] = []
    body: Optional[Annotated[str, Field(max_length=160)]] = None
    example: Optional[Example] = None
    steps: Annotated[list[Step], Field(max_length=4)] = []


class Block(BaseModel):
    heading: Annotated[str, Field(max_length=70)]
    body: Annotated[str, Field(max_length=160)]
    tone: TONES = "default"


class Visual(BaseModel):
    type: VISUALS
    caption: Annotated[str, Field(max_length=140)] = ""


class Quote(BaseModel):
    text: Annotated[str, Field(max_length=160)]
    author: Annotated[str, Field(max_length=60)]
    note: Optional[Annotated[str, Field(max_length=160)]] = None


class Stat(BaseModel):
    value: Annotated[str, Field(max_length=10)]
    label: Annotated[str, Field(max_length=50)]
    body: Short = ""
    tone: TONES = "primary"


class StepList(BaseModel):
    heading: Annotated[str, Field(max_length=70)]
    items: Annotated[list[Annotated[str, Field(max_length=60)]], Field(min_length=2, max_length=4)]


# ---------- Các bố cục ----------

class _Base(BaseModel):
    kicker: Annotated[str, Field(max_length=60)] = ""
    title: Title
    notes: str = ""  # lời giảng cho giáo viên (Speaker Notes)


class TitleSlide(_Base):
    layout: Literal["TITLE"]
    subtitle: Annotated[str, Field(max_length=140)] = ""
    author: Annotated[str, Field(max_length=80)] = ""


class BulletsSlide(_Base):
    layout: Literal["BULLETS"]
    bullets: Annotated[list[Short], Field(min_length=2, max_length=6)]


class CompareSlide(_Base):
    layout: Literal["COMPARE"]
    left: Side
    right: Side
    highlight: Literal["left", "right", "none"] = "none"
    message: Optional[Annotated[str, Field(max_length=140)]] = None


class CardGridSlide(_Base):
    layout: Literal["CARD_GRID"]
    columns: Literal[2, 3, 4] = 3
    numbered: bool = False
    cards: Annotated[list[Card], Field(min_length=2, max_length=6)]


class QuoteCardsSlide(_Base):
    layout: Literal["QUOTE_CARDS"]
    quote: Quote
    cards: Annotated[list[Card], Field(min_length=2, max_length=4)]


class TextVisualSlide(_Base):
    layout: Literal["TEXT_VISUAL"]
    blocks: Annotated[list[Block], Field(min_length=1, max_length=3)]
    callout: Optional[Annotated[str, Field(max_length=160)]] = None
    visual: Visual
    visual_side: Literal["left", "right"] = "right"


class TableSlide(_Base):
    layout: Literal["TABLE"]
    columns: Annotated[list[Annotated[str, Field(max_length=30)]], Field(min_length=2, max_length=5)]
    rows: Annotated[list[list[Annotated[str, Field(max_length=60)]]], Field(min_length=2, max_length=6)]


class StatSplitSlide(_Base):
    layout: Literal["STAT_SPLIT"]
    stats: Annotated[list[Stat], Field(min_length=1, max_length=3)]
    steps: Optional[StepList] = None


class ClosingSlide(_Base):
    layout: Literal["CLOSING"]
    icon: Optional[ICONS] = None
    subtitle: Annotated[str, Field(max_length=160)] = ""
    contact: Annotated[str, Field(max_length=80)] = ""


Slide = Annotated[
    Union[
        TitleSlide, BulletsSlide, CompareSlide, CardGridSlide, QuoteCardsSlide,
        TextVisualSlide, TableSlide, StatSplitSlide, ClosingSlide,
    ],
    Field(discriminator="layout"),
]


class Meta(BaseModel):
    title: str
    subject: str = ""
    chapter: str = ""
    audience: Literal["THCS", "THPT", "DAI_HOC", "DI_LAM"] = "DAI_HOC"
    language: Literal["vi", "en"] = "vi"
    theme: str = "modern-blue"


class Deck(BaseModel):
    meta: Meta
    slides: Annotated[list[Slide], Field(min_length=3, max_length=30)]

    def check(self, requested: int | None = None) -> list[str]:
        """Các kiểm tra mà JSON Schema không diễn đạt được. Trả về danh sách lỗi."""
        errors = []
        if requested and abs(len(self.slides) - requested) > 1:
            errors.append(f"Yêu cầu {requested} slide nhưng có {len(self.slides)}.")
        if self.slides[0].layout != "TITLE":
            errors.append("Slide đầu phải là TITLE.")
        for i, s in enumerate(self.slides, 1):
            if s.layout == "TABLE":
                n = len(s.columns)
                for r in s.rows:
                    if len(r) != n:
                        errors.append(f"Slide {i}: hàng bảng có {len(r)} ô, cần {n}.")
        return errors
