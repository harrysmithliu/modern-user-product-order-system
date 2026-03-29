FROM node:20-alpine AS build

WORKDIR /workspace

COPY frontend/package*.json ./frontend/
WORKDIR /workspace/frontend
RUN npm ci

COPY frontend ./ 
ARG VITE_API_BASE_URL=http://localhost:8000
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}
RUN npm run build

FROM nginx:1.27-alpine

COPY infra/docker/frontend/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/frontend/dist /usr/share/nginx/html

EXPOSE 80
