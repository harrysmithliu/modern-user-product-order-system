package proxy

import (
	"context"
	"errors"
	"io"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"modern-user-product-order-system/gateway-go/internal/config"
	"modern-user-product-order-system/gateway-go/internal/security"
)

type fakeLimiter struct {
	current *int
	key     string
	window  int
}

func (f *fakeLimiter) IncrementRateLimit(ctx context.Context, key string, windowSeconds int) *int {
	f.key = key
	f.window = windowSeconds
	return f.current
}

type fakeSecurity struct {
	user *security.CurrentUser
	err  error
}

type roundTripFunc func(*http.Request) (*http.Response, error)

func (f roundTripFunc) RoundTrip(r *http.Request) (*http.Response, error) {
	return f(r)
}

func (f *fakeSecurity) GetCurrentUser(ctx context.Context, authorization string) (*security.CurrentUser, error) {
	return f.user, f.err
}

func (f *fakeSecurity) GetOptionalCurrentUser(ctx context.Context, authorization string) (*security.CurrentUser, error) {
	return f.user, f.err
}

func testConfig() config.Config {
	return config.Config{
		UserServiceURL:                    "http://user-service:8001",
		ProductServiceURL:                 "http://product-service:8002",
		OrderServiceURL:                   "http://order-service:8080",
		RequestTimeoutSeconds:             15,
		UpstreamMaxConnections:            2048,
		UpstreamMaxKeepaliveConnections:   512,
		UpstreamKeepaliveExpirySeconds:    30,
		LoginRateLimitMaxRequests:         2,
		LoginRateLimitWindowSeconds:       60,
		OrderCreateRateLimitMaxRequests:   20,
		OrderCreateRateLimitWindowSeconds: 60,
	}
}

func intPtr(value int) *int {
	return &value
}

func TestResolveTargetStripsAPIPrefix(t *testing.T) {
	cfg := testConfig()
	cases := map[string]string{
		"/api/auth/login":          "http://user-service:8001/auth/login",
		"/api/users/me":            "http://user-service:8001/users/me",
		"/api/admin/users":         "http://user-service:8001/admin/users",
		"/api/products/1":          "http://product-service:8002/products/1",
		"/api/admin/products":      "http://product-service:8002/admin/products",
		"/api/orders":              "http://order-service:8080/orders",
		"/api/admin/orders?page=1": "http://order-service:8080/admin/orders?page=1",
	}
	for path, expected := range cases {
		got, err := ResolveTarget(cfg, path)
		if err != nil {
			t.Fatalf("ResolveTarget(%q) err: %v", path, err)
		}
		if got != expected {
			t.Fatalf("ResolveTarget(%q) = %q, want %q", path, got, expected)
		}
	}
}

func TestIsPublicRouteMatchesPythonGateway(t *testing.T) {
	if !IsPublicRoute(http.MethodPost, "/api/auth/login") {
		t.Fatalf("expected login public")
	}
	if !IsPublicRoute(http.MethodGet, "/api/products") {
		t.Fatalf("expected product list public")
	}
	if !IsPublicRoute(http.MethodGet, "/api/products/123") {
		t.Fatalf("expected product detail public")
	}
	if IsPublicRoute(http.MethodGet, "/api/products/me/coupons") {
		t.Fatalf("expected user coupon route protected")
	}
}

func TestEnforceRoleRequiresAdmin(t *testing.T) {
	if err := EnforceRole("/api/admin/orders", nil); !errors.Is(err, ErrAuthentication) {
		t.Fatalf("expected authentication err, got %v", err)
	}
	err := EnforceRole("/api/admin/orders", &security.CurrentUser{Role: "USER"})
	if !errors.Is(err, ErrAdminRoleRequired) {
		t.Fatalf("expected admin err, got %v", err)
	}
}

func TestApplyRateLimitBlocksLoginAfterThreshold(t *testing.T) {
	limiter := &fakeLimiter{current: intPtr(3)}
	handler := NewHandler(testConfig(), limiter, nil)
	req := httptest.NewRequest(http.MethodPost, "/api/auth/login", nil)
	req.RemoteAddr = "127.0.0.1:50000"

	err := handler.ApplyRateLimit(req, nil)

	if !errors.Is(err, ErrLoginRateLimited) {
		t.Fatalf("expected login rate limit err, got %v", err)
	}
	if limiter.key != "gateway:ratelimit:login:127.0.0.1" {
		t.Fatalf("unexpected key: %s", limiter.key)
	}
}

func TestApplyRateLimitUsesUserScopeForOrderCreation(t *testing.T) {
	limiter := &fakeLimiter{current: intPtr(1)}
	handler := NewHandler(testConfig(), limiter, nil)
	req := httptest.NewRequest(http.MethodPost, "/api/orders", nil)
	req.RemoteAddr = "127.0.0.1:50000"
	user := &security.CurrentUser{UserID: 42, Username: "john_smith", Role: "USER"}

	err := handler.ApplyRateLimit(req, user)

	if err != nil {
		t.Fatalf("unexpected err: %v", err)
	}
	if limiter.key != "gateway:ratelimit:order-create:user:42" {
		t.Fatalf("unexpected key: %s", limiter.key)
	}
	if limiter.window != 60 {
		t.Fatalf("unexpected window: %d", limiter.window)
	}
}

func TestForwardInjectsCurrentUserHeaders(t *testing.T) {
	cfg := testConfig()
	cfg.OrderServiceURL = "http://order-service:8080"
	handler := NewHandler(cfg, nil, &fakeSecurity{user: &security.CurrentUser{UserID: 42, Username: "john_smith", Role: "USER"}})
	handler.client.Transport = roundTripFunc(func(r *http.Request) (*http.Response, error) {
		if r.Header.Get("x-user-id") != "42" {
			t.Fatalf("missing x-user-id: %#v", r.Header)
		}
		if r.Header.Get("x-username") != "john_smith" {
			t.Fatalf("missing x-username")
		}
		if r.Header.Get("x-user-role") != "USER" {
			t.Fatalf("missing x-user-role")
		}
		if r.URL.String() != "http://order-service:8080/orders/my?page=1" {
			t.Fatalf("unexpected upstream url: %s", r.URL.String())
		}
		return &http.Response{
			StatusCode: http.StatusOK,
			Header:     http.Header{"Content-Type": []string{"application/json"}},
			Body:       io.NopCloser(strings.NewReader(`{"ok":true}`)),
			Request:    r,
		}, nil
	})
	req := httptest.NewRequest(http.MethodGet, "/api/orders/my?page=1", nil)
	rec := httptest.NewRecorder()

	handler.ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("unexpected status: %d body=%s", rec.Code, rec.Body.String())
	}
	if rec.Body.String() != `{"ok":true}` {
		t.Fatalf("unexpected body: %s", rec.Body.String())
	}
}
