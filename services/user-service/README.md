# user-service

FastAPI service responsible for authentication and user profile management.

## Responsibility

- username/password login
- JWT issuance
- JWT revocation support through Redis-backed blacklist entries
- current-user lookup
- profile update
- password change
- logout
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
- `app/core/cache.py`: Redis-backed token blacklist helpers
- `app/core/security.py`: password hashing, JWT helpers, and blacklist-aware auth
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

## API Docs

- Swagger UI: `http://localhost:8001/docs`
- Health probes are intentionally hidden from the generated schema:
  - `http://localhost:8001/health`
  - `http://localhost:8001/ready`
  - `http://localhost:8001/live`

## Environment Variables

See:

- `services/user-service/.env.example`

## Docker

- Dockerfile: `services/user-service/Dockerfile`
- Compose service name: `user-service`
- Local container docs: `http://localhost:8001/docs`

## Current Endpoints

- `POST /auth/login`
- `POST /auth/logout`
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
- Access token logout now works by writing token blacklist entries into Redis until the token naturally expires.
- Refresh token flow is still reserved for a later phase.

## Near-Term TODO

- add registration endpoint
- add unified exception middleware
- add audit log for login attempts
- add admin list/search users endpoint
- move repeated response shaping into a shared utility module
- add refresh token issuance and rotation on top of the blacklist foundation
