# ────────────────────────────────────────────────────────────────────
# Stage 1: Builder — Compiles TypeScript to JavaScript
# ────────────────────────────────────────────────────────────────────
FROM node:22-bookworm-slim AS builder

WORKDIR /app

# Install dependencies first (leverage Docker layer caching)
COPY package*.json ./
RUN npm install

# Copy source files and build the project
COPY . .
RUN npm run build

# ────────────────────────────────────────────────────────────────────
# Stage 2: Runner — Production image with FFmpeg + Chromium
# ────────────────────────────────────────────────────────────────────
FROM node:22-bookworm-slim AS runner

# Install FFmpeg, Chromium, and all necessary shared libraries for headless rendering
RUN apt-get update && apt-get install -y --no-install-recommends \
    ffmpeg \
    chromium \
    fonts-liberation \
    fonts-noto \
    fonts-noto-cjk \
    libasound2 \
    libatk-bridge2.0-0 \
    libatk1.0-0 \
    libcairo2 \
    libcups2 \
    libdrm2 \
    libgbm1 \
    libglib2.0-0 \
    libgtk-3-0 \
    libnspr4 \
    libnss3 \
    libpango-1.0-0 \
    libpangocairo-1.0-0 \
    libx11-xcb1 \
    libxcomposite1 \
    libxcursor1 \
    libxdamage1 \
    libxext6 \
    libxfixes3 \
    libxi6 \
    libxrandr2 \
    libxrender1 \
    libxshmfence1 \
    libxss1 \
    libxtst6 \
    ca-certificates \
    xdg-utils \
    && rm -rf /var/lib/apt/lists/*

# Tell Puppeteer/Remotion to use the system Chromium instead of downloading its own[cite: 2, 3]
ENV PUPPETEER_EXECUTABLE_PATH=/usr/bin/chromium
ENV PUPPETEER_SKIP_CHROMIUM_DOWNLOAD=true

WORKDIR /app

# Install only production dependencies[cite: 2, 3]
COPY package*.json ./
RUN npm ci --omit=dev

# Copy compiled JS from builder stage
COPY --from=builder /app/dist ./dist

# Copy Remotion compositions (required for the bundler at runtime)
COPY --from=builder /app/src ./src

# Copy thư mục public (chứa bgm.mp3 và tài nguyên tĩnh của Remotion)
COPY --from=builder /app/public ./public

# Ensure output directory exists for video/audio[cite: 5]
RUN mkdir -p out/audio

# Expose port 3001 to match docker-compose.yml
EXPOSE 3001

# Start the compiled server[cite: 5]
CMD ["node", "dist/server.js"]