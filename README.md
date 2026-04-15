# maichess-api-contracts

Protobuf API contracts for the maichess project. This repository is the single source of truth for all service interfaces and generates typed client/server stubs for TypeScript, Scala, and C#.

## Repository structure

```
protos/
  <service-name>/
    v1/
      <resource>.proto
gen/           # generated code (not committed)
  ts/
  scala/
  csharp/
buf.yaml       # Buf workspace & lint/breaking config
buf.gen.yaml   # Code generation config
```

## Services

| Service | Proto path | Description |
|---|---|---|
| engine-service | `protos/engine-service/v1/` | Chess engine / bot |
| move-validator-service | `protos/move-validator-service/v1/` | Move validation |
| match-maker-service | `protos/match-maker-service/v1/` | Player matchmaking |
| match-manager-service | `protos/match-manager-service/v1/` | Match management |
| user-service | `protos/user-service/v1/` | User management |
| auth-service | `protos/auth-service/v1/` | Authentication |

## Prerequisites

Install the [Buf CLI](https://buf.build/docs/installation):

```bash
brew install bufbuild/buf/buf   # macOS
```

## Usage

**Lint proto files:**
```bash
buf lint
```

**Check for breaking changes against the current branch:**
```bash
buf breaking --against '.git#branch=main'
```

**Generate code locally:**
```bash
buf generate
```

Generated files are written to `gen/ts`, `gen/scala`, and `gen/csharp`.

## Distribution

On pushes of tags matching `v*`, GitHub Actions publishes generated packages to GitHub Packages:

| Language | Package registry |
|---|---|
| TypeScript | GitHub npm registry (`@maichess/*`) |
| Scala | GitHub Maven registry |
| C# | GitHub NuGet registry |

## Plugins

| Language | Plugin | Notes |
|---|---|---|
| TypeScript | `buf.build/community/stephenh-ts-proto` | grpc-js compatible |
| Scala | `buf.build/community/scalapb-scala` | ScalaPB |
| C# | `buf.build/protocolbuffers/csharp` + `buf.build/grpc/csharp` | Messages + service stubs |
