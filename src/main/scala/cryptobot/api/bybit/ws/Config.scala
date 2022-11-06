package cryptobot.api.bybit.ws

import zio.{ ZIO, URIO, ZLayer, Layer, Duration }
import zio.config.read
import zio.config.{ ReadError, Interpolator }
import zio.config.typesafe.TypesafeConfigSource
import zio.config.magnolia.descriptor

final case class Config(
  reconnectInterval: Duration,
  pingInterval     : Duration
)

object Config:
  val layer: Layer[ReadError[String], Config] =
    ZLayer(
      read(
        descriptor[Config] from TypesafeConfigSource.fromResourcePath.at(path"api.bybit.ws")
      )
    )

  val reconnectInterval: URIO[Config, Duration] = ZIO.serviceWith(_.reconnectInterval)
  val pingInterval:      URIO[Config, Duration] = ZIO.serviceWith(_.pingInterval)
