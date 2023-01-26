package cryptobot.exchange.bybit

import zio.*
import zhttp.http.*
import zhttp.socket.*
import zhttp.service.*
import zhttp.service.ChannelEvent.*

import cryptobot.config.Config
import cryptobot.exchange.bybit.ws.{ WsApp, InverseWsApp }

object Boot extends ZIOAppDefault:

  private val inverseWsApp = new InverseWsApp

  private val app =
    Http.collectZIO[Request] {
      case Method.GET -> !! / "inverseWs" / "subscriptions" =>
        inverseWsApp.msgOutLogic.toResponse
    }

  override val run: UIO[ExitCode] =
    ZIO.scoped(
      for
        _ <- Console.printLine(s"Starting the server at http://localhost:8080").orDie
        _ <- Server.start(8080, app)
        _ <- Console.readLine("Press ENTER to stop the application\n").orDie
        _ <- Console.printLine("Stopping the application...").orDie
      yield ()
    )
    .provide(
      // Types of EventLoopGroup from netty to investigate
      EventLoopGroup.auto(nThreads = 4),
      ChannelFactory.auto,
      Config.ws,
      WsApp.initialState
    )
    .exitCode
