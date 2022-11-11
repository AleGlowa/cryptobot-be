package cryptobot.exchange.bybit.ws

import zio.{ ZIO, URIO, IO, ZLayer, Layer, Duration }
import zio.config.read
import zio.config.{ ReadError, Interpolator }
import zio.config.typesafe.TypesafeConfigSource
import zio.config.magnolia.descriptor

final case class Config(
  reconnectInterval: Duration,
  pingInterval     : Duration,
  reconnectTries   : Int
)

object Config:
  val layer: Layer[ReadError[String], Config] =
    ZLayer(
      read(
        descriptor[Config] from TypesafeConfigSource.fromResourcePath.at(path"bybit.ws")
      )
    )

  val reconnectInterval: URIO[Config, Duration] = ZIO.serviceWith(_.reconnectInterval)
  val pingInterval     : URIO[Config, Duration] = ZIO.serviceWith(_.pingInterval)
  val reconnectTries   : URIO[Config, Int]      = ZIO.serviceWith(_.reconnectTries)

  val getConfig: IO[ReadError[String], Config] =
    (for
      reconnectInterval <- Config.reconnectInterval
      pingInterval      <- Config.pingInterval
      reconnectTries    <- Config.reconnectTries
    yield Config(reconnectInterval, pingInterval, reconnectTries))
      .provideLayer(Config.layer)
