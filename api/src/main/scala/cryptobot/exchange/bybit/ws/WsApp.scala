package cryptobot.exchange.bybit.ws

import zio.*
import zhttp.socket.SocketApp
import zhttp.service.{ EventLoopGroup, ChannelFactory }

import cryptobot.config.WsConfig

trait WsApp:
  import WsApp.SocketEnv

  protected val getConfig: URIO[WsConfig, WsConfig] =
    for
      reconnectInterval <- WsConfig.reconnectInterval
      pingInterval      <- WsConfig.pingInterval
      reconnectTries    <- WsConfig.reconnectTries
    yield WsConfig(reconnectInterval, pingInterval, reconnectTries)

  protected def msgLogic: SocketApp[WsConfig]

  def connect(): RIO[SocketEnv, Unit]

end WsApp


object WsApp:

  type SocketEnv = EventLoopGroup & (ChannelFactory & Scope) & WsConfig

  val inverse: ULayer[InverseWsApp] = ZLayer(ZIO.succeed(new InverseWsApp))

end WsApp
