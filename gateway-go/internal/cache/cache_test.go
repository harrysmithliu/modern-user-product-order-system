package cache

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"errors"
	"testing"
	"time"

	"github.com/redis/go-redis/v9"
)

type fakeRedis struct {
	values      map[string]string
	current     int64
	ttl         time.Duration
	getCalls    []string
	incrCalls   []string
	ttlCalls    []string
	expireCalls []expireCall
	err         error
}

type expireCall struct {
	key string
	ttl time.Duration
}

func (f *fakeRedis) Get(ctx context.Context, key string) *redis.StringCmd {
	f.getCalls = append(f.getCalls, key)
	cmd := redis.NewStringCmd(ctx, "get", key)
	if f.err != nil {
		cmd.SetErr(f.err)
		return cmd
	}
	value, ok := f.values[key]
	if !ok {
		cmd.SetErr(redis.Nil)
		return cmd
	}
	cmd.SetVal(value)
	return cmd
}

func (f *fakeRedis) Incr(ctx context.Context, key string) *redis.IntCmd {
	f.incrCalls = append(f.incrCalls, key)
	cmd := redis.NewIntCmd(ctx, "incr", key)
	if f.err != nil {
		cmd.SetErr(f.err)
		return cmd
	}
	cmd.SetVal(f.current)
	return cmd
}

func (f *fakeRedis) TTL(ctx context.Context, key string) *redis.DurationCmd {
	f.ttlCalls = append(f.ttlCalls, key)
	cmd := redis.NewDurationCmd(ctx, f.ttl, "ttl", key)
	if f.err != nil {
		cmd.SetErr(f.err)
		return cmd
	}
	cmd.SetVal(f.ttl)
	return cmd
}

func (f *fakeRedis) Expire(ctx context.Context, key string, expiration time.Duration) *redis.BoolCmd {
	f.expireCalls = append(f.expireCalls, expireCall{key: key, ttl: expiration})
	cmd := redis.NewBoolCmd(ctx, "expire", key, expiration)
	if f.err != nil {
		cmd.SetErr(f.err)
		return cmd
	}
	cmd.SetVal(true)
	return cmd
}

func TestBlacklistKeyMatchesPythonGateway(t *testing.T) {
	sum := sha256.Sum256([]byte("token-123"))
	expected := "user-service:blacklist:" + hex.EncodeToString(sum[:])

	if got := BlacklistKey("token-123"); got != expected {
		t.Fatalf("unexpected blacklist key: %q", got)
	}
}

func TestIsTokenBlacklistedUsesSharedKey(t *testing.T) {
	client := &fakeRedis{values: map[string]string{BlacklistKey("token-123"): "1"}}
	cache := NewWithClient(client)

	if !cache.IsTokenBlacklisted(context.Background(), "token-123") {
		t.Fatalf("expected token to be blacklisted")
	}
	if len(client.getCalls) != 1 || client.getCalls[0] != BlacklistKey("token-123") {
		t.Fatalf("unexpected get calls: %#v", client.getCalls)
	}
}

func TestIsTokenBlacklistedDegradesOpenOnRedisError(t *testing.T) {
	cache := NewWithClient(&fakeRedis{err: errors.New("redis down")})

	if cache.IsTokenBlacklisted(context.Background(), "token-123") {
		t.Fatalf("expected redis error to degrade as not blacklisted")
	}
}

func TestIncrementRateLimitSetsExpiryWhenTTLMissing(t *testing.T) {
	client := &fakeRedis{current: 3, ttl: -1 * time.Second}
	cache := NewWithClient(client)

	current := cache.IncrementRateLimit(context.Background(), "gateway:ratelimit:login:127.0.0.1", 60)

	if current == nil || *current != 3 {
		t.Fatalf("unexpected current value: %#v", current)
	}
	if len(client.incrCalls) != 1 || client.incrCalls[0] != "gateway:ratelimit:login:127.0.0.1" {
		t.Fatalf("unexpected incr calls: %#v", client.incrCalls)
	}
	if len(client.ttlCalls) != 1 || client.ttlCalls[0] != "gateway:ratelimit:login:127.0.0.1" {
		t.Fatalf("unexpected ttl calls: %#v", client.ttlCalls)
	}
	if len(client.expireCalls) != 1 {
		t.Fatalf("expected expire call, got %#v", client.expireCalls)
	}
	if client.expireCalls[0].ttl != 60*time.Second {
		t.Fatalf("unexpected expire ttl: %s", client.expireCalls[0].ttl)
	}
}

func TestIncrementRateLimitDegradesOpenOnRedisError(t *testing.T) {
	cache := NewWithClient(&fakeRedis{err: errors.New("redis down")})

	if current := cache.IncrementRateLimit(context.Background(), "key", 60); current != nil {
		t.Fatalf("expected nil on redis error, got %#v", current)
	}
}
