package cryptobot.exchange.bybit.ws

import zio.*
import zio.json.DecoderOps
import zio.json.ast.{ Json, JsonCursor }
import zhttp.http.Http
import zhttp.socket.{ SocketApp, WebSocketFrame, WebSocketChannelEvent }
import zhttp.service.{ EventLoopGroup, ChannelFactory, ChannelEvent }
import zhttp.service.ChannelEvent.{ UserEventTriggered, UserEvent, ExceptionCaught, ChannelRead }

import java.net.SocketTimeoutException

import cryptobot.exchange.bybit.MarketType
import cryptobot.exchange.bybit.ws.WsApp.SocketEnv
import cryptobot.config.WsConfig

class InverseWsApp extends WsApp:

  override protected def msgLogic: SocketApp[WsConfig] =
    Http.fromZIO(getConfig).flatMap ( config =>
      Http.collectZIO[WebSocketChannelEvent] {

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
              val retMsg  = parsed.get(cursor1).map(_.value)
              retMsg match
                case Right("pong") =>
                  val cursor2   = JsonCursor.field("success").isBool
                  val isSuccess = parsed.get(cursor2).map(_.value)
                  isSuccess.fold(
                    fieldNotFound => ZIO.fail(new RuntimeException(fieldNotFound)),
                    isSuccess     =>
                      if isSuccess then
                        ZIO.sleep(config.pingInterval) *> ch.writeAndFlush(WebSocketFrame.Text("""{"op": "ping"}"""))
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
    ).toSocketApp

  override def connect(): RIO[SocketEnv, Unit] =
    (for
      config         <- getConfig
      reconnectsNumR <- Ref.make(0)
      connToRepeat    =
        for
          connP         <- Promise.make[Nothing, Throwable]
          _             <-
            msgLogic
              .connect(MarketType.InverseWssPath)
              .tap(_ => reconnectsNumR.set(0))
              .catchAll(connP.succeed)
          connFail      <- connP.await
          _             <- ZIO.logError(s"Bybit ws connection for inverse market type failed: $connFail")
          _             <- ZIO.logError("Trying to reconnect...")
          reconnectsNum <- reconnectsNumR.updateAndGet(_ + 1)
        yield reconnectsNum
      _              <-
        connToRepeat.repeat(
          Schedule.spaced(config.reconnectInterval)
            .zipLeft(Schedule.recurWhile[Int](_ < config.reconnectTries))
            .tapOutput(exNum => checkReconnectTries(exNum.toInt - 1, config.reconnectTries))
          )
    yield ())
      .tapDefect ( defect =>
        ZIO.logError(s"Got a defect from inverse socket app: ${defect.dieOption.get}")
      )

  private def checkReconnectTries(reconnectsNum: => Int, reconnectTries: => Int) =
    if reconnectsNum < reconnectTries then
      ZIO.die(new RuntimeException("Cumulative reconnection attempts've reached the maximum"))
    else
      ZIO.unit

object InverseWsApp:

  private type InverseWsAppType = InverseWsApp & SocketEnv

  val start: URIO[InverseWsAppType, Fiber.Runtime[Throwable, Unit]] =
    ZIO.serviceWithZIO[InverseWsApp](_.connect()).forkScoped
