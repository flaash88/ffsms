# --- Build ---------------------------------------------------------------
FROM node:22-alpine AS build

WORKDIR /app

COPY package.json package-lock.json* ./
RUN npm ci

COPY tsconfig*.json nest-cli.json ./
COPY src ./src
RUN npm run build && npm prune --omit=dev

# --- Laufzeit ------------------------------------------------------------
FROM node:22-alpine

WORKDIR /app
ENV NODE_ENV=production

# Zeitzonendaten werden fuer den Wochenreport gebraucht: ohne sie faellt
# Intl in Alpine auf UTC zurueck und der Report kaeme zur falschen Stunde.
RUN apk add --no-cache tzdata

COPY --from=build /app/node_modules ./node_modules
COPY --from=build /app/dist ./dist
COPY package.json ./

USER node
EXPOSE 3000

CMD ["node", "dist/main"]
