package middleware

import "testing"

func TestExcludedMetricsPath(t *testing.T) {
	for _, path := range []string{"/health", "/ready", "/live", "/metrics"} {
		if !excludedMetricsPath(path) {
			t.Fatalf("expected %s to be excluded", path)
		}
	}
	if excludedMetricsPath("/api/products") {
		t.Fatalf("expected api path to be measured")
	}
}
