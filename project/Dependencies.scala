import sbt._

object Dependencies {

  val Zio               = "dev.zio" %% "zio"                 % Versions.Zio
  val ZioStreams        = "dev.zio" %% "zio-streams"         % Versions.Zio
  val ZioJson           = "dev.zio" %% "zio-json"            % Versions.ZioJson
  val ZioConfig         = "dev.zio" %% "zio-config"          % Versions.ZioConfig
  val ZioConfigTypesafe = "dev.zio" %% "zio-config-typesafe" % Versions.ZioConfig // for HOCON files
  val ZioConfigMagnolia = "dev.zio" %% "zio-config-magnolia" % Versions.ZioConfig // for auto derivation
  val ZioHttp           = "io.d11"  %% "zhttp"               % Versions.ZioHttp
  val ZioTest           = "dev.zio" %% "zio-test"            % Versions.Zio       % Test
  val ZioTestSbt        = "dev.zio" %% "zio-test-sbt"        % Versions.Zio       % Test

}

object Versions {

  val Zio           = "2.0.5"
  val ZioJson       = "0.4.2"
  val ZioConfig     = "3.0.7"
  val ZioHttp       = "2.0.0-RC11"

}
