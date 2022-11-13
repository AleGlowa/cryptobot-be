package cryptobot.exchange.bybit.ws

import zio.{ ZIOAppDefault, UIO, ZIO, ExitCode, Console }
import zhttp.service.{ EventLoopGroup, ChannelFactory }

import cryptobot.config.Config

object Boot extends ZIOAppDefault:

  // When `inverseWsApp` is dead or interrupted we still need to press ENTER to stop the application.
  override def run: UIO[ExitCode] =
    ZIO.scoped(
      for
        _              <- Console.printLine(s"Starting the application").orDie
        _              <- InverseWsApp.start
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
