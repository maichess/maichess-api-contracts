# Anti-Cheat Service — REST API

**Base URL:** `http://anticheat-service`
**Implementation:** ASP.NET

Analyses finished games (engine correlation + statistical features, pre-move aware),
maintains per-user suspicion cases in `anticheat-db`, and emits flag changes on
`cheat.events.v1`. This REST surface is the **Dev** interface only: an overview of
cases/evidence and a remove-flag action for clearing false positives. The flag itself
propagates exclusively via `cheat.events.v1` — there is no REST flag mutation besides
unflag, and no public (non-dev) endpoint.

**Auth (all endpoints):** Bearer JWT. The caller must additionally have `dev_mode`
enabled on their user account (resolved server-side via user-service `Users.GetUser`);
otherwise `403 Forbidden`.

Evidence is stored as *references* into match-db (`match_id` + ply indexes) — responses
never embed moves, FENs, or game documents.

---

## GET /anticheat/cases

List anti-cheat cases, most recently updated first.

**Query parameters**

| Name | Type | Required | Description |
|---|---|---|---|
| `status` | string | No | Filter: `open`, `flagged`, or `cleared`. Omit for all. |
| `limit` | int | No | Page size, default 50, max 200 |
| `offset` | int | No | Default 0 |

**`200 OK`**
```json
{
  "cases": [
    {
      "case_id": "0b6c9d3e-...",
      "user_id": "7e9f2b4a-...",
      "status": "flagged",
      "score": 0.83,
      "games_analyzed": 6,
      "live_signals": 2,
      "created_at_ms": 1765000000000,
      "updated_at_ms": 1765400000000,
      "flagged_at_ms": 1765400000000
    }
  ]
}
```

`status` lifecycle: `open` (games analysed, below threshold) → `flagged` (combined score
crossed the flag threshold over enough games) → `cleared` (a dev removed the flag; the
case stays for audit and keeps accumulating analyses). `flagged_at_ms` is `null` unless
currently flagged. `live_signals` counts advisory in-game signals (iteration 2) recorded
against the case — advisory only, never a flag by itself.

**`401 Unauthorized`** — missing/invalid token
**`403 Forbidden`** — caller is not a dev (`dev_mode` false)

---

## GET /anticheat/cases/{case_id}

Full evidence for one case: per-game verdicts and the audit trail.

**`200 OK`**
```json
{
  "case_id": "0b6c9d3e-...",
  "user_id": "7e9f2b4a-...",
  "status": "flagged",
  "score": 0.83,
  "flagged_at_ms": 1765400000000,
  "games": [
    {
      "match_id": "a1b2c3d4-...",
      "score": 0.91,
      "correlation": 0.95,
      "statistical": 0.78,
      "suspicious_plies": [14, 18, 22, 30],
      "analyzed_at_ms": 1765399000000
    }
  ],
  "audit": [
    { "action": "live_suspicion", "actor": "system", "reason": "match a1b2c3d4 ply 22 score 0.74", "at_ms": 1765398000000 },
    { "action": "flagged",        "actor": "system", "reason": "combined score 0.83 over 6 games", "at_ms": 1765400000000 }
  ]
}
```

`games` entries reference match-db by `match_id`; `suspicious_plies` are ply indexes into
that match's move list. `audit[].actor` is `system` or the dev's user id (for `unflagged`).

**`401 Unauthorized`** / **`403 Forbidden`**
**`404 Not Found`** — unknown `case_id`

---

## POST /anticheat/cases/{case_id}/unflag

Clear a flag (false positive). Writes an `unflagged` audit entry (actor = caller's user
id) and emits a clearing `PlayerUnflagged` on `cheat.events.v1` so the user read models
drop `flagged`. The case status becomes `cleared`; analysis continues and the user can be
flagged again by future games.

**Request body**

| Field | Type | Required | Description |
|---|---|---|---|
| `reason` | string | Yes | Free-text justification, stored in the audit trail |

```json
{ "reason": "Reviewed games — strong but human play; timing consistent with premoves." }
```

**`204 No Content`**

**`400 Bad Request`** — missing/empty `reason`
**`401 Unauthorized`** / **`403 Forbidden`**
**`404 Not Found`** — unknown `case_id`
**`409 Conflict`** — case is not currently `flagged`
