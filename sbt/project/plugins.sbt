addSbtPlugin("com.thesamet" % "sbt-protoc" % "1.0.8")
libraryDependencies ++= Seq(
  "com.thesamet.scalapb"          %% "compilerplugin"   % "0.11.17",
  "com.thesamet.scalapb.zio-grpc" %% "zio-grpc-codegen" % "0.6.3",
)
