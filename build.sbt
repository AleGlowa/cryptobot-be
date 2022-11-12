import Settings._

ThisBuild / name         := "CryptoBot"
ThisBuild / version      := "0.1"
ThisBuild / scalaVersion := "3.2.0"

lazy val core =
  project
    .settings(CoreSettings)
    .settings(libraryDependencies ++= CoreDependencies)

lazy val api =
  project
    .settings(CoreSettings)
    .settings(libraryDependencies ++= CoreDependencies ::: ApiDependencies)
    .dependsOn(core)

lazy val root =
  (project in file("."))
    .settings(CoreSettings)
    .aggregate(core, api)
