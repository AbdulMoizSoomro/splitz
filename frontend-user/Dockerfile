# =============================================================================
# SPLITZ Frontend User Dockerfile
# Optimized for Development and Production
# =============================================================================

# -----------------------------------------------------------------------------
# Stage 1: Dependencies
# -----------------------------------------------------------------------------
FROM node:22-alpine AS deps
WORKDIR /app
COPY frontend-user/package*.json ./
RUN npm ci

# -----------------------------------------------------------------------------
# Stage 2: Development (For live-reloading with bind mounts)
# -----------------------------------------------------------------------------
FROM node:22-alpine AS dev
WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
COPY frontend-user/package*.json ./
# Bind mounts will provide the rest of the source
CMD ["npm", "run", "dev", "--", "--host"]

# -----------------------------------------------------------------------------
# Stage 3: Builder (For production build)
# -----------------------------------------------------------------------------
FROM deps AS builder
WORKDIR /app
COPY frontend-user/ ./
ARG VITE_USER_SERVICE_URL
ENV VITE_USER_SERVICE_URL=$VITE_USER_SERVICE_URL
ARG VITE_EXPENSE_SERVICE_URL
ENV VITE_EXPENSE_SERVICE_URL=$VITE_EXPENSE_SERVICE_URL
# Skip lint during build if needed, or keep it if production builds must be clean
RUN npm run build

# -----------------------------------------------------------------------------
# Stage 4: Runtime
# -----------------------------------------------------------------------------
FROM nginx:alpine AS runtime
COPY --from=builder /app/dist /usr/share/nginx/html
RUN echo 'server { \
    listen 80; \
    location / { \
        root /usr/share/nginx/html; \
        index index.html index.htm; \
        try_files $uri $uri/ /index.html; \
    } \
}' > /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
