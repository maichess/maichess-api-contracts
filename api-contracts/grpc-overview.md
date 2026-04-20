# gRPC Service Communication Overview

Clients communicate with Auth, User, Match Maker, and Match Manager exclusively over HTTP REST. All internal service-to-service calls use gRPC.

## Call graph

```
Client (Next.js)
  │
  ├─── REST ──► Auth (Express.js)
  │                └─── gRPC ──► User (ASP.NET)
  │                               CreateUser, GetUser
  │
  ├─── REST ──► User (ASP.NET)
  │
  ├─── REST ──► Match Maker (ASP.NET)
  │                └─── gRPC ──► Match Manager (ASP.NET)
  │                               CreateMatch
  │
  └─── REST ──► Match Manager (ASP.NET)
                   ├─── gRPC ──► Move Validator (Scala ZIO)
                   │              ValidateMove, GetLegalMoves
                   │
                   ├─── gRPC ──► Engine (Scala ZIO)
                   │              GetBestMove, ListBots
                   │
                   └─── gRPC ──► User (ASP.NET)
                                  GetUser
```

> Any service may also call `Auth.ValidateToken` via gRPC to verify an access token server-side (enables revocation without trusting the JWT signature alone).

## Proto files by callee

| Proto file | Service exposed | Called by |
|---|---|---|
| `protos/engine-service/v1/bots.proto` | `Bots` | Match Manager |
| `protos/move-validator-service/v1/moves.proto` | `Moves` | Match Manager |
| `protos/match-manager-service/v1/matches.proto` | `Matches` | Match Maker (`CreateMatch`) |
| `protos/user-service/v1/users.proto` | `Users` | Auth (`CreateUser`, `GetUser`), Match Manager (`GetUser`) |
| `protos/auth-service/v1/auth.proto` | `Auth` | Any service (`ValidateToken`) |
| `protos/match-maker-service/v1/matchmaker.proto` | *(no service)* | — |

## REST API contracts

| Service | Contract |
|---|---|
| Auth | [`rest/auth.md`](rest/auth.md) |
| User | [`rest/users.md`](rest/users.md) |
| Match Maker | [`rest/match-maker.md`](rest/match-maker.md) |
| Match Manager | [`rest/match-manager.md`](rest/match-manager.md) |
