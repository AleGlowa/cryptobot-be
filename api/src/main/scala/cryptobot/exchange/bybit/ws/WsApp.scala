package cryptobot.exchange.bybit.ws

import zio.*
import zio.stream.{ UStream, SubscriptionRef }
import zio.json.ast.Json.Obj
import zhttp.socket.{ SocketApp, WebSocketFrame }
import zhttp.service.{ EventLoopGroup, ChannelFactory, Channel }

import cryptobot.config.WsConfig
import cryptobot.exchange.bybit.ws.models.{ Topic, MsgIn }
import cryptobot.exchange.bybit.ws.models.RespType.InstrumentInfoResp
import cryptobot.exchange.bybit.ws.models.Topic.InstrumentInfo

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

  protected def subscribe(topic: => Topic)  : RIO[SocketEnv, Unit]
  protected def unsubscribe(topic: => Topic): RIO[SocketEnv, Unit]

  protected def setInitialState: URIO[SocketEnv, Unit] =
    setIsConnected(false) <&> setChannel(None) <&> clearTopics()


  /** `isConnected` - get & set */
  val getIsConnected: URIO[ZState[WsState], Ref[Boolean]] =
    ZIO.getStateWith[WsState](_.isConnected)

  protected def setIsConnected(value: => Boolean): URIO[SocketEnv, Unit] =
    for
      isConn <- getIsConnected
      _      <- isConn.set(value)
      _      <- ZIO.updateState[WsState](_.copy(isConnected = isConn))
    yield ()
  /** `isConnected` - get & set */


  /** `channel` - get & set */
  val getChannel: URIO[ZState[WsState], Ref[Option[WsChannel]]] =
    ZIO.getStateWith[WsState](_.channel)

  protected def setChannel(value: => Option[WsChannel]): URIO[SocketEnv, Unit] =
    for
      ch <- getChannel
      _  <- ch.set(value)
      _  <- ZIO.updateState[WsState](_.copy(channel = ch))
    yield ()
  /** `channel` - get & set */


  /** `topics` - get, add, delete, clear */
  val getTopics: URIO[ZState[WsState], Ref[Set[Topic]]] =
    ZIO.getStateWith[WsState](_.topics)

  protected def addTopic(topic: Topic): URIO[SocketEnv, Unit] =
    for
      topics <- getTopics
      _      <- topics.update(_ + topic)
      _      <- ZIO.updateState[WsState](_.copy(topics = topics))
    yield ()

  protected def deleteTopic(topic: Topic): URIO[SocketEnv, Unit] =
    for
      topics <- getTopics
      _      <- topics.update(_ - topic)
      _      <- ZIO.updateState[WsState](_.copy(topics = topics))
    yield ()

  protected def clearTopics(): URIO[SocketEnv, Unit] =
    for
      topics <- getTopics
      _      <- topics.set(Set.empty)
      _      <- ZIO.updateState[WsState](_.copy(topics = topics))
    yield ()
  /** `topics` - add, delete, clear */


  /** `subMsgs` - add 
   *    `InstrumentInfo - get
  */
  protected val getInstrumentInfoMsgs: URIO[ZState[WsState], UStream[Obj]] =
    for
      subMsgs           <- ZIO.getStateWith[WsState](_.subMsgs)
      instrumentInfoMsgs =
        subMsgs.changes
          .collect ( msg =>
            msg.topic match
              case _: InstrumentInfo => msg.obj
          )
    yield instrumentInfoMsgs

  protected def addMsg(msg: MsgIn): URIO[ZState[WsState], Unit] =
    for
      subMsgs <- ZIO.getStateWith[WsState](_.subMsgs)
      _       <- subMsgs.setAsync(msg)
    yield ()
  /** `subMsgs` - add 
   *    `InstrumentInfo - get
  */

end WsApp


object WsApp:

  type SocketEnv = EventLoopGroup & (ChannelFactory & Scope) & WsConfig & ZState[WsState]
  type WsChannel = Channel[WebSocketFrame]
  type Conn      = Fiber.Runtime[Throwable, Unit]

  final case class WsState(
    isConnected: Ref[Boolean],
    channel    : Ref[Option[WsChannel]],
    topics     : Ref[Set[Topic]],
    subMsgs    : SubscriptionRef[MsgIn]
  )
  val initialState: ULayer[ZState[WsState]] =
    ZLayer(
      for
        isConnected <- Ref.make(false)
        channel     <- Ref.make[Option[WsChannel]](None)
        topics      <- Ref.make(Set.empty[Topic])
        subMsgs     <- SubscriptionRef.make(MsgIn.originMsg)
      yield WsState(isConnected, channel, topics, subMsgs)
    )
    .flatMap(env => ZState.initial(env.get[WsState]))

  val inverse: ULayer[InverseWsApp] = ZLayer(ZIO.succeed(new InverseWsApp))

end WsApp
