# ---- Build stage ----
FROM node:20-alpine AS build
WORKDIR /app
COPY merchant-web/package.json merchant-web/package-lock.json ./
RUN npm ci
COPY merchant-web/ .
RUN npm run build

# ---- Runtime stage ----
FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY docker/merchant-web/nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
