package cryptobot.exchange.bybit.ws

import zio.{ ZIO, UIO, Ref, Scope, Exit }
import zio.durationInt
import zio.duration2DurationOps
import zio.test.*
import zhttp.socket.WebSocketFrame
import zhttp.service.{ ChannelEvent, EventLoopGroup, ChannelFactory }
import zhttp.service.ChannelEvent.{ UserEventTriggered, ChannelRead, ChannelUnregistered }
import zhttp.service.ChannelEvent.UserEvent.HandshakeComplete
import scala.collection.immutable.{ Queue => ScalaQueue }

import java.io.{ Closeable => JCloseable }

import cryptobot.config.{ Config, WsConfig }
import cryptobot.exchange.bybit.ws.models.{ MessageCollector, TestInverseWsApp }
import cryptobot.exchange.bybit.ws.WsApp.Conn

object InverseWsAppSpec extends ZIOSpecDefault:

  val createTestApp =
    for
      msgCollector <- MessageCollector.make[ChannelEvent.Event[WebSocketFrame]]
    yield new TestInverseWsApp(msgCollector)

  val connTestApp =
    for
      app <- createTestApp
      _   <-
        ZIO.acquireRelease(app.connect())( conn =>
          closeRes(conn) *> ZIO.logInfo("After resource release")
        )
        // ZIO.acquireRelease(app.connect())( implicit conn =>
        //   app.disconnect().orDie *> ZIO.logInfo("After resource release")
        // )
    yield app

  def closeRes(res: JCloseable): UIO[Unit] = ZIO.attempt(res.close()).orDie

  override def spec: Spec[Environment & (TestEnvironment & Scope), Any] =
    suite("exchange.bybit.ws.InverseWsAppSpec")(

      test("Get a pong response after `pingInterval` seconds") {
        val appWithMsg =
          ZIO.scoped(
            for
              app    <- connTestApp
              // Wait untill connection is established
              _      <- app.getIsConnected.repeatUntilZIO(_.get)
            yield app
          )
        (for
          f      <- appWithMsg.fork
          app    <- f.join
          _ <- ZIO.logInfo("After join")
          events <- ZIO.scoped(app.getEvents)
          expected      =
            ScalaQueue(
              UserEventTriggered(HandshakeComplete),
              ChannelRead(WebSocketFrame.text("FOO")),
              ChannelUnregistered
            )
        yield assertTrue(events == expected))
          .provide(
            EventLoopGroup.nio(nThreads = 4),
            ChannelFactory.nio,
            Config.ws,
            WsApp.initialState
          )
      }
    )
