package cryptobot.api.bybit.ws

import zio.Scope
import zio.test.{ ZIOSpecDefault, Spec, TestEnvironment }

class BootSpec extends ZIOSpecDefault:

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("bybit.ws.BootSpec")(

      test("Get a pong response after `pingInterval` units of time")(

      )
    )
