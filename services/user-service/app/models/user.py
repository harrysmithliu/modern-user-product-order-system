from datetime import datetime

from sqlalchemy import DateTime, Integer, String, func
from sqlalchemy.orm import Mapped, mapped_column

from app.db.session import Base


class User(Base):
    __tablename__ = "t_user"

    id: Mapped[int] = mapped_column(primary_key=True)
    userno: Mapped[str] = mapped_column(String(10), nullable=False)
    username: Mapped[str] = mapped_column(String(50), nullable=False)
    password: Mapped[str] = mapped_column(String(100), nullable=False)
    nickname: Mapped[str | None] = mapped_column(String(50))
    phone: Mapped[str | None] = mapped_column(String(20))
    email: Mapped[str | None] = mapped_column(String(100))
    role: Mapped[str] = mapped_column(String(20), nullable=False, default="USER")
    create_time: Mapped[datetime] = mapped_column(DateTime, server_default=func.now())
    update_time: Mapped[datetime | None] = mapped_column(
        DateTime,
        server_default=func.now(),
        onupdate=func.now(),
    )
    version: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
