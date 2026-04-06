# Kubernetes Production Notes

This directory is reserved for the next step after the sandbox baseline:

- production-grade manifests or Helm charts
- stronger secret handling
- replica and autoscaling tuning
- ingress annotations for cloud load balancers
- environment-specific image tags

## Planned Production Enhancements

- separate image tags from sandbox
- managed secret injection
- dedicated ingress and TLS annotations
- stronger resource requests and limits
- rollout strategy tuning
- HPA based on real workload metrics
- monitoring sidecar or exporter integration

## Relationship to Sandbox

The current Phase 3 implementation starts with:

- `infra/k8s/sandbox`

That manifest set is intentionally simpler and local-cluster friendly.

`infra/k8s/prod` is where the stricter production flavor should evolve next.
