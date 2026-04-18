from fastapi import HTTPException, status
from sqlalchemy.orm import Session

from app.core.config import settings
from app.core.security import create_access_token, hash_password, revoke_token, verify_password
from app.models.system_config import SystemConfig
from app.models.user import User
from app.schemas.auth import LoginResponse
from app.schemas.user import AdminUserListItem, AdminUserPageResponse, UpdateProfileRequest

USER_LOGIN_ENABLED_KEY = "USER_LOGIN_ENABLED"

def login(db: Session, username: str, password: str) -> LoginResponse:
    user = db.query(User).filter(User.username == username).one_or_none()
    if not user or not verify_password(password, user.password):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid username or password")

    if user.role == "USER" and not get_user_login_enabled(db):
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="User login is currently disabled")

    if user.login_enabled == 0:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Your account is disabled")

    token = create_access_token(user.id, user.username, user.role)
    return LoginResponse(
        access_token=token,
        expires_in=settings.jwt_expire_minutes * 60,
        user_id=user.id,
        username=user.username,
        role=user.role,
    )


def list_users_by_admin(db: Session, page: int, size: int) -> AdminUserPageResponse: 
    page = max(page, 1)
    size = max(min(size, 100), 1)

    total = db.query(User).count()

    rows = (
        db.query(User)
        .order_by(User.id)
        .offset((page - 1) * size)
        .limit(size)
        .all()
    )

    items = [
        AdminUserListItem(
            id=user.id,
            userno=user.userno,
            username=user.username,
            nickname=user.nickname,
            phone=user.phone,
            email=user.email,
            role=user.role,
            login_enabled=bool(user.login_enabled),
        )
        for user in rows
    ]

    return AdminUserPageResponse(
        page=page,
        size=size,
        total=total,
        items=items,
    )


def get_user_login_enabled(db: Session) -> bool:
    config = (
        db.query(SystemConfig)
        .filter(SystemConfig.config_key == USER_LOGIN_ENABLED_KEY)
        .one_or_none()
    )
    if not config:
        return True
    return config.config_value == "1"


def set_user_login_enabled(db: Session, enabled: bool) -> bool:
    config = (
        db.query(SystemConfig)
        .filter(SystemConfig.config_key == USER_LOGIN_ENABLED_KEY)
        .one_or_none()
    )
    if config:
        config.config_value = "1" if enabled else "0"
        db.add(config)
    else:
        db.add(
            SystemConfig(
                config_key=USER_LOGIN_ENABLED_KEY, 
                config_value="1" if enabled else "0"
            )
        )
    db.commit()
    return enabled


def get_user_by_id(db: Session, user_id: int) -> User:
    user = db.query(User).filter(User.id == user_id).one_or_none()
    if not user:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="User not found")
    return user


def set_user_login_enabled_by_admin(db: Session, user_id: int, enabled: bool) -> User:
    user = get_user_by_id(db, user_id)

    if user.role != "USER":
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Only USER accounts can be enabled/disabled")

    user.login_enabled = 1 if enabled else 0
    user.version += 1
    db.add(user)
    db.commit()
    db.refresh(user)
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
