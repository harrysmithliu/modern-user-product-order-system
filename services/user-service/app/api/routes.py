from fastapi import APIRouter, Depends, Header, HTTPException, status
from sqlalchemy.orm import Session

from app.core.security import get_current_claims
from app.db.session import get_db
from app.schemas.auth import LoginRequest
from app.schemas.common import ApiResponse
from app.schemas.user import ChangePasswordRequest, UpdateProfileRequest, UserProfileResponse
from app.services.auth_service import change_password, get_user_by_id, login, update_profile

router = APIRouter()


@router.get("/health")
def health():
    return {"status": "UP", "service": "user-service"}


@router.get("/ready")
def ready():
    return {"status": "READY", "service": "user-service"}


@router.get("/live")
def live():
    return {"status": "LIVE", "service": "user-service"}


@router.post("/auth/login", response_model=ApiResponse)
def login_endpoint(payload: LoginRequest, db: Session = Depends(get_db)):
    return ApiResponse(data=login(db, payload.username, payload.password))


@router.get("/users/me", response_model=ApiResponse)
def me(
    claims: dict = Depends(get_current_claims),
    db: Session = Depends(get_db),
):
    user = get_user_by_id(db, int(claims["user_id"]))
    return ApiResponse(data=UserProfileResponse.model_validate(user, from_attributes=True))


@router.put("/users/me/profile", response_model=ApiResponse)
def update_me(
    payload: UpdateProfileRequest,
    claims: dict = Depends(get_current_claims),
    db: Session = Depends(get_db),
):
    user = get_user_by_id(db, int(claims["user_id"]))
    updated_user = update_profile(db, user, payload)
    return ApiResponse(data=UserProfileResponse.model_validate(updated_user, from_attributes=True))


@router.put("/users/me/password", response_model=ApiResponse)
def change_my_password(
    payload: ChangePasswordRequest,
    claims: dict = Depends(get_current_claims),
    db: Session = Depends(get_db),
):
    user = get_user_by_id(db, int(claims["user_id"]))
    change_password(db, user, payload.old_password, payload.new_password)
    return ApiResponse(message="password updated")


@router.get("/admin/users/{user_id}", response_model=ApiResponse)
def get_user_by_admin(
    user_id: int,
    db: Session = Depends(get_db),
    x_user_role: str | None = Header(default=None),
):
    if x_user_role != "ADMIN":
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Admin role required")
    user = get_user_by_id(db, user_id)
    return ApiResponse(data=UserProfileResponse.model_validate(user, from_attributes=True))
