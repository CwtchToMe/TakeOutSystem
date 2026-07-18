# ---- Build stage ----
FROM node:20-alpine AS build
WORKDIR /app
COPY h5/package.json h5/package-lock.json ./
RUN npm ci
COPY h5/ .
RUN npm run build

# ---- Runtime stage ----
FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY docker/h5/nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
