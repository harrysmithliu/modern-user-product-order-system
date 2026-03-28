from decimal import Decimal

from pydantic import BaseModel, Field


class ProductResponse(BaseModel):
    id: int
    product_name: str
    product_code: str
    price: Decimal
    stock: int
    category: str | None
    status: int
    version: int


class ProductPageResponse(BaseModel):
    items: list[ProductResponse]
    page: int
    size: int
    total: int


class ProductCreateRequest(BaseModel):
    product_name: str = Field(min_length=2, max_length=200)
    product_code: str = Field(min_length=2, max_length=50)
    price: Decimal = Field(gt=0)
    stock: int = Field(ge=0)
    category: str | None = Field(default=None, max_length=50)
    status: int = Field(default=1, ge=0, le=1)


class ProductUpdateRequest(BaseModel):
    product_name: str = Field(min_length=2, max_length=200)
    price: Decimal = Field(gt=0)
    stock: int = Field(ge=0)
    category: str | None = Field(default=None, max_length=50)
    status: int = Field(default=1, ge=0, le=1)


class UpdateProductStatusRequest(BaseModel):
    status: int = Field(ge=0, le=1)


class UpdateProductStockRequest(BaseModel):
    stock: int = Field(ge=0)


class ReserveStockRequest(BaseModel):
    quantity: int = Field(gt=0)


class ProductInternalResponse(BaseModel):
    id: int
    product_name: str
    product_code: str
    price: Decimal
    stock: int
    status: int
