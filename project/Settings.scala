import sbt._
import sbt.Keys._

import Dependencies.*

object Settings {

  val CommonSettings =
    Seq(
      scalacOptions :=
        Seq(
          "-unchecked",
          "-deprecation",
          "-feature"
        ),
      run / cancelable := true  // https://github.com/sbt/sbt/issues/2274
    )

  val CommonDependencies = Zio :: Nil

  val ApiDependencies =
    ZioHttp ::
      ZioJson ::
      ZioConfig ::
      ZioConfigTypesafe ::
      ZioConfigMagnolia ::
      ZioHttpTest ::
      Nil

}
