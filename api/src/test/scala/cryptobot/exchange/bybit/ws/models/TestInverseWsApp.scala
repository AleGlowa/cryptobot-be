package cryptobot.exchange.bybit.ws.models

import zio.Ref
import zhttp.service.ChannelEvent
import zhttp.service.ChannelEvent.{ Event, ChannelUnregistered, UserEventTriggered }
import zhttp.service.ChannelEvent.UserEvent.HandshakeComplete
import zhttp.socket.{ SocketApp, WebSocketFrame }

import cryptobot.exchange.bybit.ws.InverseWsApp
import cryptobot.exchange.bybit.ws.WsApp.SocketEnv

final class TestInverseWsApp(
  msgCollector: MessageCollector[Event[WebSocketFrame]],
  isConnected : Ref[Boolean]
) extends InverseWsApp:

    override def msgLogic: SocketApp[SocketEnv] =
      val logicWithoutCollector = super.msgLogic
      logicWithoutCollector.copy(
        message =
          logicWithoutCollector.message.map ( cb =>
            {
              case ev @ ChannelEvent(_, ChannelUnregistered)                   =>
                cb(ev) <* msgCollector.add(ev.event, isDone = true) <* isConnected.set(false)
              case ev @ ChannelEvent(_, UserEventTriggered(HandshakeComplete)) =>
                cb(ev) <* msgCollector.add(ev.event) <* isConnected.set(true)
              case ev @ ChannelEvent(_, _)                   =>
                cb(ev) <* msgCollector.add(ev.event)
            }
          )
      )
