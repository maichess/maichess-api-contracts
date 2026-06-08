# Event Contracts (Avro)

Source of truth for all **Kafka event and command schemas**. Proto (`../protos`) remains the
source of truth for the surviving synchronous query/storage RPCs; this directory covers the
event-driven flows introduced by [event-driven-architecture.md](../../maichess-knowledge-base/event-driven-architecture.md).

## Layout

```
events/
  v1/
    match.commands.v1.avsc        # commands directed at the match aggregate
    match.events.v1.avsc          # the match event log (source of truth)
    matchmaking.commands.v1.avsc
    matchmaking.events.v1.avsc
    analysis.commands.v1.avsc
    analysis.events.v1.avsc
    user.events.v1.avsc
    socket.outbound.v1.avsc       # fan-out envelope for client push
    cheat.events.v1.avsc          # anti-cheat flag state per user (feature-prompts/14)
```

> **CDC streams** `user.cdc.v1` / `match.cdc.v1` (Debezium, see
> [change-data-capture.md](../../maichess-knowledge-base/change-data-capture.md)) are **internal**
> raw change streams, not consumer contracts — they have no `.avsc` here and are produced by Kafka
> Connect, not by services. As of `feature-prompts/10` the public `user.events.v1` schema below is
> **curated from `user.cdc.v1`** (a relay maps CDC change rows → this envelope); the schema is
> unchanged, only its production path moved. Consume `user.events.v1`, never `user.cdc.v1`.

One Avro schema per topic. Each schema is the **topic value** schema: an envelope record whose
`payload` field is a union of the concrete event/command records for that topic. Keys are plain
strings (the aggregate id) and are not Avro-encoded.

## Envelope

Every topic value record begins with the same header fields, then a `payload` union:

| Field | Avro type | Purpose |
|---|---|---|
| `event_id` | string (uuid) | idempotency key |
| `event_type` | string | e.g. `match.MoveApplied` |
| `aggregate_id` | string | partition key (matchId, playerId, sessionId, userId) |
| `sequence` | long | per-aggregate monotonic; dedupe + gap detection |
| `occurred_at` | long | event time, epoch ms |
| `correlation_id` | string | one logical flow (a move round-trip) |
| `causation_id` | string | the `event_id` that caused this one (empty if originating) |
| `producer` | string | emitting service name |
| `payload` | union | the typed body (see each schema) |

## Schema Registry

- One subject per topic, named `<topic>-value` (TopicNameStrategy).
- Compatibility: **BACKWARD** (new schema can read old data). Add fields with defaults; never
  remove or rename a field in place — introduce a new union branch or a `v2` topic.
- Avro enums carry a `default` symbol so unknown future symbols degrade gracefully.

Topics are created by the `kafka-topics-init` Job in
[maichess-deploy](../../maichess-deploy). Schemas register with the registry on first produce
(Confluent serdes default to `auto.register.schemas=true`); a CI step in this repo may
pre-register them against the registry to gate breaking changes before deploy.

## Code generation per language

No shared codegen step is mandated (Avro tooling differs per stack). Each service generates or
loads types from these `.avsc` files at build time:

- **C# (.NET):** `Chr.Avro` or `Apache.Avro` (`avrogen`), reading the `.avsc` from this package.
- **Scala (ZIO):** `avro4s` schema derivation, with `zio-kafka` + Confluent Avro serde.
- **Node (TS):** `@kafkajs/confluent-schema-registry` (runtime) and/or `avsc` for static types.

## Adding or changing an event

Per the contract policy in the root `CLAUDE.md`: do not change a schema silently. Update the
`.avsc`, run a registry compatibility check, note the change in the affected service's
`CONTRACT_NOTES.md`, and keep [event-driven-architecture.md](../../maichess-knowledge-base/event-driven-architecture.md)
in sync.
