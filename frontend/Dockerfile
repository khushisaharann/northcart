# syntax=docker/dockerfile:1
# =====================================================================================
# NorthCart frontend image: build the React app with Node, serve the static files
# with nginx. Node is NOT in the final image; it only exists to run "vite build".
#
#   docker build -t northcart-frontend ./frontend
# =====================================================================================

# ---------- stage 1: build ------------------------------------------------------------
FROM node:24-alpine AS build
WORKDIR /app

# Lockfile first: "npm ci" re-runs only when dependencies change.
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund

COPY . .
# Vite bakes VITE_* variables into the JS at BUILD time (the browser has no env vars).
# Default "/api/v1" = same origin: nginx below forwards /api to the backend, so no CORS.
ARG VITE_API_BASE_URL=/api/v1
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}
RUN npm run build

# ---------- stage 2: serve ------------------------------------------------------------
FROM nginx:1.28-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80
HEALTHCHECK --interval=15s --timeout=3s --retries=3 CMD ["wget", "-qO", "/dev/null", "http://127.0.0.1/"]
