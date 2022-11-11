import sbt._

object Dependencies {

  val Zio               = "dev.zio" %% "zio"                 % Versions.Zio
  val ZioJson           = "dev.zio" %% "zio-json"            % Versions.ZioJson
  val ZioConfig         = "dev.zio" %% "zio-config"          % Versions.ZioConfig
  val ZioConfigTypesafe = "dev.zio" %% "zio-config-typesafe" % Versions.ZioConfig // for HOCON files
  val ZioConfigMagnolia = "dev.zio" %% "zio-config-magnolia" % Versions.ZioConfig // for auto derivation
  val ZioHttp           = "io.d11"  %% "zhttp"               % Versions.ZioHttp
  val ZioHttpTest       = "io.d11"  %% "zhttp"               % Versions.ZioHttp   % Test

}

object Versions {

  val Zio       = "2.0.2"
  val ZioJson   = "0.3.0-RC8"
  val ZioConfig = "3.0.2"
  val ZioHttp   = "2.0.0-RC11"

}