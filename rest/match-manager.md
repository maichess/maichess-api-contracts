# Match Manager Service — REST API

**Base URL:** `http://match-manager-service`
**Implementation:** ASP.NET

Accepts player moves and serves match state. Move validation is delegated to Move Validator via gRPC; bot moves are requested from Engine via gRPC after each human ply. Real-time events (moves, match end, draw offers) are pushed to clients via the socket service — Match Manager calls `Socket.EmitEvent` over gRPC after each state change.

Player objects in responses are either a user `{"user_id": "...", "username": "..."}` or a bot `{"bot_id": "...", "name": "..."}`.

---

## GET /matches/{id}

Return the current state of a match.

**Auth:** Bearer token

**Path parameters**

| Name | Type | Description |
|---|---|---|
| `id` | UUID | Match ID |

**`200 OK`**
```json
{
  "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "white": { "user_id": "3f2504e0-...", "username": "alice" },
  "black": { "bot_id": "stockfish-3", "name": "Stockfish Level 3" },
  "current_fen": "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1",
  "status": "ongoing",
  "moves": ["e2e4"],
  "time_control": "blitz",
  "white_time_ms": 179500,
  "black_time_ms": 180000,
  "analyzable": false
}
```

`status` is one of: `ongoing`, `white_won`, `black_won`, `draw`

`analyzable` is `true` when position navigation is available for this match: either at least one
side is a bot, or the match has ended. The Analysis service uses this flag to determine whether
`POST /games/from-match/{match_id}` is permitted.

**`401 Unauthorized`**
**`403 Forbidden`** — match is between other players and the requestor is not a participant
**`404 Not Found`**

---

## GET /matches/{id}/legal-moves

Return all legal moves for the current position. Used by the client to highlight valid target squares.

**Auth:** Bearer token

**Path parameters**

| Name | Type | Description |
|---|---|---|
| `id` | UUID | Match ID |

**Query parameters**

| Name | Type | Required | Description |
|---|---|---|---|
| `from` | string | No | Filter by origin square in algebraic notation (e.g. `e2`) |

**`200 OK`**
```json
{
  "moves": ["e2e3", "e2e4"]
}
```

Moves are in UCI notation. An empty array means no legal moves exist for the active side; check `status` for checkmate or stalemate.

**`401 Unauthorized`**
**`404 Not Found`**

---

## POST /matches/{id}/moves

Submit a move on behalf of the authenticated player.

**Auth:** Bearer token — must be a participant and it must be their turn

**Path parameters**

| Name | Type | Description |
|---|---|---|
| `id` | UUID | Match ID |

**Request body**

| Field | Type | Required | Description |
|---|---|---|---|
| `move` | string | Yes | Move in UCI notation (e.g. `e2e4`, `e7e8q` for promotion) |

```json
{ "move": "e2e4" }
```

**`200 OK`** — updated Match object (same schema as `GET /matches/{id}`)

**`400 Bad Request`** — move is illegal; body contains `{"error": "reason"}`
**`401 Unauthorized`**
**`403 Forbidden`** — not a participant, or not the requestor's turn
**`409 Conflict`** — match has already ended

---

## POST /matches/{id}/resign

Forfeit the match on behalf of the authenticated player.

**Auth:** Bearer token — must be a participant

**Path parameters**

| Name | Type | Description |
|---|---|---|
| `id` | UUID | Match ID |

**`200 OK`** — final Match object with `status` set to `white_won` or `black_won`

**`401 Unauthorized`**
**`403 Forbidden`** — not a participant
**`409 Conflict`** — match has already ended

