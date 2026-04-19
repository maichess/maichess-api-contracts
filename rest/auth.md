# Auth Service — REST API

**Base URL:** `http://auth-service`
**Implementation:** Express.js

Handles account registration, credential login, and JWT token lifecycle. Access tokens are short-lived JWTs (15 min). Refresh tokens are long-lived opaque UUIDs stored server-side (30 days). On registration, Auth calls `Users.CreateUser` via gRPC.

Tokens are delivered and consumed exclusively via HttpOnly cookies — never in response or request bodies. This allows them to be shared across subdomains by setting `COOKIE_DOMAIN=.yourdomain.com` on the auth service.

## Cookies

| Cookie | Path | MaxAge | Description |
|---|---|---|---|
| `access_token` | `/` | 15 min | Signed JWT; sent to all services |
| `refresh_token` | `/auth` | 30 days | Opaque UUID; only sent back to auth service endpoints |

Both cookies are `HttpOnly`, `SameSite=Lax`. `Secure` is enabled when `NODE_ENV=production` or `COOKIE_SECURE=true`. Domain is set via `COOKIE_DOMAIN` env var (optional; omit for localhost).

---

## POST /auth/register

Create a new account.

**Auth:** None

**Request body**

| Field | Type | Required | Description |
|---|---|---|---|
| `username` | string | Yes | Unique, 3–32 alphanumeric characters |
| `password` | string | Yes | Plaintext; min 8 characters — hashed server-side before calling User service |

```json
{
  "username": "alice",
  "password": "s3cr3t123"
}
```

**`201 Created`**
```json
{
  "user_id": "3f2504e0-4f89-11d3-9a0c-0305e82c3301"
}
```

Sets `access_token` and `refresh_token` cookies.

**`409 Conflict`** — username already taken
**`422 Unprocessable Entity`** — validation failed

---

## POST /auth/login

Authenticate with username and password.

**Auth:** None

**Request body**

| Field | Type | Required |
|---|---|---|
| `username` | string | Yes |
| `password` | string | Yes |

```json
{
  "username": "alice",
  "password": "s3cr3t123"
}
```

**`200 OK`**
```json
{
  "user_id": "3f2504e0-4f89-11d3-9a0c-0305e82c3301"
}
```

Sets `access_token` and `refresh_token` cookies.

**`401 Unauthorized`** — incorrect credentials

---

## POST /auth/refresh

Exchange the `refresh_token` cookie for a new token pair. The submitted refresh token is invalidated (rotation).

**Auth:** None — reads `refresh_token` cookie automatically

**Request body:** None

**`200 OK`** — no body

Sets new `access_token` and `refresh_token` cookies. The old refresh token is invalidated.

**`401 Unauthorized`** — refresh token cookie missing, invalid, or expired

---

## POST /auth/logout

Invalidate the current session.

**Auth:** None — reads `refresh_token` cookie automatically

**Request body:** None

**`204 No Content`**

Clears `access_token` and `refresh_token` cookies. If no refresh token cookie is present the request still succeeds.
