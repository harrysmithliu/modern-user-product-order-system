# Monitoring Bootstrap

This directory contains the initial monitoring assets for Batch D / Phase 3.

Current scope:

- Prometheus bootstrap configuration
- Grafana datasource provisioning
- Grafana dashboard provisioning
- a starter dashboard JSON for service uptime and sandbox health visibility

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

- The project does not yet expose a full Prometheus metrics surface from every service.
- The dashboard is prepared for the next monitoring iteration and is safe to evolve incrementally.
- Health endpoints remain useful for readiness and liveness, but true metric scraping will be expanded in later Phase 3 work.
