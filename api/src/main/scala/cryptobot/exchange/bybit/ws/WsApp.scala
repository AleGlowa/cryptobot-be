package cryptobot.exchange.bybit.ws

import zio.*
import zio.stream.{ ZStream, UStream }
import zio.json.ast.Json.Obj
import zhttp.socket.{ SocketApp, WebSocketFrame }
import zhttp.service.{ EventLoopGroup, ChannelFactory, Channel }
import scala.collection.mutable.{ Map => MutMap }

import cryptobot.config.WsConfig
import cryptobot.exchange.bybit.Currency
import cryptobot.exchange.bybit.ws.model.{ Topic, MsgIn }
import cryptobot.exchange.bybit.ws.model.Topic.InstrumentInfo

trait WsApp:
  import WsApp.{ SocketEnv, WsChannel, Conn, Subs, WsState }

  protected val subs: Subs = MutMap.empty

  protected val getConfig: URIO[WsConfig, WsConfig] =
    for
      reconnectInterval <- WsConfig.reconnectInterval
      pingInterval      <- WsConfig.pingInterval
      reconnectTries    <- WsConfig.reconnectTries
    yield WsConfig(reconnectInterval, pingInterval, reconnectTries)

  protected val msgInLogic: SocketApp[SocketEnv]
  val msgOutLogic         : SocketApp[SocketEnv]

  def connect()   : RIO[SocketEnv, Conn]
  def disconnect(): URIO[SocketEnv, Unit] =
    for
      isConn   <- getIsConnected.flatMap(_.get)
      conn     <- getConn.flatMap(_.get)
      closeConn =
        for
          _ <- conn.get.interrupt
          _ <- setInitialState()
          _ <- ZIO.logInfo(s"Connection [id=${conn.get.id.id}] has been closed")
        yield ()
      _      <- closeConn.when(isConn)
    yield ()

  protected def waitUntilConnEstablished(): URIO[ZState[WsState], Unit] =
    getIsConnected.repeat(Schedule.spaced(1.second) && Schedule.recurUntilZIO(_.get)).ignore

  protected def subscribe(topic: => Topic)  : RIO[SocketEnv, Unit]
  protected def unsubscribe(topic: => Topic): RIO[SocketEnv, Unit]

  private def setInitialState(): URIO[SocketEnv, Unit] =
    setConn(None) <&> setIsConnected(false) <&> setChannel(None) <&> clearTopics()


  /** `conn` - get & set */
  val getConn: URIO[ZState[WsState], Ref[Option[Conn]]] =
    ZIO.getStateWith[WsState](_.conn)

  protected def setConn(value: => Option[Conn]): URIO[ZState[WsState], Unit] =
    for
      conn <- getConn
      _    <- conn.set(value)
      _    <- ZIO.updateState[WsState](_.copy(conn = conn))
    yield ()
  /** `conn` - get & set */

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

  protected def addMsg(msg: => MsgIn): UIO[Unit] = msg.topic match
    case InstrumentInfo(curr1, curr2) =>
      val pair = (curr1, curr2)
      val hub = subs.get(pair)

      (for
        newHub <- ZIO.when(hub.isEmpty)(
          for
            hub <- Hub.sliding[Obj](64)
            _    = subs += pair -> hub
          yield hub
        )
        _      <- hub.orElse(newHub).get.publish(msg.obj)
      yield ()).ignore

  protected def getMsgs(curr1: Currency, curr2: Currency): UStream[Obj] =
    ZStream.fromHub(subs((curr1, curr2)))

end WsApp


object WsApp:

  type SocketEnv = EventLoopGroup & (ChannelFactory & Scope) & WsConfig & ZState[WsState]
  type WsChannel = Channel[WebSocketFrame]
  type Conn      = Fiber.Runtime[Throwable, Unit]
  type Subs      = MutMap[(Currency, Currency), Hub[Obj]]

  final case class WsState(
    conn       : Ref[Option[Conn]],
    isConnected: Ref[Boolean],
    channel    : Ref[Option[WsChannel]],
    topics     : Ref[Set[Topic]],
  )
  val initialState: ULayer[ZState[WsState]] =
    ZLayer(
      for
        conn        <- Ref.make[Option[Conn]](None)
        isConnected <- Ref.make(false)
        channel     <- Ref.make[Option[WsChannel]](None)
        topics      <- Ref.make(Set.empty[Topic])
      yield WsState(conn, isConnected, channel, topics)
    )
    .flatMap(env => ZState.initial(env.get[WsState]))

  val inverse: ULayer[InverseWsApp] = ZLayer(ZIO.succeed(new InverseWsApp))

end WsApp
