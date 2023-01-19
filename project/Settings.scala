import sbt._
import sbt.Keys._

import Dependencies._

object Settings {

  val CoreSettings =
    Seq(
      scalacOptions    :=
        Seq(
          "-unchecked",
          "-deprecation",
          "-feature"
        ),
      run / cancelable := true,  // https://github.com/sbt/sbt/issues/2274
      testFrameworks   := Seq(new TestFramework("zio.test.sbt.ZTestFramework"))
    )

  val CoreDependencies =
    Zio ::
      ZioConfig ::
      ZioConfigTypesafe ::
      ZioConfigMagnolia ::
      ZioTest ::
      ZioTestSbt ::
      Nil

  val ApiDependencies =
    ZioHttp ::
      ZioStreams ::
      ZioJson ::
      Nil

}
