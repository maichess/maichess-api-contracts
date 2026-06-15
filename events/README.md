# Event Contracts (Protobuf)

Source of truth for all **Kafka event and command schemas**. As of the
[Kafka Protobuf migration](../../maichess-knowledge-base/knowledge/architecture/serialization-protobuf-migration.md)
(program `tasks/planned/kafka/`, completed in task `09`) every topic is **Protobuf**, serialized
as **raw Protobuf bytes** — the Confluent Schema Registry has been removed. The schemas live under
[`../protos/events/v1/`](../protos/events/v1/) (package `maichess.events.v1`) alongside the
surviving synchronous query/storage RPCs; this directory no longer holds any `.avsc` (the original
Avro schemas were retired per-topic during the migration). See
[event-driven-architecture.md](../../maichess-knowledge-base/knowledge/architecture/event-driven-architecture.md).

## Layout

```
protos/events/v1/
  match_commands.proto       # commands directed at the match aggregate
  match_events.proto         # the match event log (source of truth)
  matchmaking_events.proto
  analysis_commands.proto
  analysis_events.proto
  user_events.proto
  socket_outbound.proto      # fan-out envelope for client push
  cheat_events.proto         # anti-cheat flag state per user
  insights_events.proto      # insights Spark job lifecycle (relayed to socket.outbound.v1)
```

> **CDC streams** `user.cdc.v1` / `match.cdc.v1` (Debezium, see
> [change-data-capture.md](../../maichess-knowledge-base/knowledge/architecture/change-data-capture.md))
> are **internal** raw change streams, not consumer contracts — they have no schema here and are
> produced by Kafka Connect, not by services. The public `user.events.v1` schema is **curated from
> `user.cdc.v1`** (a relay maps CDC change rows → the `UserEvent` envelope). Consume
> `user.events.v1`, never `user.cdc.v1`.

One Protobuf message per topic value: an envelope message whose `payload` is a `oneof` of the
concrete event/command messages for that topic. Keys are plain strings (the aggregate id) and are
not Protobuf-encoded.

## Envelope

Every topic value message begins with the same header fields, then a `oneof payload`:

| Field | Type | Purpose |
|---|---|---|
| `event_id` | string (uuid) | idempotency key |
| `event_type` | string | e.g. `match.MoveApplied` |
| `aggregate_id` | string | partition key (matchId, playerId, sessionId, userId) |
| `sequence` | int64 | per-aggregate monotonic; dedupe + gap detection |
| `occurred_at` | int64 | event time, epoch ms |
| `correlation_id` | string | one logical flow (a move round-trip) |
| `causation_id` | string | the `event_id` that caused this one (empty if originating) |
| `producer` | string | emitting service name |
| `payload` | oneof | the typed body (see each schema) |

## Serialization

- **Raw Protobuf bytes** — `message.ToByteArray()` / `Parser.ParseFrom(bytes)` (C#),
  `companion.parseFrom`/`toByteArray` (Scala ScalaPB), `Message.decode`/`encode` (Node ts-proto).
  No Schema Registry, no Confluent framing, no magic byte.
- Compatibility is managed by Protobuf field rules: add fields with new tags; never reuse or
  renumber a tag. Removing a field is allowed only if no producer/consumer still reads it.
- Topics are created by the `kafka-topics-init` Job in
  [maichess-deploy](../../maichess-deploy).

## Code generation per language

Generated types ship in the `Maichess.PlatformProtos` package (C#/Scala/TS) published from this
repo on a `v*` tag, exactly like the gRPC stubs. Services consume the generated `maichess.events.v1`
types directly — there is no separate per-stack schema-loading step.

## Adding or changing an event

Per the contract policy in the root `CLAUDE.md`: do not change a schema silently. Update the
`.proto` under `../protos/events/v1/`, run `buf lint` + `buf breaking`, note the change in the
affected service's `CONTRACT_NOTES.md`, publish a new `Maichess.PlatformProtos` version, bump every
consumer, and keep
[event-driven-architecture.md](../../maichess-knowledge-base/knowledge/architecture/event-driven-architecture.md)
in sync.
