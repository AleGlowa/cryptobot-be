package cryptobot.exchange.bybit.ws

import zio.*
import zio.stream.UStream
import zio.json.{ EncoderOps, DecoderOps }
import zio.json.ast.Json
import zhttp.http.Http
import zhttp.socket.*
import zhttp.service.ChannelEvent
import zhttp.service.ChannelEvent.*

import java.net.SocketTimeoutException

import cryptobot.exchange.bybit.{ MarketType, Currency }
import cryptobot.exchange.bybit.Extensions.*
import cryptobot.exchange.bybit.ws.WsApp.{ SocketEnv, Conn }
import cryptobot.exchange.bybit.ws.model.{ Topic, MsgIn }
import cryptobot.exchange.bybit.ws.model.Topic.InstrumentInfo
import cryptobot.exchange.bybit.ws.model.RespType.*
import cryptobot.exchange.bybit.ws.model.SubRespType.*
import cryptobot.exchange.bybit.ws.Cursors.{ dataCursor, updateCursor, argsCursor }
import cryptobot.exchange.bybit.Currency.*
import cryptobot.exchange.bybit.ws.Cursors
import cryptobot.exchange.bybit.ws.Codecs.given
import cryptobot.exchange.bybit.ws.response.*
import cryptobot.exchange.bybit.ws.request.sub.LastPriceSub
import cryptobot.exchange.bybit.ws.request.unsub.LastPriceUnsub

class InverseWsApp extends WsApp:

  override protected val msgInLogic: SocketApp[SocketEnv] =
    Http.fromZIO(getConfig).flatMap ( config =>
      Http.collectZIO[WebSocketChannelEvent] {

        case ChannelEvent(ch, UserEventTriggered(userEvent)) =>
          if userEvent == UserEvent.HandshakeTimeout then
            ZIO.fail(new SocketTimeoutException())
          else
            for
              _  <- setIsConnected(true)
              _  <- ZIO.logInfo("Bybit ws connection for inverse market type has been opened")
              _  <- ch.sendPing()
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
                        ZIO.sleep(config.pingInterval) *> ch.sendPing()
                      else
                        ZIO.fail(new RuntimeException("Unseccussful pong response"))
                  )

                case Right(SuccessfulSub) =>
                  parsed.get(argsCursor)
                    .fold(
                      notFound => ZIO.fail(new RuntimeException(notFound)),
                      sub      => ZIO.logInfo(s"Successful subscription: $sub")
                    )

                case Right(SuccessfulUnsub) =>
                  parsed.get(argsCursor)
                    .fold(
                      notFound => ZIO.fail(new RuntimeException(notFound)),
                      unsub    => ZIO.logInfo(s"Successful unsubscription: $unsub")
                    )

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

  override val msgOutLogic: SocketApp[SocketEnv] =
    Http.collectZIO[WebSocketChannelEvent] {
      case ChannelEvent(ch, UserEventTriggered(UserEvent.HandshakeComplete))  =>
        for
          _ <- connect()
          _ <- waitUntilConnEstablished()
          _ <- ch.sendJson(IsConnResp(true))
        yield ()

      case ChannelEvent(ch, ChannelRead(WebSocketFrame.Text(json)))            =>
        json.fromJson[LastPriceSub] orElse json.fromJson[LastPriceUnsub] match
          case Left(unknown) =>
            ch.sendJson(s"""{err: "Unknown request ($unknown)"}""")
          case Right(LastPriceSub(_, _, curr1, curr2)) =>
            for
              stream <- getLastPrice(curr1, curr2)
              _      <- stream.runForeach(r => ch.sendJson(r))
            yield ()
          case Right(LastPriceUnsub(_, e, curr1, curr2)) =>
            for
              _ <- unsubLastPrice(curr1, curr2)
              _ <- ch.sendJson(UnsubResp(true, e, List(curr1, curr2)))
            yield ()

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
          )
      _              <- ZIO.die(new RuntimeException("Cumulative reconnection attempts've reached the maximum"))    
    yield ())
      .ensuring(disconnect())
      .forkScoped
      .tap(v => setConn(Some(v)))
      .tapDefect ( defect =>
        ZIO.logError(s"Got a defect from inverse socket app: ${defect.dieOption.get}")
      )

  override def subscribe(topic: => Topic): RIO[SocketEnv, Unit] =
    for
      ch <- getChannel.flatMap(_.get)
      _  <- ch match
        case None     => ZIO.logInfo("Can't subscribe, because a channel isn't registered")
        case Some(ch) =>
          ch.sendToBybit("subscribe", topic.parse) *> addTopic(topic)
    yield ()

  override def unsubscribe(topic: => Topic): RIO[SocketEnv, Unit] =
    for
      ch <- getChannel.flatMap(_.get)
      _  <- ch match
        case None     => ZIO.logInfo("Can't unsubscribe, because a channel isn't registered")
        case Some(ch) =>
          ch.sendToBybit("unsubscribe", topic.parse) *> deleteTopic(topic)
    yield ()

  def getLastPrice(curr1: Currency, curr2: Currency): RIO[SocketEnv, UStream[LastPriceResp]] =
    for
      _        <- subscribe(InstrumentInfo(curr1, curr2))
      msgs     <- getMsgs[InstrumentInfo]
      lastPrice =
        msgs
          .map(json => RespDiscriminator.getLastPriceResp(json, curr1, curr2))
          .collectRight
    yield lastPrice

  def unsubLastPrice(curr1: Currency, curr2: Currency): RIO[SocketEnv, Unit] =
    unsubscribe(InstrumentInfo(curr1, curr2))
