package cryptobot.exchange.bybit.ws

import zio.json.ast.Json

import cryptobot.exchange.bybit.{ Currency, currPairs }
import cryptobot.exchange.bybit.ws.models.{ RespType, SubRespType }
import cryptobot.exchange.bybit.ws.models.RespType.*
import cryptobot.exchange.bybit.ws.models.SubRespType.*
import cryptobot.exchange.bybit.ws.models.Cursors.*

object RespDiscriminator:

  val instrumentalInfoRegex = raw"instrument_info.100ms.([A-Z]+)".r

  def getRespType(json: Json): Either[String, RespType] =
    for
      raw      <- json.get(retMsgCursor) orElse json.get(topicCursor)
      respType  =
        raw.value match
          case "pong"                      => Pong
          case ""                          => SuccessfulSub
          case instrumentalInfoRegex(pair) =>
            val dPair = currPairs(pair)
            InstrumentInfoResp(dPair(0), dPair(1))
          case err                         => UnsuccessfulSub(err)
    yield respType

  def getPongResp(json: Json): Either[String, Boolean] =
    json.get(successCursor).map(_.value)

  def getLastPriceResp(
    json : Json,
    curr1: Currency,
    curr2: Currency
  ): Either[String, String] =
    for
      value <- json.get(lastPriceCursor).map(_.value)
      time  <- json.get(updatedAtCursor).map(_.value)
    yield s"""{curr1: "$curr1", curr2: "$curr2", lastPrice: "$value", time: "$time"}"""

  def getSubRespType(json: Json): Either[String, SubRespType] =
    for
      raw <- json.get(typeCursor)
      respType =
        raw.value match
          case "snapshot" => Snapshot
          case "delta"    => Delta
    yield respType
