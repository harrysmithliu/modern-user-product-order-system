from fastapi import HTTPException, status
from sqlalchemy.orm import Session

from app.core.config import settings
from app.core.security import create_access_token, hash_password, revoke_token, verify_password
from app.models.user import User
from app.schemas.auth import LoginResponse
from app.schemas.user import UpdateProfileRequest


def login(db: Session, username: str, password: str) -> LoginResponse:
    user = db.query(User).filter(User.username == username).one_or_none()
    if not user or not verify_password(password, user.password):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid username or password")

    token = create_access_token(user.id, user.username, user.role)
    return LoginResponse(
        access_token=token,
        expires_in=settings.jwt_expire_minutes * 60,
        user_id=user.id,
        username=user.username,
        role=user.role,
    )


def get_user_by_id(db: Session, user_id: int) -> User:
    user = db.query(User).filter(User.id == user_id).one_or_none()
    if not user:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="User not found")
    return user


def update_profile(db: Session, user: User, payload: UpdateProfileRequest) -> User:
    user.nickname = payload.nickname
    user.phone = payload.phone
    user.email = payload.email
    user.version += 1
    db.add(user)
    db.commit()
    db.refresh(user)
    return user


def change_password(db: Session, user: User, old_password: str, new_password: str) -> None:
    if not verify_password(old_password, user.password):
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Old password is incorrect")
    user.password = hash_password(new_password)
    user.version += 1
    db.add(user)
    db.commit()


def logout(token: str, claims: dict) -> None:
    revoke_token(token, claims)
