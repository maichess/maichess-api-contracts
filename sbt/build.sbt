organization := "io.github.maichess"
name         := "platform-protos"
version      := sys.env.getOrElse("RELEASE_VERSION", "0.0.0-SNAPSHOT")
scalaVersion := "3.3.4"

val zioGrpcVersion = "0.6.3"

// Proto sources live one level above the sbt subproject
Compile / PB.protoSources := Seq(baseDirectory.value / ".." / "protos")
Compile / PB.targets := Seq(
  scalapb.gen(grpc = false)              -> (Compile / sourceManaged).value / "scalapb",
  scalapb.zio_grpc.ZioCodeGenerator     -> (Compile / sourceManaged).value / "scalapb",
)

libraryDependencies ++= Seq(
  // % "protobuf" makes protobuf-java available to the protoc invocation
  "com.thesamet.scalapb"          %% "scalapb-runtime"  % scalapb.compiler.Version.scalapbVersion % "protobuf",
  "com.thesamet.scalapb.zio-grpc" %% "zio-grpc-core"    % zioGrpcVersion,
)

// GitHub Packages Maven registry
publishTo := Some(
  "GitHub Package Registry" at "https://maven.pkg.github.com/maichess/maichess-api-contracts"
)

// GITHUB_ACTOR and GITHUB_TOKEN are default environment variables in all GitHub Actions runs
credentials += Credentials(
  "GitHub Package Registry",
  "maven.pkg.github.com",
  sys.env.getOrElse("GITHUB_ACTOR", "_"),
  sys.env.getOrElse("GITHUB_TOKEN", ""),
)

publishMavenStyle      := true
Test / publishArtifact := false
