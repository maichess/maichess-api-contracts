# Analysis Service — REST API

**Base URL:** `http://analysis-service`
**Implementation:** ASP.NET

Manages saved analysis games and supports PGN import. Engine analysis itself is streamed to the
client via a dedicated WebSocket service (see `grpc-overview.md`); these REST endpoints cover
game persistence only.

All endpoints require a valid Bearer token. The authenticated user is the implicit owner of all
operations — a user can only access their own saved games.

---

## GET /games

List all saved analysis games for the authenticated user.

**Auth:** Bearer token

**Query parameters**

| Name | Type | Required | Description |
|---|---|---|---|
| `page` | integer | No | 1-based page number (default: 1) |
| `page_size` | integer | No | Results per page, max 100 (default: 20) |

**`200 OK`**
```json
{
  "games": [
    {
      "id": "c3d4e5f6-a7b8-9012-cdef-345678901234",
      "source": "pgn",
      "white": { "name": "Fischer" },
      "black": { "name": "Spassky" },
      "result": "1-0",
      "move_count": 41,
      "created_at": "2026-04-24T10:30:00Z",
      "tags": { "Event": "World Championship", "Date": "1972.07.11" }
    },
    {
      "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
      "source": "match",
      "match_id": "b2c3d4e5-f6a7-8901-bcde-f12345678901",
      "white": { "user_id": "3f2504e0-...", "username": "alice" },
      "black": { "bot_id": "stockfish-3", "name": "Stockfish Level 3" },
      "result": "0-1",
      "move_count": 38,
      "created_at": "2026-04-24T09:00:00Z",
      "tags": {}
    }
  ],
  "total": 2,
  "page": 1,
  "page_size": 20
}
```

**`401 Unauthorized`**

---

## GET /games/{id}

Return a single saved analysis game including the full move list and PGN.

**Auth:** Bearer token

**Path parameters**

| Name | Type | Description |
|---|---|---|
| `id` | UUID | Analysis game ID |

**`200 OK`**
```json
{
  "id": "c3d4e5f6-a7b8-9012-cdef-345678901234",
  "source": "pgn",
  "white": { "name": "Fischer" },
  "black": { "name": "Spassky" },
  "result": "1-0",
  "moves": ["e2e4", "e7e5", "g1f3", "b8c6"],
  "fens": [
    "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1",
    "rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq e6 0 2"
  ],
  "pgn": "[Event \"World Championship\"] ...",
  "created_at": "2026-04-24T10:30:00Z",
  "tags": { "Event": "World Championship", "Date": "1972.07.11" }
}
```

`moves` is the full UCI move list, oldest first. `fens` is the FEN after each move in the same
order; `fens[0]` is the position after `moves[0]`. The starting FEN is always the standard
opening position and is not included.

**`401 Unauthorized`**
**`403 Forbidden`** — game belongs to another user
**`404 Not Found`**

---

## POST /games

Import a game from PGN and save it. The PGN is parsed to extract moves and standard headers.
Non-standard or missing headers are tolerated.

**Auth:** Bearer token

**Request body**

| Field | Type | Required | Description |
|---|---|---|---|
| `pgn` | string | Yes | Full PGN string (single game; multi-game PGN is rejected) |

```json
{
  "pgn": "[Event \"World Championship\"]\n[White \"Fischer\"]\n[Black \"Spassky\"]\n...\n\n1. e4 e5 2. Nf3 Nc6 ..."
}
```

**`201 Created`** — returns the full game object (same schema as `GET /games/{id}`)

**`400 Bad Request`** — PGN could not be parsed; body contains `{"error": "reason"}`
**`401 Unauthorized`**

---

## POST /games/from-match/{match_id}

Import a finished match from the Match Manager as a saved analysis game. The match must have
ended. If the user has already imported this match, a new copy is still created (duplicates are
allowed — the user may want multiple annotation attempts).

**Auth:** Bearer token — must be a participant of the match or the match must be public

**Path parameters**

| Name | Type | Description |
|---|---|---|
| `match_id` | UUID | Match ID from the Match Manager |

**`201 Created`** — returns the full game object (same schema as `GET /games/{id}`)

**`400 Bad Request`** — match is still ongoing
**`401 Unauthorized`**
**`403 Forbidden`** — match is private and the requestor was not a participant
**`404 Not Found`** — match does not exist

---

## DELETE /games/{id}

Delete a saved analysis game.

**Auth:** Bearer token — must be the owner

**Path parameters**

| Name | Type | Description |
|---|---|---|
| `id` | UUID | Analysis game ID |

**`204 No Content`**

**`401 Unauthorized`**
**`403 Forbidden`** — game belongs to another user
**`404 Not Found`**
