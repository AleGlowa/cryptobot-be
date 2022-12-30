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

  def connect()   : RIO[SocketEnv, Unit]
  def disconnect(): RIO[SocketEnv, Unit] =
    for
      ch <- getChannel.flatMap(_.get)
      _  <- ZIO.when(ch.nonEmpty)(ch.get.close(await = true))
      _  <- setIsConnected(false)
      _  <- setChannel(None)
      _  <- ch.fold(ZIO.unit)(ch => ZIO.logInfo(s"Closing the connection with channel ${ch.id}"))
    yield ()

  def subscribe(sub: => SubArg)  : RIO[SocketEnv, Unit]
  def unsubscribe(sub: => SubArg): RIO[SocketEnv, Unit]

  /** `isConnected` - get & set */
  protected val getIsConnected: URIO[SocketEnv, Ref[Boolean]] =
    ZIO.getStateWith[WsState](_.isConnected)

  protected def setIsConnected(value: => Boolean): URIO[SocketEnv, Unit] =
    for
      isConn <- getIsConnected
      _      <- isConn.set(value)
      _      <- ZIO.updateState[WsState](_.copy(isConnected = isConn))
    yield ()
  /** `isConnected` - get & set */


  /** `channel` - get & set */
  protected val getChannel: URIO[SocketEnv, Ref[Option[WsChannel]]] =
    ZIO.getStateWith[WsState](_.channel)

  protected def setChannel(value: => Option[WsChannel]): URIO[SocketEnv, Unit] =
    for
      ch <- getChannel
      _  <- ch.set(value)
      _  <- ZIO.updateState[WsState](_.copy(channel = ch))
    yield ()
  /** `channel` - get & set */


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
