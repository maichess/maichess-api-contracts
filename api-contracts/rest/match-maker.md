# Match Maker Service — REST API

**Base URL:** `http://match-maker-service`
**Implementation:** ASP.NET

Handles session initialisation. For human opponents the service queues the player and waits for a peer; for bot opponents it resolves immediately. In both cases, once an opponent is found, Match Maker calls `Matches.CreateMatch` on Match Manager via gRPC and makes the resulting `match_id` available via a polling endpoint.

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
{ "queue_token": "7e9f2b4a-1c3d-4e5f-8a6b-0c2d4e6f8a0b" }
```

**`400 Bad Request`** — invalid `time_control` or unknown `bot_id`
**`401 Unauthorized`**
**`409 Conflict`** — player is already in the queue

---

## GET /queue/{queue_token}/status

Poll the current queue status. Clients should call this endpoint repeatedly until `status` is `matched`.

**Auth:** Bearer token

**Path parameters**

| Name | Type | Description |
|---|---|---|
| `queue_token` | UUID | Token returned by `POST /queue` |

**`200 OK`**

| Field | Type | Description |
|---|---|---|
| `status` | string | `waiting` or `matched` |
| `match_id` | string \| null | Present once `status` is `matched` |

While waiting:
```json
{ "status": "waiting", "match_id": null }
```

Once matched:
```json
{ "status": "matched", "match_id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890" }
```

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
