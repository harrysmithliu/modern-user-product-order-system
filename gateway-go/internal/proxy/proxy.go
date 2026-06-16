package proxy

import (
	"context"
	"errors"
	"fmt"
	"io"
	"net"
	"net/http"
	"net/url"
	"regexp"
	"strings"
	"time"

	"modern-user-product-order-system/gateway-go/internal/config"
	"modern-user-product-order-system/gateway-go/internal/security"
)

var publicProductDetailPattern = regexp.MustCompile(`^/api/products/\d+$`)

var (
	ErrRouteNotFound       = errors.New("Route not found")
	ErrAuthentication      = errors.New("Authentication required")
	ErrAdminRoleRequired   = errors.New("Admin role required")
	ErrLoginRateLimited    = errors.New("Too many login attempts. Please try again later.")
	ErrOrderRateLimited    = errors.New("Order submission rate limit exceeded. Please retry shortly.")
	ErrUpstreamClientReady = errors.New("Gateway upstream client not ready")
)

type RateLimiter interface {
	IncrementRateLimit(ctx context.Context, key string, windowSeconds int) *int
}

type SecurityService interface {
	GetCurrentUser(ctx context.Context, authorization string) (*security.CurrentUser, error)
	GetOptionalCurrentUser(ctx context.Context, authorization string) (*security.CurrentUser, error)
}

type Handler struct {
	cfg      config.Config
	limiter  RateLimiter
	security SecurityService
	client   *http.Client
}

func NewHandler(cfg config.Config, limiter RateLimiter, securityService SecurityService) *Handler {
	return &Handler{
		cfg:      cfg,
		limiter:  limiter,
		security: securityService,
		client:   newHTTPClient(cfg),
	}
}

func newHTTPClient(cfg config.Config) *http.Client {
	transport := &http.Transport{
		MaxConnsPerHost:     cfg.UpstreamMaxConnections,
		MaxIdleConnsPerHost: cfg.UpstreamMaxKeepaliveConnections,
		IdleConnTimeout:     time.Duration(cfg.UpstreamKeepaliveExpirySeconds) * time.Second,
	}
	return &http.Client{
		Timeout:   time.Duration(cfg.RequestTimeoutSeconds) * time.Second,
		Transport: transport,
	}
}

func ResolveTarget(cfg config.Config, path string) (string, error) {
	switch {
	case strings.HasPrefix(path, "/api/auth"):
		return joinTarget(cfg.UserServiceURL, stripAPIPrefix(path)), nil
	case strings.HasPrefix(path, "/api/users"), strings.HasPrefix(path, "/api/admin/users"):
		return joinTarget(cfg.UserServiceURL, stripAPIPrefix(path)), nil
	case strings.HasPrefix(path, "/api/products"), strings.HasPrefix(path, "/api/admin/products"):
		return joinTarget(cfg.ProductServiceURL, stripAPIPrefix(path)), nil
	case strings.HasPrefix(path, "/api/orders"), strings.HasPrefix(path, "/api/admin/orders"):
		return joinTarget(cfg.OrderServiceURL, stripAPIPrefix(path)), nil
	default:
		return "", ErrRouteNotFound
	}
}

func IsPublicRoute(method string, path string) bool {
	if path == "/api/auth/login" {
		return true
	}
	if method == http.MethodGet && path == "/api/products" {
		return true
	}
	if method == http.MethodGet && publicProductDetailPattern.MatchString(path) {
		return true
	}
	return path == "/health" || path == "/ready" || path == "/live"
}

func EnforceRole(path string, currentUser *security.CurrentUser) error {
	if !strings.Contains(path, "/api/admin/") {
		return nil
	}
	if currentUser == nil {
		return ErrAuthentication
	}
	if currentUser.Role != "ADMIN" {
		return ErrAdminRoleRequired
	}
	return nil
}

func (h *Handler) ApplyRateLimit(r *http.Request, currentUser *security.CurrentUser) error {
	if h.limiter == nil {
		return nil
	}
	if r.Method == http.MethodPost && r.URL.Path == "/api/auth/login" {
		key := fmt.Sprintf("gateway:ratelimit:login:%s", clientIP(r))
		current := h.limiter.IncrementRateLimit(r.Context(), key, h.cfg.LoginRateLimitWindowSeconds)
		if current != nil && *current > h.cfg.LoginRateLimitMaxRequests {
			return ErrLoginRateLimited
		}
		return nil
	}
	if r.Method == http.MethodPost && r.URL.Path == "/api/orders" {
		principal := fmt.Sprintf("ip:%s", clientIP(r))
		if currentUser != nil {
			principal = fmt.Sprintf("user:%d", currentUser.UserID)
		}
		key := fmt.Sprintf("gateway:ratelimit:order-create:%s", principal)
		current := h.limiter.IncrementRateLimit(r.Context(), key, h.cfg.OrderCreateRateLimitWindowSeconds)
		if current != nil && *current > h.cfg.OrderCreateRateLimitMaxRequests {
			return ErrOrderRateLimited
		}
	}
	return nil
}

func (h *Handler) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	if h.client == nil {
		writeError(w, http.StatusServiceUnavailable, ErrUpstreamClientReady.Error())
		return
	}
	currentUser, err := h.currentUser(r)
	if err != nil {
		writeSecurityError(w, err)
		return
	}
	if err := EnforceRole(r.URL.Path, currentUser); err != nil {
		writeGatewayError(w, err)
		return
	}
	if err := h.ApplyRateLimit(r, currentUser); err != nil {
		writeGatewayError(w, err)
		return
	}
	h.forward(w, r, currentUser)
}

func (h *Handler) currentUser(r *http.Request) (*security.CurrentUser, error) {
	if h.security == nil {
		return nil, nil
	}
	authorization := r.Header.Get("Authorization")
	if IsPublicRoute(r.Method, r.URL.Path) {
		return h.security.GetOptionalCurrentUser(r.Context(), authorization)
	}
	return h.security.GetCurrentUser(r.Context(), authorization)
}

func (h *Handler) forward(w http.ResponseWriter, r *http.Request, currentUser *security.CurrentUser) {
	target, err := ResolveTarget(h.cfg, r.URL.Path)
	if err != nil {
		writeGatewayError(w, err)
		return
	}
	targetURL, err := url.Parse(target)
	if err != nil {
		writeError(w, http.StatusBadGateway, "Upstream request error: InvalidURL")
		return
	}
	targetURL.RawQuery = r.URL.RawQuery
	req, err := http.NewRequestWithContext(r.Context(), r.Method, targetURL.String(), r.Body)
	if err != nil {
		writeError(w, http.StatusBadGateway, "Upstream request error: RequestBuildError")
		return
	}
	copyRequestHeaders(req.Header, r.Header)
	if currentUser != nil {
		req.Header.Set("x-user-id", fmt.Sprintf("%d", currentUser.UserID))
		req.Header.Set("x-username", currentUser.Username)
		req.Header.Set("x-user-role", currentUser.Role)
	}
	resp, err := h.client.Do(req)
	if err != nil {
		if errors.Is(err, context.DeadlineExceeded) || isNetTimeout(err) {
			writeError(w, http.StatusGatewayTimeout, fmt.Sprintf("Upstream timeout: %T", err))
			return
		}
		writeError(w, http.StatusBadGateway, fmt.Sprintf("Upstream request error: %T", err))
		return
	}
	defer resp.Body.Close()

	copyResponseHeaders(w.Header(), resp.Header)
	w.WriteHeader(resp.StatusCode)
	_, _ = io.Copy(w, resp.Body)
}

func stripAPIPrefix(path string) string {
	return strings.Replace(path, "/api", "", 1)
}

func joinTarget(baseURL string, path string) string {
	return strings.TrimRight(baseURL, "/") + path
}

func clientIP(r *http.Request) string {
	host, _, err := net.SplitHostPort(r.RemoteAddr)
	if err == nil && host != "" {
		return host
	}
	if r.RemoteAddr != "" {
		return r.RemoteAddr
	}
	return "unknown"
}

func copyRequestHeaders(dst http.Header, src http.Header) {
	for key, values := range src {
		if strings.EqualFold(key, "host") {
			continue
		}
		for _, value := range values {
			dst.Add(key, value)
		}
	}
}

func copyResponseHeaders(dst http.Header, src http.Header) {
	excluded := map[string]struct{}{
		"content-encoding":  {},
		"transfer-encoding": {},
		"connection":        {},
	}
	for key, values := range src {
		if _, ok := excluded[strings.ToLower(key)]; ok {
			continue
		}
		for _, value := range values {
			dst.Add(key, value)
		}
	}
}

func writeSecurityError(w http.ResponseWriter, err error) {
	switch {
	case errors.Is(err, security.ErrAuthorizationBearerRequired):
		writeError(w, http.StatusUnauthorized, security.ErrAuthorizationBearerRequired.Error())
	case errors.Is(err, security.ErrInvalidAccessToken):
		writeError(w, http.StatusUnauthorized, security.ErrInvalidAccessToken.Error())
	case errors.Is(err, security.ErrAccessTokenRevoked):
		writeError(w, http.StatusUnauthorized, security.ErrAccessTokenRevoked.Error())
	default:
		writeError(w, http.StatusUnauthorized, "Invalid access token")
	}
}

func writeGatewayError(w http.ResponseWriter, err error) {
	switch {
	case errors.Is(err, ErrRouteNotFound):
		writeError(w, http.StatusNotFound, ErrRouteNotFound.Error())
	case errors.Is(err, ErrAuthentication):
		writeError(w, http.StatusUnauthorized, ErrAuthentication.Error())
	case errors.Is(err, ErrAdminRoleRequired):
		writeError(w, http.StatusForbidden, ErrAdminRoleRequired.Error())
	case errors.Is(err, ErrLoginRateLimited):
		writeError(w, http.StatusTooManyRequests, ErrLoginRateLimited.Error())
	case errors.Is(err, ErrOrderRateLimited):
		writeError(w, http.StatusTooManyRequests, ErrOrderRateLimited.Error())
	default:
		writeError(w, http.StatusInternalServerError, err.Error())
	}
}

func writeError(w http.ResponseWriter, statusCode int, detail string) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(statusCode)
	_, _ = fmt.Fprintf(w, `{"detail":%q}`, detail)
}

func isNetTimeout(err error) bool {
	var netErr net.Error
	return errors.As(err, &netErr) && netErr.Timeout()
}
