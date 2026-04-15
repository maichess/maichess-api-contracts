# maichess-api-contracts

Protobuf API contracts for the maichess project. This repository is the single source of truth for all gRPC service interfaces and generates typed client/server stubs for TypeScript, Scala, and C#.

Packages are published to GitHub Packages automatically on every `v*` tag push.

## Repository structure

```
protos/                         # source of truth — all .proto files
  <service-name>/
    v1/
      <resource>.proto
npm/                            # TypeScript package template (package.json, tsconfig.json)
sbt/                            # Scala build (build.sbt, project/)
dotnet/                         # C# project (Maichess.PlatformProtos.csproj)
.github/workflows/              # CI: publish-ts.yml, publish-scala.yml, publish-csharp.yml
buf.yaml                        # Buf workspace — lint & breaking-change config
buf.gen.yaml                    # Generate all three languages locally
buf.gen.ts.yaml                 # Generate TypeScript only (used by CI)
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

---

## Consuming the generated packages

All packages are hosted on **GitHub Packages** and require a GitHub personal access token (classic or fine-grained) with at least `read:packages` scope to install.

### TypeScript — `@maichess/platform-protos`

**1. Authenticate**

Add a `.npmrc` file to your project (or to `~/.npmrc` globally):

```
@maichess:registry=https://npm.pkg.github.com
//npm.pkg.github.com/:_authToken=${GITHUB_TOKEN}
```

Set `GITHUB_TOKEN` in your environment or CI secrets.

**2. Install**

```bash
npm install @maichess/platform-protos @grpc/grpc-js
```

`@grpc/grpc-js` is a peer dependency and must be installed alongside the package.

**3. Use**

Generated files follow the path structure of the proto files, e.g. `engine-service/v1/bots`:

```ts
import * as grpc from '@grpc/grpc-js';
import { BotsClient } from '@maichess/platform-protos/engine-service/v1/bots';

const client = new BotsClient(
  'localhost:50051',
  grpc.credentials.createInsecure(),
);
```

---

### Scala — `io.github.maichess:platform-protos_3`

**1. Add the resolver and credentials**

In your `build.sbt`:

```scala
resolvers += "GitHub Packages" at "https://maven.pkg.github.com/maichess/maichess-api-contracts"

credentials += Credentials(
  "GitHub Package Registry",
  "maven.pkg.github.com",
  sys.env.getOrElse("GITHUB_ACTOR", "_"),
  sys.env.getOrElse("GITHUB_TOKEN", ""),
)
```

Set `GITHUB_TOKEN` (and optionally `GITHUB_ACTOR`) in your environment. In GitHub Actions both are available automatically.

**2. Add the dependency**

```scala
libraryDependencies += "io.github.maichess" %% "platform-protos" % "<version>"
```

**3. Use**

```scala
import io.grpc.ManagedChannelBuilder
import maichess.engine.v1.bots.BotsGrpc

val channel = ManagedChannelBuilder
  .forAddress("localhost", 50051)
  .usePlaintext()
  .build()

val stub = BotsGrpc.stub(channel)
```

---

### C# — `Maichess.PlatformProtos`

**1. Add the GitHub Packages NuGet source**

Create or update `nuget.config` at the root of your solution:

```xml
<?xml version="1.0" encoding="utf-8"?>
<configuration>
  <packageSources>
    <add key="nuget.org" value="https://api.nuget.org/v3/index.json" />
    <add key="github" value="https://nuget.pkg.github.com/maichess/index.json" />
  </packageSources>
  <packageSourceCredentials>
    <github>
      <add key="Username" value="%GITHUB_ACTOR%" />
      <add key="Password" value="%GITHUB_TOKEN%" />
    </github>
  </packageSourceCredentials>
</configuration>
```

Set `GITHUB_TOKEN` (and optionally `GITHUB_ACTOR`) in your environment. In GitHub Actions both are available automatically.

**2. Install**

```bash
dotnet add package Maichess.PlatformProtos --version <version>
```

**3. Use**

```csharp
using Grpc.Net.Client;
using Maichess.Engine.V1;

using var channel = GrpcChannel.ForAddress("http://localhost:50051");
var client = new Bots.BotsClient(channel);
```

---

## Working with this repository

**Prerequisites:** [Buf CLI](https://buf.build/docs/installation)

```bash
brew install bufbuild/buf/buf   # macOS
```

**Lint proto files:**
```bash
buf lint
```

**Check for breaking changes against main:**
```bash
buf breaking --against '.git#branch=main'
```

**Generate all languages locally:**
```bash
buf generate
```

Output is written to `gen/ts`, `gen/scala`, and `gen/csharp` (not committed).
