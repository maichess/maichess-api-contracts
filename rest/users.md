# User Service — REST API

**Base URL:** `http://user-service`
**Implementation:** ASP.NET

Manages public player profiles and statistics. Profile creation is handled internally via gRPC by Auth on registration — there is no public `POST /users` endpoint.

The authenticated user's identity is always inferred from the `access_token` cookie set by the Auth service. No user ID is accepted in the URL.

---

## GET /users/me

Return the authenticated user's profile.

**Auth:** `access_token` cookie

**`200 OK`**
```json
{
  "id": "3f2504e0-4f89-11d3-9a0c-0305e82c3301",
  "username": "alice",
  "elo": 1240,
  "wins": 32,
  "losses": 18,
  "draws": 5
}
```

**`401 Unauthorized`**

---

## PATCH /users/me

Update mutable fields on the authenticated user's profile.

**Auth:** `access_token` cookie

**Request body** — all fields optional; at least one required

| Field | Type | Description |
|---|---|---|
| `username` | string | New unique username |

```json
{
  "username": "alice2"
}
```

**`200 OK`** — updated User object (same schema as `GET /users/me`)

**`401 Unauthorized`**
**`409 Conflict`** — username already taken
**`422 Unprocessable Entity`** — validation failed
