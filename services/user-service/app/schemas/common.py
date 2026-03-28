from datetime import datetime
from typing import Generic, TypeVar

from pydantic import BaseModel, Field

T = TypeVar("T")


class ApiResponse(BaseModel, Generic[T]):
    code: int = 0
    message: str = "success"
    data: T | None = None
    trace_id: str = Field(default="-", alias="traceId")
    timestamp: datetime = Field(default_factory=datetime.utcnow)

    model_config = {"populate_by_name": True}
