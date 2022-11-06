package cryptobot.api.bybit.ws

import zio.*
import zio.config.ReadError
import zio.json.DecoderOps
import zio.json.ast.{ Json, JsonCursor }
import zhttp.http.*
import zhttp.service.{ ChannelEvent, EventLoopGroup, ChannelFactory }
import zhttp.service.ChannelEvent.{ UserEventTriggered, UserEvent, ExceptionCaught, ChannelRead }
import zhttp.socket.{ SocketApp, WebSocketFrame }

import java.net.SocketTimeoutException

import cryptobot.api.Exchange.ByBit
import cryptobot.api.bybit.MarketType
import cryptobot.api.bybit.ws.Config

object Boot extends ZIOAppDefault:

  type SocketEnv = EventLoopGroup & (ChannelFactory & Scope)

  // Maybe later add @@ Middleware.debug to ws app
  private def makeInverseSocketApp(pingInterval: Duration) =
    SocketApp[Any] {

      case ChannelEvent(ch, UserEventTriggered(userEvent)) =>
        if userEvent == UserEvent.HandshakeTimeout then
          ZIO.fail(new SocketTimeoutException())
        else
          ZIO.logInfo("Bybit ws connection for inverse market type has been opened") *>
            ch.writeAndFlush(WebSocketFrame.Text("""{"op": "ping"}"""))

      case ChannelEvent(ch, ChannelEvent.ChannelRegistered) =>
        ZIO.logInfo(s"Channel [id=${ch.id}] has been registered")

      case ChannelEvent(ch, ChannelEvent.ChannelUnregistered) =>
        ZIO.logInfo(s"Channel [id=${ch.id}] has been unregistered")

      case ChannelEvent(_, ExceptionCaught(t)) =>
        ZIO.fail(t)

      case ChannelEvent(ch, ChannelRead(WebSocketFrame.Text(json))) =>
        // Later make more beauty json response handler
        json.fromJson[Json].fold(
          parseErr => ZIO.fail(new RuntimeException(parseErr)),
          parsed  =>
            val cursor1 = JsonCursor.field("ret_msg").isString
            val tmp1    = parsed.get(cursor1).map(_.value)
            tmp1 match
              case Right("pong") =>
                val cursor2 = JsonCursor.field("success").isBool
                val tmp2    = parsed.get(cursor2).map(_.value)
                tmp2.fold(
                  fieldNotFound   => ZIO.fail(new RuntimeException(fieldNotFound)),
                  isSuccess       =>
                    if isSuccess then
                      ZIO.sleep(pingInterval) *> ch.writeAndFlush(WebSocketFrame.Text("""{"op": "ping"}"""))
                    else
                      ZIO.fail(new RuntimeException("Unseccussful ping request"))
                )
              case Right(resp) =>
                ZIO.fail(new RuntimeException(s"Unrecognized response: $resp"))
              case Left(fieldNotFound) =>
                ZIO.fail(new RuntimeException(fieldNotFound))
        )

      case ChannelEvent(ch, ChannelRead(_)) =>
        ZIO.fail(new RuntimeException("Got a non-text response"))
    }

  // Maybe later change from Promise to Ref or ZState, creater a counter as a Ref describing cumulate connection retries
  private val inverseSocketApp: ZIO[SocketEnv, ReadError[String], Unit] =
    ((for
      reconnectInterval <- Config.reconnectInterval
      pingInterval      <- Config.pingInterval
      pConn             <- Promise.make[Nothing, Throwable]
      _                 <- makeInverseSocketApp(pingInterval).connect(MarketType.InverseWssPath).catchAll(pConn.succeed)
      connFail          <- pConn.await
      _                 <- ZIO.logError(s"Bybit ws connection for inverse market type failed: $connFail")
      _                 <- ZIO.logError("Trying to reconnect...")
      _                 <- ZIO.sleep(reconnectInterval)
    yield ()) *> inverseSocketApp)
      .provideSomeLayer[SocketEnv](Config.layer)

  override def run: UIO[ExitCode] =
    ZIO.scoped(
      for
        _ <- Console.printLine(s"Starting the application").orDie
        _ <- inverseSocketApp.forkScoped
        _ <- Console.readLine("Press ENTER to stop the application\n").orDie *> Console.printLine("Stopping the application...")
      yield ()
    )
    .provide(
      // Types of EventLoopGroup from netty to investigate
      EventLoopGroup.nio(nThreads = 4),
      ChannelFactory.nio
    )
    .exitCode

  // private val httpApp = Http.collectZIO[Request] {
  //   case Method.GET -> !! / ByBit.name / "inverseWs" => inverseSocketApp.connect(MarketType.InverseWssPath)
  // } @@ Middleware.debug

  // override def run: UIO[ExitCode] =
  //   ZIO.scoped(
  //     for
  //       _ <- Console.printLine(s"Starting server on http://localhost:$Port").orDie
  //       _ <- Server.start(Port, httpApp).forkScoped
  //       _ <- Console.readLine("Press ENTER to stop the server\n").orDie *> Console.printLine("Closing the server connection...")
  //     yield ()
  //   )
  //   .provide(
  //     EventLoopGroup.default,
  //     ChannelFactory.nio
  //   )
  //   .exitCode
