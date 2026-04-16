# Auth Service — REST API

**Base URL:** `http://auth-service`
**Implementation:** Express.js

Handles account registration, credential login, and JWT token lifecycle. Access tokens are short-lived JWTs (15 min). Refresh tokens are long-lived opaque UUIDs stored server-side (30 days). On registration, Auth calls `Users.CreateUser` via gRPC.

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
  "user_id": "3f2504e0-4f89-11d3-9a0c-0305e82c3301",
  "access_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refresh_token": "d4f9a1e2-0b3c-4d6e-8f7a-9b0c1d2e3f4a"
}
```

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
  "user_id": "3f2504e0-4f89-11d3-9a0c-0305e82c3301",
  "access_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refresh_token": "d4f9a1e2-0b3c-4d6e-8f7a-9b0c1d2e3f4a"
}
```

**`401 Unauthorized`** — incorrect credentials

---

## POST /auth/refresh

Exchange a valid refresh token for a new token pair. The submitted refresh token is invalidated (rotation).

**Auth:** None

**Request body**

| Field | Type | Required |
|---|---|---|
| `refresh_token` | string | Yes |

```json
{
  "refresh_token": "d4f9a1e2-0b3c-4d6e-8f7a-9b0c1d2e3f4a"
}
```

**`200 OK`**
```json
{
  "access_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refresh_token": "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
}
```

**`401 Unauthorized`** — refresh token invalid or expired

---

## POST /auth/logout

Invalidate a refresh token.

**Auth:** None

**Request body**

| Field | Type | Required |
|---|---|---|
| `refresh_token` | string | Yes |

**`204 No Content`**
