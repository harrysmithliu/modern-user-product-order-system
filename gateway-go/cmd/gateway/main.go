package main

import (
	"encoding/json"
	"log"
	"net/http"

	"github.com/prometheus/client_golang/prometheus/promhttp"

	"modern-user-product-order-system/gateway-go/internal/cache"
	"modern-user-product-order-system/gateway-go/internal/config"
	"modern-user-product-order-system/gateway-go/internal/middleware"
	"modern-user-product-order-system/gateway-go/internal/proxy"
	"modern-user-product-order-system/gateway-go/internal/security"
)

func main() {
	cfg := config.Load()
	cacheClient := cache.New(cfg)
	securityService := security.New(cfg, cacheClient)
	proxyHandler := proxy.NewHandler(cfg, cacheClient, securityService)

	mux := http.NewServeMux()
	mux.HandleFunc("/health", healthHandler("UP"))
	mux.HandleFunc("/ready", healthHandler("READY"))
	mux.HandleFunc("/live", healthHandler("LIVE"))
	mux.Handle("/metrics", promhttp.Handler())
	mux.Handle("/api/", proxyHandler)

	handler := middleware.CORS(cfg.CORSOrigins, middleware.Metrics(mux))

	log.Printf("gateway-go listening on %s", cfg.HTTPAddr)
	if err := http.ListenAndServe(cfg.HTTPAddr, handler); err != nil {
		log.Fatalf("gateway-go stopped: %v", err)
	}
}

func healthHandler(status string) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		_ = json.NewEncoder(w).Encode(map[string]string{
			"status":  status,
			"service": "gateway",
		})
	}
}
