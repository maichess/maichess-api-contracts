# Search Service — REST API

**Base URL:** `http://search-service`
**Implementation:** ASP.NET

Search over derived **Elasticsearch** read models (maichess-knowledge-base/tasks/implemented/13,
[search-elasticsearch.md](../../maichess-knowledge-base/search-elasticsearch.md)). The indexes are
projected from Mongo via CDC (`match.cdc.v1`) + the analysis-game change stream; **Elasticsearch
is never a source of truth** — results carry ids + summary fields and the client hydrates detail
from the owning service (analysis-service / match-manager).

**Auth:** All endpoints require a Bearer token (same shared JWT + `access_token` cookie fallback
as the other services). Results are scoped to the authenticated user:

- `/search/games` and `/search/matches` return only the caller's own games/matches.
- `/search/positions` searches within the caller's own games/matches.

Paging is `page` (1-based) + `page_size` (default 20, max 100), mirroring the match-manager list
contracts. All JSON fields are `snake_case`.

---

## Name matching (games vs matches)

Free-text (`q`) and `opponent` match against a **searchable name blob** that carries every
identifier of both players, with **partial (prefix) matching**: `magn` finds `Magnus`
(an `edge_ngram` analyzer; prefixes only — `nus` does **not** find `Magnus`).

What that blob contains differs by index, because of where the data comes from:

- **Games** (`analysis_games`) — the analysis service denormalises resolved **human
  usernames and bot display names** (plus ids) onto each game, so games are searchable by
  full or partial **username / bot name / id**.
- **Matches** (`matches`) — match-db stores **only player ids and bot ids** (no resolved
  names; clients hydrate display names from match-manager). So match free-text matches
  **user-ids and bot-ids**, not human usernames or bot display names. Full username/bot-name
  search for matches is a documented follow-up (needs a user replica in search-service).

## GET /search/games

Faceted / full-text search over the authenticated user's saved analysis games.

**Query parameters**

| Param | Type | Required | Description |
|---|---|---|---|
| `q` | string | No | Free text over PGN headers, player usernames/bot names (full or partial), opening, tags |
| `opponent` | string | No | Opponent username / bot name / id (full or partial) |
| `opening` | string | No | Opening name or ECO code |
| `result` | string | No | `1-0` \| `0-1` \| `1/2-1/2` \| `*` |
| `source` | string | No | `pgn` \| `match` \| `fen` |
| `from_ms` / `to_ms` | long | No | `created_at` epoch-ms range |
| `page` / `page_size` | int | No | Paging |

**200 response**

```json
{
  "results": [
    {
      "game_id": "uuid",
      "white": "name-or-display",
      "black": "name-or-display",
      "result": "1-0",
      "opening": "Sicilian Defense",
      "eco": "B20",
      "source": "match",
      "created_at_ms": 0
    }
  ],
  "total": 0,
  "page": 1,
  "page_size": 20
}
```

---

## GET /search/matches

Faceted search over the authenticated user's Past Matches (matches where they are white, black,
or `created_by`). Complements match-manager `ListUserMatches`; use this for rich filtering.

**Query parameters**

| Param | Type | Required | Description |
|---|---|---|---|
| `q` | string | No | Free text over player ids / bot ids (full or partial). See **Name matching** above — match-db has no resolved usernames, so this does not match human usernames/bot display names |
| `opponent` | string | No | Opponent id / bot id (full or partial) |
| `result` | string | No | `white_won` \| `black_won` \| `draw` |
| `source` | string | No | `native` \| `external` |
| `external_provider` | string | No | e.g. `lichess` (only when `source=external`) |
| `from_ms` / `to_ms` | long | No | `finished_at_ms` range |
| `page` / `page_size` | int | No | Paging |

**200 response**

```json
{
  "results": [
    {
      "match_id": "uuid",
      "white": "name-or-display",
      "black": "name-or-display",
      "status": "white_won",
      "source": "native",
      "external_provider": "",
      "move_count": 0,
      "finished_at_ms": 0
    }
  ],
  "total": 0,
  "page": 1,
  "page_size": 20
}
```

---

## GET /search/positions

Find the caller's games/matches that reached a given position. Backed by the per-ply `positions`
index (one entry per ply, keyed by a normalised FEN piece-placement key).

**Query parameters**

| Param | Type | Required | Description |
|---|---|---|---|
| `fen` | string | Yes | FEN of the target position; only field 1 (piece placement) + side-to-move are matched (move counters ignored) |
| `scope` | string | No | `games` \| `matches` \| `all` (default `all`) |
| `page` / `page_size` | int | No | Paging |

**200 response**

```json
{
  "results": [
    {
      "kind": "game",
      "id": "uuid",
      "ply": 24,
      "fen": "rnbq.../...",
      "white": "name-or-display",
      "black": "name-or-display"
    }
  ],
  "total": 0,
  "page": 1,
  "page_size": 20
}
```

`kind` is `game` (analysis_games) or `match` (matches); `id` is the corresponding `game_id` /
`match_id`. `ply` is the half-move index at which the position occurred (0 = starting position).

---

## Errors

| Status | When |
|---|---|
| 400 | Missing/invalid `fen` on `/search/positions`; malformed paging |
| 401 | Missing/invalid token |

Search never 404s for an empty result set — it returns `results: []` with `total: 0`.
