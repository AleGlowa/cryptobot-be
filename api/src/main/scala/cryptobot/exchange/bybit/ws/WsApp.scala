package cryptobot.exchange.bybit.ws

import zio.*
import zio.json.DecoderOps
import zio.json.ast.{ Json, JsonCursor }
import zhttp.socket.{ SocketApp, WebSocketFrame }
import zhttp.service.{ EventLoopGroup, ChannelFactory, ChannelEvent }
import zhttp.service.ChannelEvent.{ UserEventTriggered, UserEvent, ExceptionCaught, ChannelRead }

import java.net.SocketTimeoutException

import cryptobot.config.{ WsConfig, WsConnConfig }
import cryptobot.exchange.bybit.MarketType

trait WsApp:
  import WsApp.SocketEnv

  def connect(reconnectsNumR: => Ref[Int]): RIO[SocketEnv, Unit]

end WsApp


object WsApp:

  type SocketEnv = EventLoopGroup & (ChannelFactory & Scope) & WsConfig

  private def inverseMsgLogic(pingInterval: Duration) =
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
            val retMsg  = parsed.get(cursor1).map(_.value)
            retMsg match
              case Right("pong") =>
                val cursor2   = JsonCursor.field("success").isBool
                val isSuccess = parsed.get(cursor2).map(_.value)
                isSuccess.fold(
                  fieldNotFound => ZIO.fail(new RuntimeException(fieldNotFound)),
                  isSuccess     =>
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

  val inverse: URLayer[WsConfig, InverseWsApp] =
    ZLayer(
      for
        reconnectInterval <- WsConfig.reconnectInterval
        pingInterval      <- WsConfig.pingInterval
        reconnectTries    <- WsConfig.reconnectTries
        connConfig         = WsConnConfig(reconnectInterval, reconnectTries)
        messageLogic       = inverseMsgLogic(pingInterval)
      yield new InverseWsApp(connConfig, messageLogic)
    )

end WsApp
