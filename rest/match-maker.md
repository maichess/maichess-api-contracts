# Match Maker Service — REST API

**Base URL:** `http://match-maker-service`
**Implementation:** ASP.NET

Handles session initialisation. For human opponents the service queues the player and waits for a peer; for bot opponents it resolves immediately. In both cases, once an opponent is found, Match Maker calls `Matches.CreateMatch` on Match Manager via gRPC and then calls `Socket.EmitEvent` to push a `matched` event containing the `match_id` to the client.

---

## GET /bots

Return all available bots.

**Auth:** None

**`200 OK`**
```json
{
  "bots": [
    { "id": "bullet", "name": "Bullet", "elo": 1400 },
    { "id": "blitz", "name": "Blitz", "elo": 1700 }
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
| `time_control` | string | Yes | `bullet`, `blitz`, `rapid`, or `classical` |
| `opponent.type` | string | Yes | `human` or `bot` |
| `opponent.bot_id` | string | Conditional | Required when `opponent.type` is `bot` |

Human opponent:
```json
{
  "time_control": "blitz",
  "opponent": { "type": "human" }
}
```

Bot opponent:
```json
{
  "time_control": "rapid",
  "opponent": { "type": "bot", "bot_id": "stockfish-3" }
}
```

**`201 Created`**
```json
{
  "queue_token": "7e9f2b4a-1c3d-4e5f-8a6b-0c2d4e6f8a0b"
}
```

The `queue_token` identifies this queue slot for cancellation. When a match is found, the socket service delivers a `matched` event to the client — see `rest/socket.md`.

**`400 Bad Request`** — invalid `time_control` or unknown `bot_id`
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
