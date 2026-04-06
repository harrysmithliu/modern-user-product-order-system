# Monitoring Bootstrap

This directory contains the initial monitoring assets for Batch D / Phase 3.

Current scope:

- Prometheus bootstrap configuration
- Grafana datasource provisioning
- Grafana dashboard provisioning
- a starter dashboard JSON for service uptime and sandbox health visibility
- a dedicated monitoring Compose file under `infra/docker/docker-compose.monitoring.yml`

At the current stage, these files are intended as a baseline for:

- local Docker-based observability experiments
- later Kubernetes ConfigMap mounting
- future sandbox and production monitoring rollout

## Contents

- `prometheus/prometheus.yml`
  - starter scrape config
- `grafana/provisioning/datasources/datasource.yml`
  - default Prometheus datasource
- `grafana/provisioning/dashboards/dashboard.yml`
  - dashboard auto-loading
- `grafana/dashboards/modern-upo-overview.json`
  - starter dashboard for service availability and request-rate style metrics

## Notes

- The current Batch D baseline exposes Prometheus metrics from the sandbox gateway, user-service, product-service, and order-service.
- Grafana automatically provisions the Prometheus datasource and the starter dashboard on first boot.
- The dashboard is safe to evolve incrementally as more service-level metrics are added.
- Health endpoints remain useful for readiness and liveness, but Prometheus now provides the primary request and latency baseline for sandbox observation.

## Local Run

From the repository root:

```bash
docker compose --env-file infra/docker/.env.monitoring.example -f infra/docker/docker-compose.monitoring.yml up -d
```

Default local entrypoints:

- Prometheus: `http://localhost:9090`
- Grafana: `http://localhost:3000`

Default Grafana credentials:

- username: `admin`
- password: `admin123`

The current Prometheus bootstrap scrapes service metrics through host-mapped ports.

Sandbox targets:

- `localhost:8000`
- `localhost:8001`
- `localhost:8002`
- `localhost:8080`

Dev targets:

- `localhost:8010`
- `localhost:8011`
- `localhost:8012`
- `localhost:8081`

## Verified Batch D State

Validated against the sandbox stack:

- Prometheus container healthy and ready
- Grafana API healthy
- Prometheus targets `up` for:
  - `localhost:8000`
  - `localhost:8001`
  - `localhost:8002`
  - `localhost:8080`

The dev targets remain in the scrape config intentionally, and may report `down` whenever no local dev services are running.
