package cache

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"fmt"
	"log"
	"time"

	"github.com/redis/go-redis/v9"

	"modern-user-product-order-system/gateway-go/internal/config"
)

type RedisClient interface {
	Get(ctx context.Context, key string) *redis.StringCmd
	Incr(ctx context.Context, key string) *redis.IntCmd
	TTL(ctx context.Context, key string) *redis.DurationCmd
	Expire(ctx context.Context, key string, expiration time.Duration) *redis.BoolCmd
}

type Cache struct {
	client RedisClient
}

func New(cfg config.Config) *Cache {
	if !cfg.RedisEnabled {
		return &Cache{}
	}
	client := redis.NewClient(&redis.Options{
		Addr:         fmt.Sprintf("%s:%d", cfg.RedisHost, cfg.RedisPort),
		Password:     cfg.RedisPassword,
		DB:           cfg.RedisDB,
		DialTimeout:  time.Second,
		ReadTimeout:  time.Second,
		WriteTimeout: time.Second,
	})
	return &Cache{client: client}
}

func NewWithClient(client RedisClient) *Cache {
	return &Cache{client: client}
}

func BlacklistKey(token string) string {
	sum := sha256.Sum256([]byte(token))
	return "user-service:blacklist:" + hex.EncodeToString(sum[:])
}

func (c *Cache) IsTokenBlacklisted(ctx context.Context, token string) bool {
	if c.client == nil {
		return false
	}
	value, err := c.client.Get(ctx, BlacklistKey(token)).Result()
	if err == redis.Nil {
		return false
	}
	if err != nil {
		log.Printf("Redis check blacklist failed: %v", err)
		return false
	}
	return value != ""
}

func (c *Cache) IncrementRateLimit(ctx context.Context, key string, windowSeconds int) *int {
	if c.client == nil {
		return nil
	}
	current, err := c.client.Incr(ctx, key).Result()
	if err != nil {
		log.Printf("Redis increment rate limit failed: %v", err)
		return nil
	}
	ttl, err := c.client.TTL(ctx, key).Result()
	if err != nil {
		log.Printf("Redis rate limit ttl failed: %v", err)
		return nil
	}
	if ttl < 0 {
		if err := c.client.Expire(ctx, key, time.Duration(windowSeconds)*time.Second).Err(); err != nil {
			log.Printf("Redis rate limit expire failed: %v", err)
			return nil
		}
	}
	value := int(current)
	return &value
}
