from typing import Optional

import jwt
from fastapi import Header, HTTPException, status

from app.core.config import settings


def require_admin(
    x_user_role: Optional[str] = Header(default=None),
    authorization: Optional[str] = Header(default=None),
) -> None:
    if x_user_role == "ADMIN":
        return
    if authorization:
        scheme, _, token = authorization.partition(" ")
        if scheme.lower() == "bearer" and token:
            try:
                payload = jwt.decode(token, settings.jwt_secret, algorithms=[settings.jwt_algorithm])
                if payload.get("role") == "ADMIN":
                    return
            except jwt.PyJWTError:
                pass
    raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Admin role required")
