# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Purpose

This repository holds the Protobuf API contracts for the **maichess** project — a chess application with the following microservices:

- **engine-service** — chess engine / bot functionality (`protos/engine-service/v1/*`)
- **move-validator-service** — move validation (`protos/move-validator-service/v1/*`)
- **match-maker-service** — player matchmaking (`protos/match-maker-service/v1/*`)
- **match-manager-service** — match management (`protos/match-manager-service/v1/*`)
- **user-service** — user management (`protos/user-service/v1/*`)
- **auth-service** — authentication (`protos/auth-service/v1/*`)

Proto files live under `protos/<service-name>/v1/`.

## Distribution

The repository is hosted on GitHub and should publish packages with generated code for Typescript, Scala and C# integrations on GitHub Packages on git tag pushes matching `v*`.

## Commands

```bash
buf lint                                        # lint all proto files
buf breaking --against '.git#branch=main'       # check for breaking changes
buf generate                                    # generate code into gen/ts, gen/scala, gen/csharp
```

## Protobuf conventions

- Package versioning via directory: `v1/`, `v2/`, etc.
- One service per proto file, named after the service directory.
- Follow standard protobuf style: `snake_case` for field names, `PascalCase` for message/service/enum names, `SCREAMING_SNAKE_CASE` for enum values.
