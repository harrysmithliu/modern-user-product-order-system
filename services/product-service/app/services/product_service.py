import csv
from decimal import ROUND_HALF_UP, Decimal, InvalidOperation
from io import StringIO

from fastapi import HTTPException, status
from pydantic import ValidationError
from sqlalchemy import update
from sqlalchemy.orm import Session

from app.core.cache import (
    bump_catalog_version,
    claim_best_coupon_balance,
    increment_rate_limit,
    issue_coupon_balance,
)
from app.core.config import settings
from app.models.product import Product
from app.schemas.product import (
    CouponClaimBestResponse,
    CouponIssueResponse,
    ProductCreateRequest,
    ProductUpdateRequest,
)

COUPON_RATE_MAP: dict[int, Decimal] = {
    10: Decimal("0.10"),
    20: Decimal("0.20"),
    30: Decimal("0.30"),
}


def paginate_products(
    db: Session,
    page: int,
    size: int,
    keyword: str | None,
    include_off_sale: bool,
):
    query = db.query(Product)
    if keyword:
        query = query.filter(Product.product_name.ilike(f"%{keyword}%"))
    if not include_off_sale:
        query = query.filter(Product.status == 1)
    total = query.count()
    items = (
        query.order_by(Product.id.desc())
        .offset((page - 1) * size)
        .limit(size)
        .all()
    )
    return total, items


def get_product_or_404(db: Session, product_id: int) -> Product:
    product = db.query(Product).filter(Product.id == product_id).one_or_none()
    if not product:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Product not found")
    return product


def create_product(db: Session, payload: ProductCreateRequest) -> Product:
    exists = db.query(Product).filter(Product.product_code == payload.product_code).one_or_none()
    if exists:
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="Product code already exists")
    product = Product(**payload.model_dump())
    db.add(product)
    db.commit()
    db.refresh(product)
    return product


def update_product(db: Session, product: Product, payload: ProductUpdateRequest) -> Product:
    for field, value in payload.model_dump().items():
        setattr(product, field, value)
    product.version += 1
    db.add(product)
    db.commit()
    db.refresh(product)
    return product


def update_product_status(db: Session, product: Product, status_value: int) -> Product:
    product.status = status_value
    product.version += 1
    db.add(product)
    db.commit()
    db.refresh(product)
    return product


def update_product_stock(db: Session, product: Product, stock: int) -> Product:
    product.stock = stock
    product.version += 1
    db.add(product)
    db.commit()
    db.refresh(product)
    return product


def reserve_stock(db: Session, product_id: int, quantity: int) -> Product:
    stmt = (
        update(Product)
        .where(Product.id == product_id, Product.status == 1, Product.stock >= quantity)
        .values(stock=Product.stock - quantity, version=Product.version + 1)
    )
    result = db.execute(stmt)
    if result.rowcount != 1:
        db.rollback()
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="Insufficient stock or product unavailable")
    db.commit()
    return get_product_or_404(db, product_id)


def release_stock(db: Session, product_id: int, quantity: int) -> Product:
    stmt = (
        update(Product)
        .where(Product.id == product_id)
        .values(stock=Product.stock + quantity, version=Product.version + 1)
    )
    result = db.execute(stmt)
    if result.rowcount != 1:
        db.rollback()
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Product not found")
    db.commit()
    return get_product_or_404(db, product_id)


def _round_money(value: Decimal) -> Decimal:
    return value.quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)


def _select_issue_coupon_type(order_amount: Decimal) -> int | None:
    if order_amount >= Decimal("800"):
        return 30
    if order_amount >= Decimal("500"):
        return 20
    if order_amount >= Decimal("200"):
        return 10
    return None


def _claim_allowed_coupon_types(order_amount: Decimal) -> list[int]:
    if order_amount >= Decimal("900"):
        return [30, 20, 10]
    if order_amount >= Decimal("600"):
        return [20, 10]
    if order_amount >= Decimal("300"):
        return [10]
    return []


def issue_coupon_for_order(_: Session, user_id: int, order_amount: Decimal) -> CouponIssueResponse:
    amount = _round_money(order_amount)
    rate_limit_key = f"product-service:coupon:ratelimit:issue:user:{user_id}"
    current = increment_rate_limit(rate_limit_key, settings.coupon_rate_limit_window_seconds)
    if current is not None and current > settings.coupon_issue_rate_limit_max_requests:
        raise HTTPException(status_code=status.HTTP_429_TOO_MANY_REQUESTS, detail="Issue coupon rate limit exceeded")

    coupon_type = _select_issue_coupon_type(amount)
    if coupon_type is None:
        return CouponIssueResponse(
            user_id=user_id,
            order_amount=amount,
            issued=False,
            message="Order amount does not meet issue threshold",
        )

    balance_after_issue = issue_coupon_balance(user_id, coupon_type)
    if balance_after_issue is None:
        raise HTTPException(status_code=status.HTTP_503_SERVICE_UNAVAILABLE, detail="Coupon engine unavailable")

    return CouponIssueResponse(
        user_id=user_id,
        order_amount=amount,
        issued=True,
        coupon_type=coupon_type,
        discount_rate=COUPON_RATE_MAP[coupon_type],
        balance_after_issue=balance_after_issue,
        message="Coupon issued",
    )


def claim_best_coupon_for_order(_: Session, user_id: int, order_amount: Decimal) -> CouponClaimBestResponse:
    amount = _round_money(order_amount)
    rate_limit_key = f"product-service:coupon:ratelimit:claim:user:{user_id}"
    current = increment_rate_limit(rate_limit_key, settings.coupon_rate_limit_window_seconds)
    if current is not None and current > settings.coupon_claim_rate_limit_max_requests:
        raise HTTPException(status_code=status.HTTP_429_TOO_MANY_REQUESTS, detail="Claim coupon rate limit exceeded")

    allowed_types = _claim_allowed_coupon_types(amount)
    if not allowed_types:
        return CouponClaimBestResponse(
            user_id=user_id,
            order_amount=amount,
            claimed=False,
            final_amount=amount,
            message="Order amount does not meet claim threshold",
        )

    claimed_coupon_type = claim_best_coupon_balance(user_id, allowed_types)
    if claimed_coupon_type is None:
        raise HTTPException(status_code=status.HTTP_503_SERVICE_UNAVAILABLE, detail="Coupon engine unavailable")
    if claimed_coupon_type == 0:
        return CouponClaimBestResponse(
            user_id=user_id,
            order_amount=amount,
            claimed=False,
            final_amount=amount,
            message="No eligible coupon available",
        )
    if claimed_coupon_type not in COUPON_RATE_MAP:
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Invalid coupon type claimed")

    discount_rate = COUPON_RATE_MAP[claimed_coupon_type]
    discount_amount = _round_money(amount * discount_rate)
    final_amount = _round_money(amount - discount_amount)
    return CouponClaimBestResponse(
        user_id=user_id,
        order_amount=amount,
        claimed=True,
        coupon_type=claimed_coupon_type,
        discount_rate=discount_rate,
        discount_amount=discount_amount,
        final_amount=final_amount,
        message="Coupon claimed",
    )


def import_products_from_csv(db: Session, filename: str | None, raw: bytes) -> dict[str, object]: 
    if not filename:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Filename is required")
    if not filename.lower().endswith(".csv"):
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Only .csv file is supported for now")
    if not raw:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Uploaded file is empty")
    
    try:
        text = raw.decode("utf-8-sig")
    except UnicodeDecodeError as exc:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="CSV must be UTF-8-sig encoded") from exc
    
    rows = [row for row in csv.reader(StringIO(text)) if any(cell.strip() for cell in row)]
    if not rows:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="CSV file contains no data")
    
    expected_header = ["product_name", "product_code", "price", "stock", "category", "status"]
    header =  [cell.strip() for cell in rows[0]]
    if header != expected_header:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail={
                "message": "Invalid CSV Header",
                "expected": expected_header,
                "actual": header,
            },
        )
    data_rows = rows[1:]
    row_errors: list[dict[str, object]] = []
    validated_rows: list[tuple[int, ProductCreateRequest]] = []

    for idx, row in enumerate(data_rows, start=2):
        normalized_row = [cell.strip() for cell in row]
        if len(normalized_row) != len(expected_header):
            row_errors.append(
                {
                    "row_no": idx,
                    "reason": f"Column count mismatch: expected {len(header)}, got {len(normalized_row)}",
                }
            )
            continue
        row_dict = dict(zip(header, normalized_row))

        try:
            row_dict["price"] = Decimal(row_dict["price"])
            row_dict["stock"] = int(row_dict["stock"])
            row_dict["status"] = int(row_dict["status"])
        except (InvalidOperation, ValueError) as exc:
            row_errors.append(
                {
                    "row_no": idx,
                    "reason": f"Type conversion failed: {str(exc)}",
                }
            )
            continue

        try: 
            validated = ProductCreateRequest.model_validate(row_dict)
        except ValidationError as exc:
            row_errors.append(
                {
                    "row_no": idx,
                    "reason": f"Validation failed",
                    "details": exc.errors(),
                }
            )
            continue
        validated_rows.append((idx, validated))

    imported_count = 0
    updated_count = 0
    for row_no, item in validated_rows:
        payload = item.model_dump()
        exists = db.query(Product).filter(Product.product_code == payload["product_code"]).one_or_none()

        if exists:
            row_errors.append(
                {
                    "row_no": row_no,
                    "reason": f"Product with code '{payload['product_code']}' already exists",
                }
            )
            continue
        db.add(Product(**payload))
        imported_count += 1

    db.commit()
    bump_catalog_version()

    total = len(data_rows)
    failed = len(row_errors)
    success = total - failed

    return {
        "total": total,
        "imported": imported_count,
        "updated": updated_count,
        "failed": failed,
        "success": success,
        "errors": row_errors,
    }
