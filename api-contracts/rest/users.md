# User Service — REST API

**Base URL:** `http://user-service`
**Implementation:** ASP.NET

Manages public player profiles and statistics. Profile creation is handled internally via gRPC by Auth on registration — there is no public `POST /users` endpoint.

---

## GET /users/{id}

Return a player's public profile.

**Auth:** Bearer token

**Path parameters**

| Name | Type | Description |
|---|---|---|
| `id` | UUID | User ID |

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
**`404 Not Found`** — user does not exist

---

## PATCH /users/{id}

Update mutable profile fields. The authenticated user may only update their own profile.

**Auth:** Bearer token (must match `id`)

**Path parameters**

| Name | Type | Description |
|---|---|---|
| `id` | UUID | User ID |

**Request body** — all fields optional; at least one required

| Field | Type | Description |
|---|---|---|
| `username` | string | New unique username |

```json
{ "username": "alice2" }
```

**`200 OK`** — updated User object (same schema as `GET /users/{id}`)

**`401 Unauthorized`**
**`403 Forbidden`** — token belongs to a different user
**`404 Not Found`** — user does not exist
**`409 Conflict`** — username already taken
**`422 Unprocessable Entity`** — validation failed
