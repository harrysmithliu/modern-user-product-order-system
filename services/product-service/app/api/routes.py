from fastapi import APIRouter, Depends, Header, HTTPException, Query, status
from sqlalchemy.orm import Session

from app.core.cache import (
    bump_catalog_version,
    get_cached_json,
    make_product_detail_cache_key,
    make_product_list_cache_key,
    set_cached_json,
)
from app.core.security import require_admin
from app.db.session import get_db
from app.schemas.common import ApiResponse
from app.schemas.product import (
    ProductCreateRequest,
    ProductInternalResponse,
    ProductPageResponse,
    ProductResponse,
    ProductUpdateRequest,
    ReserveStockRequest,
    UpdateProductStatusRequest,
    UpdateProductStockRequest,
)
from app.services.product_service import (
    create_product,
    get_product_or_404,
    paginate_products,
    release_stock,
    reserve_stock,
    update_product,
    update_product_status,
    update_product_stock,
)

router = APIRouter()


@router.get("/health", include_in_schema=False)
def health():
    return {"status": "UP", "service": "product-service"}


@router.get("/ready", include_in_schema=False)
def ready():
    return {"status": "READY", "service": "product-service"}


@router.get("/live", include_in_schema=False)
def live():
    return {"status": "LIVE", "service": "product-service"}


@router.get("/products", response_model=ApiResponse)
def list_products(
    page: int = Query(default=1, ge=1),
    size: int = Query(default=10, ge=1, le=100),
    keyword: str | None = Query(default=None),
    include_off_sale: bool = Query(default=False),
    db: Session = Depends(get_db),
    x_user_role: str | None = Header(default=None),
):
    if include_off_sale and x_user_role != "ADMIN":
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Admin role required")
    cache_key = make_product_list_cache_key(page, size, keyword, include_off_sale)
    cached_payload = get_cached_json(cache_key)
    if cached_payload:
        return ApiResponse(data=ProductPageResponse.model_validate(cached_payload))
    total, items = paginate_products(db, page, size, keyword, include_off_sale)
    payload = ProductPageResponse(
        items=[ProductResponse.model_validate(item, from_attributes=True) for item in items],
        page=page,
        size=size,
        total=total,
    )
    set_cached_json(cache_key, payload.model_dump(mode="json"))
    return ApiResponse(data=payload)


@router.get("/products/{product_id}", response_model=ApiResponse)
def get_product(product_id: int, db: Session = Depends(get_db)):
    cache_key = make_product_detail_cache_key(product_id, internal=False)
    cached_payload = get_cached_json(cache_key)
    if cached_payload:
        return ApiResponse(data=ProductResponse.model_validate(cached_payload))
    product = get_product_or_404(db, product_id)
    payload = ProductResponse.model_validate(product, from_attributes=True)
    set_cached_json(cache_key, payload.model_dump(mode="json"))
    return ApiResponse(data=payload)


@router.post("/admin/products", response_model=ApiResponse, dependencies=[Depends(require_admin)])
def create_product_endpoint(payload: ProductCreateRequest, db: Session = Depends(get_db)):
    product = create_product(db, payload)
    bump_catalog_version()
    return ApiResponse(data=ProductResponse.model_validate(product, from_attributes=True))


@router.put("/admin/products/{product_id}", response_model=ApiResponse, dependencies=[Depends(require_admin)])
def update_product_endpoint(
    product_id: int,
    payload: ProductUpdateRequest,
    db: Session = Depends(get_db),
):
    product = get_product_or_404(db, product_id)
    updated = update_product(db, product, payload)
    bump_catalog_version()
    return ApiResponse(data=ProductResponse.model_validate(updated, from_attributes=True))


@router.put("/admin/products/{product_id}/status", response_model=ApiResponse, dependencies=[Depends(require_admin)])
def update_product_status_endpoint(
    product_id: int,
    payload: UpdateProductStatusRequest,
    db: Session = Depends(get_db),
):
    product = get_product_or_404(db, product_id)
    updated = update_product_status(db, product, payload.status)
    bump_catalog_version()
    return ApiResponse(data=ProductResponse.model_validate(updated, from_attributes=True))


@router.put("/admin/products/{product_id}/stock", response_model=ApiResponse, dependencies=[Depends(require_admin)])
def update_product_stock_endpoint(
    product_id: int,
    payload: UpdateProductStockRequest,
    db: Session = Depends(get_db),
):
    product = get_product_or_404(db, product_id)
    updated = update_product_stock(db, product, payload.stock)
    bump_catalog_version()
    return ApiResponse(data=ProductResponse.model_validate(updated, from_attributes=True))


@router.get("/internal/products/{product_id}", response_model=ApiResponse)
def get_internal_product(product_id: int, db: Session = Depends(get_db)):
    cache_key = make_product_detail_cache_key(product_id, internal=True)
    cached_payload = get_cached_json(cache_key)
    if cached_payload:
        return ApiResponse(data=ProductInternalResponse.model_validate(cached_payload))
    product = get_product_or_404(db, product_id)
    payload = ProductInternalResponse.model_validate(product, from_attributes=True)
    set_cached_json(cache_key, payload.model_dump(mode="json"))
    return ApiResponse(data=payload)


@router.post("/internal/products/{product_id}/reserve", response_model=ApiResponse)
def reserve_stock_endpoint(
    product_id: int,
    payload: ReserveStockRequest,
    db: Session = Depends(get_db),
):
    product = reserve_stock(db, product_id, payload.quantity)
    bump_catalog_version()
    return ApiResponse(data=ProductInternalResponse.model_validate(product, from_attributes=True))


@router.post("/internal/products/{product_id}/release", response_model=ApiResponse)
def release_stock_endpoint(
    product_id: int,
    payload: ReserveStockRequest,
    db: Session = Depends(get_db),
):
    product = release_stock(db, product_id, payload.quantity)
    bump_catalog_version()
    return ApiResponse(data=ProductInternalResponse.model_validate(product, from_attributes=True))
