package cryptobot.config

import zio.{ ZIO, URIO, IO, ZLayer, Layer, Duration }
import zio.config.{ ReadError, Interpolator }
import zio.config.read
import zio.config.typesafe.TypesafeConfigSource
import zio.config.magnolia.descriptor

sealed trait Config
object Config:

  val ws: Layer[ReadError[String], WsConfig] =
    ZLayer(
      read(
        descriptor[WsConfig] from TypesafeConfigSource.fromResourcePath.at(path"api.bybit.ws")
      )
    )

end Config


final case class WsConfig(
  reconnectInterval: Duration,
  pingInterval     : Duration,
  reconnectTries   : Int
) extends Config

object WsConfig:

  val reconnectInterval: URIO[WsConfig, Duration] = ZIO.serviceWith(_.reconnectInterval)
  val pingInterval     : URIO[WsConfig, Duration] = ZIO.serviceWith(_.pingInterval)
  val reconnectTries   : URIO[WsConfig, Int]      = ZIO.serviceWith(_.reconnectTries)

end WsConfig
