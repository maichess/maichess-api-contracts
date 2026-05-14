# Match Maker Service — REST API

**Base URL:** `http://match-maker-service`
**Implementation:** ASP.NET

Handles session initialisation. For human opponents the service queues the player and waits for a peer; for bot opponents it resolves immediately. In both cases, once an opponent is found, Match Maker calls `Matches.CreateMatch` on Match Manager via gRPC and then calls `Socket.EmitEvent` to push a `matched` event containing the `match_id` to the client. Bot-vs-bot matches are created on demand via `POST /matches/bot-vs-bot` and bypass the queue entirely.

---

## GET /time-formats

Return the canonical list of supported time-format presets. Used by the client to populate the time-format picker on the new-game screen.

**Auth:** None

**`200 OK`**
```json
{
  "formats": [
    { "id": "1+0",   "base_ms": 60000,   "increment_ms": 0,     "category": "bullet"    },
    { "id": "2+1",   "base_ms": 120000,  "increment_ms": 1000,  "category": "bullet"    },
    { "id": "3+0",   "base_ms": 180000,  "increment_ms": 0,     "category": "blitz"     },
    { "id": "3+2",   "base_ms": 180000,  "increment_ms": 2000,  "category": "blitz"     },
    { "id": "5+0",   "base_ms": 300000,  "increment_ms": 0,     "category": "blitz"     },
    { "id": "5+3",   "base_ms": 300000,  "increment_ms": 3000,  "category": "blitz"     },
    { "id": "10+0",  "base_ms": 600000,  "increment_ms": 0,     "category": "rapid"     },
    { "id": "10+5",  "base_ms": 600000,  "increment_ms": 5000,  "category": "rapid"     },
    { "id": "15+10", "base_ms": 900000,  "increment_ms": 10000, "category": "rapid"     },
    { "id": "30+0",  "base_ms": 1800000, "increment_ms": 0,     "category": "classical" },
    { "id": "30+20", "base_ms": 1800000, "increment_ms": 20000, "category": "classical" }
  ]
}
```

The `id` field is the stable identifier accepted by other endpoints (e.g. `POST /queue`, `POST /matches/bot-vs-bot`). The `category` field maps to bot timing buckets and to the existing client labels.

---

## GET /bots

Return all available bots.

**Auth:** None

**`200 OK`**
```json
{
  "bots": [
    { "id": "bullet", "name": "Bullet", "elo": 1400, "description": "A fully-featured bitboard engine..." },
    { "id": "blitz",  "name": "Blitz",  "elo": 1700, "description": "A fully-featured bitboard engine..." }
  ]
}
```

---

## POST /queue

Enter the matchmaking queue.

**Auth:** Bearer token

**Request body**

| Field | Type | Required | Description |
|---|---|---|---|
| `time_format_id` | string | Yes | One of the `id` values returned by `GET /time-formats` |
| `opponent.type` | string | Yes | `human` or `bot` |
| `opponent.bot_id` | string | Conditional | Required when `opponent.type` is `bot` |

Human opponent:
```json
{
  "time_format_id": "5+0",
  "opponent": { "type": "human" }
}
```

Bot opponent:
```json
{
  "time_format_id": "10+5",
  "opponent": { "type": "bot", "bot_id": "stockfish-3" }
}
```

**`201 Created`**
```json
{
  "queue_token": "7e9f2b4a-1c3d-4e5f-8a6b-0c2d4e6f8a0b"
}
```

The `queue_token` identifies this queue slot for cancellation. When a match is found, the socket service delivers a `matched` event to the client — see `rest/socket.md`. Players are paired only with others on the exact same `time_format_id` so increments are consistent on both sides.

**`400 Bad Request`** — unknown `time_format_id` or unknown `bot_id`
**`401 Unauthorized`**
**`409 Conflict`** — player is already in the queue

---

## DELETE /queue/{queue_token}

Leave the matchmaking queue. No-op if the player has already been matched.

**Auth:** Bearer token

**Path parameters**

| Name | Type | Description |
|---|---|---|
| `queue_token` | UUID | Token returned by `POST /queue` |

**`204 No Content`**

**`401 Unauthorized`**
**`404 Not Found`** — queue token does not exist or belongs to a different user

---

## POST /matches/bot-vs-bot

Create a match between two bots. The caller has no role in the resulting match and is expected to watch it via the Watch feature. Useful for exhibition games and bot evaluation.

**Auth:** Bearer token (any authenticated user)

**Request body**

| Field | Type | Required | Description |
|---|---|---|---|
| `white_bot_id` | string | Yes | Bot id from `GET /bots` |
| `black_bot_id` | string | Yes | Bot id from `GET /bots` (may equal `white_bot_id`) |
| `time_format_id` | string | Yes | One of the `id` values returned by `GET /time-formats` |

```json
{
  "white_bot_id": "stockfish-5",
  "black_bot_id": "stockfish-3",
  "time_format_id": "5+0"
}
```

**`201 Created`**
```json
{
  "match_id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
}
```

The match starts immediately; Match Manager schedules the first bot move (white) on creation and chains subsequent moves automatically.

**`400 Bad Request`** — unknown `white_bot_id`, `black_bot_id`, or `time_format_id`
**`401 Unauthorized`**
