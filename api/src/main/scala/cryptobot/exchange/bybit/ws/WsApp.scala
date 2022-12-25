package cryptobot.exchange.bybit.ws

import zio.*
import zhttp.socket.{ SocketApp, WebSocketFrame }
import zhttp.service.{ EventLoopGroup, ChannelFactory, Channel }

import cryptobot.config.WsConfig
import cryptobot.exchange.bybit.ws.models.SubArg

trait WsApp:
  import WsApp.{ SocketEnv, WsChannel, WsState }

  protected val getConfig: URIO[WsConfig, WsConfig] =
    for
      reconnectInterval <- WsConfig.reconnectInterval
      pingInterval      <- WsConfig.pingInterval
      reconnectTries    <- WsConfig.reconnectTries
    yield WsConfig(reconnectInterval, pingInterval, reconnectTries)

  protected def msgLogic: SocketApp[SocketEnv]

  def connect(): RIO[SocketEnv, Unit]

  def subscribe(sub: => SubArg)  : RIO[SocketEnv, Unit]
  def unsubscribe(sub: => SubArg): RIO[SocketEnv, Unit]

  /** `isConnected` - get & update */
  protected val getIsConnected: URIO[SocketEnv, Ref[Boolean]] =
    ZIO.getStateWith[WsState](_.isConnected)

  protected def updateIsConnected(value: => Boolean): URIO[SocketEnv, Unit] =
    for
      isConnected <- getIsConnected
      _           <- isConnected.set(value)
      _           <- ZIO.updateState[WsState](_.copy(isConnected = isConnected))
    yield ()
  /** `isConnected` - get & update */


  /** `channel` - get & update */
  protected val getChannel: URIO[SocketEnv, Ref[Option[WsChannel]]] =
    ZIO.getStateWith[WsState](_.channel)

  protected def updateChannel(value: => Option[WsChannel]): URIO[SocketEnv, Unit] =
    for
      channel <- getChannel
      _       <- channel.set(value)
      _       <- ZIO.updateState[WsState](_.copy(channel = channel))
    yield ()
  /** `channel` - get & update */


  protected def addSub(sub: SubArg): URIO[SocketEnv, Unit] =
    ZIO.updateState[WsState] ( state =>
      state.copy(subscriptions = state.subscriptions + sub)
    )

end WsApp


object WsApp:

  type SocketEnv = EventLoopGroup & (ChannelFactory & Scope) & WsConfig & ZState[WsState]
  type WsChannel = Channel[WebSocketFrame]

  final case class WsState(isConnected: Ref[Boolean], channel: Ref[Option[WsChannel]], subscriptions: Set[SubArg])
  val initialState: ULayer[ZState[WsState]] =
    ZLayer(
      for
        isConnected <- Ref.make(false)
        channel     <- Ref.make[Option[WsChannel]](None)
      yield WsState(isConnected, channel, Set.empty[SubArg])
    )
    .flatMap(env => ZState.initial(env.get[WsState]))

  val inverse: ULayer[InverseWsApp] = ZLayer(ZIO.succeed(new InverseWsApp))

end WsApp
