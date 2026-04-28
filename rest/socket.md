# Socket Service — WebSocket API

**Base URL:** `ws://socket-service`
**Implementation:** Node.js / socket.io

Gateway for all real-time push events. Clients maintain a single persistent socket.io connection here. Other services (Match Manager, Match Maker, Analysis) call `Socket.EmitEvent` over gRPC to deliver events to a connected user.

---

## Connection

Connect using a socket.io client. Pass the JWT access token in the handshake `auth` object:

```js
import { io } from 'socket.io-client';

const socket = io('ws://socket-service', {
  auth: { token: '<access_token>' },
});
```

The server validates the token by calling `Auth.ValidateToken` over gRPC. If validation fails the connection is rejected with an `unauthorized` error and the socket is closed.

---

## Events

All events are emitted by the server to the client. Clients do not send events.

### `matched`

Emitted by **Match Maker** when a match has been found and created.

```json
{
  "match_id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
}
```

| Field | Type | Description |
|---|---|---|
| `match_id` | UUID | ID of the newly created match |

---

### `move_made`

Emitted by **Match Manager** after every move, including bot moves.

```json
{
  "move": "e7e5",
  "resulting_fen": "rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq e6 0 2",
  "player": { "user_id": "3f2504e0-..." },
  "white_time_ms": 179500,
  "black_time_ms": 178200,
  "index": 2
}
```

| Field | Type | Description |
|---|---|---|
| `move` | string | Move in UCI notation |
| `resulting_fen` | string | Board position after the move |
| `player` | object | `{ user_id }` for a human or `{ bot_id }` for a bot |
| `white_time_ms` | number | Remaining clock time for white in milliseconds |
| `black_time_ms` | number | Remaining clock time for black in milliseconds |
| `index` | number | Move index in match history; use with `GET /matches/{id}/position?index=N` |

---

### `match_ended`

Emitted by **Match Manager** when the game concludes.

```json
{
  "status": "black_won",
  "reason": "checkmate"
}
```

| Field | Type | Description |
|---|---|---|
| `status` | string | `white_won`, `black_won`, or `draw` |
| `reason` | string | `checkmate`, `resignation`, `stalemate`, `timeout`, `draw_agreement`, `fifty_move_rule`, `threefold_repetition`, or `insufficient_material` |

---

### `draw_offered`

Emitted by **Match Manager** when a player offers a draw.

```json
{
  "player": { "user_id": "3f2504e0-..." }
}
```

| Field | Type | Description |
|---|---|---|
| `player` | object | `{ user_id }` of the player who made the offer |

---

### `draw_declined`

Emitted by **Match Manager** when a draw offer is declined or retracted.

```json
{
  "player": { "user_id": "3f2504e0-..." }
}
```

| Field | Type | Description |
|---|---|---|
| `player` | object | `{ user_id }` of the player who declined or retracted the offer |

---

### `analysis_update`

Emitted by **Analysis** once per completed engine search depth. Contains all evaluated lines at
that depth. Multiple `analysis_update` events will arrive in sequence as the engine searches
deeper; each supersedes the previous for the same position.

The `session_id` identifies which analysis session the update belongs to. Clients should discard
updates whose `session_id` does not match their active session.

Some or all early-depth updates may arrive in rapid succession from the analysis cache; later
depths arrive in real time from the engine.

```json
{
  "session_id": "s-7f3a2b1c",
  "depth": 14,
  "lines": [
    { "rank": 1, "evaluation_cp": 35, "moves": ["e2e4", "e7e5", "g1f3"] },
    { "rank": 2, "evaluation_cp": 12, "moves": ["d2d4", "d7d5", "c2c4"] }
  ]
}
```

| Field | Type | Description |
|---|---|---|
| `session_id` | string | Analysis session identifier |
| `depth` | number | Engine search depth completed |
| `lines` | array | All evaluated lines at this depth, best first |
| `lines[].rank` | number | Rank among all lines (1 = best) |
| `lines[].evaluation_cp` | number | Evaluation in centipawns from the moving side's perspective |
| `lines[].moves` | string[] | Principal variation in UCI notation |

---

### `analysis_complete`

Emitted by **Analysis** when the engine terminates naturally (depth cap or no further
improvement). Not emitted when analysis is stopped explicitly by the client.

```json
{
  "session_id": "s-7f3a2b1c",
  "final_depth": 22
}
```

| Field | Type | Description |
|---|---|---|
| `session_id` | string | Analysis session identifier |
| `final_depth` | number | Deepest depth reached |

---

### `analysis_error`

Emitted by **Analysis** when the engine or socket service is unavailable during an analysis
stream. The session remains alive; the client may retry by calling `POST /sessions/{id}/analysis`.

```json
{
  "session_id": "s-7f3a2b1c",
  "message": "engine unavailable"
}
```

| Field | Type | Description |
|---|---|---|
| `session_id` | string | Analysis session identifier |
| `message` | string | Human-readable error description |
