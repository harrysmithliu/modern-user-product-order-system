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


class CouponIssueRequest(BaseModel):
    order_amount: Decimal = Field(gt=0)
    order_no: str | None = Field(default=None, min_length=1, max_length=64)


class CouponIssueResponse(BaseModel):
    user_id: int
    order_amount: Decimal
    issued: bool
    coupon_type: int | None = None
    discount_rate: Decimal | None = None
    balance_after_issue: int | None = None
    message: str


class CouponClaimBestRequest(BaseModel):
    order_amount: Decimal = Field(gt=0)
    order_no: str | None = Field(default=None, min_length=1, max_length=64)


class CouponClaimBestResponse(BaseModel):
    user_id: int
    order_amount: Decimal
    claimed: bool
    coupon_type: int | None = None
    discount_rate: Decimal = Decimal("0")
    discount_amount: Decimal = Decimal("0")
    final_amount: Decimal
    message: str


class CouponBalanceItem(BaseModel):
    coupon_type: int
    discount_rate: Decimal
    quantity: int = Field(ge=0)


class UserCouponBalanceResponse(BaseModel):
    user_id: int
    items: list[CouponBalanceItem]


class UserOrderCouponIssueResponse(BaseModel):
    user_id: int
    order_no: str
    order_amount: Decimal
    issued: bool
    coupon_type: int | None = None
    discount_rate: Decimal | None = None
    balance_after_issue: int | None = None
    message: str


class UserOrderCouponSelectionResponse(BaseModel):
    user_id: int
    order_no: str
    order_amount: Decimal
    claimed: bool
    coupon_type: int | None = None
    discount_rate: Decimal = Decimal("0")
    discount_amount: Decimal = Decimal("0")
    final_amount: Decimal
    message: str
