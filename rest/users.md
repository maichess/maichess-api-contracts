# User Service — REST API

**Base URL:** `http://user-service`
**Implementation:** ASP.NET

Manages public player profiles and statistics. Profile creation is handled internally via gRPC by Auth on registration — there is no public `POST /users` endpoint. Win/loss/draw counters are likewise updated internally via the `RecordMatchResult` gRPC, called by Match Manager once per human participant when a match ends — there is no public endpoint for mutating stats.

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
  "draws": 5,
  "dev_mode": false,
  "rating": 1240.37,
  "rating_deviation": 62.18,
  "volatility": 0.05998
}
```

| Field | Type | Description |
|---|---|---|
| `elo` | integer | Display rating — `rating` rounded to the nearest integer. New accounts start at `400`. |
| `rating` | number | Glicko-2 rating on the display scale (unrounded). |
| `rating_deviation` | number | Glicko-2 rating deviation. New accounts start at `350`; shrinks as games accumulate. A high value indicates a provisional rating. |
| `volatility` | number | Glicko-2 volatility. New accounts start at `0.06`. |

**`401 Unauthorized`**

---

## PATCH /users/me

Update mutable fields on the authenticated user's profile.

**Auth:** `access_token` cookie

**Request body** — all fields optional; at least one required

| Field | Type | Description |
|---|---|---|
| `username` | string | New unique username |
| `dev_mode` | boolean | Toggle the developer area on the profile |

```json
{
  "username": "alice2",
  "dev_mode": true
}
```

**`200 OK`** — updated User object (same schema as `GET /users/me`)

**`401 Unauthorized`**
**`409 Conflict`** — username already taken
**`422 Unprocessable Entity`** — validation failed
