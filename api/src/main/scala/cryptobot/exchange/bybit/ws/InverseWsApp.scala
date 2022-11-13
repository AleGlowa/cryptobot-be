package cryptobot.exchange.bybit.ws

import zio.{ ZIO, RIO, Console, Promise, Ref }
import zhttp.socket.SocketApp

import cryptobot.config.WsConnConfig
import cryptobot.exchange.bybit.MarketType
import cryptobot.exchange.bybit.ws.WsApp.SocketEnv

class InverseWsApp(config: WsConnConfig, msgLogic: SocketApp[Any]) extends WsApp:

  private def checkReconnectTries(reconnectsNum: Int, reconnectTries: Int) =
    if reconnectsNum >= reconnectTries then
      ZIO.die(new RuntimeException("Cumulative reconnection attempts've reached the maximum"))
    else
      ZIO.unit

  override def connect(reconnectsNumR: => Ref[Int]): RIO[SocketEnv, Unit] =
    ((for
      pConn         <- Promise.make[Nothing, Throwable]
      _             <-
        msgLogic
          .connect(MarketType.InverseWssPath)
          .tap(_ => reconnectsNumR.set(0))
          .catchAll(pConn.succeed)
      connFail      <- pConn.await
      _             <- ZIO.logError(s"Bybit ws connection for inverse market type failed: $connFail")
      _             <- ZIO.logError("Trying to reconnect...")
      _             <- ZIO.sleep(config.reconnectInterval)
      reconnectsNum <- reconnectsNumR.updateAndGet(_ + 1)
      _             <- checkReconnectTries(reconnectsNum, config.reconnectTries)
    yield ()) *> connect(reconnectsNumR))
      .tapDefect ( defect =>
        Console.printLineError(s"Got a defect from inverse socket app: ${defect.dieOption.get}")
      )

object InverseWsApp:

  val start =
    ZIO.serviceWithZIO[InverseWsApp] ( app =>
      for
        reconnectsNumR <- Ref.make(0)
        _              <- app.connect(reconnectsNumR)
      yield ()
    ).forkScoped
