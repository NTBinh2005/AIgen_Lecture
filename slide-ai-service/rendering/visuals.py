"""Thư viện hình minh họa vẽ bằng SVG. Mô hình chỉ chọn `visual.type`, code vẽ hình."""

BLUE, TEAL, INDIGO, RED, INK, AMBER, AXIS = "#2563eb", "#0e9fb5", "#4f46e5", "#ef4444", "#0f172a", "#f59e0b", "#94a3b8"


def _axes(x0, y0, w, h):
    return (f'<path d="M{x0} {y0} V{y0 + h} H{x0 + w}" fill="none" stroke="{AXIS}" stroke-width="2"/>')


def _label(x, y, text, color, anchor="start"):
    return (f'<text x="{x}" y="{y}" fill="{color}" font-size="15" font-weight="700" '
            f'text-anchor="{anchor}">{text}</text>')


def classification(ox=0, label=True):
    dots = [(70, 90), (100, 75), (125, 105), (85, 130)]
    squares = [(170, 160), (205, 145), (225, 185), (190, 205)]
    s = _axes(ox + 30, 40, 220, 220)
    s += f'<line x1="{ox + 50}" y1="235" x2="{ox + 220}" y2="60" stroke="{BLUE}" stroke-width="3" stroke-dasharray="7 6"/>'
    s += "".join(f'<circle cx="{ox + x}" cy="{y}" r="11" fill="{BLUE}"/>' for x, y in dots)
    s += "".join(f'<rect x="{ox + x - 10}" y="{y - 10}" width="20" height="20" rx="3" fill="#06b6d4"/>' for x, y in squares)
    if label:
        s += _label(ox + 20, 20, "Đồ thị Phân loại (Ranh giới lớp)", BLUE)
    return s


def regression(ox=0, label=True):
    pts = [(55, 230), (85, 215), (115, 175), (150, 165), (180, 135), (215, 115)]
    s = _axes(ox + 30, 40, 220, 220)
    s += f'<path d="M{ox + 45} 240 Q{ox + 140} 150 {ox + 235} 95" fill="none" stroke="{TEAL}" stroke-width="4"/>'
    s += "".join(f'<circle cx="{ox + x}" cy="{y}" r="8" fill="{INK}"/>' for x, y in pts)
    s += f'<line x1="{ox + 158}" y1="158" x2="{ox + 158}" y2="260" stroke="{RED}" stroke-width="2" stroke-dasharray="4 4"/>'
    s += f'<circle cx="{ox + 158}" cy="150" r="11" fill="{RED}"/>'
    if label:
        s += _label(ox + 20, 20, "Đồ thị Hồi quy (Đường xu hướng)", TEAL)
    return s


def clustering(anomaly=False):
    groups = [((110, 190), BLUE, [(-25, -12), (12, -18), (-12, 12), (8, 10)]),
              ((240, 110), TEAL, [(-22, -8), (14, -14), (-10, 12), (8, 6)]),
              ((290, 210), INDIGO, [(-16, -2), (0, -14), (16, 10)])]
    s = _axes(40, 40, 400, 220)
    for (cx, cy), color, offs in groups:
        s += (f'<ellipse cx="{cx}" cy="{cy}" rx="62" ry="45" fill="{color}" fill-opacity="0.08" '
              f'stroke="{color}" stroke-width="2" stroke-dasharray="6 5"/>')
        s += "".join(f'<circle cx="{cx + dx}" cy="{cy + dy}" r="9" fill="{color}"/>' for dx, dy in offs)
    s += _label(20, 20, "Phân cụm dữ liệu (Clustering)", BLUE)
    if anomaly:
        s += f'<circle cx="400" cy="70" r="20" fill="none" stroke="{RED}" stroke-width="3"/><circle cx="400" cy="70" r="13" fill="{RED}"/>'
        s += _label(460, 20, "Điểm dị biệt (Anomaly)", RED, "end")
    return s


def fit_curves():
    pts = [(40, 250), (90, 140), (140, 185), (190, 110), (245, 165), (300, 85), (345, 105)]
    s = _axes(25, 40, 350, 230)
    s += f'<line x1="35" y1="240" x2="345" y2="125" stroke="{AMBER}" stroke-width="3" stroke-dasharray="8 6"/>'
    s += f'<path d="M40 250 Q160 110 345 105" fill="none" stroke="{BLUE}" stroke-width="4"/>'
    s += f'<polyline points="{" ".join(f"{x},{y}" for x, y in pts)}" fill="none" stroke="{RED}" stroke-width="2.5"/>'
    s += "".join(f'<circle cx="{x}" cy="{y}" r="7" fill="{INK}"/>' for x, y in pts)
    legend = [(30, "-- Underfitting", AMBER), (160, "— Tối ưu", BLUE), (265, "— Overfitting", RED)]
    s += "".join(_label(x, 300, t, c) for x, t, c in legend)
    return s, "0 0 400 310"  # tiêu đề hình lấy từ visual.caption


def render(vtype):
    """Trả về chuỗi <svg> cho loại hình; loại lạ trả chuỗi rỗng."""
    if vtype == "classification":
        body, vb = classification(), "0 0 270 270"
    elif vtype == "regression":
        body, vb = regression(), "0 0 270 270"
    elif vtype == "classification+regression":
        body, vb = classification() + regression(ox=290), "0 0 560 270"
    elif vtype in ("clustering", "clustering+anomaly"):
        body, vb = clustering(anomaly=vtype.endswith("anomaly")), "0 0 470 270"
    elif vtype == "fit-curves":
        body, vb = fit_curves()
    else:
        return ""
    return f'<svg class="visual" viewBox="{vb}" xmlns="http://www.w3.org/2000/svg">{body}</svg>'
