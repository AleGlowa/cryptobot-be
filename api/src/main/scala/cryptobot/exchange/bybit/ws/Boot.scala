package cryptobot.exchange.bybit.ws

import zio.{ ZIOAppDefault, UIO, ZIO, ExitCode, Console }
import zhttp.service.{ EventLoopGroup, ChannelFactory }
import zio.durationInt

import cryptobot.config.Config
import cryptobot.exchange.bybit.ws.models.{ SubArg, Topic }
import cryptobot.exchange.bybit.ws.WsApp.Conn

object Boot extends ZIOAppDefault:

  // When `inverseWsApp` is dead or interrupted we still need to press ENTER to stop the application.
  override def run: UIO[ExitCode] =
    ZIO.scoped(
      for
        _    <- Console.printLine(s"Starting the application").orDie
        app   = new InverseWsApp
        given Conn <- app.connect()
        // Subscribe `instrument_info` topic to get the latest price for ETH/USD pair
        _ <- app.subscribe(SubArg(Topic.InstrumentInfo, Set("ETHUSD")))
        _ <- app.disconnect().delay(5.seconds)
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
