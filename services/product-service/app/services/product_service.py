import csv
from decimal import Decimal, InvalidOperation
from io import StringIO

from fastapi import HTTPException, status
from pydantic import ValidationError
from sqlalchemy import update
from sqlalchemy.orm import Session

from app.core.cache import bump_catalog_version
from app.models.product import Product
from app.schemas.product import (
    ProductCreateRequest,
    ProductUpdateRequest,
)


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