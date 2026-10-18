FROM node:22-alpine AS build
WORKDIR /app
COPY frontend/biuro-frontend/package*.json ./
RUN npm ci
COPY frontend/biuro-frontend/ ./
ARG VITE_API_BASE_URL=/
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
RUN npm run build

FROM nginx:stable-alpine
COPY deploy/nginx/biuro-frontend.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
