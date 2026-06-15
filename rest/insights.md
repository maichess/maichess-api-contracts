# Insights Service — REST API

**Base URL:** `http://insights-service`
**Implementation:** ASP.NET

Analyzes massive historical chess corpora (Lichess monthly dumps and manual PGN
uploads first; Chess.com / TWIC later) with **Apache Spark on k8s**. Work runs as
two job stages — **ingestion** (download → decompress-once → parse → partitioned
Parquet in MinIO) and **analysis** (Spark jobs that materialize the `insights_*`
collections) — and the query endpoints read those materialized rows.

Every metric is computed **per corpus**. A *corpus* is one analyzed dataset
identified by a `corpus_id` (e.g. `lichess-2024-12`,
`lichess-2024-12-blitz-1600plus`, `upload-{id}`), so results from different
sources or slices never blur together. Each corpus records the **filter** that
produced it (source, rating band, time control, date slice, sample rate).

Analysis is **annotations-first**: blunder / eval-swing / think-time come from
Lichess's embedded `%eval` / `%clk`; the engine is never on the default path.
Ingestion defaults to a filtered/sampled slice — full-month runs are explicit
opt-in via the filter.

This REST surface mirrors `protos/insights-service/v1/insights.proto` (the source
of truth for the data model). Job lifecycle is also pushed live over the socket
(`insights.events.v1` → `socket.outbound.v1`) for the submitting user; polling
`GET /insights/jobs` works without it.

**Auth:** All endpoints require a Bearer token (any authenticated user). The
authenticated user is recorded as the submitter of a job and is the target of its
live lifecycle pushes.

---

## Job control

### POST /insights/ingestions

Submit an ingestion: download + parse a source into a new corpus of partitioned
Parquet. Creates and returns an ingestion job in `pending`.

**Request body**

Exactly one of `lichess_month` or `upload` must be present; it selects the source.
`filter` is optional — omit it (or pass an empty object) to ingest the full source.

| Field | Type | Required | Description |
|---|---|---|---|
| `lichess_month` | object | Conditional | `{ "year_month": "YYYY-MM" }` — a monthly standard-games dump |
| `upload` | object | Conditional | `{ "object_key": "...", "label": "..." }` — a PGN already staged via `POST /insights/uploads` |
| `filter` | object | No | Narrows which games are kept (see below) |

`filter`:

| Field | Type | Required | Description |
|---|---|---|---|
| `rating_band` | string | No | e.g. `1600-1999`; omit for all bands |
| `time_control` | string | No | e.g. `blitz`; omit for all |
| `date_from_ms` | int | No | Inclusive event-date slice start (epoch ms); 0/omit = unbounded |
| `date_to_ms` | int | No | Inclusive slice end (epoch ms); 0/omit = unbounded |
| `sample_rate` | number | No | Fraction in `(0,1]` of games to keep; 0/1/omit = full corpus |

```json
{
  "lichess_month": { "year_month": "2024-12" },
  "filter": { "rating_band": "1600-1999", "time_control": "blitz", "sample_rate": 0.15 }
}
```

**`202 Accepted`** — the created job (see the job shape under `GET /insights/jobs/{id}`).

**`400 Bad Request`** — no source / more than one source present, malformed
`year_month`, unknown `object_key`, or a `sample_rate` outside `(0,1]`.
**`401 Unauthorized`**

---

### POST /insights/uploads

Stage a manual multi-game PGN for later ingestion. The body is `multipart/form-data`
with a single `file` part (one `.pgn`, optionally `.pgn.zst`). The service writes it
to the `insights-raw` bucket and returns the object key to pass as the `upload`
source of `POST /insights/ingestions`.

**Request:** `multipart/form-data`

| Part | Type | Required | Description |
|---|---|---|---|
| `file` | file | Yes | A `.pgn` (or `.pgn.zst`) containing one or more games |
| `label` | text | No | Human label shown in the corpus list |

**`201 Created`**
```json
{ "object_key": "uploads/2026-06-15/abcd1234.pgn", "label": "club games" }
```

**`400 Bad Request`** — missing `file`, empty body, or an unparseable PGN.
**`401 Unauthorized`**
**`413 Payload Too Large`** — upload exceeds the configured size cap.

---

### POST /insights/analyses

Submit an analysis run over an already-ingested corpus, materializing the
`insights_*` rows. Creates and returns an analysis job in `pending`.

**Request body**

| Field | Type | Required | Description |
|---|---|---|---|
| `corpus_id` | string | Yes | Corpus to analyze (must already be ingested) |
| `kinds` | string[] | No | Which jobs to run: any of `openings`, `endgames`, `positions`, `tricky`, `summary`. Empty/omit ⇒ all |

```json
{ "corpus_id": "lichess-2024-12-blitz-1600plus", "kinds": ["openings", "tricky"] }
```

**`202 Accepted`** — the created job.

**`400 Bad Request`** — missing `corpus_id` or an unknown `kinds` value.
**`401 Unauthorized`**
**`404 Not Found`** — no corpus with that id.

---

### GET /insights/jobs

List job records, newest first.

**Query parameters**

| Name | Type | Description |
|---|---|---|
| `status` | string | Optional: `pending` \| `running` \| `succeeded` \| `failed`. Omit for all. |
| `limit` | int | Defaults to 20; capped at 100 |
| `offset` | int | Defaults to 0 |

**`200 OK`**
```json
{
  "jobs": [
    {
      "id": "…", "type": "ingestion", "corpus_id": "lichess-2024-12-blitz-1600plus",
      "source": { "lichess_month": { "year_month": "2024-12" } },
      "filter": { "rating_band": "1600-1999", "time_control": "blitz", "sample_rate": 0.15 },
      "status": "succeeded", "analysis_kinds": [],
      "created_at_ms": 1717200000000, "started_at_ms": 1717200030000, "finished_at_ms": 1717201800000,
      "spark_application": "insights-ingest-lichess-2024-12-ab12", "error": ""
    }
  ]
}
```

**`401 Unauthorized`**

---

### GET /insights/jobs/{id}

Get one job record.

**`200 OK`** — the job shape shown above. `analysis_kinds` is populated for
analysis jobs and empty for ingestion jobs; `started_at_ms` / `finished_at_ms` are
`0` until the job reaches those states; `error` is set only when `status` is
`failed`.

**`401 Unauthorized`**
**`404 Not Found`** — no job with that id.

---

## Corpora & queries

### GET /insights/corpora

List analyzed corpora, newest first.

**Query parameters**

| Name | Type | Description |
|---|---|---|
| `limit` | int | Defaults to 20; capped at 100 |
| `offset` | int | Defaults to 0 |

**`200 OK`**
```json
{
  "corpora": [
    {
      "id": "lichess-2024-12-blitz-1600plus",
      "source": { "lichess_month": { "year_month": "2024-12" } },
      "filter": { "rating_band": "1600-1999", "time_control": "blitz", "sample_rate": 0.15 },
      "game_count": 13500000,
      "created_at_ms": 1717201800000
    }
  ]
}
```

**`401 Unauthorized`**

---

### GET /insights/corpora/{id}/summary

Headline aggregates for one corpus (`insights_summary`).

**`200 OK`**
```json
{
  "summary": {
    "corpus_id": "lichess-2024-12-blitz-1600plus",
    "total_games": 13500000,
    "date_from": "2024-12", "date_to": "2024-12",
    "draw_rate": 0.041, "avg_ply_count": 74.2,
    "rating_distribution": [ { "rating_band": "1600-1999", "game_count": 13500000 } ],
    "termination_mix": [
      { "termination": "resign", "game_count": 7100000 },
      { "termination": "timeout", "game_count": 4200000 },
      { "termination": "mate", "game_count": 1900000 },
      { "termination": "other", "game_count": 300000 }
    ],
    "first_moves": [ { "san": "e4", "game_count": 6800000 }, { "san": "d4", "game_count": 3100000 } ]
  }
}
```

**`401 Unauthorized`**
**`404 Not Found`** — no corpus, or no summary materialized yet.

---

### GET /insights/corpora/{id}/openings

Opening-success rows (`insights_openings`), most popular first.

**Query parameters**

| Name | Type | Description |
|---|---|---|
| `color` | string | Optional split: `white` \| `black`. Omit for the un-split aggregate. |
| `rating_band` | string | Optional split, e.g. `1600-1999` |
| `time_control` | string | Optional split, e.g. `blitz` |
| `limit` | int | Defaults to 50; capped at 500 |
| `offset` | int | Defaults to 0 |

**`200 OK`**
```json
{
  "openings": [
    {
      "eco": "B10", "opening_name": "Caro-Kann Defense",
      "game_count": 480000, "white_win_rate": 0.51, "black_win_rate": 0.45, "draw_rate": 0.04,
      "color": "", "rating_band": "", "time_control": "",
      "trend": [ { "year_month": "2024-12", "game_count": 480000, "white_win_rate": 0.51, "black_win_rate": 0.45, "draw_rate": 0.04 } ]
    }
  ]
}
```

**`401 Unauthorized`**
**`404 Not Found`** — no corpus with that id.

---

### GET /insights/corpora/{id}/endgames

Endgame material-signature rows (`insights_endgames`), most frequent first. A
position is an endgame when total piece count ≤ 7; the `material_signature` is the
canonical multiset of non-king pieces per side, stronger side first (e.g. `KRPvKR`).

**Query parameters:** `limit` (default 50, cap 500), `offset` (default 0).

**`200 OK`**
```json
{
  "endgames": [
    { "material_signature": "KRPvKR", "frequency": 210000, "stronger_side_win_rate": 0.38, "draw_rate": 0.55, "stronger_side_loss_rate": 0.07 }
  ]
}
```

**`401 Unauthorized`**
**`404 Not Found`**

---

### GET /insights/corpora/{id}/positions

Most-reached normalized-FEN rows (`insights_positions`). The FEN has the
halfmove-clock and fullmove-number fields stripped so transpositions and clock
differences collapse.

**Query parameters**

| Name | Type | Description |
|---|---|---|
| `exclude_book` | bool | When true, exclude positions still inside opening book (early plies) to surface middlegame convergence. Default false. |
| `limit` | int | Defaults to 50; capped at 500 |
| `offset` | int | Defaults to 0 |

**`200 OK`**
```json
{
  "positions": [
    { "normalized_fen": "r1bqkbnr/pppp1ppp/2n5/1B2p3/4P3/5N2/PPPP1PPP/RNBQK2R b KQkq -", "reach_count": 95000, "white_win_rate": 0.52, "black_win_rate": 0.44, "draw_rate": 0.04 }
  ]
}
```

**`401 Unauthorized`**
**`404 Not Found`**

---

### GET /insights/corpora/{id}/tricky

Tricky-position rows (`insights_tricky`): positions reached often enough (support)
with **both** high average centipawn loss and high think time — where humans most
often blunder under time pressure. Most tricky first.

**Query parameters:** `limit` (default 50, cap 500), `offset` (default 0).

**`200 OK`**
```json
{
  "positions": [
    { "normalized_fen": "…", "support": 4200, "avg_centipawn_loss": 168.0, "blunder_probability": 0.34, "avg_think_time_ms": 9100.0 }
  ]
}
```

**`401 Unauthorized`**
**`404 Not Found`**
