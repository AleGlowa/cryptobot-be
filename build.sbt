name         := "CryptoBot"
version      := "0.1"
scalaVersion := "3.2.0"

scalacOptions ++=
  Seq(
    "-unchecked",
    "-deprecation",
    "-feature"
  )

lazy val ZioConfigV = "3.0.2"
lazy val ZioHttpV   = "2.0.0-RC11"

libraryDependencies ++=
  Seq(
    "dev.zio" %% "zio"                 % "2.0.2",
    "dev.zio" %% "zio-json"            % "0.3.0-RC8",
    "dev.zio" %% "zio-config"          % ZioConfigV,
    "dev.zio" %% "zio-config-typesafe" % ZioConfigV,         // for HOCON files
    "dev.zio" %% "zio-config-magnolia" % ZioConfigV,         // for auto derivation
    "io.d11"  %% "zhttp"               % ZioHttpV,
    "io.d11"  %% "zhttp"               % ZioHttpV % Test
  )
