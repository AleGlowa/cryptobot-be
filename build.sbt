import Settings._

ThisBuild / name         := "CryptoBot"
ThisBuild / version      := "0.1"
ThisBuild / scalaVersion := "3.2.0"

lazy val api =
  project
    .settings(CommonSettings)
    .settings(libraryDependencies ++= CommonDependencies ::: ApiDependencies)

lazy val root =
  (project in file("."))
    .settings(CommonSettings)
    .aggregate(api)
