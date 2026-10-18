# slide-ai-service

Service HTTP bọc bộ sinh slide **slAI**: biến văn bản tài liệu thành bộ slide JSON có 9 bố cục
(so sánh, lưới thẻ, bảng, số liệu, hình minh họa…) và dựng HTML trình chiếu.

- Model chính: **Ollama** (`qwen3.5:4b`) chạy trên laptop có GPU, server gọi về qua **Tailscale**.
- Dự phòng: **Gemini**. Ollama không kết nối được, quá thời gian hoặc đang bận thì tự chuyển sang Gemini.
- Đã có trong `docker-compose.yml` chung (`aigen-lecture-slide-ai-service`). Backend gọi service này khi tạo slide AI và khi trình chiếu (xem mục **Tích hợp với backend**).
- File `slide-ai-service/docker-compose.yml` chỉ để chạy riêng service khi phát triển. Đừng chạy song song với stack chung, vì cả hai cùng dùng cổng 5100.

## Tích hợp với backend

| Phần | Cách hoạt động |
| --- | --- |
| Tạo slide AI (`PresentationGenerationProcessor`) | Gọi `POST /generate` trước. Service tắt hoặc lỗi thì dùng luồng Gemini cũ (`LlmService`). Số slide lấy từ `generationParameters.slideCount` (mặc định 10), đối tượng lấy từ `generationParameters.audience` (mặc định `DAI_HOC`) |
| Lưu (`slides_json`) | Mỗi slide có thêm trường `data` chứa slide gốc của slide-ai. `contentBlocks` là bản chữ phẳng, nên trình soạn thảo và xuất PowerPoint vẫn dùng được. Slide cũ không có `data` |
| Trình chiếu | `GET /api/presentations/{id}/render?versionId=` trả HTML. Slide có `data` giữ nguyên bố cục; slide thường chuyển thành `BULLETS` hoặc `TITLE`. Cần 3–30 slide |
| Theme | Lấy theo `templateId`: `standard` → `modern-blue`, `academic` → `academic`, `minimal` → `fresh` |
| Cấu hình backend | `SLIDE_AI_SERVICE_URL` (để rỗng thì tắt), `SLIDE_AI_TOKEN`, `SLIDE_AI_GENERATE_TIMEOUT_SECONDS` (mặc định 900) |

## Cấu trúc

```
slide-ai-service/
├── app.py                  FastAPI: /health, /generate, /render (mới)
├── llm/
│   └── providers.py        Ollama và Gemini, cơ chế dự phòng, mỗi lần chỉ 1 yêu cầu dùng GPU (mới)
├── generation/
│   ├── generate.py         Sinh dàn ý rồi viết từng nhóm slide (từ slAI)
│   └── schema.py           Schema JSON của deck, 9 bố cục (từ slAI)
├── rendering/
│   ├── render_html.py      Dựng HTML từ deck JSON (từ slAI)
│   ├── icons.py            Icon Lucide nhúng sẵn (từ slAI)
│   └── visuals.py          Hình minh họa vẽ bằng code (từ slAI)
├── prompts/system.txt      System prompt
├── templates/deck.html.j2  Template HTML
├── examples/hoc-may.json   Bộ slide mẫu, dùng làm ví dụ cho model
└── tests/test_app.py
```

Code lấy từ thư mục `slAI`. So với bản gốc chỉ khác các chỗ sau:
- Các file được chia vào các package như trên, nên import đổi theo, ví dụ `from generation.schema import Deck`.
- `HERE` trỏ về gốc service để tìm `prompts/`, `templates/`, `examples/`.
- `call_model` trong `generate.py` gọi qua `llm.providers.Router` thay cho `ollama.chat`.
- Thanh tiến trình chỉ hiện khi chạy trong terminal.

Chạy CLI như slAI, nhưng gọi theo kiểu module và đứng ở thư mục `slide-ai-service`:

```powershell
python -m generation.generate tai-lieu.txt --slides 8
python -m rendering.render_html examples/hoc-may.json --theme dark
```

## API

| Method | Path | Body | Trả về |
| --- | --- | --- | --- |
| GET | `/health` | | Trạng thái Ollama (có kết nối được không) và Gemini (đã cấu hình chưa) |
| POST | `/generate` | `{"text": "...", "slides": 12, "audience": "DAI_HOC", "theme": "modern-blue", "author": ""}` | `{"deck": {...}, "providers": ["ollama"], "calls": 5, "seconds": 140.2, "warnings": []}` |
| POST | `/render` | `{"deck": {...}, "theme": "academic"}` | HTML |

- `audience`: `THCS` \| `THPT` \| `DAI_HOC` \| `DI_LAM`.
- `theme`: `modern-blue` \| `academic` \| `fresh` \| `bright` \| `dark`.
- Mã lỗi:
  - `503`: không có model nào dùng được.
  - `502`: model trả về JSON sai sau nhiều lần thử.
  - `401`: sai token.
- Nếu đặt `SLIDE_AI_TOKEN` thì mọi request trừ `/health` phải gửi header `X-Slide-AI-Token`.

## Chạy trên máy (dev)

```powershell
cd slide-ai-service
pip install -r requirements.txt
$env:OLLAMA_URL = "http://localhost:11434"
$env:GEMINI_API_KEY = "<khóa>"      # tùy chọn, để có dự phòng
python -m uvicorn app:app --host 127.0.0.1 --port 5100
```

Hoặc chạy bằng Docker (từ thư mục gốc dự án; lấy `GEMINI_API_KEY` từ `.env` chung):

```powershell
docker compose --env-file .env -f slide-ai-service/docker-compose.yml up -d --build
```

Container gọi Ollama trên Windows qua `http://host.docker.internal:11434`.

Thử nhanh:

```powershell
curl http://127.0.0.1:5100/health
```

Test (dùng provider giả, không cần Ollama hay Gemini):

```powershell
pip install pytest
python -m pytest tests -q
```

## Biến môi trường

| Biến | Mặc định | Ý nghĩa |
| --- | --- | --- |
| `SLIDE_AI_PROVIDER` | `auto` | `auto` = Ollama rồi Gemini; `ollama` hoặc `gemini` = chỉ dùng một bên |
| `OLLAMA_URL` | `http://localhost:11434` | Địa chỉ Ollama (trong Docker: `host.docker.internal`; trên server: IP Tailscale của laptop) |
| `OLLAMA_MODEL` | `qwen3.5:4b` | |
| `OLLAMA_TIMEOUT` | `300` | Số giây tối đa cho một lần gọi |
| `OLLAMA_QUEUE_WAIT` | `30` | Số giây chờ tới lượt dùng GPU; quá thời gian thì dùng Gemini |
| `OLLAMA_COOLDOWN` | `60` | Ollama không kết nối được thì bỏ qua trong bấy nhiêu giây, khỏi phải chờ timeout lại |
| `GEMINI_API_KEY` | | Dùng chung với backend |
| `GEMINI_API_URL` | giống mặc định của backend | URL `generateContent` đầy đủ |
| `GEMINI_TIMEOUT` | `120` | |
| `SLIDE_AI_TOKEN` | | Bật xác thực giữa backend và service |

## Cho server gọi Ollama trên laptop (Tailscale)

Ollama **không có xác thực**. Không được mở cổng 11434 ra internet; chỉ mở trong mạng Tailscale.

1. **Laptop** (Windows):
   - Cài Tailscale từ <https://tailscale.com/download> rồi đăng nhập.
   - Ghi lại IP `100.x.y.z` của laptop: chạy `tailscale ip -4`.
2. **Cho Ollama nghe ngoài localhost:**
   - Vào *System Properties*, chọn *Environment Variables*, thêm biến người dùng `OLLAMA_HOST` = `0.0.0.0:11434`.
   - Thoát hẳn Ollama ở khay hệ thống rồi mở lại.
3. **Firewall: chỉ cho dải IP Tailscale vào cổng 11434.** Chạy trong PowerShell quyền Administrator:
   ```powershell
   New-NetFirewallRule -DisplayName "Ollama via Tailscale" -Direction Inbound -Protocol TCP `
     -LocalPort 11434 -RemoteAddress 100.64.0.0/10 -Action Allow
   ```
   Kiểm tra Windows **không** có rule nào khác cho phép 11434 từ mọi nơi. Lần đầu mở Ollama, nếu Windows hỏi thì chọn chặn mạng Public.
4. **Server (EC2):**
   - Cài Tailscale (`curl -fsSL https://tailscale.com/install.sh | sh && sudo tailscale up`), đăng nhập cùng tài khoản.
   - Kiểm tra: `curl http://100.x.y.z:11434/api/version`.
5. Trong `.env` trên server, đặt `OLLAMA_URL=http://100.x.y.z:11434`.

Laptop tắt, ngủ hoặc mất mạng thì service tự chuyển sang Gemini sau khoảng thời gian `OLLAMA_COOLDOWN`. Hệ thống vẫn chạy, chỉ là không dùng GPU của laptop.

## Giới hạn

- Chỉ dùng 12.000 ký tự đầu của tài liệu (`MAX_CHARS` trong `generate.py`).
- Một GPU 6GB chỉ xử lý được một yêu cầu một lúc. Yêu cầu khác phải chờ tối đa `OLLAMA_QUEUE_WAIT` giây, sau đó dùng Gemini.
- Chưa xuất PDF/PPTX từ service này. PowerPoint vẫn xuất bằng backend như hiện tại.
