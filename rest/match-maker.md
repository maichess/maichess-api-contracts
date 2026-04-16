# Match Maker Service — REST API

**Base URL:** `http://match-maker-service`
**Implementation:** ASP.NET

Handles session initialisation. For human opponents the service queues the player and waits for a peer; for bot opponents it resolves immediately. In both cases, once an opponent is found, Match Maker calls `Matches.CreateMatch` on Match Manager via gRPC and streams the resulting `match_id` back to the client via SSE.

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

**`400 Bad Request`** — invalid `time_control` or unknown `bot_id`
**`401 Unauthorized`**
**`409 Conflict`** — player is already in the queue

---

## GET /queue/{queue_token}/events

Stream queue status via Server-Sent Events. The connection should be held open until a `match_found` event is received or the client calls `DELETE /queue/{queue_token}`.

**Auth:** Bearer token

**Path parameters**

| Name | Type | Description |
|---|---|---|
| `queue_token` | UUID | Token returned by `POST /queue` |

**Response:** `Content-Type: text/event-stream`

```
event: queued
data: {"estimated_wait_seconds": 45}

event: match_found
data: {"match_id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890"}
```

| Event | When emitted | Fields |
|---|---|---|
| `queued` | Once, immediately after joining | `estimated_wait_seconds` (0 for bot matches) |
| `match_found` | When an opponent is paired and a match has been created | `match_id` |

**`401 Unauthorized`**
**`404 Not Found`** — queue token does not exist or belongs to a different user

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
