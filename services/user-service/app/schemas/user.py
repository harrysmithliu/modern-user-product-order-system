from pydantic import BaseModel, EmailStr, Field


class UserProfileResponse(BaseModel):
    id: int
    userno: str
    username: str
    nickname: str | None
    phone: str | None
    email: str | None
    role: str


class UpdateProfileRequest(BaseModel):
    nickname: str | None = Field(default=None, max_length=50)
    phone: str | None = Field(default=None, max_length=20)
    email: EmailStr | None = None


class ChangePasswordRequest(BaseModel):
    old_password: str = Field(min_length=6, max_length=50)
    new_password: str = Field(min_length=6, max_length=50)


class LoginPolicyResponse(BaseModel):
    user_login_enabled: bool


class LoginPolicyRequest(BaseModel):
    user_login_enabled: bool


class AdminUserListItem(BaseModel):
    id: int
    userno: str
    username: str
    nickname: str | None
    phone: str | None
    email: str | None
    role: str
    login_enabled: bool


class AdminUserPageResponse(BaseModel):
    page: int
    size: int
    total: int
    items: list[AdminUserListItem]