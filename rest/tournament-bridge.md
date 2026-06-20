# Tournament Bridge Service — REST API

**Base URL:** `http://tournament-bridge-service`
**Implementation:** ASP.NET

Proxies tournament lifecycle to an external tournament server, registers a maichess bot to play, drives moves via the Engine service, and mirrors each game into match-db as an `external` match. The bridge is the only maichess service that communicates with external tournament servers.

All tournament endpoints accept an optional `?server=<url>` query parameter to target a specific tournament server. When omitted, the configured default URL is used. The `/external/*` endpoints target fixed third-party providers (currently Lichess) and ignore `?server`.

Player objects in responses follow the same shape as Match Manager: `{"user_id": "...", "username": "..."}`, `{"bot_id": "...", "name": "..."}`, or `{"external_name": "..."}`.

---

## GET /tournaments

List tournaments from the target server, grouped by status.

**Auth:** Bearer token

**Query parameters**

| Name | Type | Required | Description |
|---|---|---|---|
| `server` | string | No | Tournament server URL (default: configured default) |

**`200 OK`**
```json
{
  "created": [
    {
      "id": "t7kXq2",
      "fullName": "Friday Night Bots",
      "clock": { "limit": 300, "increment": 3 },
      "nbPlayers": 4,
      "nbRounds": 5,
      "format": "swiss",
      "matchesPerPairing": 1,
      "startPosition": "standard",
      "createdBy": "userId"
    }
  ],
  "started": [],
  "finished": []
}
```

**`502 Bad Gateway`** — tournament server unreachable

---

## POST /tournaments

Create a tournament on the target server. The authenticated user becomes the director.

**Auth:** Bearer token

**Request body**

| Field | Type | Required | Description |
|---|---|---|---|
| `name` | string | Yes | Tournament name |
| `nbRounds` | integer | Yes | Number of rounds |
| `clockLimit` | integer | Yes | Base time in seconds |
| `clockIncrement` | integer | Yes | Increment per move in seconds |
| `rated` | boolean | No | Default: true |
| `format` | string | No | `swiss` (default), `singleElimination`, `doubleElimination`, `groupStage`, `league`, `randomKnockout` |
| `startPosition` | string | No | FEN or `standard` (default) |
| `opening` | string | No | Key of a named opening (see `GET /openings`). Takes precedence over `startPosition` |
| `openings` | string | No | Comma-separated opening keys forming a thematic book. Each pairing plays every listed position twice with reversed colours (`matchesPerPairing` becomes `2 * openings`). Takes precedence over `opening`/`startPosition` |
| `matchesPerPairing` | integer | No | Games per pairing (default: 1) |
| `groupSize` | integer | No | Required when format is `groupStage` |
| `maxConcurrentGames` | integer | No | Max games active at once per round; extras stay pending. Omit for unlimited |
| `server` | string | No | Tournament server URL |

```json
{
  "name": "Friday Night Bots",
  "nbRounds": 5,
  "clockLimit": 300,
  "clockIncrement": 3,
  "format": "swiss"
}
```

**`201 Created`** — Tournament object from the server, plus `registration_id` for tracking

```json
{
  "registration_id": "reg_abc123",
  "tournament": { "id": "t7kXq2", "status": "created", "..." : "..." }
}
```

**`400 Bad Request`** — invalid config
**`401 Unauthorized`**
**`502 Bad Gateway`** — tournament server unreachable

---

## GET /tournaments/{id}

Get tournament details including bracket, standings, and match-db mappings for mirrored games.

**Auth:** Bearer token

**Path parameters**

| Name | Type | Description |
|---|---|---|
| `id` | string | Tournament ID on the tournament server |

**Query parameters**

| Name | Type | Required | Description |
|---|---|---|---|
| `server` | string | No | Tournament server URL |

**`200 OK`**
```json
{
  "tournament": {
    "id": "t7kXq2",
    "fullName": "Friday Night Bots",
    "status": "started",
    "round": 2,
    "nbPlayers": 8,
    "nbRounds": 5,
    "format": "swiss",
    "clock": { "limit": 300, "increment": 3 },
    "standing": {
      "page": 1,
      "players": [
        { "rank": 1, "points": 1.5, "tieBreak": 3.0, "bot": { "id": "bot1", "name": "Engine1" }, "nbGames": 2, "wins": 1, "draws": 1, "losses": 0 }
      ]
    }
  },
  "registration": {
    "registration_id": "reg_abc123",
    "maichess_bot_id": "blitz-enhanced-3",
    "status": "active"
  },
  "game_mappings": [
    { "tournament_game_id": "j0nPtcjl", "match_db_id": "a1b2c3d4-..." }
  ]
}
```

**`404 Not Found`**

---

## DELETE /tournaments/{id}

Terminate a tournament. Only the director may terminate, and only while status is `created`.

**Auth:** Bearer token

**`204 No Content`**
**`401 Unauthorized`**
**`403 Forbidden`** — not the director
**`409 Conflict`** — tournament already started or finished

---

## POST /tournaments/{id}/start

Start the tournament. Only the director may start. Requires at least 2 joined bots.

After starting, the bridge opens the tournament NDJSON stream and begins driving games automatically.

**Auth:** Bearer token

**`200 OK`** — Tournament object with `status: "started"`

**`401 Unauthorized`**
**`403 Forbidden`** — not the director
**`409 Conflict`** — already started, or fewer than 2 bots

---

## POST /tournaments/{id}/register

Register a maichess bot for the tournament. The bridge registers a bot identity on the tournament server and joins the tournament.

**Auth:** Bearer token

**Request body**

| Field | Type | Required | Description |
|---|---|---|---|
| `bot_id` | string | Yes | Maichess bot to play (from `GET /bots`) |

```json
{ "bot_id": "blitz-enhanced-3" }
```

**`200 OK`**
```json
{
  "registration_id": "reg_abc123",
  "tournament_id": "t7kXq2",
  "bot_id": "blitz-enhanced-3",
  "status": "registered"
}
```

**`400 Bad Request`** — invalid bot_id
**`401 Unauthorized`**
**`409 Conflict`** — already registered, or tournament not in `created` status

---

## DELETE /tournaments/{id}/register

Withdraw the registered bot from the tournament. Only allowed while status is `created`.

**Auth:** Bearer token

**`204 No Content`**
**`409 Conflict`** — tournament already started

---

## POST /tournaments/{id}/participants

Add an already permanently-registered bot (see `GET /registry` / `POST /registry`) to
the tournament by its registry id. Only the director may do this, and only while the
tournament is in `created` status. The bridge mints a bot token for the registered bot
(the registry id is auth-backed) and sets up driving, so the bot plays automatically
once the tournament starts — exactly like `POST /tournaments/{id}/register`, but reusing
the permanent registry entry (stable id + analytics metadata) instead of an ephemeral
join.

**Auth:** Bearer token

**Request body**

| Field | Type | Required | Description |
|---|---|---|---|
| `bot_id` | string | Yes | Maichess bot to play (from `GET /bots`) |
| `registry_id` | string | Yes | Tournament-server registry id of the bot (from `GET /registry`) |

```json
{ "bot_id": "blitz-enhanced-3", "registry_id": "bot_ab12cd34" }
```

**`200 OK`**
```json
{
  "registration_id": "reg_abc123",
  "tournament_id": "t7kXq2",
  "bot_id": "blitz-enhanced-3",
  "registry_id": "bot_ab12cd34",
  "status": "registered"
}
```

**`400 Bad Request`** — unknown maichess `bot_id`, or unknown `registry_id`
**`403 Forbidden`** — caller is not the director of this tournament
**`409 Conflict`** — bot already registered, or tournament not in `created` status

---

## GET /tournaments/{id}/rounds/{round}

Get pairings for a round, enriched with match-db match IDs for mirrored games.

**Auth:** Bearer token

**`200 OK`**
```json
{
  "round": 2,
  "pairings": [
    {
      "white": { "id": "bot1", "name": "Engine1" },
      "black": { "id": "bot2", "name": "Engine2" },
      "gameId": "j0nPtcjl",
      "match_db_id": "a1b2c3d4-...",
      "winner": null
    }
  ]
}
```

---

## GET /tournaments/{id}/results

Get standings as JSON (not NDJSON — the bridge collects the stream for the client).

**Auth:** Bearer token

**`200 OK`**
```json
{
  "results": [
    { "rank": 1, "points": 3.5, "tieBreak": 9.0, "bot": { "id": "bot1", "name": "Engine1" }, "nbGames": 4, "wins": 3, "draws": 1, "losses": 0 }
  ]
}
```

---

## GET /tournaments/{id}/export

Export all games of the tournament as PGN. Proxies the tournament server's game export.

**Auth:** Bearer token

**Query parameters**

| Name | Type | Required | Description |
|---|---|---|---|
| `server` | string | No | Tournament server URL |

**`200 OK`** — `Content-Type: application/x-chess-pgn`; standard PGN, one game per block

**`404 Not Found`** — tournament not found
**`502 Bad Gateway`** — tournament server unreachable

---

## GET /tournaments/{id}/analytics

Proxy the tournament server's **analytics export** — a single, versioned JSON document
containing every game (UCI moves, winner, termination, timing) and the final standings,
with per-bot analytics metadata (family / strategy / engine / model version). This is the
structured data the maichess insights (Spark) pipeline ingests, and what the client's
tournament analytics view renders. Available only once the tournament is `finished`.

**Auth:** Bearer token

**Query parameters**

| Name | Type | Required | Description |
|---|---|---|---|
| `server` | string | No | Tournament server URL |

**`200 OK`** — `Content-Type: application/json`; the tournament server's `AnalyticsExport`
document (gate on its `schemaVersion`, currently `"1.0"`, before deserializing).

```json
{
  "schemaVersion": "1.0",
  "tournamentId": "t7kXq2",
  "format": "swiss",
  "clock": { "limit": 300, "increment": 3 },
  "rated": true,
  "nbRounds": 5,
  "exportedAt": "2025-06-17T12:31:00Z",
  "standings": [ { "botId": "bot_abc", "botName": "Engine1", "rank": 1, "points": 3.5, "wins": 3, "draws": 1, "losses": 0, "nbGames": 4, "tieBreak": 9.0 } ],
  "games": [ { "gameId": "j0nPtcjl", "round": 1, "whiteBotId": "bot_abc", "blackBotId": "bot_xyz", "winner": "white", "terminationReason": "checkmate", "totalPly": 5, "moves": "e2e4 f7f6 d2d4 g7g5 d1h5" } ]
}
```

**`404 Not Found`** — tournament not found
**`409 Conflict`** — tournament not finished yet
**`502 Bad Gateway`** — tournament server unreachable

---

## GET /tournaments/{id}/stream

Server-Sent Events (SSE) stream of tournament events. The bridge translates the tournament server's NDJSON stream into SSE for browser consumption.

**Auth:** Bearer token

**Event types** (sent as SSE `data:` lines):

```
event: tournamentStarted
data: {}

event: roundStarted
data: {"round": 1}

event: gameStart
data: {"round": 1, "gameId": "j0nPtcjl", "color": "white", "match_db_id": "a1b2c3d4-..."}

event: roundFinished
data: {"round": 1}

event: tournamentFinished
data: {"winner": {"id": "bot1", "name": "Engine1"}}
```

The `gameStart` event is enriched with `match_db_id` once the bridge creates the mirrored match. Game-level events (moves, game end) flow through the existing socket.io pipeline via match-db.

---

## GET /bots

List available maichess bots. Proxies to Engine Service `ListBots`.

**Auth:** Bearer token

**`200 OK`**
```json
{
  "bots": [
    { "id": "blitz-enhanced-3", "name": "Enhanced Blitz L3", "elo": 1800, "description": "..." }
  ]
}
```

---

## GET /openings

List named starting positions (the built-in opening catalog plus any custom entries)
from the target server, for use as the `opening` field when creating a tournament.
Proxies the tournament server's opening catalog.

**Auth:** Bearer token

**Query parameters**

| Name | Type | Required | Description |
|---|---|---|---|
| `server` | string | No | Tournament server URL |

**`200 OK`**
```json
{
  "openings": [
    { "key": "vienna", "name": "Vienna Opening", "fen": "rnbqkbnr/pppp1ppp/8/4p3/4P3/2N5/PPPP1PPP/R1BQKBNR b KQkq - 1 2" }
  ]
}
```

**`502 Bad Gateway`** — tournament server unreachable

---

## POST /openings

Register a **custom named starting position** (by FEN) on the target server, so it can be
used as the `opening` (or part of an `openings` book) when creating a tournament. The
`key` is derived from `name` when omitted; built-in catalog keys are reserved.

**Auth:** Bearer token

**Query parameters**

| Name | Type | Required | Description |
|---|---|---|---|
| `server` | string | No | Tournament server URL |

**Request body**

| Field | Type | Required | Description |
|---|---|---|---|
| `name` | string | Yes | Human-readable opening name |
| `fen` | string | Yes | FEN of the starting position |
| `key` | string | No | Explicit key; derived from `name` when omitted |

```json
{ "name": "London System", "fen": "rnbqkbnr/ppp1pppp/8/3p4/3P1B2/8/PPP1PPPP/RN1QKBNR b KQkq - 2 2" }
```

**`201 Created`** — the registered `Opening` (`{ "key", "name", "fen" }`)

**`400 Bad Request`** — invalid name or FEN
**`409 Conflict`** — key already exists (or collides with a reserved catalog key)

---

## GET /registry

List maichess bots permanently registered in the target server's bot registry.
Each entry is annotated with the matching maichess `bot_id` (by name) when one exists.

**Auth:** Bearer token

**Query parameters**

| Name | Type | Required | Description |
|---|---|---|---|
| `server` | string | No | Tournament server URL |

**`200 OK`**
```json
{
  "bots": [
    { "id": "bot_ab12cd34", "name": "Enhanced Blitz L3", "maichess_bot_id": "blitz-enhanced-3" }
  ]
}
```

**`502 Bad Gateway`** — tournament server unreachable

---

## POST /registry

Permanently register a maichess bot in the target server's bot registry so it can be
reused across tournaments. The registry id is auth-backed, so the bridge can drive the
bot's moves once it joins a tournament. Idempotent by bot name.

The bridge populates the server's analytics-grouping metadata for the registered bot:
`family` = `"maichess"`, `strategyType` = the engine variant id (the maichess `bot_id`),
and `engineType` = `"internal"`. These flow through into the tournament's analytics
export so bots can be grouped/compared by the insights pipeline.

**Auth:** Bearer token

**Request body**

| Name | Type | Required | Description |
|---|---|---|---|
| `bot_id` | string | Yes | Maichess bot to register (from `GET /bots`) |
| `server` | string | No | Tournament server URL (query parameter) |

**`200 OK`**
```json
{
  "id": "bot_ab12cd34",
  "name": "Enhanced Blitz L3",
  "maichess_bot_id": "blitz-enhanced-3"
}
```

**`400 Bad Request`** — unknown bot

---

## DELETE /registry/{id}

Remove a permanently registered bot from the target server's registry, by its registry id.

**Auth:** Bearer token

**Query parameters**

| Name | Type | Required | Description |
|---|---|---|---|
| `server` | string | No | Tournament server URL |

**`204 No Content`**

---

## GET /config

Get current bridge configuration.

**Auth:** Bearer token

**`200 OK`**
```json
{
  "default_server_url": "http://tournament-server:8080"
}
```

---

## PUT /config

Update bridge configuration.

**Auth:** Bearer token

**Request body**

| Field | Type | Required | Description |
|---|---|---|---|
| `default_server_url` | string | Yes | Default tournament server URL |

**`200 OK`** — updated config

---

## POST /external/lichess

Register a maichess bot to play an existing **Lichess** game. The bridge opens the
Lichess bot game stream, drives our moves with the Engine (engine-drives/we-mirror, same
model as tournament-server games), submits them to Lichess, and mirrors the game into
match-db as a read-only `external` match (provider `"lichess"`). The mirror match is
created from Lichess's `gameFull` handshake before the response returns, so the resulting
`match_id` is immediately watchable; the game then plays out in the background and lands
in Past Matches tagged **External** and **unrated** (`RecordMatchResult` is never called).

The Lichess account behind `lichess_token` must be a **bot account**, and the token must
carry the bot scopes (`bot:play`). Clocks from Lichess are milliseconds and are mirrored
verbatim (no conversion).

**Auth:** Bearer token

**Request body**

| Field | Type | Required | Description |
|---|---|---|---|
| `bot_id` | string | Yes | Maichess bot to play (from `GET /bots`) |
| `lichess_token` | string | Yes | Lichess bot-account OAuth token (bearer, `bot:play` scope) |
| `game_id` | string | Yes | Lichess game id to play (e.g. the id of an accepted challenge) |

```json
{ "bot_id": "blitz-enhanced-3", "lichess_token": "lip_xxx", "game_id": "j0nPtcjl" }
```

**`200 OK`** — the game is being mirrored; `match_id` is the watchable match-db id

```json
{
  "match_id": "a1b2c3d4-...",
  "provider": "lichess"
}
```

**`400 Bad Request`** — missing `bot_id` / `lichess_token` / `game_id`, or unknown `bot_id`
**`401 Unauthorized`**
**`502 Bad Gateway`** — the Lichess game stream could not be started (unknown game id, revoked token, Lichess unreachable)

---

## POST /external/lichess/challenge

Create a Lichess game by **challenging an opponent**, then drive + mirror it exactly like
`POST /external/lichess`. The opponent is either a Lichess username (challenge that user/bot
— the game starts once they accept) or the literal `"ai"` (play Lichess's Stockfish — the
game starts immediately). Returns the watchable maichess `match_id`.

Clock fields are in **seconds** (Lichess's challenge API unit). The bot account behind
`lichess_token` must have the `challenge:write` scope (plus `bot:play`).

**Auth:** Bearer token

**Request body**

| Field | Type | Required | Description |
|---|---|---|---|
| `bot_id` | string | Yes | Maichess bot to play (from `GET /bots`) |
| `lichess_token` | string | Yes | Lichess bot-account OAuth token (bearer) |
| `opponent` | string | Yes | Lichess username to challenge, or `"ai"` for Stockfish |
| `level` | integer | No | Stockfish level 1–8 (only when `opponent` is `"ai"`; default 1) |
| `clock_limit` | integer | No | Base time in **seconds** (default 300) |
| `clock_increment` | integer | No | Increment per move in **seconds** (default 0) |
| `rated` | boolean | No | Rated on Lichess (ignored for `"ai"`; default false) |

```json
{ "bot_id": "blitz-enhanced-3", "lichess_token": "lip_xxx", "opponent": "ai", "level": 4, "clock_limit": 300, "clock_increment": 2 }
```

**`200 OK`** — same shape as `POST /external/lichess` (`{ "match_id": "...", "provider": "lichess" }`)

**`400 Bad Request`** — missing `bot_id` / `lichess_token` / `opponent`, or unknown `bot_id`
**`401 Unauthorized`**
**`502 Bad Gateway`** — the challenge could not be created, or the game never started (e.g. a user challenge that was not accepted)
