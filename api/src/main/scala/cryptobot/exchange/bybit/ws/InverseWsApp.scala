package cryptobot.exchange.bybit.ws

import zio.*
import zio.stream.UStream
import zio.json.DecoderOps
import zio.json.ast.Json
import zhttp.http.Http
import zhttp.socket.*
import zhttp.service.ChannelEvent
import zhttp.service.ChannelEvent.*

import java.net.SocketTimeoutException

import cryptobot.exchange.bybit.{ MarketType, Currency }
import cryptobot.exchange.bybit.ws.WsApp.{ SocketEnv, Conn }
import cryptobot.exchange.bybit.ws.models.{ Topic, MsgIn }
import cryptobot.exchange.bybit.ws.models.Topic.InstrumentInfo
import cryptobot.exchange.bybit.ws.models.RespType.*
import cryptobot.exchange.bybit.ws.models.SubRespType.*
import cryptobot.exchange.bybit.ws.models.Cursors.{ dataCursor, updateCursor }
import cryptobot.exchange.bybit.Currency.*

class InverseWsApp extends WsApp:

  override protected def msgInLogic: SocketApp[SocketEnv] =
    Http.fromZIO(getConfig).flatMap ( config =>
      Http.collectZIO[WebSocketChannelEvent] {

        case ChannelEvent(ch, UserEventTriggered(userEvent)) =>
          if userEvent == UserEvent.HandshakeTimeout then
            ZIO.fail(new SocketTimeoutException())
          else
            for
              _  <- setIsConnected(true)
              _  <- ZIO.logInfo("Bybit ws connection for inverse market type has been opened")
              _  <- ch.writeAndFlush(WebSocketFrame.text("""{"op": "ping"}"""))
            yield ()

        case ChannelEvent(ch, ChannelEvent.ChannelRegistered) =>
          for
            _ <- setChannel(Some(ch))
            _ <- ZIO.logInfo(s"Channel [id=${ch.id}] has been registered")
          yield ()

        case ChannelEvent(ch, ChannelEvent.ChannelUnregistered) =>
          for
            _ <- setChannel(None)
            _ <- ZIO.logInfo(s"Channel [id=${ch.id}] has been unregistered")
          yield ()

        case ChannelEvent(_, ExceptionCaught(t)) =>
          ZIO.fail(t)

        case ChannelEvent(ch, ChannelRead(WebSocketFrame.Text(json))) =>
          json.fromJson[Json].fold(
            parseErr => ZIO.fail(new RuntimeException(parseErr)),
            parsed   =>
              RespDiscriminator.getRespType(parsed) match
                case Right(Pong) =>
                  RespDiscriminator.getPongResp(parsed).fold(
                    fieldNotFound => ZIO.fail(new RuntimeException(fieldNotFound)),
                    isSuccessful  =>
                      if isSuccessful then
                        ZIO.sleep(config.pingInterval) *> ch.writeAndFlush(WebSocketFrame.text("""{"op": "ping"}"""))
                      else
                        ZIO.fail(new RuntimeException("Unseccussful pong response"))
                  )

                case Right(SuccessfulSub) =>
                  ZIO.logInfo("Successful subscription")

                case Right(InstrumentInfoResp(curr1, curr2)) =>
                  RespDiscriminator.getSubRespType(parsed) match
                    case Right(Snapshot) =>
                      parsed.get(dataCursor) match
                        case Right(data)         => addMsg(MsgIn(data, InstrumentInfo(curr1, curr2)))
                        case Left(fieldNotFound) => ZIO.fail(new RuntimeException(fieldNotFound))

                    case Right(Delta)    =>
                      parsed.get(updateCursor) match
                        case Right(update)       => addMsg(MsgIn(update, InstrumentInfo(curr1, curr2)))
                        case Left(fieldNotFound) => ZIO.fail(new RuntimeException(fieldNotFound))

                    case Left(fieldNotFound) =>
                      ZIO.fail(new RuntimeException(fieldNotFound))

                case Right(UnsuccessfulSub(err)) =>
                  ZIO.fail(new RuntimeException(s"Unseccussful subscription response: $err"))

                case Left(fieldNotFound) =>
                  ZIO.fail(new RuntimeException(fieldNotFound))
          )

        case ChannelEvent(ch, ChannelRead(_)) =>
          ZIO.fail(new RuntimeException("Got a non-text response"))
        }
    ).toSocketApp

  override def msgOutLogic: SocketApp[SocketEnv] =
    Http.collectZIO[WebSocketChannelEvent] {
      case ChannelEvent(ch, UserEventTriggered(UserEvent.HandshakeComplete))  =>
        for
          _ <- connect()
          // Wait ~1 second to establish connection with Bybit API in other fiber
          _ <- ZIO.sleep(1.second)
          _ <- ch.writeAndFlush(WebSocketFrame.text("Connected"))
        yield ()

      // Accept only text messages from frontend. Maybe later make messages require parsable to json
      case ChannelEvent(ch, ChannelRead(WebSocketFrame.Text(msg)))            =>
        msg match
          case "end\n" => disconnect() *> ch.writeAndFlush(WebSocketFrame.text("Disconnected"))
          case "sub.BTCUSD\n" =>
            for
              stream <- getLastPrice(BTC, USD)
              _      <- stream.runForeach(price => ch.writeAndFlush(WebSocketFrame.text(price)))
            yield ()
          case "sub.ETHUSD\n" =>
            for
              stream <- getLastPrice(ETH, USD)
              _      <- stream.runForeach(price => ch.writeAndFlush(WebSocketFrame.text(price)))
            yield ()
          case unknown => ch.writeAndFlush(WebSocketFrame.text(s"Unknown subscription: $unknown"))

      case ChannelEvent(_, ExceptionCaught(cause))                            =>
        Console.printLine(s"Channel ERROR: ${cause.getMessage}")
    }
    .toSocketApp
    .withDecoder(SocketDecoder.default.withExtensions(allowed = true))
    .withProtocol(SocketProtocol.default.withSubProtocol(Some("json")))

  override def connect(): URIO[SocketEnv, Conn] =
    (for
      config         <- getConfig
      reconnectsNumR <- Ref.make(0)
      connToRepeat    =
        for
          connP         <- Promise.make[Nothing, Throwable]
          _             <-
            msgInLogic
              .connect(MarketType.InverseWssPath)
              .tap(_ => reconnectsNumR.set(0))
              .catchAll(connP.succeed)
          connFail      <- connP.await
          _             <- setIsConnected(false)
          _             <- ZIO.logError(s"Bybit ws connection for inverse market type failed: $connFail")
          _             <- ZIO.logError("Trying to reconnect...")
          reconnectsNum <- reconnectsNumR.updateAndGet(_ + 1)
        yield reconnectsNum
      _              <-
        connToRepeat.repeat(
          Schedule.spaced(config.reconnectInterval)
            .zipLeft(Schedule.recurWhile[Int](_ < config.reconnectTries))
            .tapOutput(recNum => checkReconnectTries(recNum.toInt - 1, config.reconnectTries))
          )
    yield ())
      .ensuring(setInitialState)
      .forkScoped
      .tap(v => setConn(Some(v)))
      .tapDefect ( defect =>
        ZIO.logError(s"Got a defect from inverse socket app: ${defect.dieOption.get}")
      )

  override def subscribe(topic: => Topic): RIO[SocketEnv, Unit] =
    ZIO.ifZIO(getIsConnected.flatMap(_.get))(
      for
        ch <- getChannel.flatMap(_.get)
        _  <- ch match
          case None => ZIO.logInfo("Can't subscribe, because a channel is't registered")
          case Some(ch) =>
            ch.writeAndFlush(
              WebSocketFrame.text(
                s"""
                |{
                |  "op": "subscribe",
                |  "args": ["${topic.parse}"]
                |}
                """.stripMargin
              ),
              await = true
            ) *> addTopic(topic)
      yield (),
      ZIO.logInfo("A connection isn't established")
    )

  override def unsubscribe(topic: => Topic): RIO[SocketEnv, Unit] = ???

  def getLastPrice(curr1: Currency, curr2: Currency): RIO[SocketEnv, UStream[String]] =
    for
      _        <- subscribe(InstrumentInfo(curr1, curr2))
      msgs     <- getInstrumentInfoMsgs
      lastPrice =
        msgs
          .map(json => RespDiscriminator.getLastPriceResp(json, curr1, curr2))
          .collectRight
    yield lastPrice

  private def checkReconnectTries(reconnectsNum: => Int, reconnectTries: => Int) =
    if reconnectsNum < reconnectTries then
      ZIO.die(new RuntimeException("Cumulative reconnection attempts've reached the maximum"))
    else
      ZIO.unit
