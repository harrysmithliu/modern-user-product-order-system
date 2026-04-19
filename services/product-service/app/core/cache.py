import base64
import json
import logging
from functools import lru_cache
from typing import Any

from redis import Redis
from redis.exceptions import RedisError

from app.core.config import settings

logger = logging.getLogger(__name__)

CATALOG_VERSION_KEY = "product-service:catalog:version"
COUPON_BALANCE_KEY_PREFIX = "product-service:coupon:balance:user:"

CLAIM_BEST_COUPON_LUA = """
for i = 1, #ARGV do
  local coupon_type = ARGV[i]
  local qty = tonumber(redis.call('HGET', KEYS[1], coupon_type) or '0')
  if qty > 0 then
    redis.call('HINCRBY', KEYS[1], coupon_type, -1)
    return coupon_type
  end
end
return "0"
"""


def _encode_fragment(value: str | None) -> str:
    raw = (value or "").encode("utf-8")
    return base64.urlsafe_b64encode(raw).decode("ascii") or "empty"


@lru_cache(maxsize=1)
def get_redis_client() -> Redis | None:
    if not settings.redis_enabled:
        return None
    return Redis(
        host=settings.redis_host,
        port=settings.redis_port,
        db=settings.redis_db,
        password=settings.redis_password or None,
        decode_responses=True,
        socket_connect_timeout=1,
        socket_timeout=1,
    )


def _safe_execute(operation: str, func):
    client = get_redis_client()
    if client is None:
        return None
    try:
        return func(client)
    except RedisError as exc:
        logger.warning("Redis %s failed: %s", operation, exc)
        return None


def get_catalog_version() -> int:
    value = _safe_execute("get version", lambda client: client.get(CATALOG_VERSION_KEY))
    if value is None:
        _safe_execute("init version", lambda client: client.setnx(CATALOG_VERSION_KEY, 0))
        return 0
    return int(value)


def bump_catalog_version() -> None:
    _safe_execute("bump version", lambda client: client.incr(CATALOG_VERSION_KEY))


def make_product_list_cache_key(page: int, size: int, keyword: str | None, include_off_sale: bool) -> str:
    version = get_catalog_version()
    keyword_key = _encode_fragment(keyword)
    return (
        f"product-service:catalog:v{version}:list:"
        f"page={page}:size={size}:keyword={keyword_key}:include_off_sale={int(include_off_sale)}"
    )


def make_product_detail_cache_key(product_id: int, internal: bool) -> str:
    version = get_catalog_version()
    scope = "internal" if internal else "public"
    return f"product-service:catalog:v{version}:{scope}:product:{product_id}"


def get_cached_json(key: str) -> dict[str, Any] | None:
    value = _safe_execute("get cache", lambda client: client.get(key))
    if not value:
        return None
    return json.loads(value)


def set_cached_json(key: str, payload: dict[str, Any], ttl_seconds: int | None = None) -> None:
    ttl = ttl_seconds or settings.redis_cache_ttl_seconds
    _safe_execute("set cache", lambda client: client.setex(key, ttl, json.dumps(payload)))


def make_coupon_balance_key(user_id: int) -> str:
    return f"{COUPON_BALANCE_KEY_PREFIX}{user_id}"


def increment_rate_limit(key: str, window_seconds: int) -> int | None:
    current = _safe_execute("rate limit incr", lambda client: client.incr(key))
    if current is None:
        return None
    if int(current) == 1:
        _safe_execute("rate limit expire", lambda client: client.expire(key, window_seconds))
    return int(current)


def issue_coupon_balance(user_id: int, coupon_type: int) -> int | None:
    key = make_coupon_balance_key(user_id)
    result = _safe_execute(
        "coupon issue",
        lambda client: client.hincrby(key, str(coupon_type), 1),
    )
    if result is None:
        return None
    return int(result)


def claim_best_coupon_balance(user_id: int, coupon_types: list[int]) -> int | None:
    if not coupon_types:
        return 0
    key = make_coupon_balance_key(user_id)
    raw_result = _safe_execute(
        "coupon claim best",
        lambda client: client.eval(CLAIM_BEST_COUPON_LUA, 1, key, *[str(item) for item in coupon_types]),
    )
    if raw_result is None:
        return None
    return int(raw_result)
