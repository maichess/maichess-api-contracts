# Analysis Service — REST API

**Base URL:** `http://analysis-service`
**Implementation:** ASP.NET

Manages saved analysis games and analysis sessions. Engine analysis results are streamed to the
client via `analysis_update` socket events on the shared socket.io connection — not via a separate
WebSocket. These REST endpoints cover game persistence and session lifecycle.

All endpoints require a valid Bearer token. The authenticated user is the implicit owner of all
operations.

---

## Games

### GET /games

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

### GET /games/{id}

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
  "starting_fen": "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
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
order; `fens[0]` is the position after `moves[0]`. `starting_fen` is the position before any
moves; for standard games this is always the initial chess position.

**`401 Unauthorized`**
**`403 Forbidden`** — game belongs to another user
**`404 Not Found`**

---

### POST /games

Import a game from PGN and save it.

**Auth:** Bearer token

**Request body**

| Field | Type | Required | Description |
|---|---|---|---|
| `pgn` | string | Yes | Full PGN string (single game; multi-game PGN is rejected) |

**`201 Created`** — returns the full game object (same schema as `GET /games/{id}`)

**`400 Bad Request`** — PGN could not be parsed; body contains `{"error": "reason"}`
**`401 Unauthorized`**

---

### POST /games/from-match/{match_id}

Import a finished match from match-db as a saved analysis game. The match must have ended.
Duplicates are allowed — the user may import the same match multiple times.

**Auth:** Bearer token — must be a participant of the match

**Path parameters**

| Name | Type | Description |
|---|---|---|
| `match_id` | UUID | Match ID |

**`201 Created`** — returns the full game object (same schema as `GET /games/{id}`)

**`400 Bad Request`** — match is still ongoing
**`401 Unauthorized`**
**`403 Forbidden`** — user was not a participant
**`404 Not Found`** — match does not exist

---

### POST /games/from-fen

Create an analysis game from an arbitrary FEN position with no moves. The FEN becomes the
starting position; whatif moves can be explored once a session is opened.

**Auth:** Bearer token

**Request body**

| Field | Type | Required | Description |
|---|---|---|---|
| `fen` | string | Yes | Starting position in FEN notation |

**`201 Created`** — returns the full game object (same schema as `GET /games/{id}`, `moves` and `fens` are empty)

**`400 Bad Request`** — FEN is invalid; body contains `{"error": "reason"}`
**`401 Unauthorized`**

---

## Analysis Configuration

### GET /analysis/config

Returns the server's recommended default analysis bot and the full list of available bots.
Clients should default to `default_bot_id` and only expose bot switching in an advanced settings
menu. Only analysis for the default bot is cached; switching bots disables caching for that
session.

**Auth:** Bearer token

**`200 OK`**
```json
{
  "default_bot_id": "stockfish-5",
  "default_line_count": 3,
  "bots": [
    { "id": "stockfish-3", "name": "Stockfish Level 3", "elo": 1400 },
    { "id": "stockfish-5", "name": "Stockfish Level 5", "elo": 2000 }
  ]
}
```

**`401 Unauthorized`**

---

## Sessions

A session represents one active analysis context: a game with a current board position and an
optional running engine stream. Only one session per user exists at a time — creating a new
session auto-cancels the previous one.

Sessions are in-memory only. They are not persisted across server restarts; if the server
restarts the client must create a new session.

### POST /sessions

Create a new analysis session for a saved game. If the user already has an active session it is
destroyed first (analysis stopped, session removed).

**Auth:** Bearer token

**Request body**

| Field | Type | Required | Description |
|---|---|---|---|
| `game_id` | UUID | Yes | ID of a saved analysis game owned by the authenticated user |
| `bot_id` | string | Yes | Engine to use for analysis |
| `line_count` | integer | Yes | Number of principal variations (1–5) |

**`201 Created`**
```json
{
  "session_id": "s-7f3a2b1c",
  "game_id": "c3d4e5f6-...",
  "current_index": 0,
  "current_fen": "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
  "whatif_moves": [],
  "analysis_running": false
}
```

`current_index` is the position in the game's move list (0 = starting position, N = after Nth
move). Analysis does not start automatically; call `POST /sessions/{id}/analysis` to begin.

**`401 Unauthorized`**
**`403 Forbidden`** — game belongs to another user
**`404 Not Found`** — game does not exist

---

### DELETE /sessions/{id}

Stop analysis (if running) and destroy the session.

**Auth:** Bearer token — must be the session owner

**`204 No Content`**

**`401 Unauthorized`**
**`403 Forbidden`**
**`404 Not Found`**

---

## Session Navigation

All navigation endpoints cancel any running analysis and restart it at the new position.
They also clear the active whatif branch.

### POST /sessions/{id}/navigate

Jump to a specific position in the game's move history. Clears whatif moves.

**Auth:** Bearer token

**Request body**

| Field | Type | Required | Description |
|---|---|---|---|
| `index` | integer | Yes | Target position index (0 = starting position, N = after Nth move, max = `moves.length`) |

**`200 OK`**
```json
{
  "current_index": 5,
  "current_fen": "r1bqkb1r/pppp1ppp/2n2n2/4p3/2B1P3/5N2/PPPP1PPP/RNBQK2R w KQkq - 4 4"
}
```

**`400 Bad Request`** — index out of range
**`401 Unauthorized`**
**`403 Forbidden`**
**`404 Not Found`** — session does not exist

---

## Whatif Branch

Whatif moves are speculative moves layered on top of the current game position. They are never
saved to the game. Any navigation action (`POST /sessions/{id}/navigate`) clears the whatif
branch.

### POST /sessions/{id}/whatif

Play a speculative move from the current position (game position at `current_index`, or the tip
of the existing whatif branch). Restarts analysis at the resulting position.

**Auth:** Bearer token

**Request body**

| Field | Type | Required | Description |
|---|---|---|---|
| `move` | string | Yes | Move in UCI notation (e.g. `e2e4`, `e7e8q`) |

**`200 OK`**
```json
{
  "whatif_index": 1,
  "current_fen": "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"
}
```

`whatif_index` is the number of whatif moves now on the stack.

**`400 Bad Request`** — move is illegal
**`401 Unauthorized`**
**`403 Forbidden`**
**`404 Not Found`**

---

### DELETE /sessions/{id}/whatif

Reset the entire whatif branch, returning to the game position at `current_index`. Restarts
analysis at the game position.

**Auth:** Bearer token

**`200 OK`**
```json
{
  "current_index": 5,
  "current_fen": "..."
}
```

**`401 Unauthorized`**
**`403 Forbidden`**
**`404 Not Found`**

---

### DELETE /sessions/{id}/whatif/last

Undo the most recent whatif move. Restarts analysis at the resulting position. If no whatif
moves are active, returns `400`.

**Auth:** Bearer token

**`200 OK`**
```json
{
  "whatif_index": 0,
  "current_fen": "..."
}
```

**`400 Bad Request`** — no whatif moves to undo
**`401 Unauthorized`**
**`403 Forbidden`**
**`404 Not Found`**

---

### GET /sessions/{id}/whatif/pgn

Export the current whatif branch as a PGN string. The PGN starts from the game position at
`current_index` (using `[FEN]` and `[SetUp "1"]` headers) and contains only the whatif moves in
SAN notation. Returns `400` if no whatif moves are active.

**Auth:** Bearer token

**`200 OK`**
```json
{
  "pgn": "[FEN \"r1bqkb1r/.../...\"]\n[SetUp \"1\"]\n\n1. e4 e5 2. Nf3 *"
}
```

**`400 Bad Request`** — no whatif moves active
**`401 Unauthorized`**
**`403 Forbidden`**
**`404 Not Found`**

---

## Analysis Control

### POST /sessions/{id}/analysis

Start or restart analysis at the current position. If analysis is already running for this
session it is cancelled and restarted. Optional overrides allow switching bot or line count for
this restart.

**Auth:** Bearer token

**Request body (all fields optional)**

| Field | Type | Description |
|---|---|---|
| `bot_id` | string | Override the session's bot for this analysis run |
| `line_count` | integer | Override the session's line count (1–5) |

**`204 No Content`** — analysis started; updates arrive via socket events

**`401 Unauthorized`**
**`403 Forbidden`**
**`404 Not Found`**

---

### DELETE /sessions/{id}/analysis

Stop the running engine stream. The session remains alive with its current position intact.

**Auth:** Bearer token

**`204 No Content`**

**`401 Unauthorized`**
**`403 Forbidden`**
**`404 Not Found`**
