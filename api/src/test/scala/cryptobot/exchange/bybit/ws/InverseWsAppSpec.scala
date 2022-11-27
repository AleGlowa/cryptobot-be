package cryptobot.exchange.bybit.ws

import zio.{ ZIO, Ref, Scope }
import zio.test.*
import zhttp.socket.WebSocketFrame
import zhttp.service.{ ChannelEvent, EventLoopGroup, ChannelFactory }
import zhttp.service.ChannelEvent.{ UserEventTriggered, ChannelRead, ChannelUnregistered }
import zhttp.service.ChannelEvent.UserEvent.HandshakeComplete
import scala.collection.immutable.{ Queue => ScalaQueue }

import cryptobot.config.{ Config, WsConfig }
import cryptobot.exchange.bybit.ws.models.{ MessageCollector, TestInverseWsApp }

object InverseWsAppSpec extends ZIOSpecDefault:

  override def spec: Spec[Environment & (TestEnvironment & Scope), Any] =
    suite("exchange.bybit.ws.InverseWsAppSpec")(

      test("Get a pong response after `pingInterval` seconds")(
        ZIO.scoped(
          for
            msgCollector <- MessageCollector.make[ChannelEvent.Event[WebSocketFrame]]
            isConnected  <- Ref.make(false)
            testApp       = new TestInverseWsApp(msgCollector, isConnected)
            wsConnection <- testApp.connect().fork
            // Wait until connection is established
            _            <- ZIO.succeed(isConnected).repeatUntilZIO(_.get)
            // Stop the test app after `pingInterval`
            pingInterval <- WsConfig.pingInterval
            _            <- TestClock.adjust(pingInterval)
            _            <- wsConnection.interrupt
            // Gather saved events from test app
            events       <- msgCollector.await
            expected      =
              ScalaQueue(
                UserEventTriggered(HandshakeComplete),
                ChannelRead(WebSocketFrame.text("FOO")),
                ChannelUnregistered
              )
          yield assertTrue(events == expected)
        )
        .provide(
          EventLoopGroup.nio(nThreads = 4),
          ChannelFactory.nio,
          Config.ws
        )
      )
    )
