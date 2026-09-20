# Docker Configurations and Code

This document aggregates all Docker-related files across the **AIGen Lecture** project.

---

## 1. `docker-compose.yml` (Root)
This is the main orchestration file that links all services together.

```yaml
version: '3.8'

services:
  # ────────────────────────────────────────────────────────────────────
  # PostgreSQL — Main database for Spring Boot backend
  # ────────────────────────────────────────────────────────────────────
  postgres:
    image: postgres:16
    container_name: aigen-lecture-postgres
    environment:
      POSTGRES_DB: aigen_lecture
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgres
    ports:
      # Bound to localhost for security on EC2. Backend connects via internal network.
      - "127.0.0.1:5435:5432"
    volumes:
      - postgres-data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U postgres -d aigen_lecture"]
      interval: 10s
      timeout: 5s
      retries: 5
    restart: unless-stopped

  # ────────────────────────────────────────────────────────────────────
  # Backend — Spring Boot Application
  # ────────────────────────────────────────────────────────────────────
  backend:
    build: 
      context: ./backend
      dockerfile: Dockerfile
    container_name: aigen-lecture-backend
    ports:
      - "8080:8080"
    environment:
      - SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/aigen_lecture
      - SPRING_DATASOURCE_USERNAME=postgres
      - SPRING_DATASOURCE_PASSWORD=postgres
      - GEMINI_API_KEY=${GEMINI_API_KEY:-}
      - VIDEO_SERVICE_URL=http://video-service:3001
    depends_on:
      postgres:
        condition: service_healthy # Waits for DB to be fully ready before starting
    restart: unless-stopped

  # ────────────────────────────────────────────────────────────────────
  # Video Service — Node.js + Remotion render engine
  # ────────────────────────────────────────────────────────────────────
  video-service:
    build:
      context: ./video-service
      dockerfile: Dockerfile
    container_name: aigen-lecture-video-service
    ports:
      - "3001:3001"
    environment:
      PORT: "3001"
      TTS_PROVIDER: "${TTS_PROVIDER:-mock}"
      ELEVENLABS_API_KEY: "${ELEVENLABS_API_KEY:-}"
      PUPPETEER_EXECUTABLE_PATH: /usr/bin/chromium
      UPLOAD_TO_SUPABASE: "${UPLOAD_TO_SUPABASE:-false}"
      SUPABASE_URL: "${SUPABASE_URL:-}"
      SUPABASE_SERVICE_ROLE_KEY: "${SUPABASE_SERVICE_ROLE_KEY:-}"
      SUPABASE_BUCKET_NAME: "${SUPABASE_BUCKET_NAME:-lecture-videos}"
    volumes:
      - video-output:/app/out
    shm_size: "2gb"  # CRITICAL: Prevents Chromium headless from crashing during rendering
    restart: unless-stopped
    healthcheck:
      # FIX: Uses native Node.js fetch instead of curl, which isn't installed in slim images
      test: ["CMD-SHELL", "node -e \"fetch('http://localhost:3001/health').then(r => r.ok ? process.exit(0) : process.exit(1)).catch(() => process.exit(1))\""]
      interval: 30s
      timeout: 10s
      retries: 3

  # ────────────────────────────────────────────────────────────────────
  # Frontend — React/Vite (served via Nginx)
  # ────────────────────────────────────────────────────────────────────
  frontend:
    build:
      context: ./frontend
      dockerfile: Dockerfile
    container_name: aigen-lecture-frontend
    ports:
      - "80:80"
    depends_on:
      - backend
    restart: unless-stopped

volumes:
  postgres-data:
  video-output:
```

---

## 2. `video-service/Dockerfile`
Builds the Node.js application containing Remotion. Includes FFmpeg and Chromium dependencies.

```dockerfile
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

# Ensure output directory exists for video/audio[cite: 2]
RUN mkdir -p out/audio

# Expose port (standardized to 3000 based on typical configuration)
EXPOSE 3000

# Start the compiled server[cite: 2]
CMD ["node", "dist/server.js"]
```

---

## 3. `video-service/.dockerignore`
Files to ignore during the video service Docker build.

```text
node_modules
dist
out
*.log
.env
.env.*
```

---

## 4. `backend/Dockerfile`
Builds the Spring Boot Java application using Maven.

```dockerfile
# backend/Dockerfile
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
RUN ./mvnw dependency:go-offline -B
COPY src ./src
RUN ./mvnw clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

---

## 5. `frontend/Dockerfile`
Builds the React/Vite web application and serves it via Nginx.

```dockerfile
# frontend/Dockerfile
FROM node:20-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
```
