from dataclasses import dataclass
from typing import Optional

import jwt
from fastapi import Header, HTTPException, status

from app.core.config import settings


@dataclass
class CurrentUser:
    user_id: int
    username: str
    role: str


def decode_token(token: str) -> CurrentUser:
    try:
        payload = jwt.decode(
            token,
            settings.jwt_secret,
            algorithms=[settings.jwt_algorithm],
        )
    except jwt.PyJWTError as exc:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid access token",
        ) from exc

    return CurrentUser(
        user_id=int(payload["user_id"]),
        username=str(payload["username"]),
        role=str(payload["role"]),
    )


def get_current_user(authorization: Optional[str] = Header(default=None)) -> Optional[CurrentUser]:
    if not authorization:
        return None
    scheme, _, token = authorization.partition(" ")
    if scheme.lower() != "bearer" or not token:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Authorization header must use Bearer token",
        )
    return decode_token(token)
