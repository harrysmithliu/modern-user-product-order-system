package config

import (
	"reflect"
	"testing"
)

func TestLoadUsesDefaults(t *testing.T) {
	t.Setenv("GATEWAY_HTTP_ADDR", "")
	cfg := Load()

	if cfg.JWTSecret != "change-me-in-env" {
		t.Fatalf("unexpected JWTSecret: %q", cfg.JWTSecret)
	}
	if cfg.UserServiceURL != "http://localhost:8001" {
		t.Fatalf("unexpected UserServiceURL: %q", cfg.UserServiceURL)
	}
	if cfg.RedisEnabled != true {
		t.Fatalf("expected RedisEnabled default true")
	}
	if cfg.LoginRateLimitMaxRequests != 10 {
		t.Fatalf("unexpected login max requests: %d", cfg.LoginRateLimitMaxRequests)
	}
	if !reflect.DeepEqual(cfg.CORSOrigins, []string{"http://localhost:5173", "http://127.0.0.1:5173"}) {
		t.Fatalf("unexpected CORSOrigins: %#v", cfg.CORSOrigins)
	}
}

func TestLoadUsesEnvironmentOverrides(t *testing.T) {
	t.Setenv("GATEWAY_HTTP_ADDR", ":9000")
	t.Setenv("GATEWAY_JWT_SECRET", "secret")
	t.Setenv("GATEWAY_CORS_ORIGINS", "http://a.local, http://b.local ")
	t.Setenv("GATEWAY_REDIS_ENABLED", "false")
	t.Setenv("GATEWAY_REDIS_PORT", "6380")
	t.Setenv("GATEWAY_ORDER_CREATE_RATE_LIMIT_MAX_REQUESTS", "99")

	cfg := Load()

	if cfg.HTTPAddr != ":9000" {
		t.Fatalf("unexpected HTTPAddr: %q", cfg.HTTPAddr)
	}
	if cfg.JWTSecret != "secret" {
		t.Fatalf("unexpected JWTSecret: %q", cfg.JWTSecret)
	}
	if cfg.RedisEnabled {
		t.Fatalf("expected RedisEnabled false")
	}
	if cfg.RedisPort != 6380 {
		t.Fatalf("unexpected RedisPort: %d", cfg.RedisPort)
	}
	if cfg.OrderCreateRateLimitMaxRequests != 99 {
		t.Fatalf("unexpected order create max requests: %d", cfg.OrderCreateRateLimitMaxRequests)
	}
	if !reflect.DeepEqual(cfg.CORSOrigins, []string{"http://a.local", "http://b.local"}) {
		t.Fatalf("unexpected CORSOrigins: %#v", cfg.CORSOrigins)
	}
}
