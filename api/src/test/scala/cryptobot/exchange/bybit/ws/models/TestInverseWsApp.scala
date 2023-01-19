package cryptobot.exchange.bybit.ws.models

import zio.URIO
import zhttp.service.ChannelEvent
import zhttp.service.ChannelEvent.{ Event, ChannelUnregistered, UserEventTriggered }
import zhttp.service.ChannelEvent.UserEvent.HandshakeComplete
import zhttp.socket.{ SocketApp, WebSocketFrame }
import scala.collection.immutable.{ Queue => ScalaQueue }

import cryptobot.exchange.bybit.ws.InverseWsApp
import cryptobot.exchange.bybit.ws.WsApp.SocketEnv

final class TestInverseWsApp(
  val msgCollector: MessageCollector[Event[WebSocketFrame]]
) extends InverseWsApp:

    override def msgLogic: SocketApp[SocketEnv] =
      val logicWithoutCollector = super.msgLogic
      logicWithoutCollector.copy(
        message =
          logicWithoutCollector.message.map ( cb =>
            {
              case ev @ ChannelEvent(_, ChannelUnregistered)                   =>
                cb(ev) <* msgCollector.add(ev.event, isDone = true)
              case ev @ ChannelEvent(_, UserEventTriggered(HandshakeComplete)) =>
                cb(ev) <* msgCollector.add(ev.event)
              case ev @ ChannelEvent(_, _)                   =>
                cb(ev) <* msgCollector.add(ev.event)
            }
          )
      )

    def getEvents: URIO[SocketEnv, ScalaQueue[Event[WebSocketFrame]]] =
      for
        channel   <- getChannel.flatMap(_.get)
        events    <- msgCollector.await.when(channel.isEmpty)
      yield events.get
