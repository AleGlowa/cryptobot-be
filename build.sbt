import Settings._

ThisBuild / name         := "CryptoBot"
ThisBuild / version      := "0.1"
ThisBuild / scalaVersion := "3.2.0"
ThisBuild / assemblyMergeStrategy := {
  case "META-INF/io.netty.versions.properties" =>
    MergeStrategy.first
  case x =>
    val oldStrategy = (ThisBuild / assemblyMergeStrategy).value
    oldStrategy(x)
}

lazy val core =
  project
    .settings(CoreSettings)
    .settings(libraryDependencies ++= CoreDependencies)

lazy val api =
  project
    .settings(CoreSettings ++ ApiSettings)
    .settings(libraryDependencies ++= CoreDependencies ::: ApiDependencies)
    .dependsOn(core)

lazy val root =
  (project in file("."))
    .aggregate(core, api)
