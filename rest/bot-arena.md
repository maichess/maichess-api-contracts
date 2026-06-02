# Bot Arena Service — REST API

**Base URL:** `http://bot-arena-service`
**Implementation:** ASP.NET

Orchestrates bot-vs-bot **setups**. A setup is persisted as a **collection** that
expands into an ordered list of bot-vs-bot games. The service spawns those games
through Match Maker's `POST /matches/bot-vs-bot` path (respecting a global
concurrency limit), observes their outcomes via Match Manager, and stores a typed
result view once the collection finishes. Live games are watchable through the
ordinary Watch flow.

Three setup types:

- **tournament** — all bots seeded into a random single-elimination bracket. Each
  pairing ("stage") plays `fens_per_stage` positions drawn from `fen_list`. In
  `both_colors` mode each chosen position is played twice (colors swapped); in
  `random` mode each chosen position is played once with random colors. The stage
  winner advances; ties are broken by: more wins → fewer rounds-to-win → greater
  aggregate clock advantage → greater aggregate final material → a seeded coin
  flip. Non-power-of-2 fields receive byes.
- **matrix** — round-robin: every unordered bot pair plays, for each FEN,
  `games_per_fen` games with colors always alternating.
- **single** — one match-up between two bots over a FEN list, `games_per_fen`
  games per FEN, with an optional `keep_switching_colors` toggle.

FEN-list semantics: an empty list or `["standard"]` means the standard start
position.

**Auth:** All endpoints require a Bearer token (any authenticated user). The
authenticated user is recorded as the collection's `created_by` and propagated as
the initiator of every spawned match.

---

## POST /collections

Create a setup. The service resolves the config, persists the collection as
`pending`, and begins scheduling its games.

**Request body**

Exactly one of `tournament`, `matrix`, or `single` must be present; it selects the
setup type.

| Field | Type | Required | Description |
|---|---|---|---|
| `name` | string | Yes | Display name for the collection |
| `tournament` | object | Conditional | Tournament config (see below) |
| `matrix` | object | Conditional | Matrix config |
| `single` | object | Conditional | Single config |

`tournament`:

| Field | Type | Required | Description |
|---|---|---|---|
| `bot_ids` | string[] | Yes | Bots to seed (≥ 2) |
| `fen_list` | string[] | No | Pool of start positions; empty/`["standard"]` ⇒ standard |
| `fens_per_stage` | int | Yes | Positions each stage plays (clamped to pool size, ≥ 1) |
| `color_mode` | string | Yes | `both_colors` or `random` |
| `time_format_id` | string | Yes | One of Match Maker's `GET /time-formats` ids |

`matrix`:

| Field | Type | Required | Description |
|---|---|---|---|
| `bot_ids` | string[] | Yes | Bots in the round-robin (≥ 2) |
| `fen_list` | string[] | No | Empty/`["standard"]` ⇒ standard |
| `games_per_fen` | int | Yes | Games per FEN per pair (≥ 1) |
| `time_format_id` | string | Yes | Time-format id |

`single`:

| Field | Type | Required | Description |
|---|---|---|---|
| `white_bot_id` | string | Yes | Bot id |
| `black_bot_id` | string | Yes | Bot id (may equal `white_bot_id`) |
| `fen_list` | string[] | No | Empty/`["standard"]` ⇒ standard |
| `games_per_fen` | int | Yes | Games per FEN (≥ 1) |
| `keep_switching_colors` | bool | No | Alternate colors per game when true (default false) |
| `time_format_id` | string | Yes | Time-format id |

```json
{
  "name": "Friday night blitz",
  "single": {
    "white_bot_id": "bullet",
    "black_bot_id": "blitz",
    "fen_list": ["standard"],
    "games_per_fen": 4,
    "keep_switching_colors": true,
    "time_format_id": "5+0"
  }
}
```

**`201 Created`** — the created collection (see the collection shape under
`GET /collections/{id}`).

**`400 Bad Request`** — no config / more than one config present, unknown
`bot_id` or `time_format_id`, fewer than 2 bots where required, non-positive
counts, or an invalid FEN.
**`401 Unauthorized`**

---

## GET /collections

List collections, newest first.

**Query parameters**

| Name | Type | Description |
|---|---|---|
| `status` | string | Optional: `pending` \| `running` \| `finished`. Omit for all. |
| `limit` | int | Defaults to 20; capped at 100 |
| `offset` | int | Defaults to 0 |

**`200 OK`**
```json
{
  "collections": [
    {
      "id": "…", "name": "Friday night blitz", "type": "single",
      "created_by": "…", "status": "finished",
      "created_at_ms": 1717200000000, "finished_at_ms": 1717200600000,
      "progress": { "total_games": 4, "finished_games": 4, "running_games": 0, "pending_games": 0 }
    }
  ]
}
```

List items omit the heavy `config`/`result` payloads; fetch a single collection
for the full typed view.

**`401 Unauthorized`**

---

## GET /collections/{id}

Get a collection with its resolved config, live progress, and typed result view.

**`200 OK`**
```json
{
  "id": "…",
  "name": "Friday night blitz",
  "type": "single",
  "created_by": "…",
  "status": "finished",
  "created_at_ms": 1717200000000,
  "finished_at_ms": 1717200600000,
  "config": {
    "single": {
      "white_bot_id": "bullet", "black_bot_id": "blitz",
      "fen_list": ["standard"], "games_per_fen": 4,
      "keep_switching_colors": true,
      "time_format": { "id": "5+0", "base_ms": 300000, "increment_ms": 0, "category": "blitz" }
    }
  },
  "progress": { "total_games": 4, "finished_games": 4, "running_games": 0, "pending_games": 0 },
  "result": {
    "single_series": {
      "bot_a_id": "bullet", "bot_b_id": "blitz",
      "bot_a_score": 2.5, "bot_b_score": 1.5,
      "games": [
        { "match_id": "…", "fen": "…", "fen_label": "Standard", "white_bot_id": "bullet", "black_bot_id": "blitz", "result": "white_won", "order": 0 }
      ]
    }
  }
}
```

The `result` object holds exactly one of `bracket` (tournament), `matrix_table`
(matrix), or `single_series` (single). Result views:

- **bracket**: `{ "rounds": [{ "round_number": 1, "pairings": [{ "bot_a_id", "bot_b_id", "bye", "winner_bot_id", "bot_a_score", "bot_b_score", "games": [GameResult] }] }], "winner_bot_id" }`
- **matrix_table**: `{ "bot_ids": [...], "cells": [{ "bot_a_id", "bot_b_id", "bot_a_score", "bot_b_score" }], "games": [GameResult] }`
- **single_series**: `{ "bot_a_id", "bot_b_id", "bot_a_score", "bot_b_score", "games": [GameResult] }`

A `GameResult` is `{ "match_id", "fen", "fen_label", "white_bot_id", "black_bot_id", "result", "order" }` where `result` is one of `ongoing` \| `white_won` \| `black_won` \| `draw`.

**`401 Unauthorized`**
**`404 Not Found`** — no collection with that id

---

## GET /concurrency-limit

Return the global maximum number of arena games allowed in flight at once. This is
a single global value shared by all users.

**`200 OK`**
```json
{ "limit": 4 }
```

**`401 Unauthorized`**

---

## PUT /concurrency-limit

Set the global concurrency limit. Editable by any authenticated user (not
per-user). The scheduler picks up the new value for subsequent launches.

**Request body**
```json
{ "limit": 8 }
```

**`200 OK`**
```json
{ "limit": 8 }
```

**`400 Bad Request`** — `limit` < 1
**`401 Unauthorized`**
