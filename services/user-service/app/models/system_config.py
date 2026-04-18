from datetime import datetime
from app.db.session import Base

from sqlalchemy import DateTime, String, func
from sqlalchemy.orm import Mapped, mapped_column


class SystemConfig(Base):
    __tablename__ = 't_system_config'

    config_key: Mapped[str] = mapped_column(String(100), primary_key=True)
    config_value: Mapped[str] = mapped_column(String(255), nullable=False)
    update_time: Mapped[datetime] = mapped_column( # type: ignore
        DateTime, 
        server_default=func.now(), 
        onupdate=func.now()
    )
