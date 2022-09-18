name         := "CryptoBot"
version      := "0.1"
scalaVersion := "3.2.0"

scalacOptions ++= Seq(
  "-unchecked",
  "-deprecation",
  "-feature"
)

libraryDependencies ++= Seq(
  "dev.zio" %% "zio"      % "2.0.2",
  "io.d11"  %% "zhttp"    % "2.0.0-RC11"
)
