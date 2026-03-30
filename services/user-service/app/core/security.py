from datetime import UTC, datetime, timedelta
from uuid import uuid4

import jwt
from fastapi import Header, HTTPException, status
from passlib.context import CryptContext

from app.core.cache import blacklist_token, is_token_blacklisted
from app.core.config import settings

password_context = CryptContext(schemes=["bcrypt"], deprecated="auto")


def verify_password(plain_password: str, hashed_password: str) -> bool:
    return password_context.verify(plain_password, hashed_password)


def hash_password(password: str) -> str:
    return password_context.hash(password)


def create_access_token(user_id: int, username: str, role: str) -> str:
    expires_at = datetime.now(UTC) + timedelta(minutes=settings.jwt_expire_minutes)
    payload = {
        "user_id": user_id,
        "username": username,
        "role": role,
        "exp": expires_at,
        "jti": str(uuid4()),
    }
    return jwt.encode(payload, settings.jwt_secret, algorithm=settings.jwt_algorithm)


def extract_bearer_token(authorization: str | None) -> str:
    if not authorization:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Missing bearer token")
    scheme, _, token = authorization.partition(" ")
    if scheme.lower() != "bearer" or not token:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid authorization header")
    return token


def decode_token(token: str) -> dict:
    try:
        return jwt.decode(token, settings.jwt_secret, algorithms=[settings.jwt_algorithm])
    except jwt.PyJWTError as exc:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid access token") from exc


def get_current_claims(authorization: str | None = Header(default=None)) -> dict:
    token = extract_bearer_token(authorization)
    if is_token_blacklisted(token):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Access token has been revoked")
    return decode_token(token)


def get_current_token_with_claims(authorization: str | None = Header(default=None)) -> tuple[str, dict]:
    token = extract_bearer_token(authorization)
    claims = decode_token(token)
    if is_token_blacklisted(token):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Access token has been revoked")
    return token, claims


def revoke_token(token: str, claims: dict) -> None:
    exp = claims.get("exp")
    if isinstance(exp, datetime):
        exp_timestamp = int(exp.timestamp())
    else:
        exp_timestamp = int(exp)
    blacklist_token(token, exp_timestamp)
