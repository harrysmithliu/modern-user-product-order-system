from app.core import cache


class FakeRedisClient:
    def __init__(self):
        self.values: dict[str, object] = {}
        self.setnx_calls: list[tuple[str, object]] = []
        self.incr_calls: list[str] = []
        self.setex_calls: list[tuple[str, int, str]] = []

    def get(self, key: str):
        return self.values.get(key)

    def setnx(self, key: str, value: object):
        self.setnx_calls.append((key, value))
        self.values.setdefault(key, value)

    def incr(self, key: str):
        self.incr_calls.append(key)
        self.values[key] = int(self.values.get(key, 0)) + 1
        return self.values[key]

    def setex(self, key: str, ttl_seconds: int, payload: str):
        self.setex_calls.append((key, ttl_seconds, payload))
        self.values[key] = payload


def test_get_catalog_version_initializes_default_value(monkeypatch):
    fake_client = FakeRedisClient()
    monkeypatch.setattr(cache, "get_redis_client", lambda: fake_client)

    version = cache.get_catalog_version()

    assert version == 0
    assert fake_client.setnx_calls == [(cache.CATALOG_VERSION_KEY, 0)]


def test_make_product_list_cache_key_includes_version_and_keyword(monkeypatch):
    monkeypatch.setattr(cache, "get_catalog_version", lambda: 3)

    key = cache.make_product_list_cache_key(page=2, size=5, keyword="desk", include_off_sale=True)

    assert key == "product-service:catalog:v3:list:page=2:size=5:keyword=ZGVzaw==:include_off_sale=1"


def test_set_cached_json_uses_default_ttl(monkeypatch):
    fake_client = FakeRedisClient()
    monkeypatch.setattr(cache, "get_redis_client", lambda: fake_client)
    monkeypatch.setattr(cache.settings, "redis_cache_ttl_seconds", 120)

    cache.set_cached_json("product-service:catalog:v0:list:test", {"items": []})

    assert fake_client.setex_calls == [
        ("product-service:catalog:v0:list:test", 120, '{"items": []}'),
    ]
