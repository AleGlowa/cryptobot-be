package cryptobot.exchange.bybit.ws

import zio.*
import zhttp.socket.{ SocketApp, WebSocketFrame }
import zhttp.service.{ EventLoopGroup, ChannelFactory, Channel }

import cryptobot.config.WsConfig
import cryptobot.exchange.bybit.ws.models.SubArg

trait WsApp:
  import WsApp.{ SocketEnv, WsChannel, Conn, WsState }

  protected val getConfig: URIO[WsConfig, WsConfig] =
    for
      reconnectInterval <- WsConfig.reconnectInterval
      pingInterval      <- WsConfig.pingInterval
      reconnectTries    <- WsConfig.reconnectTries
    yield WsConfig(reconnectInterval, pingInterval, reconnectTries)

  protected def msgLogic: SocketApp[SocketEnv]

  def connect(): RIO[SocketEnv, Conn]
  def disconnect(using conn: Conn)(): RIO[SocketEnv, Unit] =
    for
      isConn <- getIsConnected.flatMap(_.get)
      ch     <- getChannel.flatMap(_.get)
      closeConn =
        for
          _ <- ch.get.close(await = true)
          _ <- conn.interrupt
          _ <- setIsConnected(false)
          _ <- ZIO.logInfo(s"Connection [id=${conn.id.id}] has been closed")
        yield ()
      _      <- closeConn.when(isConn)
    yield ()

  def subscribe(sub: => SubArg)  : RIO[SocketEnv, Unit]
  def unsubscribe(sub: => SubArg): RIO[SocketEnv, Unit]

  protected def setInitialState: URIO[SocketEnv, Unit] =
    setIsConnected(false) <&> setChannel(None)


  /** `isConnected` - get & set */
  val getIsConnected: URIO[SocketEnv, Ref[Boolean]] =
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
  type Conn      = Fiber.Runtime[Throwable, Unit]

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
