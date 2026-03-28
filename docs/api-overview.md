# API Overview

## Gateway

- `POST /api/auth/login`
- `GET /api/products`
- `GET /api/products/{id}`
- `GET /api/users/me`
- `PUT /api/users/me/profile`
- `PUT /api/users/me/password`
- `POST /api/orders`
- `POST /api/orders/{id}/cancel`
- `GET /api/orders/my`
- `GET /api/admin/orders`
- `POST /api/admin/orders/{id}/approve`
- `POST /api/admin/orders/{id}/reject`
- `POST /api/admin/products`
- `PUT /api/admin/products/{id}`
- `PUT /api/admin/products/{id}/status`
- `PUT /api/admin/products/{id}/stock`

## Internal Product APIs

- `GET /internal/products/{id}`
- `POST /internal/products/{id}/reserve`
- `POST /internal/products/{id}/release`
