from fastapi import HTTPException, status
from sqlalchemy import update
from sqlalchemy.orm import Session

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
