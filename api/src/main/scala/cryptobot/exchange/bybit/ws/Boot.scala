package cryptobot.exchange.bybit.ws

import zio.*
import zhttp.service.{ EventLoopGroup, ChannelFactory }

import cryptobot.config.Config
import cryptobot.exchange.bybit.Currency.*
import cryptobot.exchange.bybit.ws.WsApp.Conn
import cryptobot.exchange.bybit.ws.models.Topic.InstrumentInfo

object Boot extends ZIOAppDefault:

  // When `inverseWsApp` is dead or interrupted we still need to press ENTER to stop the application.
  override def run: UIO[ExitCode] =
    ZIO.scoped(
      for
        _    <- Console.printLine(s"Starting the application").orDie
        app   = new InverseWsApp
        given Conn <- app.connect()
        // Wait ~2 seconds to establish connection in another fork.
        // Get the latest price for BTC/USD pair
        lastPrice <- app.getLastPrice(BTC, USD).delay(2.seconds)
        // Listen `lastPrice` stream for 5 seconds
        _ <- lastPrice.interruptAfter(5.seconds).foreach(p => ZIO.logInfo(p))
        _ <- app.disconnect()
        _ <- Console.readLine("Press ENTER to stop the application\n")
        _ <- Console.printLine("Stopping the application...")
      yield ()
    )
    .provide(
      // Types of EventLoopGroup from netty to investigate
      EventLoopGroup.nio(nThreads = 4),
      ChannelFactory.nio,
      Config.ws,
      WsApp.initialState
    )
    .exitCode
