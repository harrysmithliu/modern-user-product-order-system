# frontend

React + Vite + TypeScript frontend for the Modern User-Product-Order System.

## Responsibility

- login flow
- token-based session persistence
- product browsing and order creation
- user profile management
- admin order approval page
- admin product management page

This app does not implement business rules itself. It renders data returned by the gateway and backend services.

## Current Pages

- `/login`
- `/products`
- `/orders`
- `/profile`
- `/admin/orders`
- `/admin/products`

## Tech Stack

- React 18
- Vite
- TypeScript
- Ant Design
- Axios
- React Router

## Directory Guide

- `src/RootApp.tsx`: app shell, routes, layout
- `src/modules/auth/`: auth context and session bootstrap
- `src/api/`: API client, service wrappers, shared response types
- `src/pages/`: page-level UI
- `src/components/`: small reusable view components
- `src/styles.css`: global visual system for the current MVP

## Local Run

```bash
npm install
npm run dev
```

Default local URL:

- `http://localhost:5173`

## Backend Dependency

The frontend currently calls the gateway at:

- `http://localhost:8000`

If you change the gateway port or host, update:

- [client.ts](/Users/harryliu/Documents/workspace/portfolio/pj-modern-user-product-order-system/modern-user-product-order-system/frontend/src/api/client.ts)

## Demo Accounts

- Admin: `admin / Admin@123`
- User: `john_smith / User@123`

## Maintenance Notes

- Order and product responses currently use `snake_case` fields because the backend returns that shape.
- Route-level access control is UI-only convenience. Real authorization is enforced by the gateway and services.
- If bundle size becomes a concern, start with route-based code splitting in `RootApp.tsx`.

## Near-Term TODO

- add registration page placeholder
- add product detail page
- improve error display with per-request messages from backend
- add reusable table filters and status badges
- split admin modules into their own route groups as the app grows
