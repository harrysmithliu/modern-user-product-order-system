package config

import (
	"os"
	"strconv"
	"strings"
)

type Config struct {
	HTTPAddr                          string
	JWTSecret                         string
	JWTAlgorithm                      string
	CORSOrigins                       []string
	UserServiceURL                    string
	ProductServiceURL                 string
	OrderServiceURL                   string
	RequestTimeoutSeconds             int
	UpstreamMaxConnections            int
	UpstreamMaxKeepaliveConnections   int
	UpstreamKeepaliveExpirySeconds    int
	RedisEnabled                      bool
	RedisHost                         string
	RedisPort                         int
	RedisDB                           int
	RedisPassword                     string
	LoginRateLimitMaxRequests         int
	LoginRateLimitWindowSeconds       int
	OrderCreateRateLimitMaxRequests   int
	OrderCreateRateLimitWindowSeconds int
}

func Load() Config {
	return Config{
		HTTPAddr:                          getString("GATEWAY_HTTP_ADDR", ":8000"),
		JWTSecret:                         getString("GATEWAY_JWT_SECRET", "change-me-in-env"),
		JWTAlgorithm:                      getString("GATEWAY_JWT_ALGORITHM", "HS256"),
		CORSOrigins:                       getCSV("GATEWAY_CORS_ORIGINS", []string{"http://localhost:5173", "http://127.0.0.1:5173"}),
		UserServiceURL:                    getString("GATEWAY_USER_SERVICE_URL", "http://localhost:8001"),
		ProductServiceURL:                 getString("GATEWAY_PRODUCT_SERVICE_URL", "http://localhost:8002"),
		OrderServiceURL:                   getString("GATEWAY_ORDER_SERVICE_URL", "http://localhost:8080"),
		RequestTimeoutSeconds:             getInt("GATEWAY_REQUEST_TIMEOUT_SECONDS", 15),
		UpstreamMaxConnections:            getInt("GATEWAY_UPSTREAM_MAX_CONNECTIONS", 2048),
		UpstreamMaxKeepaliveConnections:   getInt("GATEWAY_UPSTREAM_MAX_KEEPALIVE_CONNECTIONS", 512),
		UpstreamKeepaliveExpirySeconds:    getInt("GATEWAY_UPSTREAM_KEEPALIVE_EXPIRY_SECONDS", 30),
		RedisEnabled:                      getBool("GATEWAY_REDIS_ENABLED", true),
		RedisHost:                         getString("GATEWAY_REDIS_HOST", "localhost"),
		RedisPort:                         getInt("GATEWAY_REDIS_PORT", 6379),
		RedisDB:                           getInt("GATEWAY_REDIS_DB", 0),
		RedisPassword:                     getString("GATEWAY_REDIS_PASSWORD", ""),
		LoginRateLimitMaxRequests:         getInt("GATEWAY_LOGIN_RATE_LIMIT_MAX_REQUESTS", 10),
		LoginRateLimitWindowSeconds:       getInt("GATEWAY_LOGIN_RATE_LIMIT_WINDOW_SECONDS", 60),
		OrderCreateRateLimitMaxRequests:   getInt("GATEWAY_ORDER_CREATE_RATE_LIMIT_MAX_REQUESTS", 20),
		OrderCreateRateLimitWindowSeconds: getInt("GATEWAY_ORDER_CREATE_RATE_LIMIT_WINDOW_SECONDS", 60),
	}
}

func getString(key string, fallback string) string {
	value, ok := os.LookupEnv(key)
	if !ok {
		return fallback
	}
	return value
}

func getInt(key string, fallback int) int {
	value, ok := os.LookupEnv(key)
	if !ok || strings.TrimSpace(value) == "" {
		return fallback
	}
	parsed, err := strconv.Atoi(value)
	if err != nil {
		return fallback
	}
	return parsed
}

func getBool(key string, fallback bool) bool {
	value, ok := os.LookupEnv(key)
	if !ok || strings.TrimSpace(value) == "" {
		return fallback
	}
	parsed, err := strconv.ParseBool(value)
	if err != nil {
		return fallback
	}
	return parsed
}

func getCSV(key string, fallback []string) []string {
	value, ok := os.LookupEnv(key)
	if !ok || strings.TrimSpace(value) == "" {
		return append([]string(nil), fallback...)
	}
	parts := strings.Split(value, ",")
	result := make([]string, 0, len(parts))
	for _, part := range parts {
		item := strings.TrimSpace(part)
		if item != "" {
			result = append(result, item)
		}
	}
	if len(result) == 0 {
		return append([]string(nil), fallback...)
	}
	return result
}
