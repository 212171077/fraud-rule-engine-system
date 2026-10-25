---
applyTo: "Dockerfile,compose.yaml,**/*.yml,**/*.yaml,monitoring/**,.github/workflows/**"
---

# Infrastructure conventions

- Pin container images and GitHub Actions to immutable versions/SHAs. Keep workflow permissions least-privilege.
- Keep `.env` local and ignored; `.env.example` contains obvious local-only placeholders, never production credentials.
- Bind published local service ports to loopback. Preserve the named PostgreSQL volume unless explicitly approved for deletion.
- Keep Actuator health details hidden and restrict metrics scraping to the dedicated monitoring source; do not enable anonymous scraping from application or host networks.
- Use only bounded-cardinality operational metrics and maintain Prometheus/Grafana provisioning as version-controlled configuration.
- Validate with `docker compose --env-file .env.example config --quiet`; build with `docker build --tag fraud-rule-engine:ci .`.
- Document changes, local assumptions, security boundaries, and operational consequences.
