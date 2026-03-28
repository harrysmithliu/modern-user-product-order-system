# user-service

FastAPI service responsible for authentication and user profile management.

## Responsibility

- username/password login
- JWT issuance
- current-user lookup
- profile update
- password change
- admin lookup of a specific user

## Database

- schema: `h_user_db`
- table: `t_user`

Required columns currently used by the service:

- `id`
- `userno`
- `username`
- `password`
- `nickname`
- `phone`
- `email`
- `role`
- `version`

## Directory Guide

- `app/main.py`: service entry
- `app/api/routes.py`: HTTP endpoints
- `app/core/config.py`: service settings
- `app/core/security.py`: password hashing and JWT helpers
- `app/db/session.py`: SQLAlchemy engine/session
- `app/models/user.py`: ORM model
- `app/services/auth_service.py`: core user and auth logic

## Local Run

```bash
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8001
```

## Environment Variables

See:

- [services/user-service/.env.example](/Users/harryliu/Documents/workspace/portfolio/pj-modern-user-product-order-system/modern-user-product-order-system/services/user-service/.env.example)

## Current Endpoints

- `POST /auth/login`
- `GET /users/me`
- `PUT /users/me/profile`
- `PUT /users/me/password`
- `GET /admin/users/{user_id}`
- `GET /health`
- `GET /ready`
- `GET /live`

## Known Good Accounts

- `admin / Admin@123`
- `john_smith / User@123`

## Maintenance Notes

- Password hashing relies on `passlib` plus `bcrypt==4.0.1`; that version pin matters for Python 3.13 compatibility in this workspace.
- JWT secret must match the gateway secret or protected flows will fail.
- The service currently issues access tokens directly; refresh token flow is reserved for a later phase.

## Near-Term TODO

- add registration endpoint
- add unified exception middleware
- add audit log for login attempts
- add admin list/search users endpoint
- move repeated response shaping into a shared utility module
