import hashlib
import logging
import time
from functools import lru_cache

from redis import Redis
from redis.exceptions import RedisError

from app.core.config import settings

logger = logging.getLogger(__name__)


def _blacklist_key(token: str) -> str:
    return f"user-service:blacklist:{hashlib.sha256(token.encode('utf-8')).hexdigest()}"


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


def blacklist_token(token: str, exp_timestamp: int) -> None:
    ttl_seconds = max(1, int(exp_timestamp - time.time()))
    key = _blacklist_key(token)
    _safe_execute("blacklist token", lambda client: client.setex(key, ttl_seconds, "1"))


def is_token_blacklisted(token: str) -> bool:
    key = _blacklist_key(token)
    value = _safe_execute("check blacklist", lambda client: client.get(key))
    return value is not None
