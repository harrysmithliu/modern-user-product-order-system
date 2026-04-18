# Redis Troubleshooting Guide

This guide is written for the Redis usage patterns in this repository. The examples assume a local Redis instance on `127.0.0.1:6380` and database `db0`, which matches the Redis browser shown in the workspace.

## 1. Connect First

If Redis has no password:

```bash
redis-cli -h 127.0.0.1 -p 6380
```

If Redis requires a password:

```bash
redis-cli -h 127.0.0.1 -p 6380 -a 'your-password'
```

Useful first checks after connecting:

```bash
PING
DBSIZE
INFO keyspace
SELECT 0
```

## 2. Command Safety

Prefer `SCAN` over `KEYS` when the dataset is non-trivial.

Safe pattern search:

```bash
SCAN 0 MATCH product-service:* COUNT 100
SCAN 0 MATCH gateway:ratelimit:* COUNT 100
SCAN 0 MATCH user-service:blacklist:* COUNT 100
```

`KEYS` is fine only for quick local inspection:

```bash
KEYS product-service:*
```

Avoid destructive commands on shared or production Redis unless you are sure:

```bash
FLUSHDB
FLUSHALL
DEL some:key
```

## 3. Check Product Cache

### 3.1 What to look for

The product service uses these main Redis keys:

- `product-service:catalog:version`
- `product-service:catalog:v{version}:list:page={page}:size={size}:keyword={encoded}:include_off_sale={0|1}`
- `product-service:catalog:v{version}:{public|internal}:product:{product_id}`

### 3.2 Find the current version

```bash
GET product-service:catalog:version
TTL product-service:catalog:version
TYPE product-service:catalog:version
MEMORY USAGE product-service:catalog:version
```

If the value is `11`, the current catalog cache namespace is `v11`.

### 3.3 List cached product keys

```bash
SCAN 0 MATCH product-service:catalog:v* COUNT 100
```

Examples you may see:

```bash
GET "product-service:catalog:v11:list:page=1:size=10:keyword=:include_off_sale=0"
GET "product-service:catalog:v11:public:product:1"
GET "product-service:catalog:v11:internal:product:1"
```

### 3.4 Inspect a cached value

```bash
TYPE "product-service:catalog:v11:public:product:1"
TTL "product-service:catalog:v11:public:product:1"
GET "product-service:catalog:v11:public:product:1"
MEMORY USAGE "product-service:catalog:v11:public:product:1"
```

For a list cache, the value is JSON. For a detail cache, the value is also JSON.

### 3.5 When cache misses happen

If a product update happens in the service, the version key is incremented:

```bash
INCR product-service:catalog:version
```

That means old keys are not deleted immediately. They simply become obsolete because new requests start using the new version prefix.

## 4. Check Rate Limiting

The gateway uses Redis counters for:

- login rate limiting
- order submission rate limiting

### 4.1 Login limit

Key pattern:

```text
gateway:ratelimit:login:{client_ip}
```

Useful checks:

```bash
SCAN 0 MATCH gateway:ratelimit:login:* COUNT 100
GET gateway:ratelimit:login:127.0.0.1
TTL gateway:ratelimit:login:127.0.0.1
TYPE gateway:ratelimit:login:127.0.0.1
MEMORY USAGE gateway:ratelimit:login:127.0.0.1
```

### 4.2 Order-create limit

Key pattern:

```text
gateway:ratelimit:order-create:{principal}
```

The principal is usually:

- `user:{user_id}` if the user is authenticated
- `ip:{client_ip}` if authentication is unavailable

Useful checks:

```bash
SCAN 0 MATCH gateway:ratelimit:order-create:* COUNT 100
GET gateway:ratelimit:order-create:user:1
GET gateway:ratelimit:order-create:ip:127.0.0.1
TTL gateway:ratelimit:order-create:user:1
```

### 4.3 Manual sanity test

You can simulate a counter:

```bash
INCR gateway:ratelimit:test
EXPIRE gateway:ratelimit:test 60
GET gateway:ratelimit:test
TTL gateway:ratelimit:test
```

## 5. Check JWT Blacklist

The user service and gateway both look up revoked tokens in Redis.

### 5.1 What the key looks like

```text
user-service:blacklist:{sha256(token)}
```

The raw token is not stored in Redis. A SHA-256 hash is used instead.

### 5.2 Find blacklist entries

```bash
SCAN 0 MATCH user-service:blacklist:* COUNT 100
```

### 5.3 Inspect a blacklist key

If you already know the token, compute its SHA-256 hash first, then check Redis:

```bash
GET "user-service:blacklist:your-sha256-here"
TTL "user-service:blacklist:your-sha256-here"
TYPE "user-service:blacklist:your-sha256-here"
MEMORY USAGE "user-service:blacklist:your-sha256-here"
```

If the key exists, the token is treated as revoked.

### 5.4 Operational behavior

When a user logs out:

1. `user-service` calculates the token expiration time
2. it stores the hashed token in Redis with a TTL equal to the remaining token lifetime
3. `gateway` and `user-service` both reject that token on later requests

## 6. Useful Diagnostics

These commands are useful across all scenarios:

```bash
INFO server
INFO memory
INFO clients
INFO stats
SLOWLOG GET 10
MONITOR
```

Notes:

- `MONITOR` is very noisy and should be used carefully, especially outside local dev.
- `OBJECT ENCODING` and `OBJECT IDLETIME` can help when you need extra detail.

```bash
OBJECT ENCODING product-service:catalog:version
OBJECT IDLETIME product-service:catalog:version
```

## 7. Practical Reading Order

If you want to trace a Redis-related request in code, read in this order:

1. `services/product-service/app/core/cache.py`
2. `services/product-service/app/api/routes.py`
3. `services/user-service/app/core/cache.py`
4. `services/user-service/app/core/security.py`
5. `gateway/app/core/cache.py`
6. `gateway/app/core/proxy.py`

## 8. Short Cheat Sheet

```bash
redis-cli -h 127.0.0.1 -p 6380
PING
DBSIZE
SCAN 0 MATCH product-service:* COUNT 100
GET product-service:catalog:version
TTL product-service:catalog:version
SCAN 0 MATCH gateway:ratelimit:* COUNT 100
SCAN 0 MATCH user-service:blacklist:* COUNT 100
INFO memory
SLOWLOG GET 10
```
