package cryptobot.exchange.bybit.ws

import zio.{ ZIOAppDefault, UIO, ZIO, ExitCode, Console, Ref }
import zhttp.service.{ EventLoopGroup, ChannelFactory }

import cryptobot.config.{ Config, WsConfig }
import cryptobot.exchange.bybit.MarketType

object Boot extends ZIOAppDefault:

  // When `inverseWsApp` is dead or interrupted we still need to press ENTER to stop the application.
  override def run: UIO[ExitCode] =
    ZIO.scoped(
      for
        _              <- Console.printLine(s"Starting the application").orDie
        inverseWsApp   <- ZIO.service[InverseWsApp]
        reconnectsNumR <- Ref.make(0)
        _              <- inverseWsApp.connect(reconnectsNumR).forkScoped
        _              <-
          Console.readLine("Press ENTER to stop the application\n").orDie *>
            Console.printLine("Stopping the application...")
      yield ()
    )
    .provide(
      // Types of EventLoopGroup from netty to investigate
      EventLoopGroup.nio(nThreads = 4),
      ChannelFactory.nio,
      Config.ws,
      WsApp.inverse
    )
    .exitCode
