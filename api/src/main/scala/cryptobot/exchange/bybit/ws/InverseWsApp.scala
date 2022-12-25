package cryptobot.exchange.bybit.ws

import zio.*
import zio.json.DecoderOps
import zio.json.ast.{ Json, JsonCursor }
import zhttp.http.Http
import zhttp.socket.{ SocketApp, WebSocketFrame, WebSocketChannelEvent }
import zhttp.service.{ EventLoopGroup, ChannelFactory, ChannelEvent }
import zhttp.service.ChannelEvent.{ UserEventTriggered, UserEvent, ExceptionCaught, ChannelRead }

import java.net.SocketTimeoutException

import cryptobot.exchange.bybit.MarketType
import cryptobot.exchange.bybit.ws.WsApp.SocketEnv
import cryptobot.exchange.bybit.ws.models.SubArg

class InverseWsApp extends WsApp:

  override protected def msgLogic: SocketApp[SocketEnv] =
    Http.fromZIO(getConfig).flatMap ( config =>
      Http.collectZIO[WebSocketChannelEvent] {

        case ChannelEvent(ch, UserEventTriggered(userEvent)) =>
          if userEvent == UserEvent.HandshakeTimeout then
            ZIO.fail(new SocketTimeoutException())
          else
            for
              _ <- updateIsConnected(true)
              _ <- ZIO.logInfo("Bybit ws connection for inverse market type has been opened")
              r <- ch.writeAndFlush(WebSocketFrame.text("""{"op": "ping"}"""))
            yield r

        case ChannelEvent(ch, ChannelEvent.ChannelRegistered) =>
          ZIO.logInfo(s"Channel [id=${ch.id}] has been registered") *> updateChannel(Some(ch))

        case ChannelEvent(ch, ChannelEvent.ChannelUnregistered) =>
          for
            _ <- updateIsConnected(false)
            _ <- ZIO.logInfo(s"Channel [id=${ch.id}] has been unregistered")
            _ <- updateChannel(None)
          yield ()

        case ChannelEvent(_, ExceptionCaught(t)) =>
          ZIO.fail(t)

        case ChannelEvent(ch, ChannelRead(WebSocketFrame.Text(json))) =>
          /** Later make more beauty json response handler */

          // Check if json is in valid form
          json.fromJson[Json].fold(
            parseErr => ZIO.fail(new RuntimeException(parseErr)),
            parsed   =>
              val retCursor    = JsonCursor.field("ret_msg").isString
              val topicCursor  = JsonCursor.field("topic").isString
              val resp         = (parsed.get(retCursor) orElse parsed.get(topicCursor)).map(_.value)

              // Check whether we get pong or one of the subscription responses
              resp match
                // Pong response
                case Right("pong") =>
                  val isSuccessCursor = JsonCursor.field("success").isBool
                  val isSuccess       = parsed.get(isSuccessCursor).map(_.value)

                  // Check if the pong response is successful
                  isSuccess.fold(
                    fieldNotFound => ZIO.fail(new RuntimeException(fieldNotFound)),
                    isSuccess     =>
                      if isSuccess then
                        ZIO.sleep(config.pingInterval) *> ch.writeAndFlush(WebSocketFrame.text("""{"op": "ping"}"""))
                      else
                        ZIO.fail(new RuntimeException("Unseccussful pong response"))
                  )

                // Subscription responses
                case Right("") =>
                  ZIO.logInfo("Successful subscription")

                case Right("instrument_info.100ms.ETHUSD") =>
                  val typeCursor = JsonCursor.field("type").isString
                  val respType   = parsed.get(typeCursor).map(_.value)
                  respType.fold(
                    fieldNotFound => ZIO.fail(new RuntimeException(fieldNotFound)),
                    respType      =>
                      if respType == "snapshot" then
                        // Optionaly filter a response, like here with `last_price` field
                        val lastPriceCursor = JsonCursor.field("data").isObject.field("last_price").isString
                        val lastPrice = parsed.get(lastPriceCursor).map(_.value)
                        ZIO.logInfo(s"Last price for ETH is $$${lastPrice.getOrElse(-1)}")
                      else
                        // Optionaly filter a response, like here with `last_price` field
                        val lastPriceCursor =
                          JsonCursor.field("data").isObject.field("update").isArray.element(0).isObject.field("last_price").isString
                        val lastPrice = parsed.get(lastPriceCursor)
                        if lastPrice.isLeft then
                          ZIO.unit
                        else
                          ZIO.logInfo(s"Last price for ETH is $$${lastPrice.map(_.value).getOrElse(-1)}")
                  )

                case Right(resp) =>
                  ZIO.fail(new RuntimeException(s"Unseccussful subscription response: $resp"))
                case Left(fieldNotFound) =>
                  ZIO.fail(new RuntimeException(fieldNotFound))
          )

        case ChannelEvent(ch, ChannelRead(_)) =>
          ZIO.fail(new RuntimeException("Got a non-text response"))
        }
    ).toSocketApp

  override def connect(): RIO[SocketEnv, Unit] =
    (for
      config         <- getConfig
      reconnectsNumR <- Ref.make(0)
      connToRepeat    =
        for
          connP         <- Promise.make[Nothing, Throwable]
          _             <-
            msgLogic
              .connect(MarketType.InverseWssPath)
              .tap(_ => reconnectsNumR.set(0))
              .catchAll(connP.succeed)
          connFail      <- connP.await
          _             <- updateIsConnected(false)
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
      .tapDefect ( defect =>
        ZIO.logError(s"Got a defect from inverse socket app: ${defect.dieOption.get}")
      )

  override def subscribe(sub: => SubArg): RIO[SocketEnv, Unit] =
    for
      _  <- getIsConnected.repeatUntilZIO(_.get)
      ch <- getChannel.flatMap(_.get)
      // Later inspect `None` case
      _  <- ch.get.writeAndFlush(
        WebSocketFrame.text(
          """
          |{
          |  "op": "subscribe",
          |  "args": ["instrument_info.100ms.ETHUSD"]
          |}""".stripMargin
        )
      )
      // Add a new subscription to the existing set of subscriptions. This change reflects in `WsState`.
      _ <- addSub(sub)
    yield ()

  override def unsubscribe(sub: => SubArg): RIO[SocketEnv, Unit] = ???

  private def checkReconnectTries(reconnectsNum: => Int, reconnectTries: => Int) =
    if reconnectsNum < reconnectTries then
      ZIO.die(new RuntimeException("Cumulative reconnection attempts've reached the maximum"))
    else
      ZIO.unit
