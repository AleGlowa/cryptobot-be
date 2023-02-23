package cryptobot.exchange.bybit.ws

import zio.json.ast.Json
import java.time.Instant

import cryptobot.exchange.bybit.{ Currency, currPairs }
import cryptobot.exchange.bybit.ws.model.{ RespType, SubRespType }
import cryptobot.exchange.bybit.ws.model.RespType.*
import cryptobot.exchange.bybit.ws.model.SubRespType.*
import cryptobot.exchange.bybit.ws.Cursors.*
import cryptobot.exchange.bybit.ws.Cursors
import cryptobot.exchange.bybit.ws.response.LastPriceResp

object RespDiscriminator:

  val instrumentalInfoRegex = raw"instrument_info.100ms.([A-Z]+)".r

  def getRespType(json: Json): Either[String, RespType] =
    for
      raw      <- json.get(opCursor) orElse json.get(topicCursor)
      respType  =
        raw.value match
          case "ping"                      => Pong
          case "unsubscribe"               => SuccessfulUnsub
          case "subscribe"                 => SuccessfulSub
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
  ): Either[String, LastPriceResp] =
    for
      value <- json.get(lastPriceCursor).map(_.value)
      time  <- json.get(updatedAtCursor).map(_.value)
    yield LastPriceResp(curr1, curr2, value, Instant.parse(time))

  def getSubRespType(json: Json): Either[String, SubRespType] =
    for
      raw <- json.get(typeCursor)
      respType =
        raw.value match
          case "snapshot" => Snapshot
          case "delta"    => Delta
    yield respType
