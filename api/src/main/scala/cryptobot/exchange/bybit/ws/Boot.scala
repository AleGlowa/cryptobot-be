package cryptobot.exchange.bybit.ws

import zio.*
import zhttp.http.*
import zhttp.socket.*
import zhttp.service.*
import zhttp.service.ChannelEvent.*

import cryptobot.config.Config
import cryptobot.exchange.bybit.Currency.*
import cryptobot.exchange.bybit.ws.WsApp.Conn
import cryptobot.exchange.bybit.ws.models.Topic.InstrumentInfo

object Boot extends ZIOAppDefault:

  val wsApp = new InverseWsApp

  // WsApp
  val channelSocket =
    Http.collectZIO[WebSocketChannelEvent] {
      case ChannelEvent(ch, UserEventTriggered(UserEvent.HandshakeComplete))  =>
        for
          _ <- wsApp.connect()
          // Wait ~1 second to establish connection with Bybit API in other fiber
          _ <- ZIO.sleep(1.second)
          _ <- ch.writeAndFlush(WebSocketFrame.text("Connected"))
        yield ()

      // Accept only text messages from frontend. Maybe later make messages require parsable to json
      case ChannelEvent(ch, ChannelRead(WebSocketFrame.Text(msg)))            =>
        msg match
          case "end\n" => wsApp.disconnect() *> ch.writeAndFlush(WebSocketFrame.text("Disconnected"))
          case "sub.BTCUSD\n" =>
            for
              stream <- wsApp.getLastPrice(BTC, USD)
              _      <- stream.runForeach(price => ch.writeAndFlush(WebSocketFrame.text(price)))
            yield ()
          case "sub.ETHUSD\n" =>
            for
              stream <- wsApp.getLastPrice(ETH, USD)
              _      <- stream.runForeach(price => ch.writeAndFlush(WebSocketFrame.text(price)))
            yield ()
          case unknown => ch.writeAndFlush(WebSocketFrame.text(s"Unknown subscription: $unknown"))

      case ChannelEvent(_, ExceptionCaught(cause))                            =>
        Console.printLine(s"Channel ERROR: ${cause.getMessage}")
    }
  val wsAppSocket =
    channelSocket.toSocketApp
      .withDecoder(SocketDecoder.default.withExtensions(allowed = true))
      .withProtocol(SocketProtocol.default.withSubProtocol(Some("json")))
  // WsApp

  val app =
    Http.collectZIO[Request] {
      case Method.GET -> !! / "inverseWs" / "subscriptions" => wsAppSocket.toResponse
    }

  override val run =
    ZIO.scoped(
      for
        _ <- Console.printLine(s"Starting the server at http://localhost:8080").orDie
        _ <- Server.start(8080, app).forkScoped
        _ <- Console.readLine("Press ENTER to stop the application\n").delay(1.second).orDie
        _ <- Console.printLine("Stopping the application...").orDie
      yield ()
    )
    .provide(
      // Types of EventLoopGroup from netty to investigate
      EventLoopGroup.auto(nThreads = 4),
      ChannelFactory.auto,
      Config.ws,
      WsApp.initialState
    )
    .exitCode
