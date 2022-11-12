import sbt._
import sbt.Keys._

import Dependencies.*

object Settings {

  val CoreSettings =
    Seq(
      scalacOptions :=
        Seq(
          "-unchecked",
          "-deprecation",
          "-feature"
        ),
      run / cancelable := true  // https://github.com/sbt/sbt/issues/2274
    )

  val CoreDependencies =
    Zio ::
      ZioConfig ::
      ZioConfigTypesafe ::
      ZioConfigMagnolia ::
      Nil

  val ApiDependencies =
    ZioHttp ::
      ZioJson ::
      ZioHttpTest ::
      Nil

}
