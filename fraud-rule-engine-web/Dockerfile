FROM node:22.22.0-alpine@sha256:e4bf2a82ad0a4037d28035ae71529873c069b13eb0455466ae0bc13363826e34 AS build

WORKDIR /workspace

COPY fraud-rule-engine-web/package.json fraud-rule-engine-web/package-lock.json ./
RUN npm ci

COPY fraud-rule-engine-web/angular.json fraud-rule-engine-web/tsconfig*.json ./
COPY fraud-rule-engine-web/src/ src/
COPY fraud-rule-engine-web/public/ public/
COPY fraud-rule-engine-web/branding/ branding/
RUN npm run build -- --configuration production

FROM caddy:2.10.2-alpine@sha256:4c6e91c6ed0e2fa03efd5b44747b625fec79bc9cd06ac5235a779726618e530d AS runtime

RUN setcap -r /usr/bin/caddy

COPY docker/web/Caddyfile /etc/caddy/Caddyfile
COPY --from=build /workspace/dist/fraud-rule-engine-web/browser/ /srv/

USER 1000:1000

EXPOSE 8080

HEALTHCHECK --interval=10s --timeout=3s --start-period=5s --retries=5 \
	CMD wget -q -O /dev/null http://127.0.0.1:8080/ || exit 1

CMD ["caddy", "run", "--config", "/etc/caddy/Caddyfile", "--adapter", "caddyfile"]
