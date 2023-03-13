package cryptobot.exchange.bybit

import zio.*
import zhttp.http.*
import zhttp.socket.*
import zhttp.service.*
import zhttp.service.ChannelEvent.*
import zhttp.http.middleware.Cors.CorsConfig

import cryptobot.config.Config
import cryptobot.exchange.bybit.ws.{ WsApp, InverseWsApp }

object Boot extends ZIOAppDefault:

  private val feOrigin = "http://localhost:3000"

  private val corsConfig =
    CorsConfig(
      anyOrigin = false,
      anyMethod = false,
      allowedOrigins = _ == feOrigin,
      allowedMethods = Some(Set(Method.GET))
    )

  private val inverseWsApp = new InverseWsApp
  private val app =
    Http.collectZIO[Request] {
      case req @ Method.GET -> !! / "inverseWs" / "subscriptions" =>
        val isFeOrigin = req.origin.exists(feOrigin contentEquals _)

        if isFeOrigin then
          inverseWsApp.msgOutLogic.toResponse
        else
          ZIO.succeed(Response.status(Status.Forbidden))
    } @@ Middleware.cors(corsConfig)

  override val run: UIO[ExitCode] =
    ZIO.scoped(
      for
        _ <- Console.printLine(s"Starting the server at http://localhost:8081").orDie
        _ <- Server.start(8081, app).forkScoped
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
