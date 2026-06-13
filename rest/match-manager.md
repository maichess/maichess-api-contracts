# Match Manager Service — REST API

**Base URL:** `http://match-manager-service`
**Implementation:** ASP.NET

Accepts player moves and serves match state. Player moves and resignations are accepted as commands (published to `match.commands.v1`) and projected asynchronously; move validation is delegated to Move Validator via gRPC, and native bot moves are event-sourced — the Engine consumes the move loop and replies with the bot's move on `match.events.v1`. Real-time events (moves, match end, draw offers) are pushed to clients via the socket service — Match Manager publishes an `OutboundEvent` to `socket.outbound.v1` (match-targeted) after each state change so both participants and spectators receive them.

Player objects in responses are either a user `{"user_id": "...", "username": "..."}` or a bot `{"bot_id": "...", "name": "..."}`.

A match's clock rules are represented by the `time_format` object:

```json
"time_format": { "id": "5+0", "base_ms": 300000, "increment_ms": 0, "category": "blitz" }
```

The canonical catalogue of presets is served by Match Maker at `GET /time-formats`.

---

## GET /matches

List matches filtered by status. Used by the Watch feature on the client.

**Auth:** Bearer token

**Query parameters**

| Name | Type | Required | Description |
|---|---|---|---|
| `status` | string | No | `ongoing` (default). Future revisions may add `ended` / `all`. |
| `category` | string | No | Filter by time-format category: `bullet`, `blitz`, `rapid`, or `classical` |
| `page` | integer | No | 1-based page number (default: 1) |
| `page_size` | integer | No | Results per page, max 100 (default: 20) |

**`200 OK`**
```json
{
  "matches": [
    {
      "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
      "white": { "user_id": "3f2504e0-...", "username": "alice" },
      "black": { "bot_id": "stockfish-3", "name": "Stockfish Level 3" },
      "status": "ongoing",
      "time_format": { "id": "5+0", "base_ms": 300000, "increment_ms": 0, "category": "blitz" },
      "white_time_ms": 179500,
      "black_time_ms": 180000,
      "last_move_at_ms": 1714300000000,
      "finished_at_ms": 0,
      "move_count": 12,
      "created_by": { "user_id": "3f2504e0-...", "username": "alice" },
      "source": "native",
      "external_provider": ""
    }
  ],
  "total": 1,
  "page": 1,
  "page_size": 20
}
```

The compact summary intentionally omits `current_fen` and `moves`; clients open a specific match via `GET /matches/{id}` to fetch the full state.

`finished_at_ms` is a Unix timestamp in milliseconds at which the match ended, or `0` while it is still ongoing. `created_by` is the player who initiated the match (the human participant for normal matches, or the human who started a bot-vs-bot game); it may be absent. `source` is `native` or `external`; `external_provider` names the originating platform when `source` is `external` (e.g. `lichess`, `tournament-server`) and is empty otherwise.

**`400 Bad Request`** — invalid `status` or `category`
**`401 Unauthorized`**

---

## GET /users/{user_id}/matches

List the Past Matches for a user: every match the user played (as white or black) **or** started (`created_by`), regardless of which colour they were. Filtered to ended matches by default and ordered newest first. Used by the Past Matches view under the client's Profile tab.

Bot-vs-bot games a user spawns are listed here via `created_by` attribution even though the user occupied neither colour.

**Auth:** Bearer token — a user may only list their own matches.

**Path parameters**

| Name | Type | Description |
|---|---|---|
| `user_id` | UUID | The user whose matches to list |

**Query parameters**

| Name | Type | Required | Description |
|---|---|---|---|
| `status` | string | No | `ended` (default). |
| `page` | integer | No | 1-based page number (default: 1) |
| `page_size` | integer | No | Results per page, max 100 (default: 20) |

**`200 OK`** — same paginated envelope and summary schema as `GET /matches` (including `created_by`, `source`, `external_provider`, and `finished_at_ms`), ordered by `finished_at_ms` descending.

**`400 Bad Request`** — invalid `status`
**`401 Unauthorized`**
**`403 Forbidden`** — `user_id` is not the authenticated user

---

## GET /matches/search

Global, filterable, chronological browse over **every** match on the platform — native human games, arena bot-vs-bot games, and mirrored external games — surfaced uniformly with their source tag. Powers the Dev "All games" browser.

This is a **cross-user** query and complements `maichess-search-service`'s `GET /search/matches`: that endpoint does per-user faceted/full-text/position search over the Elasticsearch read model (best-effort id labels); this one returns a chronological feed across all users with match-manager-resolved player labels (usernames and bot names) and first-class **initiator** attribution. Use search-service for full-text/opening/position lookups; use this for structured chronological browsing.

**Auth:** Bearer token. The data is consistent with the existing access model (ended matches readable via `GET /matches/{id}`, ongoing ones already listed by Watch), so a global chronological list widens nothing. The **UI** is `dev_mode`-gated; the client proxy (which already has the user) is the place for any additional server-side dev gating — match-manager is not coupled to user-service for this.

**Query parameters**

| Name | Type | Required | Description |
|---|---|---|---|
| `player_id` | UUID | No | Restrict to matches where this user is white **or** black. Empty = no participant filter. |
| `initiator_id` | UUID | No | Restrict to matches whose `created_by` is this user. Empty = no filter; primarily meaningful for bot-vs-bot games. |
| `status` | string | No | `all` (default), `ongoing`, or `ended`. |
| `source` | string | No | `all` (default), `native`, or `external`. |
| `since_ms` | integer | No | Inclusive lower bound on `finished_at_ms` (Unix ms). `0`/omitted = unbounded. |
| `until_ms` | integer | No | Inclusive upper bound on `finished_at_ms` (Unix ms). `0`/omitted = unbounded. |
| `ascending` | boolean | No | Chronological sort direction over `finished_at_ms`. `false` (default) = newest first; `true` = oldest first. |
| `page` | integer | No | 1-based page number (default: 1) |
| `page_size` | integer | No | Results per page, max 100 (default: 20) |

When `player_id` and `initiator_id` are both supplied they are **AND**ed (matches where the user participates *and* the initiator started it). The `since_ms`/`until_ms` bounds key on `finished_at_ms`, so ongoing matches (`finished_at_ms = 0`) fall outside any positive lower bound.

**`200 OK`** — same paginated envelope and summary schema as `GET /matches` (including `created_by`, `source`, `external_provider`, and `finished_at_ms`), ordered by `finished_at_ms` (descending unless `ascending=true`).

**`400 Bad Request`** — invalid `status` or `source`
**`401 Unauthorized`**

---

## GET /matches/{id}

Return the current state of a match. Any authenticated user may read an ongoing match (so spectators in Watch mode can load the board); access to finished matches is restricted to participants.

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
  "time_format": { "id": "3+2", "base_ms": 180000, "increment_ms": 2000, "category": "blitz" },
  "white_time_ms": 179500,
  "black_time_ms": 180000,
  "last_move_at_ms": 1714300000000,
  "finished_at_ms": 0,
  "created_by": { "user_id": "3f2504e0-...", "username": "alice" },
  "source": "native",
  "external_provider": "",
  "analyzable": false
}
```

`status` is one of: `ongoing`, `white_won`, `black_won`, `draw`

`last_move_at_ms` is a Unix timestamp in milliseconds indicating when the last move (or match creation) occurred. Combined with `white_time_ms` / `black_time_ms`, clients can compute the active player's current remaining time: `remaining = time_ms - (now - last_move_at_ms)`.

`analyzable` is `true` when position navigation is available for this match: either at least one
side is a bot, or the match has ended. Used by the client to show or hide the "Analyze" button.
The Analysis service determines import eligibility independently by reading match-db directly.

**`401 Unauthorized`**
**`403 Forbidden`** — match has ended and the requestor is not a participant
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
| `premove` | boolean | No | The move was committed as a pre-move (queued before the opponent moved). Default `false`. |

```json
{ "move": "e2e4", "premove": false }
```

`premove` is client-asserted and rides the move events (`MoveSubmitted.premove` →
`MoveApplied.premove`) so anti-cheat can exempt the ply from think-time analysis —
pre-moves legitimately arrive with near-zero think time. It has no effect on move
validation or clocks, and it only *reduces timing-based* suspicion (correlation evidence
is unaffected), so asserting it on every move cannot mask engine assistance.

**`202 Accepted`** — the move command was accepted; the response has no body. The move is
validated and applied asynchronously (the event-sourced move loop), and the authoritative
result is delivered over the socket connection as a `move_made` event (or `match_ended`
when the move ends the game). Clients apply the move optimistically and reconcile on the
socket event. Move legality is decided asynchronously, so an illegal move is accepted here
(202) and surfaced over the socket rather than as a `400`.

After a move is applied the mover's clock is decremented by the time they took, then `increment_ms` is added back when the match is still ongoing. A move that triggers a timeout or game-ending result does not receive the increment.

**`401 Unauthorized`**
**`403 Forbidden`** — not a participant, or not the requestor's turn
**`404 Not Found`** — no such match
**`409 Conflict`** — match has already ended

---

## POST /matches/{id}/resign

Forfeit the match on behalf of the authenticated player.

**Auth:** Bearer token — must be a participant

**Path parameters**

| Name | Type | Description |
|---|---|---|
| `id` | UUID | Match ID |

**`202 Accepted`** — the resignation command was accepted; the response has no body. The
match end is applied asynchronously and delivered over the socket as a `match_ended` event
(`status` `white_won` or `black_won`, reason `resignation`).

**`401 Unauthorized`**
**`403 Forbidden`** — not a participant
**`404 Not Found`** — no such match
**`409 Conflict`** — match has already ended

> **Draw offers** (`POST /matches/{id}/draw-offer`, `POST .../draw-offer/accept`,
> `DELETE .../draw-offer`) follow the same command model: each returns **`202 Accepted`**
> and the result is delivered over the socket (`draw_offered`, `match_ended` with reason
> `draw_agreement`, or `draw_declined`).

---

## GET /leaderboard

Ranked list of players by Glicko-2 rating, highest first. Served from a Redis sorted set
(`leaderboard:rating`) maintained from the rating event stream — ranked reads are O(log N)
with no database scan. Flagged players (anti-cheat) are excluded; provisional ratings (still
high deviation) are annotated but included.

**Auth:** Bearer token

**Query parameters**

| Name | Type | Required | Description |
|---|---|---|---|
| `limit` | integer | No | Number of entries to return, 1–200 (default: 50) |

**`200 OK`**
```json
{
  "entries": [
    {
      "rank": 1,
      "user_id": "3f2504e0-...",
      "username": "alice",
      "elo": 1842,
      "rating_deviation": 58.3,
      "provisional": false
    }
  ],
  "total": 1273
}
```

`rank` is the 1-based position in the full board (so the list may skip ranks where flagged
players are hidden). `elo` is the Glicko-2 rating rounded. `provisional` is `true` while the
rating deviation is still high (a new or inactive account). `username` is omitted if not yet
known. `total` is the number of rated players on the board (independent of `limit`).

**`401 Unauthorized`**

---

## GET /leaderboard/rank/{user_id}

The given user's own position on the leaderboard (ZREVRANK), without fetching the whole list.

**Auth:** Bearer token

**Path parameters**

| Name | Type | Description |
|---|---|---|
| `user_id` | UUID | The user whose rank to look up |

**`200 OK`**
```json
{
  "entry": {
    "rank": 312,
    "user_id": "3f2504e0-...",
    "username": "alice",
    "elo": 1421,
    "rating_deviation": 72.0,
    "provisional": false
  },
  "total": 1273
}
```

**`401 Unauthorized`**
**`404 Not Found`** — the user is not on the board (no rated game yet)

---

## External match fields

Match summaries and full match objects include the following fields for external-game support:

| Field | Type | Description |
|---|---|---|
| `source` | string | `native` (default) or `external` |
| `external_provider` | string | Provider name when `source` is `external` (e.g. `tournament-server`); empty for native |
| `external_ref` | string | Opaque ID linking to the game on the external provider (e.g. tournament-server gameId); empty for native |

External matches are created by the Tournament Bridge Service via gRPC `CreateMatch(source = EXTERNAL)` and updated via `SyncExternalMatch`. They bypass move validation, bot-move scheduling, and player-stat recording. Socket events (`move_made`, `match_ended`) are still broadcast so Watch spectators receive real-time updates.

Players on external matches may include `external_name` identity (alongside existing `user_id` and `bot_id`) for opponents that are not maichess entities:

```json
{ "external_name": "OpponentBot" }
```
