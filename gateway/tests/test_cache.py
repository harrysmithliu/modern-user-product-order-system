from app.core import cache


class FakePipeline:
    def __init__(self, current: int, ttl: int):
        self.current = current
        self.ttl_value = ttl
        self.operations: list[tuple[str, str]] = []

    def incr(self, key: str):
        self.operations.append(("incr", key))
        return self

    def ttl(self, key: str):
        self.operations.append(("ttl", key))
        return self

    def execute(self):
        return self.current, self.ttl_value


class FakeRedisClient:
    def __init__(self, current: int, ttl: int):
        self.pipeline_instance = FakePipeline(current=current, ttl=ttl)
        self.expire_calls: list[tuple[str, int]] = []
        self.get_calls: list[str] = []
        self.values: dict[str, str] = {}

    def pipeline(self):
        return self.pipeline_instance

    def expire(self, key: str, ttl_seconds: int):
        self.expire_calls.append((key, ttl_seconds))

    def get(self, key: str):
        self.get_calls.append(key)
        return self.values.get(key)


def test_increment_rate_limit_sets_expiry_when_ttl_missing(monkeypatch):
    fake_client = FakeRedisClient(current=3, ttl=-1)
    monkeypatch.setattr(cache, "get_redis_client", lambda: fake_client)

    current = cache.increment_rate_limit("gateway:ratelimit:login:127.0.0.1", 60)

    assert current == 3
    assert fake_client.pipeline_instance.operations == [
        ("incr", "gateway:ratelimit:login:127.0.0.1"),
        ("ttl", "gateway:ratelimit:login:127.0.0.1"),
    ]
    assert fake_client.expire_calls == [("gateway:ratelimit:login:127.0.0.1", 60)]


def test_is_token_blacklisted_uses_shared_blacklist_key(monkeypatch):
    fake_client = FakeRedisClient(current=0, ttl=0)
    key = cache._blacklist_key("token-123")
    fake_client.values[key] = "1"
    monkeypatch.setattr(cache, "get_redis_client", lambda: fake_client)

    assert cache.is_token_blacklisted("token-123") is True
    assert fake_client.get_calls == [key]
